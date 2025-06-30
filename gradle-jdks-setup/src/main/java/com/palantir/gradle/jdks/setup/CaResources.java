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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;

public final class CaResources {
    public static void importAllSystemCerts(Path jdkInstallationDirectory, ILogger logger) {
        CertificateSource.systemCertificates(logger)
                .ifPresent(certs ->
                        importCertificates(jdkInstallationDirectory, CertificateUtils.parseCerts(certs), logger));
    }

    private static void importCertificates(
            Path jdkInstallationDirectory, List<X509Certificate> certificates, ILogger logger) {
        try {
            char[] passwd = "changeit".toCharArray();
            Path jksPath = jdkInstallationDirectory.resolve("lib/security/cacerts");
            KeyStore jks = loadKeystore(passwd, jksPath, logger);
            Set<X509Certificate> existingCertificates = getExistingCertificates(jks);
            List<X509Certificate> newCertificates = certificates.stream()
                    .filter(CaResources::isValid)
                    .filter(CaResources::isCertUsedForTls)
                    .filter(certificate -> !existingCertificates.contains(certificate))
                    .collect(Collectors.toList());
            for (X509Certificate certificate : newCertificates) {
                String alias = getAlias(certificate, logger);
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
                    .filter(Optional::isPresent)
                    .map(Optional::get)
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

    public static String getAlias(X509Certificate certificate, ILogger logger) {
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

    private static KeyStore loadKeystore(char[] password, Path location, ILogger logger) {
        try (InputStream keystoreStream = new BufferedInputStream(Files.newInputStream(location))) {
            KeyStore keystore = KeyStore.getInstance("JKS");
            keystore.load(keystoreStream, password);
            return keystore;
        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException e) {
            logger.log(String.format("Couldn't load jks, an exception occurred %s", e));
            throw new RuntimeException(String.format("Couldn't load keystore %s", location), e);
        }
    }
}
