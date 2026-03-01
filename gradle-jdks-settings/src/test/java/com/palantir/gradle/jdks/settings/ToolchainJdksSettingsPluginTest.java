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

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.gradle.jdks.setup.common.CurrentArch;
import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.execution.InvocationResult;
import com.palantir.gradle.testing.junit.AdditionallyRunWithGradle;
import com.palantir.gradle.testing.junit.DisabledConfigurationCache;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import com.palantir.platform.OperatingSystem;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

@GradlePluginTests
@DisabledConfigurationCache
@AdditionallyRunWithGradle(
        value = {"7.6.4", "8.5", "8.8"},
        reason = "testing for the different gradle versions to make sure the reflection in the settings plugin works")
class ToolchainJdksSettingsPluginTest {

    private static final Path USER_HOME_PATH = Path.of(System.getProperty("user.home"));
    private static final String JDK_17_DISTRIBUTION = "amazon-corretto";
    private static final String JDK_17_VERSION = "17.0.3.6.1";
    private static final String SIMPLIFIED_JDK_17_VERSION = "17.0.3";

    @Test
    void non_existing_jdks_are_installed_by_the_settings_plugin(GradleInvoker gradle, RootProject rootProject)
            throws IOException {
        Files.createDirectories(USER_HOME_PATH);
        applyJdksPlugins(rootProject);

        rootProject.buildGradle().append("""
            jdks {
                jdk(17) {
                    distribution = '%s'
                    jdkVersion = '%s'
                }

                daemonTarget = '17'
            }
            """, JDK_17_DISTRIBUTION, JDK_17_VERSION);

        rootProject.gradlePropertiesFile().setProperty("palantir.jdk.setup.enabled", "true");
        gradle.withArgs("generateGradleJdkConfigs").buildsSuccessfully();

        // only gradle configuration files are generated, no jdks are installed
        String os = OperatingSystem.get().uiName();
        String arch = CurrentArch.get().uiName();
        Path jdk17LocalPath = rootProject.path().resolve(String.format("gradle/jdks/17/%s/%s/local-path", os, arch));
        String originalJdk17LocalPath = Files.readString(jdk17LocalPath).trim();
        Path originalJdkPath = Path.of(System.getProperty("user.home"))
                .resolve(".gradle/gradle-jdks")
                .resolve(originalJdk17LocalPath)
                .toAbsolutePath();
        assertThat(originalJdkPath)
                .as("only gradle configuration files are generated, no jdks are installed")
                .doesNotExist();

        // trigger a task
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        Path expectedJdkPath = USER_HOME_PATH
                .resolve(".gradle/gradle-jdks")
                .resolve("amazon-corretto-" + uniqueId + "-test1")
                .toAbsolutePath();
        Path newInstalledJdkPath = USER_HOME_PATH
                .resolve(".gradle/gradle-jdks")
                .resolve("amazon-corretto-" + uniqueId + "-test2")
                .toAbsolutePath();

        try {
            Files.writeString(jdk17LocalPath, "amazon-corretto-" + uniqueId + "-test1\n");
            InvocationResult executionResult = gradle.withArgs("javaToolchains").buildsSuccessfully();

            // the jdks are installed by the settings plugin
            executionResult
                    .assertThat()
                    .output()
                    .as("the jdks are installed by the settings plugin")
                    .contains("Gradle JDK setup is enabled (palantir.jdk.setup.enabled is true)"
                            + " but some jdks were not installed")
                    .contains("Auto-detection:     Disabled")
                    .contains("Auto-download:      Disabled")
                    .contains("JDK " + SIMPLIFIED_JDK_17_VERSION);
            assertThat(expectedJdkPath)
                    .as("expected JDK path was created after javaToolchains")
                    .exists();

            // if the jdk configured path is changed
            Files.writeString(jdk17LocalPath, "amazon-corretto-" + uniqueId + "-test2\n");
            InvocationResult resultAfterJdkChange =
                    gradle.withArgs("javaToolchains").buildsSuccessfully();

            resultAfterJdkChange
                    .assertThat()
                    .output()
                    .as("jdk setup message after path change")
                    .contains("Gradle JDK setup is enabled (palantir.jdk.setup.enabled is true)"
                            + " but some jdks were not installed");
            assertThat(newInstalledJdkPath)
                    .as("new JDK path was created after jdk path change")
                    .exists();
        } finally {
            deleteDirectoryIfExists(expectedJdkPath);
            deleteDirectoryIfExists(newInstalledJdkPath);
        }
    }

    private static void applyJdksPlugins(RootProject rootProject) {
        rootProject.settingsGradle().plugins().add("com.palantir.jdks.settings");
        rootProject.buildGradle().plugins().add("java").add("com.palantir.jdks").add("com.palantir.jdks.palantir-ca");
    }

    private static void deleteDirectoryIfExists(Path path) throws IOException {
        if (path != null && Files.exists(path)) {
            try (Stream<Path> stream = Files.walk(path)) {
                stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }
        }
    }
}
