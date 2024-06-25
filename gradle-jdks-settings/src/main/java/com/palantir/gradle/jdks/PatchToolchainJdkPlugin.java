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

// CHECKSTYLE.OFF: IllegalImport

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

public final class PatchToolchainJdkPlugin implements Plugin<Settings> {

    private static final Logger logger = Logging.getLogger(PatchToolchainJdkPlugin.class);

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
            if (method.getName().equals("find")) {
                if (args.length == 1 && args[0].equals("org.gradle.java.installations.auto-detect")) {
                    return "false";
                }
                if (args.length == 1 && args[0].equals("org.gradle.java.installations.auto-download")) {
                    return "false";
                }
                if (args.length == 1 && args[0].equals("org.gradle.java.installations.paths")) {
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
                return stream.map(path -> path.resolve(String.format("%s/%s/local-path", os, arch)))
                        .map(path -> getToolchain(path, installationDirectory))
                        .flatMap(Optional::stream)
                        .collect(Collectors.toList());
            } catch (IOException e) {
                throw new RuntimeException("Unable to list the local installation paths", e);
            }
        }

        private static Optional<Path> getToolchain(Path gradleJdkConfigurationPath, Path installationDirectory) {
            try {
                String localFilename =
                        Files.readString(gradleJdkConfigurationPath).trim();
                Path installationPath = installationDirectory.resolve(localFilename);
                if (!Files.exists(installationPath)) {
                    logger.warn("Could not find toolchain at {}", installationPath);
                    return Optional.empty();
                }
                return Optional.of(installationPath);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void apply(Settings settings) {
        if (!GradleJdkToolchainHelper.isGradleJdkSetupEnabled(
                settings.getRootDir().toPath())) {
            logger.debug("Skipping Gradle JDK gradle properties patching");
            return;
        }

        Path gradleJdksLocalDirectory = settings.getRootDir().toPath().resolve("gradle/jdks");
        if (!Files.exists(gradleJdksLocalDirectory)) {
            logger.warn("Not setting the Gradle JDK properties because gradle/jdks directory doesn't exist. Please run"
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
            throw new RuntimeException(e);
        }
    }

    static Path getToolchainInstallationDir() {
        return Path.of(Optional.ofNullable(System.getenv("GRADLE_USER_HOME"))
                        .orElseGet(() -> System.getProperty("user.home") + "/.gradle"))
                .resolve("gradle-jdks");
    }
}
