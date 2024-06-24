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

class GradleJdkPatcherIntegrationTest extends GradleJdkIntegrationTest {

    @TempDir
    Path workingDir

    def setup() {
        setupJdksHardcodedVersions()
    }

    def '#gradleVersionNumber: successfully generates Gradle JDK setup files'() {
        file('gradle.properties') << 'palantir.jdk.setup.enabled=true'
        gradleVersion = gradleVersionNumber
        runTasksSuccessfully("wrapper")

        when: 'running setupJdks'
        def result = runTasksSuccessfully("setupJdks")

        then: 'it triggers the execution of Gradle JDK setup tasks'
        result.wasExecuted('wrapperJdkPatcher')
        result.wasExecuted('generateGradleJdkConfigs')

        and: './gradlew file is patched'
        file("gradlew").text.contains("gradle/gradle-jdks-setup.sh")
        file("gradlew").text.findAll(GradleJdkPatchHelper.PATCH_HEADER).size() == 1
        file("gradlew").text.findAll(GradleJdkPatchHelper.PATCH_FOOTER).size() == 1

        and: './gradlew.bat file is patched'
        file("gradlew.bat").text.contains(Path.of("src/main/resources/batch-patch.bat").text)
        file("gradlew.bat").text.findAll("@rem # >>> Gradle JDK setup >>>").size() == 1
        file("gradlew.bat").text.findAll("@rem # <<< Gradle JDK setup <<<").size() == 1

        and: 'the `gradle/` configuration files are generated'
        checkJdksVersions(projectDir, Set.of("11", "17", "21"))
        Files.readString(projectDir.toPath().resolve("gradle/gradle-daemon-jdk-version")).trim() == "11"
        Path scriptPath = projectDir.toPath().resolve("gradle/gradle-jdks-setup.sh");
        Files.isExecutable(scriptPath)
        Path certFile = projectDir.toPath().resolve("gradle/certs/Palantir3rdGenRootCa.serial-number")
        Optional<AliasContentCert> maybePalantirCerts = new CaResources(new StdLogger()).readPalantirRootCaFromSystemTruststore()
        if (maybePalantirCerts.isPresent()) {
            Files.readString(certFile).trim() == "18126334688741185161"
        } else {
            !Files.exists(certFile)
        }

        and: '.gradle/config.properties configures java.home'
        file(".gradle/config.properties").text.contains("java.home")

        where:
        gradleVersionNumber << [GRADLE_7_6_4_VERSION, GRADLE_8_5_VERSION]
    }

    def 'no gradleWrapper patch if palantir.jdk.setup.enabled == false'() {
        when:
        def output = runTasksSuccessfully('wrapper')

        then:
        !output.wasExecuted('wrapperJdkPatcher')
        !file("gradlew").text.contains("gradle-jdks-setup.sh")
    }

    private static void checkJdksVersions(File projectDir, Set<String> versions) {
        assert Files.list(projectDir.toPath().resolve("gradle/jdks")).filter(Files::isDirectory)
                .map(path -> path.getFileName().toString())
                .collect(Collectors.toSet()) == versions
        String osName = CurrentOs.get().uiName();
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
