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

package com.palantir.gradle.jdks;

import static com.palantir.gradle.testing.assertion.GradlePluginTestAssertions.assertThat;

import com.palantir.gradle.jdks.setup.common.CurrentArch;
import com.palantir.gradle.jdks.setup.common.GradleJdksPatchHelper;
import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.execution.InvocationResult;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import com.palantir.platform.OperatingSystem;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@GradlePluginTests
class GradleJdkPatcherIntegrationTest extends GradleJdkIntegrationSpec {

    @TempDir
    Path workingDir;

    @Test
    void successfully_generates_gradle_jdk_setup_files(GradleInvoker gradle, RootProject project) throws IOException {
        GradleJdkTestUtils.setupJdksHardcodedVersions(
                project.settingsGradle().path(), project.buildGradle().path());
        gradle.withArgs("wrapper").buildsSuccessfully();
        project.gradlePropertiesFile().append("palantir.jdk.setup.enabled=true");

        // running setupJdks
        InvocationResult result = gradle.withArgs("setupJdks").buildsSuccessfully();

        // it triggers the execution of Gradle JDK setup tasks
        assertThat(result).task(":wrapperJdkPatcher").succeeded();
        assertThat(result).task(":generateGradleJdkConfigs").succeeded();

        // ./gradlew file is patched
        String gradlewContent = Files.readString(project.path().resolve("gradlew"));
        org.assertj.core.api.Assertions.assertThat(gradlewContent).contains("gradle/gradle-jdks-setup.sh");
        int patchHeaderCount = countOccurrences(gradlewContent, GradleJdksPatchHelper.PATCH_HEADER);
        org.assertj.core.api.Assertions.assertThat(patchHeaderCount).isEqualTo(1);
        int patchFooterCount = countOccurrences(gradlewContent, GradleJdksPatchHelper.PATCH_FOOTER);
        org.assertj.core.api.Assertions.assertThat(patchFooterCount).isEqualTo(1);

        // the `gradle/` configuration files are generated
        checkJdksVersions(project.path().toFile(), Set.of("11", "17", "21"));
        String daemonVersion = Files.readString(project.path().resolve("gradle/gradle-daemon-jdk-version"))
                .trim();
        org.assertj.core.api.Assertions.assertThat(daemonVersion).isEqualTo(GradleJdkTestUtils.DAEMON_MAJOR_VERSION_17);
        Path scriptPath = project.path().resolve("gradle/gradle-jdks-setup.sh");
        org.assertj.core.api.Assertions.assertThat(Files.isExecutable(scriptPath))
                .isTrue();
        Path functionsPath = project.path().resolve("gradle/gradle-jdks-functions.sh");
        org.assertj.core.api.Assertions.assertThat(Files.isExecutable(functionsPath))
                .isTrue();

        // old gradle jdk paths are removed
        Path oldPath = project.path().resolve("gradle/certs");
        org.assertj.core.api.Assertions.assertThat(Files.exists(oldPath)).isFalse();

        // .gradle/config.properties configures java.home
        String configProps = Files.readString(project.path().resolve(".gradle/config.properties"));
        org.assertj.core.api.Assertions.assertThat(configProps).contains("java.home");

        // running check
        InvocationResult checkResult = gradle.withArgs("check").buildsSuccessfully();

        assertThat(checkResult).task(":checkGradleJdkConfigs").succeeded();
        assertThat(checkResult).task(":checkWrapperJdkPatcher").succeeded();

        // running the second check
        InvocationResult secondCheckResult = gradle.withArgs("check").buildsSuccessfully();

        assertThat(secondCheckResult).task(":checkGradleJdkConfigs").upToDate();
        assertThat(secondCheckResult).task(":checkWrapperJdkPatcher").upToDate();
    }

    @Test
    void fails_if_gradle_jdk_configuration_is_wrong(GradleInvoker gradle, RootProject project) {
        GradleJdkTestUtils.setupJdksHardcodedVersions(
                project.settingsGradle().path(), project.buildGradle().path(), "15");
        project.gradlePropertiesFile().append("palantir.jdk.setup.enabled=true");

        // running setupJdks
        InvocationResult result = gradle.withArgs("setupJdks").buildsWithFailure();

        // generateGradleJdkConfigs fails
        assertThat(result).task(":generateGradleJdkConfigs").failed();
        assertThat(result).task(":wrapperJdkPatcher").notOnTaskGraph();
        assertThat(result)
                .output()
                .contains("Gradle daemon JDK version is `15` but no JDK configured for that version.");
    }

    @Test
    void fails_if_no_jdks_were_configured(GradleInvoker gradle, RootProject project) {
        GradleJdkTestUtils.applyJdksPlugins(
                project.settingsGradle().path(), project.buildGradle().path());
        project.buildGradle()
                .append("""
            jdks {
               daemonTarget = 11
            }
            """);
        gradle.withArgs("wrapper").buildsSuccessfully();
        project.gradlePropertiesFile().append("palantir.jdk.setup.enabled=true");

        // running setupJdks
        InvocationResult result = gradle.withArgs("setupJdks").buildsWithFailure();

        // generateGradleJdkConfigs fails
        assertThat(result).task(":generateGradleJdkConfigs").failed();
        assertThat(result).task(":wrapperJdkPatcher").notOnTaskGraph();
        assertThat(result).output().contains("No JDKs were configured for the gradle setup");
    }

    @Test
    void check_gradle_jdk_configs_fails_if_run_before_setup_jdks(GradleInvoker gradle, RootProject project) {
        GradleJdkTestUtils.setupJdksHardcodedVersions(
                project.settingsGradle().path(), project.buildGradle().path());
        gradle.withArgs("wrapper").buildsSuccessfully();
        project.gradlePropertiesFile().append("palantir.jdk.setup.enabled=true");

        // running check
        InvocationResult checkResult = gradle.withArgs("check").buildsWithFailure();

        assertThat(checkResult).task(":checkGradleJdkConfigs").failed();
        assertThat(checkResult).output().contains("is out of date, please run `./gradlew setupJdks`");
    }

    @Test
    void no_gradle_wrapper_patch_if_palantir_jdk_setup_enabled_is_false(GradleInvoker gradle, RootProject project)
            throws IOException {
        GradleJdkTestUtils.setupJdksHardcodedVersions(
                project.settingsGradle().path(), project.buildGradle().path());

        InvocationResult output = gradle.withArgs("wrapper").buildsSuccessfully();

        assertThat(output).task(":wrapperJdkPatcher").notOnTaskGraph();
        String gradlewContent = Files.readString(project.path().resolve("gradlew"));
        org.assertj.core.api.Assertions.assertThat(gradlewContent).doesNotContain("gradle-jdks-setup.sh");
    }

    private static void checkJdksVersions(java.io.File projectDir, Set<String> versions) throws IOException {
        Set<String> actualVersions = Files.list(projectDir.toPath().resolve("gradle/jdks"))
                .filter(Files::isDirectory)
                .map(path -> path.getFileName().toString())
                .collect(Collectors.toSet());
        org.assertj.core.api.Assertions.assertThat(actualVersions).isEqualTo(versions);

        String osName = OperatingSystem.get().uiName();
        String archName = CurrentArch.get().uiName();
        versions.stream().findFirst().ifPresent(version -> {
            Path downloadUrl = projectDir
                    .toPath()
                    .resolve("gradle/jdks/" + version + "/" + osName + "/" + archName + "/download-url");
            org.assertj.core.api.Assertions.assertThat(Files.exists(downloadUrl))
                    .isTrue();
            Path localPath = projectDir
                    .toPath()
                    .resolve("gradle/jdks/" + version + "/" + osName + "/" + archName + "/local-path");
            org.assertj.core.api.Assertions.assertThat(Files.exists(localPath)).isTrue();
        });
    }

    private int countOccurrences(String text, String substring) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(substring, index)) != -1) {
            count++;
            index += substring.length();
        }
        return count;
    }

    @Override
    Path workingDir() {
        return workingDir;
    }
}
