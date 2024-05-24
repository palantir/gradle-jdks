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
import java.util.stream.Collectors
import java.util.stream.Stream

class GenerateGradleJdkConfigsIntegrationTest extends GradleJdkIntegrationTest {

    @TempDir
    Path workingDir

    def '#gradleVersionNumber: generates the latest jdk configs set up by baseline-java-versions'() {
        setupJdksLatest()

        file('gradle.properties') << 'gradle.jdk.setup.enabled=true'
        gradleVersion = gradleVersionNumber

        buildFile << '''
            apply plugin: 'com.palantir.baseline-java-versions'
            
            javaVersions {
                libraryTarget = '11'
                distributionTarget = '17'
                runtime = '17'
            }
        '''.stripIndent(true)

        when:
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
        FileUtils.checkFilesAreTheSame(jarInProject.toFile(), originalJar.toFile())
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
        def checkResult = runGradlewTasks('check', '--info')

        then:
        !checkResult.contains(':checkGradleJdkConfigs UP-TO-DATE')

        when:
        Files.write(projectDir.toPath().resolve(String.format("gradle/jdks/17/%s/%s/local-path", CurrentOs.get().uiName(), CurrentArch.get().uiName())), "new-path\n".getBytes())
        def checkGradleJdkConfigs = runGradlewTasks('check', '--info')
        def notUpToDateGenerate = runGradlewTasks('generateGradleJdkConfigs', '--info')

        then:
        checkGradleJdkConfigs.contains("Gradle JDK configuration directory is out of date")
        !notUpToDateGenerate.contains(':generateGradleJdkConfigs UP-TO-DATE')

        where:
        gradleVersionNumber << [GRADLE_7VERSION, GRADLE_8VERSION]
    }

    def '#gradleVersionNumber: generates all the latest jdk configs'() {
        setupJdksLatest()

        file('gradle.properties') << 'gradle.jdk.setup.enabled=true'
        gradleVersion = gradleVersionNumber

        when:
        runTasksSuccessfully("wrapper", '--info')

        then:
        assert Files.list(projectDir.toPath().resolve(String.format("gradle/jdks"))).filter(Files::isDirectory).map(path -> path.getFileName().toString()).collect(Collectors.toSet()) == Set.of("8", "11", "17", "21")

        where:
        gradleVersionNumber << [GRADLE_7VERSION, GRADLE_8VERSION]
    }

    def '#gradleVersionNumber: checks the generation of hardcoded jdk configs with subprojects'() {
        setupJdksHardcodedVersions()
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
        setupJdksHardcodedVersions()

        gradleVersion = gradleVersionNumber

        buildFile << '''
            javaVersions {
                libraryTarget = '11'
                runtime = '15'
            }
        '''.stripIndent(true)
        file('gradle.properties') << 'gradle.jdk.setup.enabled=true'

        when:
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
