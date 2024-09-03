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

import com.palantir.gradle.jdks.setup.common.CommandRunner;
import com.palantir.gradle.jdks.setup.common.CurrentOs;
import com.palantir.gradle.jdks.setup.common.Os;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
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
import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;

public final class CaResources {

    private static final BigInteger PALANTIR_3RD_GEN_SERIAL = new BigInteger("18126334688741185161");
    private static final String PALANTIR_3RD_GEN_CERTIFICATE = "Palantir3rdGenRootCa";

    private final ILogger logger;

    public CaResources(ILogger logger) {
        this.logger = logger;
    }

    public Optional<AliasContentCert> readPalantirRootCaFromSystemTruststore() {
        return selectPalantirCertificate(systemCertificates());
    }

    public void importAllSystemCerts(Path jdkInstallationDirectory) {
        importCertificates(jdkInstallationDirectory, parseCerts(systemCertificates()));
    }

    private void importCertificates(Path jdkInstallationDirectory, List<Certificate> certificates) {
        try {
            char[] passwd = "changeit".toCharArray();
            Path jksPath = jdkInstallationDirectory.resolve("lib/security/cacerts");
            KeyStore jks =
                    loadKeystore(passwd, jksPath).orElseThrow(() -> new RuntimeException("Could not load keystore"));

            for (Certificate cert : certificates) {
                String alias = getAlias((X509Certificate) cert);
                logger.log(String.format("Certificate %s imported successfully into the KeyStore.", alias));
                jks.setCertificateEntry(alias, cert);
            }

            jks.store(new BufferedOutputStream(new FileOutputStream(jksPath.toFile())), passwd);

            logger.log("Certificates imported successfully into the KeyStore.");
        } catch (KeyStoreException | FileNotFoundException e) {
            throw new RuntimeException("Failed to import certificates", e);
        } catch (CertificateException | IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getAlias(X509Certificate certificate) {
        String distinguishedName = certificate.getSubjectX500Principal().getName();
        try {
            LdapName ldapName = new LdapName(distinguishedName);
            for (Rdn rdn : ldapName.getRdns()) {
                if ("CN".equalsIgnoreCase(rdn.getType())) {
                    return String.format("GradleJdks_%s", ((String) rdn.getValue()).replaceAll("\\s", ""));
                }
            }
            return String.format("GradleJdks_%s", distinguishedName);
        } catch (InvalidNameException e) {
            throw new RuntimeException(
                    String.format("Failed to read the ldapname for the cert %s", distinguishedName), e);
        }
    }

    private Optional<KeyStore> loadKeystore(char[] password, Path location) {
        try (InputStream keystoreStream = new BufferedInputStream(Files.newInputStream(location))) {
            KeyStore keystore = KeyStore.getInstance("JKS");
            keystore.load(keystoreStream, password);
            return Optional.of(keystore);
        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException e) {
            logger.log(String.format("Couldn't load jks, an exception occurred %s", e));
            return Optional.empty();
        }
    }

    private static byte[] systemCertificates() {
        Os os = CurrentOs.get();
        switch (os) {
            case MACOS:
                return macosSystemCertificates();
            case LINUX_MUSL:
            case LINUX_GLIBC:
                return linuxSystemCertificates();
            case WINDOWS:
                throw new RuntimeException("Windows is not supported");
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
        return CommandRunner.runWithOutputCollection(new ProcessBuilder()
                .command(
                        "security",
                        "export",
                        "-t",
                        "certs",
                        "-f",
                        "pemseq",
                        "-k",
                        keyChainPath.toAbsolutePath().toString()));
    }

    private static byte[] linuxSystemCertificates() {
        List<Path> possibleCaCertificatePaths = List.of(
                // Ubuntu/debian
                Paths.get("/etc/ssl/certs/ca-certificates.crt"),
                // Red hat/centos
                Paths.get("/etc/ssl/certs/ca-bundle.crt"));

        return possibleCaCertificatePaths.stream()
                .filter(Files::exists)
                .map(caCertificatePath -> {
                    try {
                        return Files.readString(caCertificatePath);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to read CA certs from " + caCertificatePath, e);
                    }
                })
                .collect(Collectors.joining("\n"))
                .getBytes(StandardCharsets.UTF_8);
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
