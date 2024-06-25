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


import nebula.test.IntegrationSpec
import org.apache.commons.lang3.tuple.Pair

import java.nio.file.Path

abstract class GradleJdkIntegrationTest extends IntegrationSpec {

    static String GRADLE_7_6_4_VERSION = "7.6.4"
    static String GRADLE_8_5_VERSION = "8.5"
    static String GRADLE_8_8_VERSION = "8.5"

    static String JDK_11_VERSION = "11.54.25-11.0.14.1"
    static String SIMPLIFIED_JDK_11_VERSION = "11.0.14"
    static String JDK_17_VERSION = "17.0.3.6.1"
    static String SIMPLIFIED_JDK_17_VERSION = "17.0.3"
    static String JDK_21_VERSION = "21.0.2.13.1"
    static String SIMPLIFIED_JDK_21_VERSION = "21.0.2"

    static String DAEMON_MAJOR_VERSION = "11"
    static Pair<String, String> JDK_11 = Pair.of("azul-zulu", JDK_11_VERSION)
    static Pair<String, String> JDK_17 = Pair.of("amazon-corretto", JDK_17_VERSION)
    static Pair<String, String> JDK_21 = Pair.of("amazon-corretto", JDK_21_VERSION)

    abstract Path workingDir();

    def applyApplicationPlugin() {
        buildFile << """
            apply plugin: 'application'
            
            application {
                mainClass = 'Main'
            }
        """.stripIndent(true)
    }

    def applyBaselineJavaVersions() {
        // language=groovy
        buildFile << """
            apply plugin: 'com.palantir.baseline-java-versions'
        """.stripIndent(true)
    }

    def setupJdksHardcodedVersions() {
        // language=groovy
        settingsFile << """
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
            
            apply plugin: 'com.palantir.jdks-properties'
        """.replace("FILES", getPluginClasspathInjector().join(",")).stripIndent(true)
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
            apply plugin: 'com.palantir.jdks.palantir-ca'
            
            jdks {
               jdk(11) {
                  distribution = JDK_11_DISTRO
                  jdkVersion = JDK_11_VERSION
               }
               
               jdk(17) {
                  distribution = JDK_17_DISTRO
                  jdkVersion = JDK_17_VERSION
               }
               
               jdk(21) {
                  distribution = JDK_21_DISTRO
                  jdkVersion = JDK_21_VERSION
               }
               
               daemonTarget = DAEMON_MAJOR_VERSION
            }
        """.replace("FILES", getPluginClasspathInjector().join(","))
                .replace("JDK_11_DISTRO", quoted(JDK_11.getLeft()))
                .replace("JDK_11_VERSION", quoted(JDK_11.getRight()))
                .replace("JDK_17_DISTRO", quoted(JDK_17.getLeft()))
                .replace("JDK_17_VERSION", quoted(JDK_17.getRight()))
                .replace("JDK_21_DISTRO", quoted(JDK_21.getLeft()))
                .replace("JDK_21_VERSION", quoted(JDK_21.getRight()))
                .replace("DAEMON_MAJOR_VERSION", quoted(DAEMON_MAJOR_VERSION))
                .stripIndent(true)
    }

    def quoted(String value) {
        return "'" + value + "'"
    }

    String runGradlewTasksSuccessfully(String... tasks) {
        String output = runGradlewTasks(tasks)
        assert output.contains("BUILD SUCCESSFUL")
        return output
    }

    String runGradlewTasksWithFailure(String... tasks) {
        String output = runGradlewTasks(tasks)
        assert output.contains("BUILD FAILED")
        return output
    }

    private String runGradlewTasks(String... tasks) {
        ProcessBuilder processBuilder = getProcessBuilder(tasks)
        Process process = processBuilder.start()
        String output = CommandRunner.readAllInput(process.getInputStream())
        return output
    }

    private ProcessBuilder getProcessBuilder(String... tasks) {
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
    static Pair readBytecodeVersion(File file) {
        try (InputStream stream = new FileInputStream(file)
             DataInputStream dis = new DataInputStream(stream)) {
            int magic = dis.readInt()
            if (magic != BYTECODE_IDENTIFIER) {
                throw new IllegalArgumentException("File " + file + " does not appear to be java bytecode")
            }
            int minorBytecodeVersion = dis.readUnsignedShort()
            int majorBytecodeVersion = dis.readUnsignedShort()
            return Pair.of(minorBytecodeVersion, majorBytecodeVersion)
        }
    }
}
