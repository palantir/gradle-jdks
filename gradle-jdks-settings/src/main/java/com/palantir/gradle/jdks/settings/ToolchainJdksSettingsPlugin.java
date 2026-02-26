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

// CHECKSTYLE.OFF: IllegalImport

import com.palantir.gradle.jdks.enablement.GradleJdksEnablement;
import com.palantir.gradle.jdks.setup.common.Arch;
import com.palantir.gradle.jdks.setup.common.CommandRunner;
import com.palantir.gradle.jdks.setup.common.CurrentArch;
import com.palantir.gradle.jdks.setup.common.GradleJdksDirectories;
import com.palantir.platform.GradleOperatingSystem;
import com.palantir.platform.OperatingSystem;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.gradle.api.Plugin;
import org.gradle.api.initialization.Settings;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.Nested;
import org.gradle.initialization.DefaultSettings;
import org.gradle.internal.jvm.inspection.JavaInstallationRegistry;
import org.gradle.jvm.toolchain.internal.InstallationLocation;
import org.gradle.util.GradleVersion;

/**
 * A plugin that changes the Gradle JDK properties (via reflection) to point to the local toolchains configured via the
 * Gradle JDK Setup.
 * @see <a href=/Volumes/git/gradle-jdks/gradle-jdks-setup/README.md>Gradle JDK Readme</a>
 */
public abstract class ToolchainJdksSettingsPlugin implements Plugin<Settings> {

    private static final Logger logger = Logging.getLogger(ToolchainJdksSettingsPlugin.class);

    @Nested
    protected abstract GradleOperatingSystem getOperatingSystem();

    @SuppressWarnings("for-rollout:PatternMatchingInstanceof")
    @Override
    public final void apply(Settings settings) {
        OperatingSystem os = getOperatingSystem().getOperatingSystem().get();

        Path rootProjectDir = settings.getRootDir().toPath();
        if (!GradleJdksEnablement.isGradleJdkSetupEnabled(os, rootProjectDir)) {
            logger.debug("Skipping Gradle JDK gradle properties patching");
            return;
        }
        if (!isGradleVersionSupported()) {
            throw new RuntimeException(String.format(
                    "Cannot apply `com.palantir.jdks` with Gradle JDK setup enabled for Gradle version < %s."
                            + " Please upgrade to a higher Gradle version in order to use the JDK setup.",
                    GradleJdksEnablement.MINIMUM_SUPPORTED_GRADLE_VERSION));
        }
        Path gradleJdksLocalDirectory = rootProjectDir.resolve("gradle/jdks");
        // Not failing here because the plugin might be applied before the `./gradlew setupJdks` is run, hence not
        // having the expected directory structure.
        if (!Files.exists(gradleJdksLocalDirectory)) {
            logger.info("Not setting the Gradle JDK properties because gradle/jdks directory doesn't exist. Please run"
                    + " ./gradlew setupJdks to set up the JDKs.");
            return;
        }
        // Validate that auto-detect and auto-download are disabled, unless setupJdks is in the
        // task graph (which will write these properties itself). We use taskGraph.whenReady rather
        // than checking getStartParameter().getTaskNames() so that the bypass also works when
        // setupJdks is a transitive dependency of the requested task.
        settings.getGradle().getTaskGraph().whenReady(taskGraph -> {
            boolean hasSetupJdks = taskGraph.getAllTasks().stream()
                    .anyMatch(task -> task.getName().equals("setupJdks"));
            if (!hasSetupJdks) {
                validateGradleProperty(settings, "org.gradle.java.installations.auto-detect", "false");
                validateGradleProperty(settings, "org.gradle.java.installations.auto-download", "false");
            }
        });

        // Forces the installation of the configured jdks if they are not installed. Fixes the case when a user doesn't
        // have the Intellij plugin installed and some jdks are missing.
        List<Path> installedJdkPaths = getOrInstallJdkPaths(rootProjectDir, gradleJdksLocalDirectory, os);

        JavaInstallationRegistry javaInstallationRegistry =
                ((DefaultSettings) settings).getGradle().getServices().get(JavaInstallationRegistry.class);

        installedJdkPaths.forEach(jdkPath -> {
            javaInstallationRegistry.addInstallation(
                    InstallationLocation.userDefined(jdkPath.toFile(), "gradle-jdks: " + jdkPath.getFileName()));
        });
    }

    private static void validateGradleProperty(Settings settings, String propertyName, String expectedValue) {
        String actualValue =
                settings.getProviders().gradleProperty(propertyName).getOrNull();
        if (!expectedValue.equals(actualValue)) {
            throw new RuntimeException(String.format(
                    "gradle-jdks requires %s=%s in gradle.properties but found '%s'."
                            + " Run ./gradlew setupJdks to configure this automatically.",
                    propertyName, expectedValue, actualValue == null ? "<not set>" : actualValue));
        }
    }

    private static List<Path> getOrInstallJdkPaths(
            Path rootProjectDir, Path gradleJdksLocalDirectory, OperatingSystem operatingSystem) {
        List<Path> jdkPaths = getConfiguredJdkPaths(gradleJdksLocalDirectory, operatingSystem);
        List<Path> missingJdkPaths = getMissingPaths(jdkPaths);
        if (!missingJdkPaths.isEmpty()) {
            logger.error(
                    "Gradle JDK setup is enabled (palantir.jdk.setup.enabled is true) but some jdks were not"
                            + " installed: {}. If running from Intellij, please make sure the"
                            + " `palantir-gradle-jdks` Intellij plugin is installed"
                            + " https://plugins.jetbrains.com/plugin/24776-palantir-gradle-jdks/versions."
                            + " To unblock the workflow, the jdks will be manually installed now ...",
                    missingJdkPaths);
            runGradleJdkSetup(rootProjectDir);
        }
        return jdkPaths;
    }

    private static List<Path> getConfiguredJdkPaths(Path gradleJdksLocalDirectory, OperatingSystem os) {
        Path installationDirectory = GradleJdksDirectories.getToolchainInstallationDir();
        Arch arch = CurrentArch.get();
        try (Stream<Path> stream = Files.list(gradleJdksLocalDirectory).filter(Files::isDirectory)) {
            return stream.map(path ->
                            path.resolve(os.toString()).resolve(arch.toString()).resolve("local-path"))
                    .map(path -> resolveJdkPath(path, installationDirectory))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to list the local JDK installation paths", e);
        }
    }

    private static Path resolveJdkPath(Path gradleJdkConfigurationPath, Path installationDirectory) {
        try {
            String localFilename = Files.readString(gradleJdkConfigurationPath).trim();
            return installationDirectory.resolve(localFilename);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    String.format("Failed to read gradle jdk configuration file %s", gradleJdkConfigurationPath), e);
        }
    }

    private static boolean isGradleVersionSupported() {
        return GradleVersion.current()
                        .compareTo(GradleVersion.version(GradleJdksEnablement.MINIMUM_SUPPORTED_GRADLE_VERSION))
                >= 0;
    }

    private static void runGradleJdkSetup(Path rootProjectDir) {
        Path buildDirectory = rootProjectDir.resolve("build");
        createDirectories(buildDirectory);
        CommandRunner.runWithLogger(
                new ProcessBuilder().command("./gradle/gradle-jdks-setup.sh").directory(rootProjectDir.toFile()),
                ToolchainJdksSettingsPlugin::writeStdOutput,
                ToolchainJdksSettingsPlugin::writeStdErr);
    }

    private static void writeStdOutput(InputStream inputStream) {
        CommandRunner.processStream(inputStream, logger::lifecycle);
    }

    private static void writeStdErr(InputStream inputStream) {
        CommandRunner.processStream(inputStream, logger::error);
    }

    private static void createDirectories(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create directory", e);
        }
    }

    private static List<Path> getMissingPaths(List<Path> paths) {
        return paths.stream().filter(path -> !Files.exists(path)).collect(Collectors.toList());
    }
}
