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

import java.nio.file.Path

abstract class GradleJdkIntegrationTest extends IntegrationSpec {

    static String GRADLE_7VERSION = "7.6.2"
    static String GRADLE_8VERSION = "8.5"

    abstract Path workingDir();

    def setupJdks() {
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

    String upgradeGradleWrapper() {
        return runGradlewTasks("getGradleJavaHomeProp", "wrapper", "--gradle-version", "8.4", "-V", "--stacktrace")
    }

    String runGradlewTasks(String... tasks) {
        ProcessBuilder processBuilder = getProcessBuilder(tasks)
        Process process = processBuilder.start()
        //assert process.waitFor() == 0
        return CommandRunner.readAllInput(process.getInputStream())
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
}
