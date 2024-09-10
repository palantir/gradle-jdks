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
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

    public static Optional<AliasContentCert> readPalantirRootCaFromSystemTruststore() {
        return selectPalantirCertificate(systemCertificates());
    }

    public void importAllSystemCerts(Path jdkInstallationDirectory) {
        importCertificates(jdkInstallationDirectory, parseCerts(systemCertificates()));
    }

    private void importCertificates(Path jdkInstallationDirectory, List<X509Certificate> certificates) {
        try {
            char[] passwd = "changeit".toCharArray();
            Path jksPath = jdkInstallationDirectory.resolve("lib/security/cacerts");
            KeyStore jks = loadKeystore(passwd, jksPath);
            Set<X509Certificate> existingCertificates = getExistingCertificates(jks);
            List<X509Certificate> newCertificates = certificates.stream()
                    .filter(CaResources::isValid)
                    .filter(CaResources::isCertUsedForTls)
                    .filter(certificate -> !existingCertificates.contains(certificate))
                    .collect(Collectors.toList());
            for (X509Certificate certificate : newCertificates) {
                String alias = getAlias(certificate);
                logger.log(String.format(
                        "Certificate %s imported successfully into the JDK truststore from the system truststore.",
                        alias));
                jks.setCertificateEntry(alias, certificate);
            }
            jks.store(new BufferedOutputStream(new FileOutputStream(jksPath.toFile())), passwd);
        } catch (KeyStoreException | CertificateException | IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to import certificates", e);
        }
    }

    private static Set<X509Certificate> getExistingCertificates(KeyStore keyStore) {
        try {
            return Collections.list(keyStore.aliases()).stream()
                    .map(alias -> {
                        try {
                            return Optional.ofNullable(keyStore.getCertificate(alias));
                        } catch (KeyStoreException e) {
                            throw new RuntimeException("Failed to load keystore", e);
                        }
                    })
                    .flatMap(Optional::stream)
                    .filter(certificate -> X509Certificate.class.isAssignableFrom(certificate.getClass()))
                    .map(X509Certificate.class::cast)
                    .collect(Collectors.toSet());
        } catch (KeyStoreException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean isCertUsedForTls(X509Certificate certificate) {
        return hasCaCertUsage(certificate) && isTlsServerAuthentication(certificate);
    }

    private static boolean isValid(X509Certificate certificate) {
        try {
            certificate.checkValidity();
            return true;
        } catch (CertificateExpiredException | CertificateNotYetValidException e) {
            return false;
        }
    }

    private static boolean hasCaCertUsage(X509Certificate certificate) {
        boolean[] keyUsage = certificate.getKeyUsage();
        if (keyUsage == null) {
            return true;
        }
        // digitalSignature and keyEncipherment are enabled
        if (keyUsage[0] && keyUsage[2]) {
            return true;
        }
        // checks it is a CA certificate (keyCertSign=true and basicConstraints.cA == true):
        // https://datatracker.ietf.org/doc/html/rfc3280#section-4.2.1.10
        if (keyUsage[5] && certificate.getBasicConstraints() != -1) {
            return true;
        }
        return false;
    }

    private static boolean isTlsServerAuthentication(X509Certificate certificate) {
        try {
            List<String> extendedKeyUsages = certificate.getExtendedKeyUsage();
            if (extendedKeyUsages == null) {
                return true;
            }
            // https://oidref.com/1.3.6.1.5.5.7.3.1
            return extendedKeyUsages.contains("1.3.6.1.5.5.7.3.1");
        } catch (CertificateParsingException e) {
            throw new RuntimeException(e);
        }
    }

    public String getAlias(X509Certificate certificate) {
        String distinguishedName = certificate.getIssuerX500Principal().getName();
        String serialNumber = certificate.getSerialNumber().toString();
        try {
            LdapName ldapName = new LdapName(distinguishedName);
            for (Rdn rdn : ldapName.getRdns()) {
                if ("CN".equalsIgnoreCase(rdn.getType())) {
                    return String.format(
                            "GradleJdks_%s_%s", ((String) rdn.getValue()).replaceAll("\\s", ""), serialNumber);
                }
            }
        } catch (InvalidNameException e) {
            logger.logError(String.format("Failed to extract ldapName from %s", distinguishedName));
        }
        return String.format("GradleJdks_%s_%s", distinguishedName.replaceAll("\\s", ""), serialNumber);
    }

    private KeyStore loadKeystore(char[] password, Path location) {
        try (InputStream keystoreStream = new BufferedInputStream(Files.newInputStream(location))) {
            KeyStore keystore = KeyStore.getInstance("JKS");
            keystore.load(keystoreStream, password);
            return keystore;
        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException e) {
            logger.log(String.format("Couldn't load jks, an exception occurred %s", e));
            throw new RuntimeException(String.format("Couldn't load keystore %s", location), e);
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
                        cert.getSerialNumber().toString()))
                .map(cert -> new AliasContentCert(
                        certSerialNumbersToAliases.get(cert.getSerialNumber().toString()), encodeCertificate(cert)));
    }

    static List<X509Certificate> parseCerts(byte[] multipleCertificateBytes) {
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
