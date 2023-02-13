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

package com.palantir.gradle.jdks

import nebula.test.IntegrationSpec
import nebula.test.functional.ExecutionResult

class PalantirCaPluginIntegrationSpec extends IntegrationSpec {

    def 'can add ca certs to a JDK'() {
        // language=gradle
        buildFile << '''
            // Can't do strict as open source CI does not have the Palantir CA
            apply plugin: 'com.palantir.jdks.palantir-ca'
    
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
            
            apply plugin: 'java-library'

            task printCaTruststoreAliases(type: JavaExec) {
                classpath = sourceSets.main.runtimeClasspath
                mainClass = 'foo.OutputCaCerts'
                logging.captureStandardOutput LogLevel.LIFECYCLE
                logging.captureStandardError LogLevel.LIFECYCLE
            }
        '''.stripIndent(true)

        // language=java
        writeJavaSourceFile '''
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
        '''.stripIndent(true)

        when:

        def stdout = runTasksSuccessfully('printCaTruststoreAliases').standardOutput

        def palantir3rdGenCaSerial = '18126334688741185161'

        then:
        // Open source CI does not have the Palantir CA
        if (System.getenv("CI") == null) {
            assert stdout.contains(palantir3rdGenCaSerial)
        }
    }

    @Override
    ExecutionResult runTasksSuccessfully(String... tasks) {
        def result = super.runTasks(tasks)

        if (result.failure) {
            println result.standardOutput
            println result.standardError
            result.rethrowFailure()
        }

        return result
    }
}
