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


import com.palantir.gradle.jdks.setup.common.CurrentArch
import com.palantir.gradle.jdks.setup.common.GradleJdksPatchHelper
import com.palantir.gradle.plugintesting.GradleTestVersions
import com.palantir.platform.OperatingSystem
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors

class GradleJdkPatcherIntegrationTest extends GradleJdkIntegrationSpec {

    @TempDir
    Path workingDir

    private void enableGradleJdks() {
        file('gradle.properties') << '''\
            palantir.jdk.setup.enabled=true
            org.gradle.java.installations.auto-detect=false
            org.gradle.java.installations.auto-download=false
        '''.stripIndent(true)
    }

    def '#gradleVersionNumber: successfully generates Gradle JDK setup files'() {
        setupJdksHardcodedVersions()
        gradleVersion = gradleVersionNumber
        runTasksSuccessfully("wrapper")
        enableGradleJdks()

        when: 'running setupJdks'
        def result = runTasksSuccessfully("setupJdks")

        then: 'it triggers the execution of Gradle JDK setup tasks'
        result.wasExecuted('wrapperJdkPatcher')
        result.wasExecuted('generateGradleJdkConfigs')

        and: './gradlew file is patched'
        file("gradlew").text.contains("gradle/gradle-jdks-setup.sh")
        file("gradlew").text.findAll(GradleJdksPatchHelper.PATCH_HEADER).size() == 1
        file("gradlew").text.findAll(GradleJdksPatchHelper.PATCH_FOOTER).size() == 1

        and: 'the `gradle/` configuration files are generated'
        checkJdksVersions(projectDir, Set.of("11", "17", "21"))
        Files.readString(projectDir.toPath().resolve("gradle/gradle-daemon-jdk-version")).trim() == GradleJdkTestUtils.DAEMON_MAJOR_VERSION_17
        Path scriptPath = projectDir.toPath().resolve("gradle/gradle-jdks-setup.sh");
        Files.isExecutable(scriptPath)
        Path functionsPath = projectDir.toPath().resolve("gradle/gradle-jdks-functions.sh");
        Files.isExecutable(functionsPath)

        and: 'old gradle jdk paths are removed'
        Path oldPath = projectDir.toPath().resolve("gradle/certs");
        !Files.exists(oldPath)

        and: '.gradle/config.properties configures java.home'
        file(".gradle/config.properties").text.contains("java.home")

        when: 'running check'
        def checkResult = runTasksSuccessfully("check")

        then:
        checkResult.wasExecuted("checkGradleJdkConfigs")
        !checkResult.wasUpToDate("checkGradleJdkConfigs")
        checkResult.wasExecuted("checkWrapperJdkPatcher")
        !checkResult.wasUpToDate("checkWrapperJdkPatcher")

        when: 'running the second check'
        def secondCheckResult = runTasksSuccessfully("check")

        then:
        secondCheckResult.wasUpToDate("checkGradleJdkConfigs")
        secondCheckResult.wasUpToDate("checkWrapperJdkPatcher")

        where:
        gradleVersionNumber << GRADLE_TEST_VERSIONS
    }

    def '#gradleVersionNumber: fails if Gradle JDK configuration is wrong'() {
        setupJdksHardcodedVersions('15')
        enableGradleJdks()
        gradleVersion = gradleVersionNumber

        when: 'running setupJdks'
        def result = runTasksWithFailure("setupJdks")

        then: 'generateGradleJdkConfigs fails'
        result.wasExecuted('generateGradleJdkConfigs')
        !result.wasExecuted('wrapperJdkPatcher')
        result.standardError.contains("Gradle daemon JDK version is `15` but no JDK configured for that version.")

        where:
        gradleVersionNumber << GRADLE_TEST_VERSIONS
    }

    def '#gradleVersionNumber: fails if no JDKs were configured'() {
        applyJdksPlugins()
        gradleVersion = gradleVersionNumber
        // language=groovy
        buildFile << """
            jdks {
               daemonTarget = 11
            }
        """.stripIndent(true)
        runTasksSuccessfully("wrapper")
        enableGradleJdks()


        when: 'running setupJdks'
        def result = runTasksWithFailure("setupJdks")

        then: 'generateGradleJdkConfigs fails'
        result.wasExecuted('generateGradleJdkConfigs')
        !result.wasExecuted('wrapperJdkPatcher')
        result.standardError.contains("No JDKs were configured for the gradle setup");

        where:
        gradleVersionNumber << GRADLE_TEST_VERSIONS
    }

    def '#gradleVersionNumber: checkGradleJdkConfigs fails if run before setupJdks'() {
        setupJdksHardcodedVersions()
        gradleVersion = gradleVersionNumber
        runTasksSuccessfully("wrapper")
        enableGradleJdks()

        when: 'running check'
        def checkResult = runTasksWithFailure("check")

        then:
        checkResult.wasExecuted("checkGradleJdkConfigs")
        checkResult.standardError.contains("is out of date, please run `./gradlew setupJdks`")

        where:
        gradleVersionNumber << GRADLE_TEST_VERSIONS
    }

    def '#gradleVersionNumber: no gradleWrapper patch if palantir.jdk.setup.enabled == false'() {
        setupJdksHardcodedVersions()
        gradleVersion = gradleVersionNumber

        when:
        def output = runTasksSuccessfully('wrapper')

        then:
        !output.wasExecuted('wrapperJdkPatcher')
        !file("gradlew").text.contains("gradle-jdks-setup.sh")
        
        where:
        gradleVersionNumber << GradleTestVersions.getGradleVersionsForTests()
    }

    private static void checkJdksVersions(File projectDir, Set<String> versions) {
        assert Files.list(projectDir.toPath().resolve("gradle/jdks")).filter(Files::isDirectory)
                .map(path -> path.getFileName().toString())
                .collect(Collectors.toSet()) == versions
        String osName = OperatingSystem.get().uiName();
        String archName = CurrentArch.get().uiName();
        versions.stream().findFirst().ifPresent(version -> {
            assert Files.exists(projectDir.toPath().resolve("gradle/jdks/${version}/${osName}/${archName}/download-url"))
            assert Files.exists(projectDir.toPath().resolve("gradle/jdks/${version}/${osName}/${archName}/local-path"))
        })
    }

    @Override
    Path workingDir() {
        return workingDir
    }
}
