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
import com.palantir.gradle.testing.files.gradle.GradleFile;
import com.palantir.gradle.testing.junit.DisabledConfigurationCache;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import com.palantir.platform.OperatingSystem;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

@GradlePluginTests
@DisabledConfigurationCache("gradle-jdks setupJdks/check tasks modify wrapper and configuration files")
class GradleJdkPatcherIntegrationTest {

    private static final String DAEMON_MAJOR_VERSION_17 = "17";
    private static final Pair<String, String> JDK_11 = Pair.of("azul-zulu", "11.54.25-11.0.14.1");
    private static final Pair<String, String> JDK_17 = Pair.of("amazon-corretto", "17.0.3.6.1");
    private static final Pair<String, String> JDK_21 = Pair.of("amazon-corretto", "21.0.2.13.1");

    private static void applyJdksPlugins(RootProject rootProject) {
        rootProject.settingsGradle().plugins().add("com.palantir.jdks.settings");
        rootProject.buildGradle().plugins().add("java").add("com.palantir.jdks").add("com.palantir.jdks.palantir-ca");
    }

    private static GradleFile setupJdksHardcodedVersions(RootProject rootProject) {
        return setupJdksHardcodedVersions(rootProject, DAEMON_MAJOR_VERSION_17);
    }

    private static GradleFile setupJdksHardcodedVersions(RootProject rootProject, String daemonTarget) {
        applyJdksPlugins(rootProject);
        return rootProject
                .buildGradle()
                .append(
                        """
                        jdks {
                            jdk(11) {
                                distribution = '%s'
                                jdkVersion = '%s'
                            }
                            jdk(17) {
                                distribution = '%s'
                                jdkVersion = '%s'
                            }
                            jdk(21) {
                                distribution = '%s'
                                jdkVersion = '%s'
                            }
                            daemonTarget = %s
                        }
                        """,
                        JDK_11.getLeft(),
                        JDK_11.getRight(),
                        JDK_17.getLeft(),
                        JDK_17.getRight(),
                        JDK_21.getLeft(),
                        JDK_21.getRight(),
                        daemonTarget);
    }

    private static void checkJdksVersions(RootProject rootProject, Set<String> versions) {
        try (Stream<Path> paths = Files.list(rootProject.path().resolve("gradle/jdks"))) {
            Assertions.assertThat(paths.filter(Files::isDirectory)
                            .map(path -> path.getFileName().toString())
                            .collect(Collectors.toSet()))
                    .as("JDK version directories match expected versions")
                    .isEqualTo(versions);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        String osName = OperatingSystem.get().uiName();
        String archName = CurrentArch.get().uiName();
        versions.stream().findFirst().ifPresent(version -> {
            rootProject
                    .file("gradle/jdks/" + version + "/" + osName + "/" + archName + "/download-url")
                    .assertThat()
                    .as("download-url file exists for version " + version)
                    .exists();
            rootProject
                    .file("gradle/jdks/" + version + "/" + osName + "/" + archName + "/local-path")
                    .assertThat()
                    .as("local-path file exists for version " + version)
                    .exists();
        });
    }

    @Test
    void successfully_generates_gradle_jdk_setup_files(GradleInvoker gradle, RootProject rootProject) {
        setupJdksHardcodedVersions(rootProject);
        gradle.withArgs("wrapper").buildsSuccessfully();
        rootProject.gradlePropertiesFile().setProperty("palantir.jdk.setup.enabled", "true");

        // when: 'running setupJdks'
        InvocationResult result = gradle.withArgs("setupJdks").buildsSuccessfully();

        // then: 'it triggers the execution of Gradle JDK setup tasks'
        assertThat(result).task(":wrapperJdkPatcher").succeeded();
        assertThat(result).task(":generateGradleJdkConfigs").succeeded();

        // and: './gradlew file is patched'
        rootProject
                .file("gradlew")
                .assertThat()
                .content()
                .as("gradlew contains reference to gradle-jdks-setup.sh")
                .contains("gradle/gradle-jdks-setup.sh")
                .as("gradlew has exactly one patch header")
                .containsOnlyOnce(GradleJdksPatchHelper.PATCH_HEADER)
                .as("gradlew has exactly one patch footer")
                .containsOnlyOnce(GradleJdksPatchHelper.PATCH_FOOTER);

        // and: 'the `gradle/` configuration files are generated'
        checkJdksVersions(rootProject, Set.of("11", "17", "21"));

        Assertions.assertThat(rootProject
                        .file("gradle/gradle-daemon-jdk-version")
                        .text()
                        .trim())
                .as("daemon JDK version matches expected")
                .isEqualTo(DAEMON_MAJOR_VERSION_17);

        rootProject
                .file("gradle/gradle-jdks-setup.sh")
                .assertThat()
                .as("gradle-jdks-setup.sh is executable")
                .isExecutable();
        rootProject
                .file("gradle/gradle-jdks-functions.sh")
                .assertThat()
                .as("gradle-jdks-functions.sh is executable")
                .isExecutable();

        // and: 'old gradle jdk paths are removed'
        rootProject
                .directory("gradle/certs")
                .assertThat()
                .as("old gradle/certs directory is removed")
                .doesNotExist();

        // and: '.gradle/config.properties configures java.home'
        rootProject
                .file(".gradle/config.properties")
                .assertThat()
                .content()
                .as("config.properties contains java.home")
                .contains("java.home");

        // when: 'running check'
        InvocationResult checkResult = gradle.withArgs("check").buildsSuccessfully();

        assertThat(checkResult).task(":checkGradleJdkConfigs").succeeded();
        assertThat(checkResult).task(":checkWrapperJdkPatcher").succeeded();

        // when: 'running the second check'
        InvocationResult secondCheckResult = gradle.withArgs("check").buildsSuccessfully();

        assertThat(secondCheckResult).task(":checkGradleJdkConfigs").upToDate();
        assertThat(secondCheckResult).task(":checkWrapperJdkPatcher").upToDate();
    }

    @Test
    void fails_if_gradle_jdk_configuration_is_wrong(GradleInvoker gradle, RootProject rootProject) {
        setupJdksHardcodedVersions(rootProject, "15");
        rootProject.gradlePropertiesFile().setProperty("palantir.jdk.setup.enabled", "true");

        // when: 'running setupJdks'
        InvocationResult result = gradle.withArgs("setupJdks").buildsWithFailure();

        // then: 'generateGradleJdkConfigs fails'
        assertThat(result).task(":generateGradleJdkConfigs").failed();
        assertThat(result).task(":wrapperJdkPatcher").notOnTaskGraph();
        assertThat(result)
                .output()
                .contains("Gradle daemon JDK version is `15` but no JDK configured for that version.");
    }

    @Test
    void fails_if_no_jdks_were_configured(GradleInvoker gradle, RootProject rootProject) {
        applyJdksPlugins(rootProject);
        rootProject.buildGradle().append("""
            jdks {
                daemonTarget = 11
            }
            """);
        gradle.withArgs("wrapper").buildsSuccessfully();
        rootProject.gradlePropertiesFile().setProperty("palantir.jdk.setup.enabled", "true");

        // when: 'running setupJdks'
        InvocationResult result = gradle.withArgs("setupJdks").buildsWithFailure();

        // then: 'generateGradleJdkConfigs fails'
        assertThat(result).task(":generateGradleJdkConfigs").failed();
        assertThat(result).task(":wrapperJdkPatcher").notOnTaskGraph();
        assertThat(result).output().contains("No JDKs were configured for the gradle setup");
    }

    @Test
    void check_gradle_jdk_configs_fails_if_run_before_setup_jdks(GradleInvoker gradle, RootProject rootProject) {
        setupJdksHardcodedVersions(rootProject);
        gradle.withArgs("wrapper").buildsSuccessfully();
        rootProject.gradlePropertiesFile().setProperty("palantir.jdk.setup.enabled", "true");

        // when: 'running check'
        InvocationResult checkResult = gradle.withArgs("check").buildsWithFailure();

        assertThat(checkResult).task(":checkGradleJdkConfigs").failed();
        assertThat(checkResult).output().contains("is out of date, please run `./gradlew setupJdks`");
    }

    @Test
    void no_gradle_wrapper_patch_if_jdk_setup_not_enabled(GradleInvoker gradle, RootProject rootProject) {
        setupJdksHardcodedVersions(rootProject);

        InvocationResult output = gradle.withArgs("wrapper").buildsSuccessfully();

        assertThat(output).task(":wrapperJdkPatcher").notOnTaskGraph();
        rootProject
                .file("gradlew")
                .assertThat()
                .content()
                .as("gradlew does not contain reference to setup script")
                .doesNotContain("gradle-jdks-setup.sh");
    }
}
