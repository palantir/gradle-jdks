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

import com.palantir.gradle.jdks.CommandRunner;
import com.palantir.gradle.jdks.CurrentOs;
import com.palantir.gradle.jdks.Os;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class CaResources {

    private static final BigInteger PALANTIR_3RD_GEN_SERIAL = new BigInteger("18126334688741185161");
    private static final String PALANTIR_3RD_GEN_CERTIFICATE = "Palantir3rdGenRootCa";

    private final ILogger logger;

    public CaResources(ILogger logger) {
        this.logger = logger;
    }

    public Optional<AliasContentCert> readPalantirRootCaFromSystemTruststore() {
        return systemCertificates().flatMap(CaResources::selectPalantirCertificate);
    }

    public void maybeImportCertsInJdk(Path jdkInstallationDirectory, Map<String, String> certSerialNumbersToAliases) {
        if (certSerialNumbersToAliases.isEmpty()) {
            logger.log("No certificates were provided to import, skipping...");
            return;
        }
        systemCertificates()
                .map(certs -> selectCertificates(certs, certSerialNumbersToAliases))
                .orElseGet(Stream::of)
                .forEach(cert -> importCertInJdk(cert, jdkInstallationDirectory));
    }

    private void importCertInJdk(AliasContentCert aliasContentCert, Path jdkInstallationDirectory) {
        Os os = CurrentOs.get();
        switch (os) {
            case MACOS:
            case LINUX_GLIBC:
            case LINUX_MUSL:
                unixImportCertInJdk(aliasContentCert, jdkInstallationDirectory);
                logger.log(String.format(
                        "Successfully imported CA certificate %s into the JDK truststore",
                        aliasContentCert.getAlias()));
                break;
            case WINDOWS:
                logger.logError(
                        String.format("Importing certificates for OS type '%s' is not yet supported", os.uiName()));
                break;
        }
    }

    private void unixImportCertInJdk(AliasContentCert aliasContentCert, Path jdkInstallationDirectory) {
        try {
            if (isCertificateInTruststore(jdkInstallationDirectory, aliasContentCert.getAlias())) {
                logger.log("Certificate already exists in the truststore, skipping...");
                return;
            }
            File palantirCertFile = File.createTempFile(aliasContentCert.getAlias(), ".pem");
            Files.write(palantirCertFile.toPath(), aliasContentCert.getContent().getBytes(StandardCharsets.UTF_8));
            String keytoolPath = jdkInstallationDirectory
                    .resolve("bin/keytool")
                    .toAbsolutePath()
                    .toString();
            List<String> importCertificateCommand = List.of(
                    keytoolPath,
                    "-import",
                    "-trustcacerts",
                    "-alias",
                    aliasContentCert.getAlias(),
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

    private static boolean isCertificateInTruststore(Path jdkInstallationDirectory, String alias) {
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

    private Optional<byte[]> systemCertificates() {
        Os os = CurrentOs.get();
        switch (os) {
            case MACOS:
                return Optional.of(macosSystemCertificates());
            case LINUX_MUSL:
            case LINUX_GLIBC:
                return linuxSystemCertificates();
            case WINDOWS:
                logger.logError(String.format(
                        "Not attempting to read Palantir CA from system truststore "
                                + "as OS type '%s' does not yet support this",
                        os.uiName()));
                return Optional.empty();
        }
        throw new IllegalStateException("Unreachable code; all Os enum values should be handled");
    }

    private static byte[] macosSystemCertificates() {
        return Stream.of(
                        "/System/Library/Keychains/SystemRootCertificates.keychain",
                        "/Library/Keychains/System.keychain")
                .map(Paths::get)
                .filter(Files::exists)
                .map(CaResources::macosSystemCertificates)
                .collect(Collectors.joining("\n"))
                .getBytes(StandardCharsets.UTF_8);
    }

    private static String macosSystemCertificates(Path keyChainPath) {
        return CommandRunner.run(List.of(
                "security",
                "export",
                "-t",
                "certs",
                "-f",
                "pemseq",
                "-k",
                keyChainPath.toAbsolutePath().toString()));
    }

    private Optional<byte[]> linuxSystemCertificates() {
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

    private static Optional<AliasContentCert> selectPalantirCertificate(byte[] multipleCertificateBytes) {
        return selectCertificates(
                        multipleCertificateBytes,
                        Map.of(PALANTIR_3RD_GEN_SERIAL.toString(), PALANTIR_3RD_GEN_CERTIFICATE))
                .findFirst();
    }

    private static Stream<AliasContentCert> selectCertificates(
            byte[] multipleCertificateBytes, Map<String, String> certSerialNumbersToAliases) {
        return parseCerts(multipleCertificateBytes).stream()
                .filter(cert -> certSerialNumbersToAliases.containsKey(
                        ((X509Certificate) cert).getSerialNumber().toString()))
                .map(cert -> new AliasContentCert(
                        certSerialNumbersToAliases.get(
                                ((X509Certificate) cert).getSerialNumber().toString()),
                        encodeCertificate(cert)));
    }

    static List<Certificate> parseCerts(byte[] multipleCertificateBytes) {
        CertificateFactory certificateFactory;
        try {
            certificateFactory = CertificateFactory.getInstance("X.509");
        } catch (CertificateException e) {
            throw new RuntimeException("Could not make X.509 certificate factory", e);
        }

        List<Certificate> certs = new ArrayList<>();

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

    private static String encodeCertificate(Certificate palantirCert) {
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

    public static String getSerialNumber(String certContent) {
        try {
            InputStream in = new ByteArrayInputStream(certContent.getBytes(StandardCharsets.UTF_8));
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) certFactory.generateCertificate(in);
            return cert.getSerialNumber().toString();
        } catch (CertificateException e) {
            throw new RuntimeException(String.format("Could not get serial number for certificate %s", certContent), e);
        }
    }
}
