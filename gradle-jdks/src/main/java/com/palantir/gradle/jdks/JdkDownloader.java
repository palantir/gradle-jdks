/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.gradle.jdks;

import java.nio.file.Path;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;

final class JdkDownloader {

    private final Project rootProject;
    private final String jdkGroup;

    JdkDownloader(Project rootProject, JdkDistributionName jdkDistributionName, String jdkBaseUrl) {
        this.rootProject = rootProject;
        this.jdkGroup = jdkDistributionName.uiName() + "-jdk";

        if (rootProject != rootProject.getRootProject()) {
            throw new IllegalArgumentException("Must pass in the root project");
        }

        rootProject.getRepositories().ivy(ivy -> {
            ivy.setName(jdkGroup);
            ivy.setUrl(jdkBaseUrl);
            ivy.patternLayout(patternLayout -> patternLayout.artifact("[module].[ext]"));
            ivy.metadataSources(metadataSources -> metadataSources.artifact());
            ivy.content(repositoryContentDescriptor -> {
                repositoryContentDescriptor.includeGroup(jdkGroup);
            });
        });

        rootProject
                .getRepositories()
                .matching(repo -> !repo.getName().equals(jdkGroup))
                .configureEach(artifactRepository -> {
                    artifactRepository.content(content -> content.excludeGroup(jdkGroup));
                });
    }

    public Path downloadJdkPath(JdkPath jdKPath) {
        Configuration configuration = rootProject
                .getConfigurations()
                .detachedConfiguration(rootProject
                        .getDependencies()
                        .create(String.format("%s:%s:@%s", jdkGroup, jdKPath.filename(), jdKPath.extension())));
        return configuration.resolve().iterator().next().toPath();
    }
}
