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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.palantir.gradle.jdks.GradleJdkTestUtils;
import com.palantir.gradle.jdks.setup.common.CurrentArch;
import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.execution.InvocationResult;
import com.palantir.gradle.testing.files.ProjectFile;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import com.palantir.platform.OperatingSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

@GradlePluginTests
class ToolchainJdksSettingsPluginTest {

    private final Path USER_HOME_PATH = Path.of(System.getProperty("user.home"));
    private Path expectedJdkPath;
    private Path newInstalledJdkPath;

    @BeforeEach
    void setup() throws IOException {
        Files.createDirectories(USER_HOME_PATH);
    }

    @Test
    void nonExistingJdksAreInstalledByTheSettingsPlugin(GradleInvoker gradle, RootProject rootProject) throws IOException {
        // Setup - using Gradle version "7.6.4" for the test
        String gradleVersionNumber = "7.6.4";
        
        GradleJdkTestUtils.applyJdksPlugins(
                rootProject.settingsGradle().path().toFile(), 
                rootProject.buildGradle().path().toFile());

        rootProject.buildGradle().append("""
            jdks {
               jdk(17) {
                  distribution = "amazon-corretto"
                  jdkVersion = "17.0.3.6.1"
               }
               
                daemonTarget = '17'
            }
        """);

        // Create gradle.properties
        ProjectFile<?> gradleProperties = rootProject.propertiesFile("gradle.properties");
        gradleProperties.overwrite("palantir.jdk.setup.enabled=true");

        // Run generateGradleJdkConfigs
        gradle.withArgs("generateGradleJdkConfigs").buildsSuccessfully();

        // Verify only gradle configuration files are generated, no jdks are installed
        String os = OperatingSystem.get().uiName();
        String arch = CurrentArch.get().uiName();
        Path jdk17LocalPath = rootProject.path().resolve(String.format("gradle/jdks/17/%s/%s/local-path", os, arch));
        String originalJdk17LocalPath = Files.readString(jdk17LocalPath).trim();
        Path originalJdkPath = Path.of(System.getProperty("user.home"))
                .resolve(".gradle/gradle-jdks")
                .resolve(originalJdk17LocalPath)
                .toAbsolutePath();
        assertFalse(Files.exists(originalJdkPath));

        // When triggering a task
        Files.writeString(jdk17LocalPath, "amazon-corretto-" + gradleVersionNumber + "-test1\n");
        InvocationResult executionResult = gradle.withArgs("javaToolchains").buildsSuccessfully();

        // Then the jdks are installed by the settings plugin
        assertTrue(executionResult.output().contains("Gradle JDK setup is enabled (palantir.jdk.setup.enabled is true)" +
                " but some jdks were not installed"));
        assertTrue(executionResult.output().contains("Auto-detection:     Disabled"));
        assertTrue(executionResult.output().contains("Auto-download:      Disabled"));
        assertTrue(executionResult.output().contains("JDK 17.0.3"));
        
        expectedJdkPath = USER_HOME_PATH.resolve(".gradle/gradle-jdks")
                .resolve("amazon-corretto-" + gradleVersionNumber + "-test1")
                .toAbsolutePath();
        assertTrue(Files.exists(expectedJdkPath));

        // When the jdk configured path is changed
        Files.writeString(jdk17LocalPath, "amazon-corretto-" + gradleVersionNumber + "-test2\n");
        InvocationResult resultAfterJdkChange = gradle.withArgs("javaToolchains").buildsSuccessfully();

        // Then
        assertTrue(resultAfterJdkChange.output().contains("Gradle JDK setup is enabled (palantir.jdk.setup.enabled is true)" +
                " but some jdks were not installed"));
        newInstalledJdkPath = USER_HOME_PATH.resolve(".gradle/gradle-jdks")
                .resolve("amazon-corretto-" + gradleVersionNumber + "-test2")
                .toAbsolutePath();
        assertTrue(Files.exists(newInstalledJdkPath));
    }

    @AfterEach
    void cleanup() throws IOException {
        // Clean up test directories if they were created
        if (expectedJdkPath != null && Files.exists(expectedJdkPath)) {
            deleteDirectory(expectedJdkPath);
        }

        if (newInstalledJdkPath != null && Files.exists(newInstalledJdkPath)) {
            deleteDirectory(newInstalledJdkPath);
        }
    }

    private void deleteDirectory(Path directory) throws IOException {
        try (Stream<Path> stream = Files.walk(directory)) {
            stream.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to delete file: " + path, e);
                        }
                    });
        }
    }
}