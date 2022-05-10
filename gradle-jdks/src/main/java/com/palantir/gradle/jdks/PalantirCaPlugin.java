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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.process.ExecResult;

final class PalantirCaPlugin implements Plugin<Project> {
    private static final BigInteger PALANTIR_3RD_GEN_SERIAL = new BigInteger("18126334688741185161");

    @Override
    public void apply(Project rootProject) {
        if (rootProject.getRootProject() != rootProject) {
            throw new IllegalArgumentException(
                    "com.palantir.jdks.palantir-ca must be applied to the root project only");
        }

        rootProject.getPluginManager().apply(JdksPlugin.class);

        rootProject
                .getExtensions()
                .getByType(JdksExtension.class)
                .getCaCerts()
                .put(
                        "Palantir3rdGenRootCa",
                        rootProject.provider(() -> readPalantirRootCaFromSystemTruststore(rootProject)));
    }

    private String readPalantirRootCaFromSystemTruststore(Project rootProject) {
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

        try {
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            Collection<? extends Certificate> certificates =
                    certificateFactory.generateCertificates(new ByteArrayInputStream(output.toByteArray()));

            Certificate palantirCert = certificates.stream()
                    .filter(cert -> PALANTIR_3RD_GEN_SERIAL.equals(((X509Certificate) cert).getSerialNumber()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException(
                            "Could not find Palantir 3rd Gen Root CA from macos system truststore"));

            Base64.Encoder encoder = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8));
            return String.join(
                    "\n",
                    "-----BEGIN CERTIFICATE-----",
                    encoder.encodeToString(palantirCert.getEncoded()),
                    "-----END CERTIFICATE-----");
        } catch (CertificateException e) {
            throw new RuntimeException("Failed to read certificate", e);
        }
    }
}
