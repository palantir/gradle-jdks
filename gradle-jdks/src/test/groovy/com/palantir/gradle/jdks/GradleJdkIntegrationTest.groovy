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

package com.palantir.gradle.jdks

import com.palantir.gradle.jdks.setup.CaResources
import com.palantir.gradle.jdks.setup.StdLogger
import nebula.test.IntegrationSpec

import java.nio.file.Path

abstract class GradleJdkIntegrationTest extends IntegrationSpec {

    static String GRADLE_7VERSION = "7.6.4"
    static String GRADLE_8VERSION = "8.5"

    abstract Path workingDir();

    def setupJdksHardcodedVersions() {
        // language=groovy
        buildFile << """
            buildscript {
                repositories {
                    mavenCentral() { metadataSources { mavenPom(); ignoreGradleMetadataRedirection() } }
                    gradlePluginPortal() { metadataSources { mavenPom(); ignoreGradleMetadataRedirection() } }
                }
                // we need to inject the classpath of the plugin under test manually. The tests call the `./gradlew` 
                // command directly in the tests (so not using the nebula-test workflow).
                dependencies {
                    classpath files(FILES)
                }
            }
            
            apply plugin: 'java'
            apply plugin: 'com.palantir.jdks'
            apply plugin: 'com.palantir.baseline-java-versions'
            apply plugin: 'com.palantir.jdks.palantir-ca'
            
            jdks {
               jdk(11) {
                  distribution = 'azul-zulu'
                  jdkVersion = '11.54.25-11.0.14.1'
               }
               
               jdk(17) {
                  distribution = 'amazon-corretto'
                  jdkVersion = '17.0.3.6.1'
               }
               
               jdk(21) {
                  distribution = 'amazon-corretto'
                  jdkVersion = '21.0.2.13.1'
               }
               
               daemonTarget = '11'
            }
        """.replace("FILES", getPluginClasspathInjector().join(",")).stripIndent(true)
    }

    def setupJdksLatest() {
        // language=groovy
        buildFile << """
            buildscript {
                repositories {
                    mavenCentral() { metadataSources { mavenPom(); ignoreGradleMetadataRedirection() } }
                    gradlePluginPortal() { metadataSources { mavenPom(); ignoreGradleMetadataRedirection() } }
                }
                // we need to inject the classpath of the plugin under test manually. The tests call the `./gradlew` 
                // command directly in the tests (so not using the nebula-test workflow).
                dependencies {
                    classpath files(FILES)
                    classpath 'com.palantir.gradle.jdkslatest:gradle-jdks-latest:0.14.0'
                }
            }
            
            apply plugin: 'java'
            apply plugin: 'com.palantir.jdks'
            apply plugin: 'com.palantir.jdks.latest'
            apply plugin: 'com.palantir.jdks.palantir-ca'
            
            jdks {
               daemonTarget = '11'
            }
        """.replace("FILES", getPluginClasspathInjector().join(",")).stripIndent(true)
    }

    String upgradeGradleWrapper() {
        return runGradlewTasksSuccessfully("getGradleJavaHomeProp", "wrapper", "--gradle-version", "8.4", "-V", "--stacktrace")
    }

    String runGradlewTasksSuccessfully(String... tasks) {
        ProcessBuilder processBuilder = getProcessBuilder(tasks)
        Process process = processBuilder.start()
        String output = CommandRunner.readAllInput(process.getInputStream())
        assert output.contains("BUILD SUCCESSFUL")
        return output
    }

    ProcessBuilder getProcessBuilder(String... tasks) {
        List<String> arguments = ["./gradlew"]
        Arrays.asList(tasks).forEach(arguments::add)
        ProcessBuilder processBuilder = new ProcessBuilder()
                .command(arguments)
                .directory(projectDir).redirectErrorStream(true)
        processBuilder.environment().put("GRADLE_USER_HOME", workingDir().toAbsolutePath().toString())
        return processBuilder
    }


    Iterable<File> getPluginClasspathInjector() {
        File propertiesFile = new File("build/pluginUnderTestMetadata/plugin-under-test-metadata.properties")
        Properties properties = new Properties()
        propertiesFile.withInputStream { inputStream ->
            properties.load(inputStream)
        }
        String classpath = properties.getProperty('implementation-classpath')
        return classpath.split(File.pathSeparator).collect { "'" + it + "'" }
    }

    private static final int BYTECODE_IDENTIFIER = (int) 0xCAFEBABE

    // See http://illegalargumentexception.blogspot.com/2009/07/java-finding-class-versions.html
    static void assertBytecodeVersion(File file, int expectedMajorBytecodeVersion,
                                      int expectedMinorBytecodeVersion) {
        try (InputStream stream = new FileInputStream(file)
             DataInputStream dis = new DataInputStream(stream)) {
            int magic = dis.readInt()
            if (magic != BYTECODE_IDENTIFIER) {
                throw new IllegalArgumentException("File " + file + " does not appear to be java bytecode")
            }
            int minorBytecodeVersion = dis.readUnsignedShort()
            int majorBytecodeVersion = dis.readUnsignedShort()

            assert majorBytecodeVersion == expectedMajorBytecodeVersion
            assert minorBytecodeVersion == expectedMinorBytecodeVersion
        }
    }

    static String getHashForDistribution(JdkDistributionName jdkDistributionName, String jdkVersion) {
        Map<String, String> certs = new CaResources(new StdLogger()).readPalantirRootCaFromSystemTruststore()
                .map(cert -> Map.of(cert.getAlias(), cert.getContent())).orElseGet(Map::of)
        return JdkSpec.builder()
                .release(JdkRelease.builder()
                        .arch(CurrentArch.get())
                        .os(CurrentOs.get())
                        .version(jdkVersion)
                        .build())
                .distributionName(jdkDistributionName)
                .caCerts(CaCerts.from(certs))
                .build()
                .consistentShortHash()
    }
}
