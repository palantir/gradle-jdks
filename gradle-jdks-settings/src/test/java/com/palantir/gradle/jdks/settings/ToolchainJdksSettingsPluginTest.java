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

import com.palantir.gradle.jdks.TestResources;
import com.palantir.gradle.jdks.settings.TestDecorators.WithCustomGradleUserHome;
import com.palantir.gradle.jdks.setup.FileUtils;
import com.palantir.gradle.jdks.setup.common.CurrentArch;
import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.execution.InvocationResult;
import com.palantir.gradle.testing.junit.AdditionallyRunWithGradle;
import com.palantir.gradle.testing.junit.DisabledConfigurationCache;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import com.palantir.platform.OperatingSystem;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@GradlePluginTests
@DisabledConfigurationCache
@AdditionallyRunWithGradle(
        value = {"8.5", "8.8"},
        reason = "testing for the different gradle versions to make sure the reflection in the settings plugin works")
class ToolchainJdksSettingsPluginTest {

    private static final String OS = OperatingSystem.get().uiName();
    private static final String ARCH = CurrentArch.get().uiName();
    private static final String TMP_GRADLE_USER_HOME = "/tmp/gradle-jdks-testing";
    private static final Path TMP_GRADLE_USER_HOME_PATH = Path.of("/tmp/gradle-jdks-testing");
    private static final Path GRADLE_JDKS_INSTALLATION_DIR = TMP_GRADLE_USER_HOME_PATH.resolve("gradle-jdks");

    @BeforeEach
    void before() {
        FileUtils.delete(GRADLE_JDKS_INSTALLATION_DIR);
    }

    @Test
    @WithCustomGradleUserHome(gradleUserHome = TMP_GRADLE_USER_HOME)
    void non_existing_jdks_are_installed_by_the_settings_plugin(GradleInvoker gradle, RootProject rootProject)
            throws IOException {
        rootProject.buildGradle().plugins().add("java").add("com.palantir.jdks");
        rootProject.buildGradle().append("""
            jdks {
                %s
                daemonTarget = '17'
            }
            """, TestResources.JDK_17.toJdkExtension());

        rootProject.gradlePropertiesFile().setProperty("palantir.jdk.setup.enabled", "true");
        gradle.withArgs("generateGradleJdkConfigs").buildsSuccessfully();

        Path jdk17LocalPath = rootProject.path().resolve(String.format("gradle/jdks/17/%s/%s/local-path", OS, ARCH));
        String originalJdk17LocalPath = Files.readString(jdk17LocalPath).trim();
        Path originalJdkPath =
                GRADLE_JDKS_INSTALLATION_DIR.resolve(originalJdk17LocalPath).toAbsolutePath();
        assertThat(originalJdkPath)
                .as("only gradle configuration files are generated, no jdks are installed")
                .doesNotExist();
        assertThat(rootProject.buildDir().path().resolve("installedJdkPaths"))
                .hasContent(
                        String.format("""
                            17:%s/gradle-jdks/%s
                            """, TMP_GRADLE_USER_HOME_PATH.toRealPath(), TestResources.JDK_17.toFileName()));

        rootProject.settingsGradle().plugins().add("com.palantir.jdks.settings");
        InvocationResult toolchainsResult = gradle.withArgs("javaToolchains").buildsSuccessfully();
        toolchainsResult
                .assertThat()
                .output()
                .as("the jdks are installed by the settings plugin")
                .contains("Gradle JDK setup is enabled (palantir.jdk.setup.enabled is true)"
                        + " but some jdks were not installed")
                .contains("Auto-detection:     Disabled")
                .contains("Auto-download:      Disabled")
                // because we are not setting the gradle.java.home the Current JVM could be different from the recently
                // installed JDK_17
                .contains(TestResources.JDK_17.toFileName());

        assertThat(originalJdkPath)
                .as("expected JDK path was created after javaToolchains")
                .exists();

        String newJdkPath =
                String.format("amazon-corretto-%s", UUID.randomUUID().toString().substring(0, 8));
        Files.writeString(jdk17LocalPath, newJdkPath + "\n");

        gradle.withArgs("javaToolchains")
                .buildsSuccessfully()
                .assertThat()
                .output()
                .as("jdk setup message after path change")
                .contains("Gradle JDK setup is enabled (palantir.jdk.setup.enabled is true)"
                        + " but some jdks were not installed");
        assertThat(GRADLE_JDKS_INSTALLATION_DIR.resolve(newJdkPath))
                .as("new JDK path was created after jdk path change")
                .exists();
    }
}
