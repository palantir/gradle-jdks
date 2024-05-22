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

import com.palantir.gradle.jdks.setup.AliasContentCert
import com.palantir.gradle.jdks.setup.CaResources
import com.palantir.gradle.jdks.setup.StdLogger
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Stream

class GenerateGradleJdkConfigsIntegrationTest extends GradleJdkIntegrationTest {

    @TempDir
    Path workingDir

    def '#gradleVersionNumber: checks the generation of the latest jdk configs'() {
        when:
        setupJdksLatest()

        file('gradle.properties') << 'gradle.jdk.setup.enabled=true'
        gradleVersion = gradleVersionNumber

        buildFile << '''
            javaVersions {
                libraryTarget = '11'
                distributionTarget = '17'
                runtime = '17'
            }
        '''.stripIndent(true)
        runTasksSuccessfully("wrapper", '--info')
        runTasksSuccessfully("wrapper", '--info')

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
        Path originalJar = Path.of("build/resources/main/gradle-jdks-setup.jar");
        Files.exists(jarInProject)
        GradleJdkConfigs.checkFilesAreTheSame(jarInProject.toFile(), originalJar.toFile())
        Path scriptPath = projectDir.toPath().resolve("gradle/gradle-jdks-setup.sh");
        Files.exists(scriptPath)
        Files.isExecutable(scriptPath)
        Path certFile = projectDir.toPath().resolve("gradle/certs/Palantir3rdGenRootCa.serial-number")
        Optional<AliasContentCert> maybePalantirCerts = new CaResources(new StdLogger()).readPalantirRootCaFromSystemTruststore()
        if (maybePalantirCerts.isPresent()) {
            Files.exists(certFile)
            Files.readString(certFile).trim() == "18126334688741185161"
        } else {
            !Files.exists(certFile)
        }

        when:
        def secondCheck = runGradlewTasks('check')
        def upToDateCheck = runGradlewTasks('check')

        then:
        !secondCheck.contains(':checkGradleJdkConfigs UP-TO-DATE')
        upToDateCheck.contains(':checkGradleJdkConfigs UP-TO-DATE')

        when:
        Files.delete(projectDir.toPath().resolve(String.format("gradle/jdks/17/%s/%s/download-url", CurrentOs.get().uiName(), CurrentArch.get().uiName())))
        def notUpToDateGenerate = runGradlewTasks('generateGradleJdkConfigs')

        then:
        !notUpToDateGenerate.contains(':generateGradleJdkConfigs UP-TO-DATE')

        where:
        gradleVersionNumber << [GRADLE_7VERSION, GRADLE_8VERSION]
    }

    def '#gradleVersionNumber: checks the generation of hardcoded jdk configs with subprojects'() {
        when:
        setupJdksHardodedVersions()
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

        runTasks("wrapper")
        runTasks("wrapper")

        then:
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
        when:
        setupJdksHardodedVersions()

        gradleVersion = gradleVersionNumber

        buildFile << '''
            javaVersions {
                libraryTarget = '11'
                runtime = '15'
            }
        '''.stripIndent(true)
        runTasks("wrapper")
        file('gradle.properties') << 'gradle.jdk.setup.enabled=true'
        def result = runTasksWithFailure("wrapper")

        then:
        result.standardError.contains("Could not find a JDK with major version 15 in project")

        where:
        gradleVersionNumber << [GRADLE_7VERSION, GRADLE_8VERSION]

    }

    @Override
    Path workingDir() {
        return workingDir
    }
}
