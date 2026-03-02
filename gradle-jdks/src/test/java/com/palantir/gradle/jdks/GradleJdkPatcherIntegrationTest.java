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

import static com.palantir.gradle.jdks.JdkDirectoriesAssert.assertThatJdkDirectories;
import static com.palantir.gradle.testing.assertion.GradlePluginTestAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.palantir.gradle.jdks.setup.JdkSetupFailureException;
import com.palantir.gradle.jdks.setup.common.CurrentArch;
import com.palantir.gradle.jdks.setup.common.GradleJdksPatchHelper;
import com.palantir.gradle.jdks.testing.WithJdkAutomanagement;
import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.execution.InvocationResult;
import com.palantir.gradle.testing.junit.DisabledConfigurationCache;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import com.palantir.platform.OperatingSystem;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@GradlePluginTests
@DisabledConfigurationCache
class GradleJdkPatcherIntegrationTest {

    private static final String DAEMON_MAJOR_VERSION_17 = "17";
    private static final Pair<String, String> JDK_11 = Pair.of("azul-zulu", "11.54.25-11.0.14.1");
    private static final Pair<String, String> JDK_17 = Pair.of("amazon-corretto", "17.0.3.6.1");
    private static final Pair<String, String> JDK_21 = Pair.of("amazon-corretto", "21.0.2.13.1");

    @BeforeEach
    void setup(RootProject rootProject) {
        rootProject.buildGradle().plugins().add("java").add("com.palantir.jdks");
    }

    @Nested
    class WithConfiguredJdks {

        @BeforeEach
        void setup(RootProject rootProject) {
            setupJdksHardcodedVersions(rootProject, DAEMON_MAJOR_VERSION_17);
        }

        private static void setupJdksHardcodedVersions(RootProject rootProject, String daemonTarget) {
            rootProject
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

        @Test
        @WithJdkAutomanagement
        void successfully_generates_gradle_jdk_setup_files(GradleInvoker gradle, RootProject rootProject) {
            InvocationResult result = gradle.withArgs("wrapper").buildsSuccessfully();

            result.assertThat().task(":wrapperJdkPatcher").succeeded();

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

            checkJdksVersions(rootProject, 11, 17, 21);

            rootProject
                    .file("gradle/gradle-daemon-jdk-version")
                    .assertThat()
                    .content()
                    .contains(DAEMON_MAJOR_VERSION_17)
                    .as("daemon JDK version matches expected");

            rootProject.file("gradle/gradle-jdks-setup.sh").assertThat().isExecutable();
            rootProject.file("gradle/gradle-jdks-functions.sh").assertThat().isExecutable();

            rootProject
                    .directory("gradle/certs")
                    .assertThat()
                    .as("old gradle/certs directory is removed")
                    .doesNotExist();

            rootProject.file(".gradle/config.properties").assertThat().content().contains("java.home");

            InvocationResult checkResult = gradle.withArgs("check").buildsSuccessfully();
            checkResult.assertThat().task(":checkGradleJdkConfigs").succeeded();
            checkResult.assertThat().task(":checkWrapperJdkPatcher").succeeded();

            InvocationResult secondCheckResult = gradle.withArgs("check").buildsSuccessfully();
            secondCheckResult.assertThat().task(":checkGradleJdkConfigs").upToDate();
            secondCheckResult.assertThat().task(":checkWrapperJdkPatcher").upToDate();
        }

        @Test
        @WithJdkAutomanagement
        void fails_if_gradle_jdk_configuration_is_wrong(GradleInvoker gradle, RootProject rootProject) {
            rootProject.buildGradle().edit(file -> file.replace("daemonTarget = 17", "daemonTarget = 15"));

            assertThatThrownBy(() -> gradle.withArgs("setupJdks").buildsWithFailure())
                    .isInstanceOf(JdkSetupFailureException.class)
                    .hasMessageContaining("Gradle daemon JDK version is `15` but no JDK configured for that version.");
        }

        @Test
        void check_gradle_jdk_configs_fails_if_run_before_setup_jdks(GradleInvoker gradle, RootProject rootProject) {
            gradle.withArgs("wrapper").buildsSuccessfully();
            rootProject.gradlePropertiesFile().setProperty("palantir.jdk.setup.enabled", "true");
            rootProject.buildGradle().append("""
                jdks {
                    daemonTarget = 21
                }
                """);

            gradle.withArgs("check")
                    .buildsWithFailure()
                    .assertThat()
                    .output()
                    .contains("is out of date, please run `./gradlew setupJdks`");
        }
    }

    @Test
    void fails_if_no_jdks_were_configured(GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().append("""
            jdks {
                daemonTarget = 11
            }
            """);
        gradle.withArgs("wrapper").buildsSuccessfully();
        rootProject.gradlePropertiesFile().setProperty("palantir.jdk.setup.enabled", "true");

        InvocationResult result = gradle.withArgs("setupJdks").buildsWithFailure();

        result.assertThat().task(":generateGradleJdkConfigs").failed();
        result.assertThat().task(":wrapperJdkPatcher").notOnTaskGraph();
        result.assertThat().output().contains("No JDKs were configured for the gradle setup");
    }

    @Test
    void no_gradle_wrapper_patch_if_jdk_setup_not_enabled(GradleInvoker gradle, RootProject rootProject) {
        InvocationResult output = gradle.withArgs("wrapper").buildsSuccessfully();

        assertThat(output).task(":wrapperJdkPatcher").notOnTaskGraph();
        rootProject
                .file("gradlew")
                .assertThat()
                .content()
                .as("gradlew does not contain reference to setup script")
                .doesNotContain("gradle-jdks-setup.sh");
    }

    private static void checkJdksVersions(RootProject rootProject, int... versions) {
        assertThatJdkDirectories(rootProject)
                .as("JDK version directories match expected versions")
                .containsExactJdks(versions);

        String osName = OperatingSystem.get().uiName();
        String archName = CurrentArch.get().uiName();
        for (int version : versions) {
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
        }
    }
}
