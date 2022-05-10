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
                                .jdkDistributionFor(jdkDistributionName)
                                .getBaseUrl()
                                .get()));
    }
}
