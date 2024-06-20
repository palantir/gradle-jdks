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

import java.lang.reflect.Field;
import java.util.Map;
import org.gradle.StartParameter;
import org.gradle.api.Plugin;
import org.gradle.api.initialization.Settings;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.initialization.DefaultGradlePropertiesController;
import org.gradle.initialization.DefaultGradlePropertiesLoader;
import org.gradle.initialization.DefaultSettings;
import org.gradle.initialization.GradlePropertiesController;
import org.gradle.initialization.IGradlePropertiesLoader;

public final class PatchToolchainJdkPlugin implements Plugin<Settings> {

    private static final Logger logger = Logging.getLogger(PatchToolchainJdkPlugin.class);

    @Override
    public void apply(Settings settings) {
        // version 1
        /*ProviderFactory providerFactory =
                ((DefaultSettings) settings).getServices().get(ProviderFactory.class);
        if (!(providerFactory instanceof DefaultProviderFactory)) {
            throw new RuntimeException(String.format(
                    "Expected osMemoryInfo to be of type '%s' but was '%s'.",
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
            Field gradleProperties = DefaultValueSourceProviderFactory.class.getDeclaredField("gradleProperties");
            gradleProperties.setAccessible(true);

            // not working because it is SharedProperties which is private to the class so I can't see it
            DefaultGradleProperties defaultGradleProperties =
                    (DefaultGradleProperties) gradleProperties.get(defaultValueSourceProviderFactory);
            Field defaultProperties = DefaultGradleProperties.class.getDeclaredField("defaultProperties");
            defaultProperties.setAccessible(true);
            defaultProperties.set(
                    defaultGradleProperties, Map.of("org.gradle.java.installations.auto-detect", "false"));
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }*/

        // version 2
        // not working because it is a ProviderBackedToolchainConfiguration which delegates back to providerFactory
        /*ToolchainConfiguration toolchainConfiguration =
                ((DefaultSettings) settings).getServices().get(ToolchainConfiguration.class);
        if (!(toolchainConfiguration instanceof DefaultToolchainConfiguration)) {
            throw new RuntimeException(String.format(
                    "Expected osMemoryInfo to be of type '%s' but was '%s'.",
                    ToolchainConfiguration.class.getCanonicalName(),
                    toolchainConfiguration.getClass().getCanonicalName()));
        }

        try {
            DefaultToolchainConfiguration defaultToolchainConfiguration =
                    (DefaultToolchainConfiguration) toolchainConfiguration;
            Field autoDetectEnabled = DefaultToolchainConfiguration.class.getDeclaredField("autoDetectEnabled");
            autoDetectEnabled.setAccessible(true);
            autoDetectEnabled.set(defaultToolchainConfiguration, false);
            Field downloadEnabled = DefaultToolchainConfiguration.class.getDeclaredField("downloadEnabled");
            downloadEnabled.setAccessible(true);
            downloadEnabled.set(defaultToolchainConfiguration, false);
            Field installationsFromPaths =
                    DefaultToolchainConfiguration.class.getDeclaredField("installationsFromPaths");
            installationsFromPaths.setAccessible(true);
            installationsFromPaths.set(
                    defaultToolchainConfiguration,
                    List.of(
                            "/Users/crogoz/.gradle/gradle-jdks/amazon-corretto-21.0.3.9.1-e25ec9ba8c6e8686",
                            "/Users/crogoz/.gradle/gradle-jdks/amazon-corretto-11.0.23.9.1-d6ef2c62dc4d4dd4",
                            "/Users/crogoz/.gradle/gradle-jdks/amazon-corretto-17.0.11.9.1-f0e4bf13f7416be0"));
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new RuntimeException(e);
        }*/

        // version 3
        GradlePropertiesController projectPropertiesLoader =
                ((DefaultSettings) settings).getServices().get(GradlePropertiesController.class);
        if (!(projectPropertiesLoader instanceof DefaultGradlePropertiesController)) {
            throw new RuntimeException(String.format(
                    "Expected osMemoryInfo to be of type '%s' but was '%s'.",
                    GradlePropertiesController.class.getCanonicalName(),
                    projectPropertiesLoader.getClass().getCanonicalName()));
        }
        DefaultGradlePropertiesController defaultProjectPropertiesLoader =
                (DefaultGradlePropertiesController) projectPropertiesLoader;

        try {
            Field propertiesLoader = DefaultGradlePropertiesController.class.getDeclaredField("propertiesLoader");
            propertiesLoader.setAccessible(true);
            IGradlePropertiesLoader iGradlePropertiesLoader =
                    (IGradlePropertiesLoader) propertiesLoader.get(defaultProjectPropertiesLoader);

            if (!(iGradlePropertiesLoader instanceof DefaultGradlePropertiesLoader)) {
                throw new RuntimeException(String.format(
                        "Expected osMemoryInfo to be of type '%s' but was '%s'.",
                        DefaultGradlePropertiesLoader.class.getCanonicalName(),
                        iGradlePropertiesLoader.getClass().getCanonicalName()));
            }
            DefaultGradlePropertiesLoader defaultGradlePropertiesLoader =
                    (DefaultGradlePropertiesLoader) iGradlePropertiesLoader;
            Field startParameterInternal = DefaultGradlePropertiesLoader.class.getDeclaredField("startParameter");
            startParameterInternal.setAccessible(true);

            // change the value
            StartParameterInternal startParameterInternalInstance =
                    (StartParameterInternal) startParameterInternal.get(defaultGradlePropertiesLoader);
            /*Field dryRun = StartParameter.class.getDeclaredField("dryRun");
            dryRun.setAccessible(true);
            dryRun.set(startParameterInternalInstance, true);*/

            Field systemPropertiesArgs = StartParameter.class.getDeclaredField("systemPropertiesArgs");
            systemPropertiesArgs.setAccessible(true);
            systemPropertiesArgs.set(
                    startParameterInternalInstance,
                    Map.of(
                            "org.gradle.java.home",
                            "/Users/crogoz/.gradle/gradle-jdks/amazon-corretto-11.0.21.9.1-94154e38f97edb8",
                            "org.gradle.java.installations.auto-download",
                            "false",
                            "org.gradle.java.installations.auto-detect",
                            "false"));

            // propertiesLoader.set(defaultProjectPropertiesLoader, defaultGradlePropertiesLoader);
        } catch (ReflectiveOperationException e) {
            logger.error("Failed to load project properties", e);
        }
    }
}
