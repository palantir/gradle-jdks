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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.palantir.gradle.jdks.setup.common.GradleJdksPatchHelper;
import com.palantir.gradle.testing.assertion.GradlePluginTestAssertions;
import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.execution.InvocationResult;
import com.palantir.gradle.testing.junit.DisabledConfigurationCache;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@GradlePluginTests
@DisabledConfigurationCache
class GradleJdkPatcherIntegrationTest {

    @BeforeEach
    void setup(RootProject rootProject) {
        rootProject.buildGradle().plugins().add("java").add("com.palantir.jdks");
        rootProject.settingsGradle().plugins().add("com.palantir.jdks.settings");
        rootProject.gradlePropertiesFile().appendProperty("palantir.jdk.setup.enabled", "true");
        rootProject.buildGradle().plugins().add("com.palantir.jdks.palantir-ca");

        rootProject.buildGradle().append("""
            jdks {
               jdk(11) {
                  distribution = 'azul-zulu'
                  jdkVersion = '11.54.25-11.0.14.1'
               }

               jdk(17) {
                  distribution = 'amazon-corretto'
                  jdkVersion = '17.0.3.6.1'
               }

               jdk(21) {
                  distribution = 'amazon-corretto'
                  jdkVersion = '21.0.2.13.1'
               }

               daemonTarget = 17
            }
            """);
    }

    @Test
    void successfully_generates_gradle_jdk_setup_files(GradleInvoker gradle, RootProject rootProject) {
        InvocationResult wrapperResult = gradle.withArgs("wrapper").buildsSuccessfully();
        GradlePluginTestAssertions.assertThat(wrapperResult)
                .task(":wrapperJdkPatcher")
                .succeeded();
        GradlePluginTestAssertions.assertThat(wrapperResult)
                .task(":generateGradleJdkConfigs")
                .succeeded();

        rootProject.file("gradlew").assertThat().content().contains("gradle/gradle-jdks-setup.sh");
        String gradlewContent = rootProject.file("gradlew").text();
        assertThat(gradlewContent.split(GradleJdksPatchHelper.PATCH_HEADER).length - 1)
                .isEqualTo(1);
        assertThat(gradlewContent.split(GradleJdksPatchHelper.PATCH_FOOTER).length - 1)
                .isEqualTo(1);

        checkJdksVersions(rootProject, Set.of("11", "17", "21"));
        rootProject
                .file("gradle/gradle-daemon-jdk-version")
                .assertThat()
                .content()
                .isEqualTo("17\n");
        rootProject.file("gradle/gradle-jdks-setup.sh").assertThat().exists();
        rootProject.file("gradle/gradle-jdks-functions.sh").assertThat().exists();
        rootProject.file("gradle/certs").assertThat().doesNotExist();

        InvocationResult checkResult = gradle.withArgs("check").buildsSuccessfully();
        assertThat(checkResult).task(":checkGradleJdkConfigs").succeeded();
        assertThat(checkResult).task(":checkWrapperJdkPatcher").succeeded();

        InvocationResult secondCheckResult = gradle.withArgs("check").buildsSuccessfully();
        assertThat(secondCheckResult).task(":checkGradleJdkConfigs").upToDate();
        assertThat(secondCheckResult).task(":checkWrapperJdkPatcher").upToDate();
    }

    @Test
    void fails_if_no_jdks_were_configured(GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().append("""
            jdks {
               daemonTarget = 24
            }
            """);
        assertThatThrownBy(() -> gradle.withArgs("wrapper").buildsSuccessfully())
                .hasMessageContaining("Gradle daemon JDK version is `24` but no JDK configured for that version.");
    }

    @Test
    void checkGradleJdkConfigs_fails_if_run_before_setupJdks(GradleInvoker gradle, RootProject rootProject) {
        rootProject.gradlePropertiesFile().appendProperty("palantir.jdk.setup.enabled", "false");
        gradle.withArgs("wrapper").buildsSuccessfully();

        rootProject.gradlePropertiesFile().appendProperty("palantir.jdk.setup.enabled", "true");
        InvocationResult checkResult = gradle.withArgs("check").buildsWithFailure();

        assertThat(checkResult).task(":checkGradleJdkConfigs").failed();
        assertThat(checkResult).output().contains("is out of date, please run `./gradlew setupJdks`");
    }

    // TODO change this to a new test
    @Test
    void no_gradleWrapper_patch_if_palantir_jdk_setup_enabled_false(GradleInvoker gradle, RootProject rootProject) {
        rootProject.gradlePropertiesFile().appendProperty("palantir.jdk.setup.enabled", "false");
        InvocationResult result = gradle.withArgs("wrapper").buildsSuccessfully();

        assertThat(result).task(":wrapperJdkPatcher").notOnTaskGraph();
        rootProject.file("gradlew").assertThat().content().doesNotContain("gradle-jdks-setup.sh");
    }

    private void checkJdksVersions(RootProject rootProject, Set<String> versions) {
        try (java.util.stream.Stream<Path> paths =
                Files.list(rootProject.directory("gradle/jdks").path())) {
            Set<String> actualVersions = paths.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toSet());
            assertThat(actualVersions).isEqualTo(versions);

            // Check that required files exist for at least one version
            String firstVersion = versions.iterator().next();
            rootProject
                    .file("gradle/jdks/" + firstVersion + "/macos/aarch64/download-url")
                    .assertThat()
                    .exists();
            rootProject
                    .file("gradle/jdks/" + firstVersion + "/macos/aarch64/local-path")
                    .assertThat()
                    .exists();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
