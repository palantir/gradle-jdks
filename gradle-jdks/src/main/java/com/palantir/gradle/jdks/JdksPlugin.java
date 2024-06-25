/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
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

import java.io.File;
import java.util.Arrays;
import java.util.Optional;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JdksPlugin implements Plugin<Project> {

    private static final String ENABLE_GRADLE_JDK_SETUP = "palantir.jdk.setup.enabled";

    private static final Logger log = LoggerFactory.getLogger(JdksPlugin.class);

    @Override
    public void apply(Project rootProject) {
        if (rootProject.getRootProject() != rootProject) {
            throw new IllegalArgumentException("com.palantir.jdks must be applied to the root project only");
        }

        if (isGradleJdkSetupEnabled(rootProject)) {
            rootProject.getPluginManager().apply(ToolchainsPlugin.class);
        } else {
            rootProject.getPluginManager().apply(BaselineJavaJdksPlugin.class);
        }
    }

    public static JdksExtension extension(Project rootProject, JdkDistributions jdkDistributions) {
        JdksExtension jdksExtension = rootProject.getExtensions().create("jdks", JdksExtension.class);
        jdksExtension
                .getJdkStorageLocation()
                .set(rootProject
                        .getLayout()
                        .dir(rootProject.provider(
                                () -> new File(System.getProperty("user.home"), ".gradle/gradle-jdks"))));

        Arrays.stream(JdkDistributionName.values()).forEach(jdkDistributionName -> {
            jdksExtension.jdkDistribution(jdkDistributionName, jdkDistributionExtension -> {
                jdkDistributionExtension
                        .getBaseUrl()
                        .set(jdkDistributions.get(jdkDistributionName).defaultBaseUrl());
            });
        });

        return jdksExtension;
    }

    public static boolean isGradleJdkSetupEnabled(Project project) {
        return !CurrentOs.get().equals(Os.WINDOWS)
                && Optional.ofNullable(project.property(ENABLE_GRADLE_JDK_SETUP))
                        .map(prop -> Boolean.parseBoolean((String) prop))
                        .orElse(false);
    }
}
