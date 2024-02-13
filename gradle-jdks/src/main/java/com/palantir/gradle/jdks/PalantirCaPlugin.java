/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.jdks;

import com.google.common.annotations.VisibleForTesting;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.process.ExecResult;

public final class PalantirCaPlugin implements Plugin<Project> {
    private static final BigInteger PALANTIR_3RD_GEN_SERIAL = new BigInteger("18126334688741185161");

    private Project rootProject;
    private PalantirCaExtension extension;

    @Override
    public void apply(Project possibleRootProject) {
        if (possibleRootProject.getRootProject() != possibleRootProject) {
            throw new IllegalArgumentException(
                    "com.palantir.jdks.palantir-ca must be applied to the root project only");
        }

        rootProject = possibleRootProject;

        extension = rootProject.getExtensions().create("palantirCa", PalantirCaExtension.class);

        rootProject.getPluginManager().apply(JdksPlugin.class);

        rootProject
                .getExtensions()
                .getByType(JdksExtension.class)
                .getCaCerts()
                .putAll(possibleRootProject.provider(() -> readPalantirRootCaFromSystemTruststore()
                        .map(cert -> Map.of("Palantir3rdGenRootCa", cert))
                        .orElseGet(() -> {
                            log("Could not find Palantir CA in system truststore");
                            return Map.of();
                        })));
    }

    private Optional<String> readPalantirRootCaFromSystemTruststore() {
        return systemCertificates().flatMap(PalantirCaPlugin::selectPalantirCertificate);
    }

    private Optional<byte[]> systemCertificates() {
        Os currentOs = CurrentOs.current();

        switch (currentOs) {
            case MACOS:
                return Optional.of(macosSystemCertificates(rootProject));
            case LINUX_GLIBC:
            case LINUX_MUSL:
                return linuxSystemCertificates();
            default:
                log(
                        "Not attempting to read Palantir CA from system truststore "
                                + "as OS type '{}' does not yet support this",
                        currentOs);
                return Optional.empty();
        }
    }

    private static byte[] macosSystemCertificates(Project rootProject) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();

        List<String> extractCertificatesCommand = List.of(
                "security", "export", "-t", "certs", "-f", "pemseq", "-k", "/Library/Keychains/System.keychain");

        ExecResult execResult = rootProject.exec(exec -> {
            exec.commandLine(extractCertificatesCommand);
            exec.setStandardOutput(output);
            exec.setErrorOutput(error);
            exec.setIgnoreExitValue(true);
        });

        if (execResult.getExitValue() != 0) {
            throw new RuntimeException(String.format(
                    "Failed to extract macos System CA certificates using command '%s'. "
                            + "Failed with exit code %d.Error output:\n\n%s\n\nStandard Output:\n\n%s",
                    String.join(" ", extractCertificatesCommand),
                    execResult.getExitValue(),
                    error.toString(StandardCharsets.UTF_8),
                    output.toString(StandardCharsets.UTF_8)));
        }

        return output.toByteArray();
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
                    log(
                            "Could not find system truststore at any of {} in order to load Palantir CA cert",
                            possibleCaCertificatePaths);
                    return Optional.empty();
                });
    }

    private static Optional<String> selectPalantirCertificate(byte[] multipleCertificateBytes) {
        return parseCerts(multipleCertificateBytes).stream()
                .filter(cert -> PALANTIR_3RD_GEN_SERIAL.equals(((X509Certificate) cert).getSerialNumber()))
                .findFirst()
                .map(PalantirCaPlugin::encodeCertificate);
    }

    @VisibleForTesting
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

    private void log(String format, Object... args) {
        rootProject.getLogger().log(extension.getLogLevel().get(), format, args);
    }
}
