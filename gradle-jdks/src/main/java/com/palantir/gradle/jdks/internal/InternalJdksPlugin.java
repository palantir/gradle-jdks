/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.gradle.jdks.internal;

import com.palantir.gradle.jdks.JdkDistributionName;
import com.palantir.gradle.jdks.JdksExtension;
import com.palantir.gradle.jdks.JdksPlugin;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

public final class InternalJdksPlugin implements Plugin<Project> {
    @Override
    public void apply(Project rootProject) {
        if (rootProject.getRootProject() != rootProject) {
            throw new IllegalArgumentException(
                    InternalJdksPlugin.class.getSimpleName() + " must be applied to the root project!");
        }

        rootProject.getPluginManager().apply(JdksPlugin.class);

        rootProject
                .getExtensions()
                .getByType(JdksExtension.class)
                .jdkDistribution(JdkDistributionName.AZUL_ZULU, jdkDistribution -> {
                    jdkDistribution.getBaseUrl().set("https://internal-artifact-server/azul-zulu");
                });
    }
}
