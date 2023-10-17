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
import spock.lang.Unroll

@Unroll
class JdksPluginIntegrationSpec extends IntegrationSpec {
    private static final List<String> GRADLE_VERSIONS = List.of("7.6.2", "8.4")

    def setup() {
        // language=gradle
        buildFile << '''
            buildscript {
                repositories {
                    mavenCentral() { metadataSources { mavenPom(); ignoreGradleMetadataRedirection() } }
                }
            
                dependencies {
                    classpath 'com.palantir.baseline:gradle-baseline-java:5.28.0'
                }
            }

            apply plugin: 'com.palantir.jdks'
            
            jdks {                
                jdkStorageLocation = layout.buildDirectory.dir('jdks')
            }
            
            javaVersions {
                libraryTarget = 11
            }
        '''.stripIndent(true)

        // language=gradle
        def subprojectDir = addSubproject 'subproject', '''
            apply plugin: 'java-library'
            
            task printJavaVersion(type: JavaExec) {
                classpath = sourceSets.main.runtimeClasspath
                mainClass = 'foo.PrintJavaVersion'
                logging.captureStandardOutput LogLevel.LIFECYCLE
                logging.captureStandardError LogLevel.LIFECYCLE
            }
        '''.stripIndent(true)

        // language=java
        writeJavaSourceFile '''
            package foo;

            public final class PrintJavaVersion {
                public static void main(String... args) {
                    System.out.printf(
                            "version: %s, vendor: %s%n",
                            System.getProperty("java.version"),
                            System.getProperty("java.vendor"));
                }
            }
        '''.stripIndent(true), subprojectDir
    }

    def '#gradleVersionNumber: can download + run an Azul Zulu JDK'() {
        gradleVersion = gradleVersionNumber

        // language=gradle
        buildFile << '''
            jdks {                
                jdk(11) {
                    distribution = 'azul-zulu'
                    jdkVersion = '11.54.25-11.0.14.1'    
                }
            }
        '''.stripIndent(true)

        when:
        def stdout = runTasksSuccessfully('printJavaVersion').standardOutput

        then:
        stdout.contains 'version: 11.0.14.1, vendor: Azul Systems, Inc.'

        where:
        gradleVersionNumber << GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: can download + run an Amazon Corretto JDK'() {
        gradleVersion = gradleVersionNumber

        // language=gradle
        buildFile << '''
            jdks {                
                jdk(11) {
                    distribution = 'amazon-corretto'
                    jdkVersion = '11.0.16.9.1'    
                }
            }
        '''.stripIndent(true)

        when:
        def stdout = runTasksSuccessfully('printJavaVersion').standardOutput

        then:
        stdout.contains 'version: 11.0.16.1, vendor: Amazon.com Inc.'

        where:
        gradleVersionNumber << GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: can download + run on GraalVM Community Edition JDK'() {
        gradleVersion = gradleVersionNumber

        // language=gradle
        buildFile << '''
            jdks {                
                jdk(11) {
                    distribution = 'graalvm-ce'
                    jdkVersion = '11.22.3.0'    
                }
            }
        '''.stripIndent(true)

        when:
        def stdout = runTasksSuccessfully('printJavaVersion').standardOutput

        then:
        stdout.contains 'version: 11.0.17, vendor: GraalVM Community'

        where:
        gradleVersionNumber << GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: can add ca certs to a JDK'() {
        gradleVersion = gradleVersionNumber

        def amazonRootCa1Serial = '143266978916655856878034712317230054538369994'

        // language=gradle
        buildFile << '''
            jdks {
                jdk(11) {
                    distribution = 'azul-zulu'
                    jdkVersion = '11.54.25-11.0.14.1'    
                }
            
                caCerts.put 'Our_Amazon_CA_Cert_1', """
                    -----BEGIN CERTIFICATE-----
                    MIIDQTCCAimgAwIBAgITBmyfz5m/jAo54vB4ikPmljZbyjANBgkqhkiG9w0BAQsF
                    ADA5MQswCQYDVQQGEwJVUzEPMA0GA1UEChMGQW1hem9uMRkwFwYDVQQDExBBbWF6
                    b24gUm9vdCBDQSAxMB4XDTE1MDUyNjAwMDAwMFoXDTM4MDExNzAwMDAwMFowOTEL
                    MAkGA1UEBhMCVVMxDzANBgNVBAoTBkFtYXpvbjEZMBcGA1UEAxMQQW1hem9uIFJv
                    b3QgQ0EgMTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBALJ4gHHKeNXj
                    ca9HgFB0fW7Y14h29Jlo91ghYPl0hAEvrAIthtOgQ3pOsqTQNroBvo3bSMgHFzZM
                    9O6II8c+6zf1tRn4SWiw3te5djgdYZ6k/oI2peVKVuRF4fn9tBb6dNqcmzU5L/qw
                    IFAGbHrQgLKm+a/sRxmPUDgH3KKHOVj4utWp+UhnMJbulHheb4mjUcAwhmahRWa6
                    VOujw5H5SNz/0egwLX0tdHA114gk957EWW67c4cX8jJGKLhD+rcdqsq08p8kDi1L
                    93FcXmn/6pUCyziKrlA4b9v7LWIbxcceVOF34GfID5yHI9Y/QCB/IIDEgEw+OyQm
                    jgSubJrIqg0CAwEAAaNCMEAwDwYDVR0TAQH/BAUwAwEB/zAOBgNVHQ8BAf8EBAMC
                    AYYwHQYDVR0OBBYEFIQYzIU07LwMlJQuCFmcx7IQTgoIMA0GCSqGSIb3DQEBCwUA
                    A4IBAQCY8jdaQZChGsV2USggNiMOruYou6r4lK5IpDB/G/wkjUu0yKGX9rbxenDI
                    U5PMCCjjmCXPI6T53iHTfIUJrU6adTrCC2qJeHZERxhlbI1Bjjt/msv0tadQ1wUs
                    N+gDS63pYaACbvXy8MWy7Vu33PqUXHeeE6V/Uq2V8viTO96LXFvKWlJbYK8U90vv
                    o/ufQJVtMVT8QtPHRh8jrdkPSHCa2XV4cdFyQzR1bldZwgJcJmApzyMZFo6IQ6XU
                    5MsI+yMRQ+hDKXJioaldXgjUkK642M4UwtBV8ob2xJNDd2ZhwLnoQdeXeGADbkpy
                    rqXRfboQnoZsG4q5WTP468SQvvG5
                    -----END CERTIFICATE-----
                """.stripIndent(true)
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
                    System.out.println(
                            ((X509Certificate) keyStore.getCertificate("our_amazon_ca_cert_1")).getSerialNumber());
                }
            }
        '''.stripIndent(true)

        when:

        def stdout = runTasksSuccessfully('printCaTruststoreAliases').standardOutput

        then:
        stdout.contains amazonRootCa1Serial

        where:
        gradleVersionNumber << GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: throws exception if there is no JDK defined for a particular jdk major version'() {
        gradleVersion = gradleVersionNumber

        when:
        def error = runTasksWithFailure('printJavaVersion').failure.cause.cause.message

        then:
        error.contains "Could not find a JDK with major version 11 in project ':subproject'"

        where:
        gradleVersionNumber << GRADLE_VERSIONS
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
