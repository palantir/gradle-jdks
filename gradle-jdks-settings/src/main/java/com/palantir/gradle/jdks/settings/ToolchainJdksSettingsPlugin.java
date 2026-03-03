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
import com.palantir.platform.GradleOperatingSystem;
import com.palantir.platform.OperatingSystem;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import org.gradle.api.Plugin;
import org.gradle.api.initialization.Settings;
import org.gradle.api.internal.properties.GradleProperties;
import org.gradle.api.internal.provider.DefaultProviderFactory;
import org.gradle.api.internal.provider.DefaultValueSourceProviderFactory;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.Nested;
import org.gradle.initialization.DefaultSettings;
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

    @Inject
    protected abstract Gradle getGradle();

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
        Path gradleUserHomeDir = getGradle().getGradleUserHomeDir().toPath();
        // Forces the installation of the configured jdks if they are not installed. Fixes the case when a user doesn't
        // have the Intellij plugin installed and some jdks are missing.
        getOrInstallJdkPaths(rootProjectDir, gradleUserHomeDir, gradleJdksLocalDirectory, os);
        ProviderFactory providerFactory =
                ((DefaultSettings) settings).getServices().get(ProviderFactory.class);
        if (!(providerFactory instanceof DefaultProviderFactory)) {
            throw new RuntimeException(String.format(
                    "Expected providerFactory to be of type '%s' but was '%s'.",
                    ProviderFactory.class.getCanonicalName(),
                    providerFactory.getClass().getCanonicalName()));
        }
        DefaultProviderFactory defaultProviderFactory = (DefaultProviderFactory) providerFactory;
        try {
            Field valueSourceProviderFactory =
                    DefaultProviderFactory.class.getDeclaredField("valueSourceProviderFactory");
            valueSourceProviderFactory.setAccessible(true);

            DefaultValueSourceProviderFactory defaultValueSourceProviderFactory =
                    (DefaultValueSourceProviderFactory) valueSourceProviderFactory.get(defaultProviderFactory);
            Field field = DefaultValueSourceProviderFactory.class.getDeclaredField("gradleProperties");
            field.setAccessible(true);
            GradleProperties originalGradleProperties = (GradleProperties) field.get(defaultValueSourceProviderFactory);
            GradleProperties ourGradleProperties = (GradleProperties) Proxy.newProxyInstance(
                    GradleProperties.class.getClassLoader(),
                    new Class[] {GradleProperties.class},
                    new GradlePropertiesInvocationHandler(
                            rootProjectDir, gradleUserHomeDir, gradleJdksLocalDirectory, originalGradleProperties, os));
            field.set(defaultValueSourceProviderFactory, ourGradleProperties);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to update the Gradle JDK properties using reflection", e);
        }
    }

    private static class GradlePropertiesInvocationHandler implements InvocationHandler {
        private final GradleProperties originalGradleProperties;
        private final Path gradleJdksLocalDirectory;
        private final Path rootProjectDir;
        private final Path gradleUserHomeDirectory;
        private final OperatingSystem operatingSystem;

        GradlePropertiesInvocationHandler(
                Path rootProjectDir,
                Path gradleUserHomeDirectory,
                Path gradleJdksLocalDirectory,
                GradleProperties originalGradleProperties,
                OperatingSystem operatingSystem) {
            this.rootProjectDir = rootProjectDir;
            this.gradleUserHomeDirectory = gradleUserHomeDirectory;
            this.gradleJdksLocalDirectory = gradleJdksLocalDirectory;
            this.originalGradleProperties = originalGradleProperties;
            this.operatingSystem = operatingSystem;
        }

        @Override
        public Object invoke(Object _proxy, Method method, Object[] args) throws Throwable {
            // see: https://github.com/gradle/gradle/blob/4bd1b3d3fc3f31db5a26eecb416a165b8cc36082/subprojects/core-api/
            // src/main/java/org/gradle/api/internal/properties/GradleProperties.java#L28
            if (method.getName().equals("find") && args.length == 1) {
                List<Path> installedLocalToolchains = getOrInstallJdkPaths(
                        rootProjectDir, gradleUserHomeDirectory, gradleJdksLocalDirectory, operatingSystem);
                String onlyArg = (String) args[0];
                if (onlyArg.equals("org.gradle.java.installations.auto-detect")
                        || onlyArg.equals("org.gradle.java.installations.auto-download")) {
                    return "false";
                }
                if (onlyArg.equals("org.gradle.java.installations.paths")) {
                    return installedLocalToolchains.stream()
                            .map(Path::toAbsolutePath)
                            .map(Path::toString)
                            .collect(Collectors.joining(","));
                }
            }
            try {
                return GradleProperties.class
                        .getDeclaredMethod(method.getName(), method.getParameterTypes())
                        .invoke(originalGradleProperties, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

    private static List<Path> getOrInstallJdkPaths(
            Path rootProjectDir,
            Path gradleUserHomeDirectory,
            Path gradleJdksLocalDirectory,
            OperatingSystem operatingSystem) {
        List<Path> jdkPaths = getConfiguredJdkPaths(gradleJdksLocalDirectory, gradleUserHomeDirectory, operatingSystem);
        List<Path> missingJdkPaths = getMissingPaths(jdkPaths);
        if (!missingJdkPaths.isEmpty()) {
            logger.error(
                    "Gradle JDK setup is enabled (palantir.jdk.setup.enabled is true) but some jdks were not"
                            + " installed: {}. If running from Intellij, please make sure the"
                            + " `palantir-gradle-jdks` Intellij plugin is installed"
                            + " https://plugins.jetbrains.com/plugin/24776-palantir-gradle-jdks/versions."
                            + " To unblock the workflow, the jdks will be manually installed now ...",
                    missingJdkPaths);
            runGradleJdkSetup(rootProjectDir, gradleUserHomeDirectory);
        }
        return jdkPaths;
    }

    private static List<Path> getConfiguredJdkPaths(
            Path gradleJdksLocalDirectory, Path gradleUserHomeDirectory, OperatingSystem os) {
        Path installationDirectory = gradleUserHomeDirectory.resolve("gradle-jdks");
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

    private static void runGradleJdkSetup(Path rootProjectDir, Path gradleUserHome) {
        Path buildDirectory = rootProjectDir.resolve("build");
        createDirectories(buildDirectory);
        ProcessBuilder processBuilder =
                new ProcessBuilder().command("./gradle/gradle-jdks-setup.sh").directory(rootProjectDir.toFile());
        // Pass GRADLE_USER_HOME environment variable to ensure the setup script uses Gradle's calculated user home directory
        processBuilder
                .environment()
                .put("GRADLE_USER_HOME", gradleUserHome.toAbsolutePath().toString());
        CommandRunner.runWithLogger(
                processBuilder, ToolchainJdksSettingsPlugin::writeStdOutput, ToolchainJdksSettingsPlugin::writeStdErr);
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
