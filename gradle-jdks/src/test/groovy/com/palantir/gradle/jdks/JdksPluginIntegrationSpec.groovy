/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.gradle.jdks

import nebula.test.IntegrationSpec
import nebula.test.functional.ExecutionResult


class JdksPluginIntegrationSpec extends IntegrationSpec {
    def setup() {
        // language=gradle
        buildFile << '''
            apply plugin: 'com.palantir.jdks'
            
            jdks {                
                jdk(11) {
                    distribution = 'azul-zulu'
                    jdkVersion = '11.54.25-11.0.14.1'    
                }
            }
            
            javaVersions {
                libraryTarget = 11
            }
            
            apply plugin: 'java-library'
        '''.stripIndent(true)
    }

    def 'can download + run an Azul Zulu JDK'() {
        // language=gradle
        buildFile << '''            
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
                    System.out.println(String.format(
                            "version: %s, vendor: %s",
                            System.getProperty("java.version"),
                            System.getProperty("java.vendor")));
                }
            }
        '''.stripIndent(true)

        when:

        def stdout = runTasksSuccessfully(
                'printJavaVersion',
                // TODO: avoid resolving from root configuration somehow, removed in Gradle 8
                '--warning-mode=none')
                .standardOutput

        then:
        stdout.contains 'version: 11.0.14.1, vendor: Azul Systems, Inc.'
    }

    def 'can add ca certs to a JDK'() {
        def amazonRootCa1Serial = '143266978916655856878034712317230054538369994'
        file('amazon-root-ca-1.pem') << '''
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
        '''.stripIndent(true)


        // language=gradle
        buildFile << '''
            jdks {
                caCerts.put 'Our_Amazon_CA_Cert_1', file('amazon-root-ca-1.pem')
            }

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
            import java.security.KeyStore;import java.security.cert.X509Certificate;
            
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

        def stdout = runTasksSuccessfully(
                'printCaTruststoreAliases',
                // TODO: avoid resolving from root configuration somehow, removed in Gradle 8
                '--warning-mode=none')
                .standardOutput

        then:
        stdout.contains amazonRootCa1Serial
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
