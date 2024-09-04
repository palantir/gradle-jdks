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

import com.palantir.gradle.jdks.setup.common.CommandRunner
import org.apache.commons.lang3.tuple.Pair

import java.nio.file.Path

class GradleJdkTestUtils {

    static String GRADLE_7_6_VERSION = "7.6"
    static String GRADLE_7_6_4_VERSION = "7.6.4"
    static String GRADLE_8_5_VERSION = "8.5"
    static String GRADLE_8_8_VERSION = "8.8"

    static String JDK_11_VERSION = "11.54.25-11.0.14.1"
    static String SIMPLIFIED_JDK_11_VERSION = "11.0.14"
    static String JDK_17_VERSION = "17.0.3.6.1"
    static String SIMPLIFIED_JDK_17_VERSION = "17.0.3"
    static String JDK_21_VERSION = "21.0.2.13.1"
    static String SIMPLIFIED_JDK_21_VERSION = "21.0.2"

    static String DAEMON_MAJOR_VERSION_11 = "11"
    static Pair<String, String> JDK_11 = Pair.of("azul-zulu", JDK_11_VERSION)
    static Pair<String, String> JDK_17 = Pair.of("amazon-corretto", JDK_17_VERSION)
    static Pair<String, String> JDK_21 = Pair.of("amazon-corretto", JDK_21_VERSION)

    static applyApplicationPlugin(File buildFile) {
       // language=groovy
        buildFile << """
            apply plugin: 'application'
            
            application {
                mainClass = 'Main'
            }
        """.stripIndent(true)
    }

    static applyBaselineJavaVersions(File buildFile) {
        // language=groovy
        buildFile << """
            apply plugin: 'com.palantir.baseline-java-versions'
        """.stripIndent(true)
    }

    static applyJdksPlugins(File settingsFile, File buildFile) {
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
            
            apply plugin: 'com.palantir.jdks.settings'
        """.replace("FILES", getSettingsPluginClasspathInjector().join(",")).stripIndent(true)

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
        """.replace("FILES", getBuildPluginClasspathInjector().join(","))
                .stripIndent(true)
    }

    static setupJdksHardcodedVersions(File settingsFile, File buildFile, String daemonJdkVersion = DAEMON_MAJOR_VERSION_11) {

        applyJdksPlugins(settingsFile, buildFile)

        // language=groovy
        buildFile << """
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
               
               daemonTarget = DAEMON_MAJOR_VERSION_11
            }
        """.replace("JDK_11_DISTRO", quoted(JDK_11.getLeft()))
                .replace("JDK_11_VERSION", quoted(JDK_11.getRight()))
                .replace("JDK_17_DISTRO", quoted(JDK_17.getLeft()))
                .replace("JDK_17_VERSION", quoted(JDK_17.getRight()))
                .replace("JDK_21_DISTRO", quoted(JDK_21.getLeft()))
                .replace("JDK_21_VERSION", quoted(JDK_21.getRight()))
                .replace("DAEMON_MAJOR_VERSION_11", quoted(daemonJdkVersion))
                .stripIndent(true)
    }

    static quoted(String value) {
        return "'" + value + "'"
    }

    private static Iterable<File> getBuildPluginClasspathInjector() {
        return getPluginClasspathInjector(Path.of("../gradle-jdks/build/pluginUnderTestMetadata/plugin-under-test-metadata.properties"))
    }

    private static Iterable<File> getSettingsPluginClasspathInjector() {
        return getPluginClasspathInjector(Path.of("../gradle-jdks-settings/build/pluginUnderTestMetadata/plugin-under-test-metadata.properties"))
    }


    private static Iterable<File> getPluginClasspathInjector(Path path) {
        File propertiesFile = path.toFile()
        Properties properties = new Properties()
        propertiesFile.withInputStream { inputStream ->
            properties.load(inputStream)
        }
        String classpath = properties.getProperty('implementation-classpath')
        return classpath.split(File.pathSeparator).collect { "'" + it + "'" }
    }

    private GradleJdkTestUtils() {}
}
