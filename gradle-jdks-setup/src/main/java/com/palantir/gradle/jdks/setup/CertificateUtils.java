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

package com.palantir.gradle.jdks.setup;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
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
import java.util.stream.Stream;

public final class CertificateUtils {
    private static final BigInteger PALANTIR_3RD_GEN_SERIAL = new BigInteger("18126334688741185161");
    private static final String PALANTIR_3RD_GEN_CERTIFICATE = "Palantir3rdGenRootCa";

    public static Optional<AliasContentCert> selectPalantirCertificate(byte[] multipleCertificateBytes) {
        return selectCertificates(
                        multipleCertificateBytes,
                        Map.of(PALANTIR_3RD_GEN_SERIAL.toString(), PALANTIR_3RD_GEN_CERTIFICATE))
                .findFirst();
    }

    private static Stream<AliasContentCert> selectCertificates(
            byte[] multipleCertificateBytes, Map<String, String> certSerialNumbersToAliases) {
        return parseCerts(multipleCertificateBytes).stream()
                .filter(cert -> certSerialNumbersToAliases.containsKey(
                        cert.getSerialNumber().toString()))
                .map(cert -> new AliasContentCert(
                        certSerialNumbersToAliases.get(cert.getSerialNumber().toString()), encodeCertificate(cert)));
    }

    public static List<X509Certificate> parseCerts(byte[] multipleCertificateBytes) {
        CertificateFactory certificateFactory;
        try {
            certificateFactory = CertificateFactory.getInstance("X.509");
        } catch (CertificateException e) {
            throw new RuntimeException("Could not make X.509 certificate factory", e);
        }

        List<X509Certificate> certs = new ArrayList<>();

        ByteArrayInputStream baos = new ByteArrayInputStream(multipleCertificateBytes);

        for (int i = 0; baos.available() != 0; i++) {
            try {
                certs.add((X509Certificate) certificateFactory.generateCertificate(baos));
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
}
