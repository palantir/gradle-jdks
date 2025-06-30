/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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

import com.palantir.gradle.jdks.setup.AliasContentCert;
import com.palantir.gradle.jdks.setup.CertificateUtils;
import com.palantir.gradle.jdks.setup.ILogger;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import javax.inject.Inject;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;

public abstract class GradleAwareCaResources {
    private static final BigInteger PALANTIR_3RD_GEN_SERIAL = new BigInteger("18126334688741185161");
    private static final String PALANTIR_3RD_GEN_CERTIFICATE = "Palantir3rdGenRootCa";

    private ILogger logger;
    private GradleAwareCertificateSource certificateSource;

    @Inject
    public GradleAwareCaResources(ILogger logger, GradleAwareCertificateSource certificateSource) {
        this.logger = logger;
        this.certificateSource = certificateSource;
    }

    @Inject
    protected abstract ProviderFactory getProviderFactory();

    /**
     * Provides the Palantir Root CA certificate from the system truststore for use in Gradle builds.
     * The {@link Optional} will be empty if the system does not support certificate retrieval (e.g., Windows)
     * or if the specific certificate is not found.
     */
    public Provider<Optional<AliasContentCert>> readPalantirRootCaFromSystemTruststore() {
        Optional<Provider<byte[]>> certProviderOpt = certificateSource.systemCertificates(logger);
        return certProviderOpt
                .map(provider -> provider.map(GradleAwareCaResources::selectPalantirCertificate))
                .orElseGet(() -> getProviderFactory().provider(Optional::empty));
    }

    private static Optional<AliasContentCert> selectPalantirCertificate(byte[] multipleCertificateBytes) {
        return selectCertificates(
                        multipleCertificateBytes,
                        Map.of(PALANTIR_3RD_GEN_SERIAL.toString(), PALANTIR_3RD_GEN_CERTIFICATE))
                .findFirst();
    }

    private static Stream<AliasContentCert> selectCertificates(
            byte[] multipleCertificateBytes, Map<String, String> certSerialNumbersToAliases) {
        return CertificateUtils.parseCerts(multipleCertificateBytes).stream()
                .filter(cert -> certSerialNumbersToAliases.containsKey(
                        cert.getSerialNumber().toString()))
                .map(cert -> new AliasContentCert(
                        certSerialNumbersToAliases.get(cert.getSerialNumber().toString()), encodeCertificate(cert)));
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
}
