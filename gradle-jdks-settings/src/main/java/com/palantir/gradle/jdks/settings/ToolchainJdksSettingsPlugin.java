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
import com.palantir.gradle.jdks.setup.common.CurrentArch;
import com.palantir.gradle.jdks.setup.common.CurrentOs;
import com.palantir.gradle.jdks.setup.common.Os;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.gradle.api.Plugin;
import org.gradle.api.initialization.Settings;
import org.gradle.api.internal.properties.GradleProperties;
import org.gradle.api.internal.provider.DefaultProviderFactory;
import org.gradle.api.internal.provider.DefaultValueSourceProviderFactory;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.initialization.DefaultSettings;

/**
 * A plugin that changes the Gradle JDK properties (via reflection) to point to the local toolchains configured via the
 * Gradle JDK Setup.
 * @see <a href=/Volumes/git/gradle-jdks/gradle-jdks-setup/README.md>Gradle JDK Readme</a>
 */
public final class ToolchainJdksSettingsPlugin implements Plugin<Settings> {

    private static final Logger logger = Logging.getLogger(ToolchainJdksSettingsPlugin.class);

    @Override
    public void apply(Settings settings) {
        if (!GradleJdksEnablement.isGradleJdkSetupEnabled(settings.getRootDir().toPath())) {
            logger.debug("Skipping Gradle JDK gradle properties patching");
            return;
        }

        Path gradleJdksLocalDirectory = settings.getRootDir().toPath().resolve("gradle/jdks");
        if (!Files.exists(gradleJdksLocalDirectory)) {
            logger.debug("Not setting the Gradle JDK properties because gradle/jdks directory doesn't exist. Please run"
                    + " ./gradlew setupJdks to set up the JDKs.");
            return;
        }
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
                    new GradlePropertiesInvocationHandler(gradleJdksLocalDirectory, originalGradleProperties));
            field.set(defaultValueSourceProviderFactory, ourGradleProperties);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to update the Gradle JDK properties using reflection", e);
        }
    }

    private static class GradlePropertiesInvocationHandler implements InvocationHandler {
        private final GradleProperties originalGradleProperties;
        private final Path gradleJdksLocalDirectory;

        GradlePropertiesInvocationHandler(Path gradleJdksLocalDirectory, GradleProperties originalGradleProperties) {
            this.gradleJdksLocalDirectory = gradleJdksLocalDirectory;
            this.originalGradleProperties = originalGradleProperties;
        }

        @Override
        public Object invoke(Object _proxy, Method method, Object[] args) throws Throwable {
            List<Path> localToolchains = getInstalledToolchains(gradleJdksLocalDirectory);
            if (localToolchains.isEmpty()) {
                throw new RuntimeException(
                        "Gradle JDK setup is enabled (palantir.jdk.setup.enabled is true) but no toolchains could be"
                                + " configured");
            }
            // see: https://github.com/gradle/gradle/blob/4bd1b3d3fc3f31db5a26eecb416a165b8cc36082/subprojects/core-api/
            // src/main/java/org/gradle/api/internal/properties/GradleProperties.java#L28
            if (method.getName().equals("find") && args.length == 1) {
                String onlyArg = (String) args[0];
                if (onlyArg.equals("org.gradle.java.installations.auto-detect")
                        || onlyArg.equals("org.gradle.java.installations.auto-download")) {
                    return "false";
                }
                if (onlyArg.equals("org.gradle.java.installations.paths")) {
                    return localToolchains.stream()
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

        private static List<Path> getInstalledToolchains(Path gradleJdksLocalDirectory) {
            Path installationDirectory = getToolchainInstallationDir();
            Os os = CurrentOs.get();
            Arch arch = CurrentArch.get();
            try (Stream<Path> stream = Files.list(gradleJdksLocalDirectory).filter(Files::isDirectory)) {
                return stream.map(path -> path.resolve(os.toString())
                                .resolve(arch.toString())
                                .resolve("local-path"))
                        .map(path -> getToolchain(path, installationDirectory))
                        .collect(Collectors.toList());
            } catch (IOException e) {
                throw new RuntimeException("Unable to list the local installation paths", e);
            }
        }

        private static Path getToolchain(Path gradleJdkConfigurationPath, Path installationDirectory) {
            try {
                String localFilename =
                        Files.readString(gradleJdkConfigurationPath).trim();
                Path installationPath = installationDirectory.resolve(localFilename);
                if (!Files.exists(installationPath)) {
                    throw new RuntimeException(
                            String.format("Failed to find the toolchain at path=%s", installationPath));
                }
                return installationPath;
            } catch (IOException e) {
                throw new RuntimeException(
                        String.format("Failed to get the toolchain configured at path=%s", gradleJdkConfigurationPath),
                        e);
            }
        }
    }

    static Path getToolchainInstallationDir() {
        return Path.of(Optional.ofNullable(System.getenv("GRADLE_USER_HOME"))
                        .orElseGet(() -> System.getProperty("user.home") + "/.gradle"))
                .resolve("gradle-jdks");
    }
}
