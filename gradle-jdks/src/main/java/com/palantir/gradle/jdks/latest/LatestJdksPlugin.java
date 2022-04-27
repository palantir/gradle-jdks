/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.gradle.jdks.latest;

import com.palantir.gradle.jdks.JdkDistributionName;
import com.palantir.gradle.jdks.JdksExtension;
import com.palantir.gradle.jdks.JdksPlugin;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

public final class LatestJdksPlugin implements Plugin<Project> {
    @Override
    public void apply(Project rootProject) {
        if (rootProject.getRootProject() != rootProject) {
            throw new IllegalArgumentException(
                    LatestJdksPlugin.class.getSimpleName() + " must be applied to the root project!");
        }

        rootProject.getPluginManager().apply(JdksPlugin.class);

        rootProject.getExtensions().getByType(JdksExtension.class).jdk(JavaLanguageVersion.of(11), jdk -> {
            jdk.setDistribution(JdkDistributionName.AZUL_ZULU);
            jdk.getJdkVersion().set("version read resources");
        });

        // etc for other versions configured
    }
}
