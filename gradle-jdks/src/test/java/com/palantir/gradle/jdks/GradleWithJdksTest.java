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

import com.google.common.collect.ImmutableList;
import com.palantir.gradle.jdks.setup.common.GradleJdksDirectories;
import com.palantir.gradle.jdks.testing.WithJdkAutomanagement;
import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.execution.InvocationResult;
import com.palantir.gradle.testing.junit.DisabledConfigurationCache;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@GradlePluginTests
@DisabledConfigurationCache("gradle-jdks is not compatible atm with CC")
@WithJdkAutomanagement
public class GradleWithJdksTest {

    private static final Pattern locationPattern = Pattern.compile("Location:\\s+(.*)");
    private static final Pattern languageVersionPattern = Pattern.compile(" Language Version:\\s+(\\d+)");
    private static final String java21Version = "21.0.9.10.1";
    private static final String java17Version = "17.0.17.10.1";
    private static final Path expectedGradleJdks21Dir = GradleJdksDirectories.getToolchainInstallationDir()
            .resolve(String.format("amazon-corretto-%s", java21Version));
    private static final Path expectedGradleJdks17Dir = GradleJdksDirectories.getToolchainInstallationDir()
            .resolve(String.format("amazon-corretto-%s", java17Version));

    @Test
    void javaToolchains_are_correctly_set(GradleInvoker invoker, RootProject rootProject) {
        addJdks21Setup(rootProject);
        assertJavaToolchainsMatch(invoker, toolchain -> toolchain.startsWith(expectedGradleJdks21Dir.toString()));

        updateJdksAndCheckSetupJdks(invoker, rootProject);
    }

    @Nested
    class CheckJavaToolchains {

        @BeforeEach
        void setup(GradleInvoker invoker, RootProject rootProject) {
            addJdks21Setup(rootProject);
            assertJavaToolchainsMatch(invoker, toolchain -> toolchain.startsWith(expectedGradleJdks21Dir.toString()));
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
        Matcher matcher = languageVersionPattern.matcher(result.output());
        ImmutableList.Builder<String> versionsBuilder = ImmutableList.builder();
        while (matcher.find()) {
            versionsBuilder.add(matcher.group(1));
        }
        List<String> versions = versionsBuilder.build();
        assertThat(versions).contains("11", "21");
    }

    @Test
    void fails_when_jdk_is_not_configured(GradleInvoker invoker, RootProject rootProject) {
        rootProject.buildGradle().append("""
            jdks {
                daemonTarget = 24
                jdk(21) {
                    distribution = 'amazon-corretto'
                    jdkVersion = '21.0.9.10.1'
                }
            }
            """);
        assertThatThrownBy(() -> invoker.withArgs("javaToolchains").buildsSuccessfully())
                .hasMessageContaining("Gradle daemon JDK version is `24` but no JDK configured for that version.");
    }

    private static void assertJavaToolchainsMatch(GradleInvoker invoker, Predicate<? super String> predicate) {
        InvocationResult result = invoker.withArgs("javaToolchains").buildsSuccessfully();
        result.assertThat().output().contains("Auto-detection:     Disabled");
        result.assertThat().output().contains("Auto-download:      Disabled");
        assertThat(locationPattern
                        .matcher(result.output())
                        .results()
                        .map(matchResult -> matchResult.group(1))
                        .toList())
                .allMatch(predicate);
    }

    private static void addJdks21Setup(RootProject rootProject) {
        rootProject.buildGradle().append("""
            jdks {
                daemonTarget = 21
                jdk(21) {
                    distribution = 'amazon-corretto'
                    jdkVersion = '%s'
                }
            }
            """, java21Version);
    }

    private static void updateJdksAndCheckSetupJdks(GradleInvoker invoker, RootProject rootProject) {
        // changing the jdks will not run again `setupJdks`
        rootProject.buildGradle().append("""
            jdks {
                jdk(17) {
                    distribution = 'amazon-corretto'
                    jdkVersion = '%s'
                }
            }
            """, java17Version);
        assertJavaToolchainsMatch(invoker, toolchain -> toolchain.startsWith(expectedGradleJdks21Dir.toString()));

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
                toolchain -> toolchain.startsWith(expectedGradleJdks21Dir.toString())
                        || toolchain.startsWith(expectedGradleJdks17Dir.toString()));
    }
}
