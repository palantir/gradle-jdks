/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.jdks.testing;

import com.palantir.gradle.jdks.setup.JdkSetupFailureException;
import com.palantir.gradle.testing.execution.GradleInvocation;
import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.execution.Options;
import com.palantir.gradle.testing.project.RootProject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * A GradleInvoker that sets up JDK automanagement before running builds.
 *
 * <p>This invoker:
 * <ol>
 *   <li>Configures the test project with JDK plugins and properties</li>
 *   <li>Runs {@code setupJdks} to download and configure JDKs</li>
 *   <li>Runs {@code javaToolchains} to make sure JDKs are properly configured</li>
 *   <li>Runs the actual build with the appropriate JDK configured</li>
 * </ol>
 */
final class GradleWithJdksInvoker implements GradleInvoker {

    private final GradleInvoker delegate;
    private final RootProject rootProject;
    private final ExtensionContext extensionContext;

    GradleWithJdksInvoker(ExtensionContext extensionContext, RootProject rootProject, GradleInvoker delegate) {
        this.extensionContext = extensionContext;
        this.rootProject = rootProject;
        this.delegate = delegate;
    }

    @Override
    public GradleInvocation with(Options options) {
        setupOrCheckRootProject(rootProject);
        if (!GradleWithJdksStore.hasRunJdksSetup(extensionContext)) {
            GradleInvocation setupJdkManagement = delegate.withArgs("setupJdks");
            return new GradleWithJdksInvocation(
                    setupJdkManagement,
                    // java.home is not available until setupJdkManagement has run
                    () -> getInvokerWithToolchainsConfigured(options),
                    // mark the jdksSetup as run
                    () -> GradleWithJdksStore.setHasRun(extensionContext));
        } else {
            return getInvokerWithToolchainsConfigured(options);
        }
    }

    private GradleInvocation getInvokerWithToolchainsConfigured(Options options) {
        return delegate.with(options.asBuilder()
                .addArgs(String.format("-Dorg.gradle.java.home=%s", getGradleJavaHome(rootProject)))
                .build());
    }

    private static Path getGradleJavaHome(RootProject rootProject) {
        try {
            String majorVersion = Files.readString(rootProject.path().resolve("gradle/gradle-daemon-jdk-version"))
                    .trim();
            return Files.readAllLines(rootProject.buildDir().path().resolve("installedJdkPaths")).stream()
                    .map(line -> jdkPathForMajorVersion(line, majorVersion))
                    .filter(Optional::isPresent)
                    .mapMulti(Optional<Path>::ifPresent)
                    .findFirst()
                    .orElseThrow(() -> new JdkSetupFailureException(
                            "Failed to set up JDK automanagement: failed to retrieve the gradle daemon jdk path"));
        } catch (IOException e) {
            throw new JdkSetupFailureException(
                    "Failed to set up JDK automanagement: failed to retrieve the gradle daemon jdk path", e);
        }
    }

    private static Optional<Path> jdkPathForMajorVersion(String line, String majorVersion) {
        Matcher matcher =
                Pattern.compile(String.format("%s:(.*)", majorVersion)).matcher(line);
        if (matcher.matches()) {
            return Optional.of(Path.of(matcher.group(1)));
        }
        return Optional.empty();
    }

    private static void setupOrCheckRootProject(RootProject rootProject) {
        rootProject.gradlePropertiesFile().setProperty("palantir.jdk.setup.enabled", "true");
        rootProject.gradlePropertiesFile().setProperty("org.gradle.java.installations.auto-detect", "false");
        rootProject.gradlePropertiesFile().setProperty("org.gradle.java.installations.auto-download", "false");
        rootProject.buildGradle().plugins().add("java");
        rootProject.buildGradle().plugins().add("com.palantir.jdks");
        rootProject.settingsGradle().plugins().add("com.palantir.jdks.settings");
    }
}
