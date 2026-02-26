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
package com.palantir.gradle.jdks.settings

import com.palantir.gradle.jdks.GradleJdkTestUtils
import com.palantir.gradle.jdks.setup.common.CurrentArch
import com.palantir.platform.OperatingSystem
import nebula.test.IntegrationSpec
import nebula.test.functional.ExecutionResult

import java.nio.file.Files
import java.nio.file.Path

class ToolchainJdksSettingsPluginTest extends IntegrationSpec {

    private final Path USER_HOME_PATH = Path.of(System.getProperty("user.home"))

    def '#gradleVersionNumber: non-existing jdks are installed by the settings plugin'() {
        setup:
        gradleVersion = gradleVersionNumber
        Files.createDirectories(USER_HOME_PATH)
        GradleJdkTestUtils.applyJdksPlugins(settingsFile, buildFile)

        // language=groovy
        buildFile << """
            jdks {
               jdk(17) {
                  distribution = JDK_17_DISTRO
                  jdkVersion = JDK_17_VERSION
               }
               
                daemonTarget = '17'
            }
        """.replace("JDK_17_DISTRO", GradleJdkTestUtils.quoted(GradleJdkTestUtils.JDK_17.getLeft()))
                .replace("JDK_17_VERSION", GradleJdkTestUtils.quoted(GradleJdkTestUtils.JDK_17.getRight()))
                .stripIndent(true)

        when:
        file('gradle.properties') << '''\
            palantir.jdk.setup.enabled=true
            org.gradle.java.installations.auto-detect=false
            org.gradle.java.installations.auto-download=false
        '''.stripIndent(true)
        runTasksSuccessfully("generateGradleJdkConfigs")

        then: 'only gradle configuration files are generated, no jdks are installed'
        String os = OperatingSystem.get().uiName()
        String arch = CurrentArch.get().uiName()
        Path jdk17LocalPath = projectDir.toPath().resolve("gradle/jdks/17/${os}/${arch}/local-path")
        String originalJdk17LocalPath = jdk17LocalPath.text.trim()
        Path originalJdkPath = Path.of(System.getProperty("user.home")).resolve(".gradle/gradle-jdks")
                .resolve(originalJdk17LocalPath).toAbsolutePath()
        !Files.exists(originalJdkPath)

        when: 'trigger a task'
        jdk17LocalPath.text = "amazon-corretto-${gradleVersion}-test1\n"
        ExecutionResult executionResult = runTasksSuccessfully("javaToolchains")

        then: 'the jdks are installed by the settings plugin'
        executionResult.standardError.contains("Gradle JDK setup is enabled (palantir.jdk.setup.enabled is true)" +
                " but some jdks were not installed")
        executionResult.standardOutput.contains("Auto-detection:     Disabled")
        executionResult.standardOutput.contains("Auto-download:      Disabled")
        executionResult.standardOutput.contains("JDK ${GradleJdkTestUtils.SIMPLIFIED_JDK_17_VERSION}")
        Path expectedJdkPath = USER_HOME_PATH.resolve(".gradle/gradle-jdks")
                .resolve("amazon-corretto-${gradleVersion}-test1").toAbsolutePath()
        Files.exists(expectedJdkPath)

        when: 'if the jdk configured path is changed'
        jdk17LocalPath.text = "amazon-corretto-${gradleVersion}-test2\n"
        ExecutionResult resultAfterJdkChange = runTasksSuccessfully("javaToolchains")

        then:
        resultAfterJdkChange.standardError.contains("Gradle JDK setup is enabled (palantir.jdk.setup.enabled is true)" +
                " but some jdks were not installed")
        Path newInstalledJdkPath = USER_HOME_PATH.resolve(".gradle/gradle-jdks")
                .resolve("amazon-corretto-${gradleVersion}-test2").toAbsolutePath()
        Files.exists(newInstalledJdkPath)

        cleanup:
        Files.walk(expectedJdkPath)
                .sorted(Comparator.reverseOrder())
                .forEach(Files::delete)

        Files.walk(newInstalledJdkPath)
                .sorted(Comparator.reverseOrder())
                .forEach(Files::delete)

        where:
        // testing for the different gradle versions to make sure the reflection in the settings plugin works
        gradleVersionNumber << [
                GradleJdkTestUtils.GRADLE_7_6_4_VERSION,
                GradleJdkTestUtils.GRADLE_8_5_VERSION,
                GradleJdkTestUtils.GRADLE_8_8_VERSION
        ]
    }

    def 'setupJdks bypasses auto-detect validation'() {
        setup:
        gradleVersion = GradleJdkTestUtils.GRADLE_8_8_VERSION
        GradleJdkTestUtils.applyJdksPlugins(settingsFile, buildFile)

        // language=groovy
        buildFile << """
            jdks {
               jdk(17) {
                  distribution = JDK_17_DISTRO
                  jdkVersion = JDK_17_VERSION
               }

                daemonTarget = '17'
            }
        """.replace("JDK_17_DISTRO", GradleJdkTestUtils.quoted(GradleJdkTestUtils.JDK_17.getLeft()))
                .replace("JDK_17_VERSION", GradleJdkTestUtils.quoted(GradleJdkTestUtils.JDK_17.getRight()))
                .stripIndent(true)

        // Set up enabled but WITHOUT auto-detect/auto-download properties.
        // wrapper must run first since setupJdks depends on the gradlew script.
        file('gradle.properties') << 'palantir.jdk.setup.enabled=true'
        runTasksSuccessfully("wrapper")

        when: 'running setupJdks does not fail on missing properties (bypass)'
        def result = runTasksSuccessfully("setupJdks")

        then: 'setupJdks bypasses the auto-detect/auto-download validation'
        result.wasExecuted("setupJdks")
        result.wasExecuted("ensureGradleJdkProperties")
    }

    def 'task depending on setupJdks also bypasses auto-detect validation'() {
        setup:
        gradleVersion = GradleJdkTestUtils.GRADLE_8_8_VERSION
        GradleJdkTestUtils.applyJdksPlugins(settingsFile, buildFile)

        // language=groovy
        buildFile << """
            jdks {
               jdk(17) {
                  distribution = JDK_17_DISTRO
                  jdkVersion = JDK_17_VERSION
               }

                daemonTarget = '17'
            }

            tasks.register('mySetup') {
                dependsOn 'setupJdks'
            }
        """.replace("JDK_17_DISTRO", GradleJdkTestUtils.quoted(GradleJdkTestUtils.JDK_17.getLeft()))
                .replace("JDK_17_VERSION", GradleJdkTestUtils.quoted(GradleJdkTestUtils.JDK_17.getRight()))
                .stripIndent(true)

        // Set up enabled but WITHOUT auto-detect/auto-download properties.
        file('gradle.properties') << 'palantir.jdk.setup.enabled=true'
        runTasksSuccessfully("wrapper")

        when: 'running a task that transitively depends on setupJdks'
        def result = runTasksSuccessfully("mySetup")

        then: 'the bypass still works because setupJdks is in the task graph'
        result.wasExecuted("setupJdks")
        result.wasExecuted("mySetup")
    }

    def 'fails when auto-detect is not disabled in gradle.properties'() {
        setup:
        gradleVersion = GradleJdkTestUtils.GRADLE_8_8_VERSION
        GradleJdkTestUtils.applyJdksPlugins(settingsFile, buildFile)

        // language=groovy
        buildFile << """
            jdks {
               jdk(17) {
                  distribution = JDK_17_DISTRO
                  jdkVersion = JDK_17_VERSION
               }

                daemonTarget = '17'
            }
        """.replace("JDK_17_DISTRO", GradleJdkTestUtils.quoted(GradleJdkTestUtils.JDK_17.getLeft()))
                .replace("JDK_17_VERSION", GradleJdkTestUtils.quoted(GradleJdkTestUtils.JDK_17.getRight()))
                .stripIndent(true)

        // Set up enabled but without auto-detect/auto-download properties
        file('gradle.properties') << 'palantir.jdk.setup.enabled=true'
        runTasksSuccessfully("generateGradleJdkConfigs")

        when:
        def result = runTasksWithFailure("javaToolchains")

        then:
        result.standardError.contains("gradle-jdks requires org.gradle.java.installations.auto-detect=false")
    }

}
