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


import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class GradleJdkWrapperPatcherIntegrationTest extends GradleJdkIntegrationTest {

    private static String JDK_11_VERSION = "11.54.25-11.0.14.1"

    @TempDir
    Path workingDir

    def setup() {

        setupJdksHardcodedVersions()
        applyBaselineJavaVersions()

        // language=groovy
        buildFile << """
            apply plugin: 'application'

            tasks.register('getGradleJavaHomeProp') {
                doLast {
                    println "Gradle java home is " + System.getProperty('org.gradle.java.home')
                }
            }
            
            application {
                mainClass = 'Main'
            }

            javaVersions {
                libraryTarget = '11'
                distributionTarget = '17_PREVIEW'
            }
        """.replace("FILES", getPluginClasspathInjector().join(",")).stripIndent(true)
    }

    def '#gradleVersionNumber: patches gradleWrapper to set up JDK'() {
        file('gradle.properties') << 'gradle.jdk.setup.enabled=true'
        gradleVersion = gradleVersionNumber

        when:
        def output = runTasksSuccessfully('wrapper', 'getGradleJavaHomeProp')

        then:
        output.wasExecuted(':wrapperJdkPatcher')
        !output.wasSkipped(':wrapperJdkPatcher')
        output.standardOutput.contains("Gradle JDK setup is enabled, patching the gradle wrapper files")
        output.standardOutput.contains("Gradle java home is null")
        file("gradlew").text.contains("gradle/gradle-jdks-setup.sh")

        when:
        String wrapperResult1 = upgradeGradleWrapper()
        String wrapperResult2 = upgradeGradleWrapper()

        then:
        file("gradlew").text.contains("gradle/gradle-jdks-setup.sh")
        Path gradleJdksPath = workingDir.resolve("gradle-jdks")
        Path expectedLocalPath = gradleJdksPath.resolve(String.format("azul-zulu-11.54.25-11.0.14.1-%s", getHashForDistribution(JdkDistributionName.AZUL_ZULU, JDK_11_VERSION)))
        wrapperResult1.contains(String.format("Successfully installed JDK distribution in %s", expectedLocalPath))
        String expectedJdkLog = "JVM:          11.0.14.1 (Azul Systems, Inc. 11.0.14.1+1-LTS)"
        wrapperResult1.contains(expectedJdkLog)
        wrapperResult1.contains("Gradle 7.6.4")
        wrapperResult1.contains("Gradle java home is " + expectedLocalPath)
        file('gradle/wrapper/gradle-wrapper.properties').text.contains("gradle-8.4-bin.zip")
        wrapperResult2.contains(String.format("already exists in '%s'", expectedLocalPath))
        wrapperResult2.contains(expectedJdkLog)
        wrapperResult2.contains("Gradle 8.4")
        wrapperResult2.contains("Gradle java home is " + expectedLocalPath)

        where:
        gradleVersionNumber << [GRADLE_7VERSION]
    }

    def '#gradleVersionNumber: gradlew file is correctly generated'() {
        gradleVersion = gradleVersionNumber
        runTasksSuccessfully('wrapper')
        List<String> initialRows = Files.readAllLines(projectDir.toPath().resolve('gradlew'))

        when:
        file('gradle.properties') << 'gradle.jdk.setup.enabled=true'
        def outputWithJdkEnabled = runTasksSuccessfully('wrapper')

        then:
        !outputWithJdkEnabled.wasSkipped(':wrapperJdkPatcher')
        outputWithJdkEnabled.standardOutput.contains("Gradle JDK setup is enabled, patching the gradle wrapper files")
        List<String> rowsAfterPatching = Files.readAllLines(projectDir.toPath().resolve('gradlew'))

        List<String> gradlewPatchRows = Files.readAllLines(Path.of('src/main/resources/gradlew-patch.sh'))
        rowsAfterPatching.removeAll(initialRows)
        rowsAfterPatching.removeAll(gradlewPatchRows)
        rowsAfterPatching.size() == 0

        where:
        gradleVersionNumber << [GRADLE_7VERSION, GRADLE_8VERSION]
    }

    def 'no gradleWrapper patch if gradle.jdk.setup.enabled == false'() {
        when:
        def output = runTasksSuccessfully('wrapper')

        then:
        !output.standardOutput.contains('wrapperJdkPatcher')
        !output.standardOutput.contains("Gradle JDK setup is enabled, patching the gradle wrapper files")
        !file("gradlew").text.contains("gradle-jdks-setup.sh")
    }

    @Override
    Path workingDir() {
        return workingDir
    }
}
