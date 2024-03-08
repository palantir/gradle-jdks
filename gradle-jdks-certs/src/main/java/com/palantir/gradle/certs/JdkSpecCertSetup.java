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

package com.palantir.gradle.certs;

import com.google.common.annotations.VisibleForTesting;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JdkSpecCertSetup {

    private static final BigInteger PALANTIR_3RD_GEN_SERIAL = new BigInteger("18126334688741185161");

    public static void main(String[] _args) {
        Optional<String> palantirCertificate =
                systemCertificates().flatMap(JdkSpecCertSetup::selectPalantirCertificate);
        palantirCertificate.ifPresent(JdkSpecCertSetup::importPalantirCertificate);
        System.out.flush();
        System.err.flush();
    }

    private static void importPalantirCertificate(String certContent) {
        String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (osName.startsWith("mac") || osName.startsWith("linux")) {
            importSystemCertificate(certContent);
            System.out.println("Successfully imported system certificate");
        } else {
            logError(String.format(
                    "Not attempting to read Palantir CA from system truststore "
                            + "as OS type '%s' does not yet support this",
                    osName));
        }
    }

    private static void importSystemCertificate(String certContent) {
        try {
            File palantirCertFile = File.createTempFile("Palantir3rdGenRootCa", ".cer");
            Files.write(palantirCertFile.toPath(), certContent.getBytes(StandardCharsets.UTF_8));
            String keytoolPath = System.getProperty("java.home") + "/bin/keytool";
            List<String> importCertificateCommand = List.of(
                    keytoolPath,
                    "-import",
                    "-trustcacerts",
                    "-alias",
                    "Palantir3rdGenRootCa",
                    "-cacerts",
                    "-storepass",
                    "changeit",
                    "-noprompt",
                    "-file",
                    palantirCertFile.getAbsolutePath());
            runCommand(importCertificateCommand);
        } catch (IOException e) {
            throw new RuntimeException("Unable to import the certificate to the jdk", e);
        }
    }

    private static Optional<byte[]> systemCertificates() {
        String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (osName.startsWith("mac")) {
            return Optional.of(macosSystemCertificates());
        } else if (osName.startsWith("linux")) {
            return linuxSystemCertificates();
        } else {
            logError(String.format(
                    "Not attempting to read Palantir CA from system truststore "
                            + "as OS type '%s' does not yet support this",
                    osName));
            return Optional.empty();
        }
    }

    private static byte[] macosSystemCertificates() {
        String output = runCommand(List.of(
                "security", "export", "-t", "certs", "-f", "pemseq", "-k", "/Library/Keychains/System.keychain"));
        return output.getBytes(StandardCharsets.UTF_8);
    }

    private static String readAllInput(InputStream inputStream) {
        try (Stream<String> lines =
                new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)).lines()) {
            return lines.collect(Collectors.joining("\n"));
        }
    }

    private static Optional<byte[]> linuxSystemCertificates() {
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
                    logError(String.format(
                            "Could not find system truststore at any of %s in order to load Palantir CA cert",
                            possibleCaCertificatePaths));
                    return Optional.empty();
                });
    }

    private static Optional<String> selectPalantirCertificate(byte[] multipleCertificateBytes) {
        return parseCerts(multipleCertificateBytes).stream()
                .filter(cert -> PALANTIR_3RD_GEN_SERIAL.equals(((X509Certificate) cert).getSerialNumber()))
                .findFirst()
                .map(JdkSpecCertSetup::encodeCertificate);
    }

    @VisibleForTesting
    static String runCommand(List<String> commandArguments) {
        try {
            Process process = new ProcessBuilder().command(commandArguments).start();
            String output = readAllInput(process.getInputStream());
            String errorOutput = readAllInput(process.getErrorStream());
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException(String.format(
                        "Failed to run command '%s'. "
                                + "Failed with exit code %d.Error output:\n\n%s\n\nStandard Output:\n\n%s",
                        String.join(" ", commandArguments), exitCode, errorOutput, output));
            }
            return output;
        } catch (IOException e) {
            throw new RuntimeException(
                    String.format("Failed to run command '%s'. ", String.join(" ", commandArguments)), e);
        } catch (InterruptedException e) {
            throw new RuntimeException(
                    String.format("Failed to run command '%s'. ", String.join(" ", commandArguments)), e);
        }
    }

    private static List<Certificate> parseCerts(byte[] multipleCertificateBytes) {
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

    private static void logError(String error) {
        System.err.println(error);
    }
}
