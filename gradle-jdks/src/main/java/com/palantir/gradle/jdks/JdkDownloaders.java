/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.gradle.jdks;

import java.util.HashMap;
import java.util.Map;
import org.gradle.api.Project;

final class JdkDownloaders {
    private final Map<JdkDistributionName, JdkDownloader> jdkDownloaders = new HashMap<>();

    private final Project rootProject;
    private final JdksExtension jdksExtension;

    JdkDownloaders(Project rootProject, JdksExtension jdksExtension) {
        if (rootProject != rootProject.getRootProject()) {
            throw new IllegalArgumentException("Must pass in the root project");
        }

        this.rootProject = rootProject;
        this.jdksExtension = jdksExtension;
    }

    public JdkDownloader jdkDownloaderFor(JdkDistributionName jdkDistributionName) {
        return jdkDownloaders.computeIfAbsent(
                jdkDistributionName,
                _ignored -> new JdkDownloader(
                        rootProject,
                        jdkDistributionName,
                        jdksExtension
                                .getJdkDistributions()
                                .getting(jdkDistributionName)
                                .get()
                                .getBaseUrl()
                                .get()));
    }
}
