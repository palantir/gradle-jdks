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

import com.google.common.collect.ImmutableList;
import com.palantir.gradle.testing.execution.GradleInvocation;
import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.project.RootProject;
import com.palantir.platform.Architecture;
import com.palantir.platform.OperatingSystem;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A GradleInvoker that sets up JDK automanagement before running builds.
 *
 * <p>This invoker:
 * <ol>
 *   <li>Configures the test project with JDK plugins and properties</li>
 *   <li>Runs {@code setupJdks} to download and configure JDKs</li>
 *   <li>Runs the actual build with the appropriate JDK configured</li>
 * </ol>
 */
final class GradleWithJdksInvoker implements GradleInvoker {

    private static final Architecture ARCH = Architecture.get();
    private static final OperatingSystem OS = OperatingSystem.get();

    private final GradleInvoker delegate;
    private final RootProject rootProject;

    @SuppressWarnings("RestrictedApi") // Decorator needs to create RootProject from path
    GradleWithJdksInvoker(Path rootProjectDir, GradleInvoker delegate) {
        this.rootProject = new RootProject(rootProjectDir);
        this.delegate = delegate;
    }

    @Override
    public GradleInvocation withArgs(String... args) {
        setupRootProject(rootProject);
        GradleInvocation setupJdkManagement = delegate.withArgs("wrapper", "setupJdks");

        return new GradleWithJdksInvocation(setupJdkManagement, () -> getInvokerWithToolchainsConfigured(args));
    }

    private GradleInvocation getInvokerWithToolchainsConfigured(String... args) {
        String[] withJavaHome = ImmutableList.<String>builder()
                .add(args)
                .add(String.format("-Dorg.gradle.java.home=%s", getGradleJavaHome(rootProject.path())))
                .build()
                .toArray(String[]::new);
        return delegate.withArgs(withJavaHome);
    }

    @SuppressWarnings("checkstyle:NestedTryDepth")
    private static Path getGradleJavaHome(Path rootProjectDir) {
        try {
            String majorVersion = Files.readString(rootProjectDir.resolve("gradle/gradle-daemon-jdk-version"))
                    .trim();

            try (Stream<Path> stream = Files.find(
                    rootProjectDir.resolve(
                            String.format("gradle/jdks/%s/%s/%s", majorVersion, OS.uiName(), ARCH.uiName())),
                    1,
                    (path, _attr) -> path.getFileName().toString().equals("local-path"))) {
                String localPath = stream.findFirst()
                        .map(GradleWithJdksInvoker::readLocalPath)
                        .orElseThrow(() -> new RuntimeException(
                                String.format("Failed to find the JDK local path for majorVersion %s", majorVersion)));
                return getGradleJdksDirectory(localPath);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to retrieve the gradle daemon jdk path", e);
        }
    }

    private static String readLocalPath(Path path) {
        try {
            return Files.readString(path).trim();
        } catch (IOException e) {
            throw new UncheckedIOException(String.format("Failed to read the path %s", path), e);
        }
    }

    private static Path getGradleJdksDirectory(String localJdkPath) {
        return Path.of(Optional.ofNullable(System.getenv("GRADLE_USER_HOME"))
                        .orElseGet(() -> System.getProperty("user.home") + "/.gradle"))
                .resolve("gradle-jdks")
                .resolve(localJdkPath);
    }

    private static void setupRootProject(RootProject rootProject) {
        rootProject.gradlePropertiesFile().appendProperty("palantir.jdk.setup.enabled", "true");
        rootProject.buildGradle().plugins().add("java");
        rootProject.buildGradle().plugins().add("com.palantir.jdks");
        rootProject.settingsGradle().plugins().add("com.palantir.jdks.settings");
    }
}
