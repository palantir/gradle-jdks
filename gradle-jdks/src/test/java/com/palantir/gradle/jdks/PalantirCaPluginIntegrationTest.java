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

import static com.palantir.gradle.testing.assertion.GradlePluginTestAssertions.assertThat;

import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.execution.InvocationResult;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import org.junit.jupiter.api.Test;

@GradlePluginTests
class PalantirCaPluginIntegrationTest {

    @Test
    void can_add_ca_certs_to_a_jdk(GradleInvoker gradle, RootProject rootProject) {
        // Can't do strict as open source CI does not have the Palantir CA
        rootProject.buildGradle().plugins().add("com.palantir.jdks.palantir-ca").add("java-library");

        rootProject.buildGradle().append("""
            jdks {
                jdk(11) {
                    distribution = 'azul-zulu'
                    jdkVersion = '11.54.25-11.0.14.1'
                }

                jdkStorageLocation = layout.buildDirectory.dir('jdks')
            }

            javaVersions {
                libraryTarget = 11
            }

            task printCaTruststoreAliases(type: JavaExec) {
                classpath = sourceSets.main.runtimeClasspath
                mainClass = 'foo.OutputCaCerts'
                logging.captureStandardOutput LogLevel.LIFECYCLE
                logging.captureStandardError LogLevel.LIFECYCLE
            }
            """);

        rootProject.mainSourceSet().java().writeClass("""
            package foo;

            import java.io.File;
            import java.security.KeyStore;
            import java.security.cert.X509Certificate;

            public final class OutputCaCerts {
                public static void main(String... args) throws Exception {
                    KeyStore keyStore = KeyStore.getInstance(
                            new File(System.getProperty("java.home"), "lib/security/cacerts"),
                            "changeit".toCharArray());
                    X509Certificate palantirCert = ((X509Certificate) keyStore.getCertificate("Palantir3rdGenRootCa"));

                    if (palantirCert != null) {
                        System.out.println(palantirCert.getSerialNumber());
                    }
                }
            }
            """);

        InvocationResult result = gradle.withArgs("printCaTruststoreAliases").buildsSuccessfully();

        String palantir3rdGenCaSerial = "18126334688741185161";

        // Open source CI does not have the Palantir CA
        if (System.getenv("CI") == null) {
            assertThat(result)
                    .output()
                    .as("Palantir 3rd gen CA serial number is present in the truststore")
                    .contains(palantir3rdGenCaSerial);
        }
    }
}
