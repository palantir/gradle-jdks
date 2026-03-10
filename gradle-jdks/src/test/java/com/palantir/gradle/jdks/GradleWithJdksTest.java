/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.palantir.gradle.jdks.setup.JdkSetupFailureException;
import com.palantir.gradle.jdks.testing.WithJdkAutomanagement;
import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.execution.InvocationResult;
import com.palantir.gradle.testing.junit.DisabledConfigurationCache;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import java.util.function.Predicate;
import org.gradle.testkit.runner.UnexpectedBuildFailure;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@GradlePluginTests
@DisabledConfigurationCache("initial migration")
@WithJdkAutomanagement
public class GradleWithJdksTest {

    @Test
    void javaToolchains_are_correctly_set(GradleInvoker invoker, RootProject rootProject) {
        addJdks21Setup(rootProject);
        assertJavaToolchainsMatch(invoker, toolchain -> toolchain.contains(TestResources.JDK_21.toFileName()));

        updateJdksAndCheckSetupJdks(invoker, rootProject);
    }

    @Nested
    class CheckJavaToolchains {

        @BeforeEach
        void setup(GradleInvoker invoker, RootProject rootProject) {
            addJdks21Setup(rootProject);
            assertJavaToolchainsMatch(invoker, toolchain -> toolchain.contains(TestResources.JDK_21.toFileName()));
        }

        @Test
        void jdks_extension_is_changed(GradleInvoker invoker, RootProject rootProject) {
            updateJdksAndCheckSetupJdks(invoker, rootProject);
        }
    }

    @Test
    void baselineJavaVersions_are_correctly_set(GradleInvoker invoker, RootProject rootProject) {
        rootProject.buildGradle().plugins().add("com.palantir.baseline-java-versions");
        rootProject.buildGradle().plugins().add("com.palantir.jdks.latest");
        rootProject.buildGradle().append("""
            javaVersions {
                libraryTarget = '11'
            }

            jdks {
                daemonTarget = 21
            }
            """);

        InvocationResult result = invoker.withArgs("javaToolchains").buildsSuccessfully();
        assertThat(TestResources.getLanguageVersions(result.output()))
                .as(String.format("Expected only JDKs 11 and 21 in the javaToolchains output %s", result.output()))
                .containsOnly(11, 21);
    }

    @Test
    void fails_when_jdk_is_not_configured(GradleInvoker invoker, RootProject rootProject) {
        rootProject.buildGradle().append("""
            jdks {
                daemonTarget = 24
                %s
            }
            """, TestResources.JDK_21.toJdkExtension());

        assertThatThrownBy(() -> invoker.withArgs("javaToolchains").buildsSuccessfully())
                .isInstanceOf(JdkSetupFailureException.class)
                .hasMessageContaining("Gradle daemon JDK version is `24` but no JDK configured for that version.");

        assertThatThrownBy(() -> invoker.withArgs("javaToolchains").buildsWithFailure())
                .isInstanceOf(JdkSetupFailureException.class)
                .hasMessageContaining("Gradle daemon JDK version is `24` but no JDK configured for that version.");
    }

    @Test
    void fails_for_script_errors(GradleInvoker invoker, RootProject rootProject) {
        rootProject.buildGradle().append("""
            throw new RuntimeException("my error")
            """);

        rootProject.sourceSet("main").java().writeClass("""
            class HelloWorld {}
            """);

        assertThatThrownBy(() -> invoker.withArgs("compileJava").buildsSuccessfully())
                .isInstanceOf(UnexpectedBuildFailure.class)
                .hasMessageContaining("my error");

        InvocationResult result = invoker.withArgs("compileJava").buildsWithFailure();
        result.assertThat().output().contains("my error");
    }

    @Test
    void fails_for_missing_jdks(RootProject rootProject, GradleInvoker invoker) {
        rootProject.buildGradle().plugins().add("com.palantir.jdks.latest");
        rootProject.buildGradle().append("""
                java {
                    toolchain {
                        languageVersion = JavaLanguageVersion.of(999) // Non-existent version
                    }
                }

            jdks {
                daemonTarget = 21
            }
            """);

        assertThatThrownBy(() -> invoker.withArgs("compileJava").buildsWithFailure())
                .isInstanceOf(JdkSetupFailureException.class)
                .hasMessageContaining(
                        "Gradle JDK Auto-management is enabled but the java versions=[999] are not configured");

        assertThatThrownBy(() -> invoker.withArgs("compileJava").buildsSuccessfully())
                .isInstanceOf(JdkSetupFailureException.class)
                .hasMessageContaining(
                        "Gradle JDK Auto-management is enabled but the java versions=[999] are not configured");
    }

    private static void assertJavaToolchainsMatch(GradleInvoker invoker, Predicate<? super String> predicate) {
        InvocationResult result = invoker.withArgs("javaToolchains").buildsSuccessfully();
        result.assertThat().output().contains("Auto-detection:     Disabled");
        result.assertThat().output().contains("Auto-download:      Disabled");
        assertThat(TestResources.getDiscoveredLocations(result.output())).allMatch(predicate);
    }

    private static void addJdks21Setup(RootProject rootProject) {
        rootProject.buildGradle().append("""
            jdks {
                daemonTarget = 21
                %s
            }
            """, TestResources.JDK_21.toJdkExtension());
    }

    private static void updateJdksAndCheckSetupJdks(GradleInvoker invoker, RootProject rootProject) {
        // changing the jdks will not run again `setupJdks`
        rootProject.buildGradle().append("""
            jdks {
                %s
            }
            """, TestResources.JDK_17.toJdkExtension());
        assertJavaToolchainsMatch(invoker, toolchain -> toolchain.contains(TestResources.JDK_21.toFileName()));

        // The gradle jdks files are out of date
        invoker.withArgs("checkGradleJdks")
                .buildsWithFailure()
                .assertThat()
                .output()
                .contains("Gradle JDK configuration file `gradle/jdks/17/macos/x86/download-url` is out of date");

        // running manually `setupJdks` makes all jdks available
        invoker.withArgs("setupJdks").buildsSuccessfully();

        assertJavaToolchainsMatch(
                invoker,
                toolchain -> toolchain.contains(TestResources.JDK_21.toFileName())
                        || toolchain.contains(TestResources.JDK_17.toFileName()));
    }
}
