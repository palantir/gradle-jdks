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

package com.palantir.gradle.jdks.settings;

import static com.palantir.gradle.testing.assertion.GradlePluginTestAssertions.assertThat;

import com.palantir.gradle.jdks.GradleJdkTestUtils;
import com.palantir.gradle.jdks.setup.common.CurrentArch;
import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.execution.InvocationResult;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import com.palantir.platform.OperatingSystem;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.junit.jupiter.api.Test;

@GradlePluginTests
class ToolchainJdksSettingsPluginTest {

    private final Path USER_HOME_PATH = Path.of(System.getProperty("user.home"));

    @Test
    void non_existing_jdks_are_installed_by_the_settings_plugin(GradleInvoker gradle, RootProject project)
            throws IOException {
        Files.createDirectories(USER_HOME_PATH);
        GradleJdkTestUtils.applyJdksPlugins(
                project.settingsGradle().path(), project.buildGradle().path());

        project.buildGradle()
                .append(
                        """
            jdks {
               jdk(17) {
                  distribution = '%s'
                  jdkVersion = '%s'
               }

                daemonTarget = '17'
            }
            """
                                .formatted(GradleJdkTestUtils.JDK_17.getLeft(), GradleJdkTestUtils.JDK_17.getRight()));

        project.gradlePropertiesFile().append("palantir.jdk.setup.enabled=true");

        InvocationResult result = gradle.withArgs("generateGradleJdkConfigs").buildsSuccessfully();

        // only gradle configuration files are generated, no jdks are installed
        String os = OperatingSystem.get().uiName();
        String arch = CurrentArch.get().uiName();
        Path jdk17LocalPath = project.path().resolve("gradle/jdks/17/" + os + "/" + arch + "/local-path");
        String originalJdk17LocalPath = Files.readString(jdk17LocalPath).trim();
        Path originalJdkPath = Path.of(System.getProperty("user.home"))
                .resolve(".gradle/gradle-jdks")
                .resolve(originalJdk17LocalPath)
                .toAbsolutePath();
        assertThat(!Files.exists(originalJdkPath))
                .as("JDK should not be installed yet")
                .isTrue();

        // trigger a task
        String gradleVersion = gradle.withArgs("--version").buildsSuccessfully().output();
        String gradleVersionNumber = extractGradleVersion(gradleVersion);
        Files.writeString(jdk17LocalPath, "amazon-corretto-" + gradleVersionNumber + "-test1\n");
        InvocationResult executionResult = gradle.withArgs("javaToolchains").buildsSuccessfully();

        // the jdks are installed by the settings plugin
        assertThat(executionResult)
                .output()
                .contains("Gradle JDK setup is enabled (palantir.jdk.setup.enabled is true)"
                        + " but some jdks were not installed");
        assertThat(executionResult).output().contains("Auto-detection:     Disabled");
        assertThat(executionResult).output().contains("Auto-download:      Disabled");
        assertThat(executionResult).output().contains("JDK " + GradleJdkTestUtils.SIMPLIFIED_JDK_17_VERSION);
        Path expectedJdkPath = USER_HOME_PATH
                .resolve(".gradle/gradle-jdks")
                .resolve("amazon-corretto-" + gradleVersionNumber + "-test1")
                .toAbsolutePath();
        assertThat(Files.exists(expectedJdkPath)).as("JDK should be installed").isTrue();

        // if the jdk configured path is changed
        Files.writeString(jdk17LocalPath, "amazon-corretto-" + gradleVersionNumber + "-test2\n");
        InvocationResult resultAfterJdkChange =
                gradle.withArgs("javaToolchains").buildsSuccessfully();

        assertThat(resultAfterJdkChange)
                .output()
                .contains("Gradle JDK setup is enabled (palantir.jdk.setup.enabled is true)"
                        + " but some jdks were not installed");
        Path newInstalledJdkPath = USER_HOME_PATH
                .resolve(".gradle/gradle-jdks")
                .resolve("amazon-corretto-" + gradleVersionNumber + "-test2")
                .toAbsolutePath();
        assertThat(Files.exists(newInstalledJdkPath))
                .as("New JDK path should be installed")
                .isTrue();

        // cleanup
        Files.walk(expectedJdkPath).sorted(Comparator.reverseOrder()).forEach(path -> {
            try {
                Files.delete(path);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        Files.walk(newInstalledJdkPath).sorted(Comparator.reverseOrder()).forEach(path -> {
            try {
                Files.delete(path);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private String extractGradleVersion(String gradleVersionOutput) {
        // Extract version number from output like "Gradle 7.6.4"
        String[] lines = gradleVersionOutput.split("\n");
        for (String line : lines) {
            if (line.contains("Gradle ")) {
                return line.split("Gradle ")[1].split(" ")[0].trim();
            }
        }
        throw new IllegalStateException("Could not extract Gradle version from: " + gradleVersionOutput);
    }
}
