/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.gradle.jdks.setup;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class CaResources {

    private static final BigInteger PALANTIR_3RD_GEN_SERIAL = new BigInteger("18126334688741185161");
    private static final String PALANTIR_3RD_GEN_CERTIFICATE = "Palantir3rdGenRootCa";

    public static Optional<Certificate> readPalantirRootCaFromSystemTruststore(ILogger logger) {
        return systemCertificates(logger).flatMap(CaResources::selectPalantirCertificate);
    }

    public static void maybeImportCertsInJdk(
            ILogger logger, Path jdkInstallationDirectory, Map<String, String> certSerialNumbersToAliases) {
        if (certSerialNumbersToAliases.isEmpty()) {
            logger.log("No certificates were provided to import, skipping...");
            return;
        }
        List<Certificate> selectedCertificates = systemCertificates(logger)
                .map(certs ->
                        selectCertificates(certs, certSerialNumbersToAliases).collect(Collectors.toList()))
                .orElseGet(List::of);
        selectedCertificates.forEach(cert -> importCertInJdk(logger, cert, jdkInstallationDirectory));
        if (selectedCertificates.size() != certSerialNumbersToAliases.size()) {
            logMissingCertificates(
                    logger,
                    selectedCertificates,
                    certSerialNumbersToAliases.values().stream().collect(Collectors.toSet()));
        }
    }

    private static void logMissingCertificates(
            ILogger logger, List<Certificate> selectedCerts, Collection<String> expectedCerts) {
        Set<String> selectedCertAliases =
                selectedCerts.stream().map(Certificate::getAlias).collect(Collectors.toSet());
        Set<String> missingCertAliases = expectedCerts.stream()
                .filter(alias -> !selectedCertAliases.contains(alias))
                .collect(Collectors.toSet());
        logger.logError(String.format(
                "Certificates '%s' could not be found in the system keystore. These certificates were not imported.",
                String.join(", ", missingCertAliases)));
    }

    private static void importCertInJdk(ILogger logger, Certificate certificate, Path jdkInstallationDirectory) {
        String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (osName.startsWith("mac") || osName.startsWith("linux")) {
            unixImportCertInJdk(logger, certificate, jdkInstallationDirectory);
            logger.log(String.format(
                    "Successfully imported CA certificate %s into the JDK truststore", certificate.getAlias()));
        } else {
            logger.logError(String.format("Importing certificates for OS type '%s' is not yet supported", osName));
        }
    }

    private static void unixImportCertInJdk(ILogger logger, Certificate certificate, Path jdkInstallationDirectory) {
        try {
            if (certificateAlreadyExistsInTruststore(jdkInstallationDirectory, certificate.getAlias())) {
                logger.log("Certificate already exists in the truststore, skipping...");
                return;
            }
            File palantirCertFile = File.createTempFile(certificate.getAlias(), ".pem");
            Files.write(palantirCertFile.toPath(), certificate.getContent().getBytes(StandardCharsets.UTF_8));
            String keytoolPath = jdkInstallationDirectory
                    .resolve("bin/keytool")
                    .toAbsolutePath()
                    .toString();
            List<String> importCertificateCommand = List.of(
                    keytoolPath,
                    "-import",
                    "-trustcacerts",
                    "-alias",
                    certificate.getAlias(),
                    "-cacerts",
                    "-storepass",
                    "changeit",
                    "-noprompt",
                    "-file",
                    palantirCertFile.getAbsolutePath());
            CommandRunner.run(importCertificateCommand);
        } catch (IOException e) {
            throw new RuntimeException("Unable to import the certificate to the jdk", e);
        }
    }

    private static boolean certificateAlreadyExistsInTruststore(Path jdkInstallationDirectory, String alias) {
        try {
            String keytoolPath = jdkInstallationDirectory
                    .resolve("bin/keytool")
                    .toAbsolutePath()
                    .toString();
            List<String> checkCertificateCommand = List.of(
                    keytoolPath,
                    "-list",
                    "-storepass",
                    "changeit",
                    "-alias",
                    alias,
                    "-keystore",
                    jdkInstallationDirectory.resolve("lib/security/cacerts").toString());
            CommandRunner.run(checkCertificateCommand);
            return true;
        } catch (Exception e) {
            if (e.getMessage().contains(String.format("Alias <%s> does not exist", alias))) {
                return false;
            }
            throw new RuntimeException("Unable to check if the certificate already exists in the truststore", e);
        }
    }

    private static Optional<byte[]> systemCertificates(ILogger logger) {
        String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (osName.startsWith("mac")) {
            return Optional.of(macosSystemCertificates());
        } else if (osName.startsWith("linux")) {
            return linuxSystemCertificates(logger);
        } else {
            logger.logError(String.format(
                    "Not attempting to read Palantir CA from system truststore "
                            + "as OS type '%s' does not yet support this",
                    osName));
            return Optional.empty();
        }
    }

    private static byte[] macosSystemCertificates() {
        String output = CommandRunner.run(List.of(
                "security", "export", "-t", "certs", "-f", "pemseq", "-k", "/Library/Keychains/System.keychain"));
        return output.getBytes(StandardCharsets.UTF_8);
    }

    private static Optional<byte[]> linuxSystemCertificates(ILogger logger) {
        List<Path> possibleCaCertificatePaths = List.of(
                // Ubuntu/debian
                Paths.get("/etc/ssl/certs/ca-certificates.crt"),
                // Red hat/centos
                Paths.get("/etc/ssl/certs/ca-bundle.crt"));

        return possibleCaCertificatePaths.stream()
                .filter(Files::exists)
                .findFirst()
                .map(caCertificatePath -> {
                    try {
                        return Files.readAllBytes(caCertificatePath);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to read CA certs from " + caCertificatePath, e);
                    }
                })
                .or(() -> {
                    logger.logError(String.format(
                            "Could not find system truststore at any of %s in order to load CA certs",
                            possibleCaCertificatePaths));
                    return Optional.empty();
                });
    }

    private static Optional<Certificate> selectPalantirCertificate(byte[] multipleCertificateBytes) {
        return selectCertificates(
                        multipleCertificateBytes,
                        Map.of(PALANTIR_3RD_GEN_SERIAL.toString(), PALANTIR_3RD_GEN_CERTIFICATE))
                .findFirst();
    }

    private static Stream<Certificate> selectCertificates(
            byte[] multipleCertificateBytes, Map<String, String> certSerialNumbersToAliases) {
        return parseCerts(multipleCertificateBytes).stream()
                .filter(cert -> certSerialNumbersToAliases.containsKey(
                        ((X509Certificate) cert).getSerialNumber().toString()))
                .map(cert -> new Certificate(
                        certSerialNumbersToAliases.get(
                                ((X509Certificate) cert).getSerialNumber().toString()),
                        encodeCertificate(cert)));
    }

    static List<java.security.cert.Certificate> parseCerts(byte[] multipleCertificateBytes) {
        CertificateFactory certificateFactory;
        try {
            certificateFactory = CertificateFactory.getInstance("X.509");
        } catch (CertificateException e) {
            throw new RuntimeException("Could not make X.509 certificate factory", e);
        }

        List<java.security.cert.Certificate> certs = new ArrayList<>();

        ByteArrayInputStream baos = new ByteArrayInputStream(multipleCertificateBytes);

        for (int i = 0; baos.available() != 0; i++) {
            try {
                certs.add(certificateFactory.generateCertificate(baos));
            } catch (CertificateException e) {
                if (e.getMessage().contains("Duplicate extensions not allowed")) {
                    continue;
                }

                if (e.getMessage().contains("no more data allowed for version 1 certificate")) {
                    continue;
                }

                if (e.getMessage().contains("Empty input")) {
                    break;
                }

                throw new RuntimeException("Failed to parse cert " + i, e);
            }
        }

        return Collections.unmodifiableList(certs);
    }

    private static String encodeCertificate(java.security.cert.Certificate palantirCert) {
        Base64.Encoder encoder = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8));
        try {
            return String.join(
                    "\n",
                    "-----BEGIN CERTIFICATE-----",
                    encoder.encodeToString(palantirCert.getEncoded()),
                    "-----END CERTIFICATE-----");
        } catch (CertificateEncodingException e) {
            throw new RuntimeException("Could not convert Palantir cert back to regular", e);
        }
    }

    private CaResources() {}
}
