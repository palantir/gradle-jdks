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
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Stream

class GenerateGradleJdkConfigsIntegrationTest extends IntegrationSpec {

    private static String GRADLE_7VERSION = "7.6.2"
    private static String GRADLE_8VERSION = "8.5"

    def setup() {
        // language=groovy
        buildFile << """
            buildscript {
                repositories {
                    mavenCentral() { metadataSources { mavenPom(); ignoreGradleMetadataRedirection() } }
                    gradlePluginPortal() { metadataSources { mavenPom(); ignoreGradleMetadataRedirection() } }
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
        """.stripIndent(true)
    }

    def '#gradleVersionNumber: checks the generation of the jdk configs'() {
        file('gradle.properties') << 'gradle.jdk.setup.enabled=true'
        gradleVersion = gradleVersionNumber

        buildFile << '''
            javaVersions {
                libraryTarget = '11'
                distributionTarget = '17_PREVIEW'
                runtime = '17_PREVIEW'
            }
            jdks {
              daemonTarget = '21'
            }
        '''.stripIndent(true)

        when:
        def checkResult = runTasksWithFailure('check')

        then:
        checkResult.standardError.contains("The gradle configuration files in `gradle/jdks` are out of date")

        when:
        runTasksSuccessfully('generateGradleJdkConfigs')

        then:
        for (String majorVersion : Stream.of("11", "17", "21")) {
            for (Os os : Os.values()) {
                for (Arch arch : Arch.values()) {
                    Path downloadUrl = projectDir.toPath().resolve(String.format("gradle/jdks/%s/%s/%s/download-url", majorVersion, os.uiName(), arch.uiName()))
                    Files.exists(downloadUrl)
                    Files.exists(projectDir.toPath().resolve(String.format("gradle/jdks/%s/%s/%s/local-path", majorVersion, os.uiName(), arch.uiName())))
                }
            }
        }
        Files.exists(projectDir.toPath().resolve("gradle/gradle-daemon-jdk-version"))
        Path jarInProject = projectDir.toPath().resolve("gradle/gradle-jdks-setup.jar");
        Path originalJar = Path.of("src/main/resources/gradle-jdks-setup.jar");
        Files.exists(jarInProject)
        GenerateGradleJdkConfigs.checkFilesAreTheSame(jarInProject.toFile(), originalJar.toFile())
        Path scriptPath = projectDir.toPath().resolve("gradle/gradle-jdks-setup.sh");
        Files.exists(scriptPath)
        Files.isExecutable(scriptPath)
        Path certFile = projectDir.toPath().resolve("gradle/certs/Palantir3rdGenRootCa.serial-number")
        Files.exists(certFile)
        Files.readString(certFile).trim() == "18126334688741185161"

        when:
        def secondCheck = runTasksSuccessfully('check')
        def upToDateCheck = runTasksSuccessfully('check')

        then:
        !secondCheck.wasUpToDate(':checkGradleJdkConfigs')
        upToDateCheck.wasUpToDate(':checkGradleJdkConfigs')

        when:
        def upToDateGenerate = runTasksSuccessfully('generateGradleJdkConfigs')

        then:
        upToDateGenerate.wasUpToDate(':generateGradleJdkConfigs')

        when:
        Files.delete(projectDir.toPath().resolve("gradle/jdks/17/macos/x86/download-url"))
        def notUpToDateGenerate = runTasksSuccessfully('generateGradleJdkConfigs')

        then:
        !notUpToDateGenerate.wasUpToDate(':generateGradleJdkConfigs')

        where:
        gradleVersionNumber << [GRADLE_7VERSION, GRADLE_8VERSION]
    }

    def '#gradleVersionNumber: checks the generation of the jdk configs with subprojects'() {
        file('gradle.properties') << 'gradle.jdk.setup.enabled=true'
        gradleVersion = gradleVersionNumber

        buildFile << '''
            javaVersions {
                libraryTarget = '11'
            }
        '''.stripIndent(true)

        def subprojectLib = addSubproject 'subprojectLib', '''
            apply plugin: 'java-library'
            
            javaVersion {
                target = 21
                runtime = 21
            }
        '''.stripIndent(true)
        writeHelloWorld(subprojectLib)

        when:
        def result = runTasksSuccessfully('wrapper', 'check')

        then:
        result.wasExecuted(':generateGradleJdkConfigs')
        !result.wasUpToDate(':generateGradleJdkConfigs')
        for (String majorVersion : Stream.of("11", "21")) {
            for (Os os : Os.values()) {
                for (Arch arch : Arch.values()) {
                    Path downloadUrl = projectDir.toPath().resolve(String.format("gradle/jdks/%s/%s/%s/download-url", majorVersion, os.uiName(), arch.uiName()))
                    Files.exists(downloadUrl)
                    Files.exists(projectDir.toPath().resolve(String.format("gradle/jdks/%s/%s/%s/local-path", majorVersion, os.uiName(), arch.uiName())))
                }
            }
        }

        where:
        gradleVersionNumber << [GRADLE_7VERSION, GRADLE_8VERSION]
    }


    def '#gradleVersionNumber: fails if the jdk version is not configured'() {
            file('gradle.properties') << 'gradle.jdk.setup.enabled=true'
            gradleVersion = gradleVersionNumber

            buildFile << '''
            javaVersions {
                libraryTarget = '11'
                runtime = '15'
            }
        '''.stripIndent(true)

            when:
            def result = runTasksWithFailure('wrapper', 'check')

            then:
            result.standardError.contains("Could not find a JDK with major version 15 in project")

            where:
            gradleVersionNumber << [GRADLE_7VERSION, GRADLE_8VERSION]

    }

}