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

import java.nio.file.Path;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository.MetadataSources;

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
            ivy.metadataSources(MetadataSources::artifact);
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
