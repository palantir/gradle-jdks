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

import com.google.common.collect.Streams
import nebula.test.IntegrationSpec
import org.apache.commons.io.filefilter.SuffixFileFilter
import org.gradle.internal.impldep.com.thoughtworks.qdox.directorywalker.SuffixFilter

import java.net.http.HttpResponse
import java.nio.file.FileVisitResult
import java.nio.file.FileVisitor
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
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
                daemonTarget = '21'
                runtime = '17_PREVIEW'
            }
        '''.stripIndent(true)

        when:
        def firstCheck = runTasksSuccessfully('check')

        then:
        firstCheck.wasExecuted(':generateGradleJdkConfigs')
        !firstCheck.wasUpToDate(':generateGradleJdkConfigs')
        for (String majorVersion : Stream.of("11", "17", "21")) {
            for (Os os : Os.values()) {
                for (Arch arch : Arch.values()) {
                    Path downloadUrl = projectDir.toPath().resolve(String.format("gradle/jdks/%s/%s/%s/download-url", majorVersion, os.uiName(), arch.uiName()))
                    Files.exists(downloadUrl)
                    Files.exists(projectDir.toPath().resolve(String.format("gradle/jdks/%s/%s/%s/local-path", majorVersion, os.uiName(), arch.uiName())))
                }
            }
        }

        when:
        def upToDateCheck = runTasksSuccessfully('check')

        then:
        upToDateCheck.wasUpToDate(':generateGradleJdkConfigs')

        when:
        Files.createDirectories(projectDir.toPath().resolve("gradle/jdks/15"))
        def secondCheck = runTasksSuccessfully('help', '--task', 'generateGradleJdkConfigs')


        then:
        secondCheck.wasExecuted(':generateGradleJdkConfigs')
        !secondCheck.wasUpToDate(':generateGradleJdkConfigs')
        !projectDir.toPath().resolve("gradle/jdks/15").toFile().exists()


        where:
        gradleVersionNumber << [GRADLE_7VERSION, GRADLE_8VERSION]
    }

}