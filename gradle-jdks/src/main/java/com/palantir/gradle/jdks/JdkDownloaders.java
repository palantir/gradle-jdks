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
import org.immutables.value.Value;

final class JdkDownloaders {
    private final Map<JdkDownloadersCacheKey, JdkDownloader> jdkDownloaders = new HashMap<>();

    private final JdksExtension jdksExtension;

    JdkDownloaders(JdksExtension jdksExtension) {
        this.jdksExtension = jdksExtension;
    }

    public JdkDownloader jdkDownloaderFor(Project project, JdkDistributionName jdkDistributionName) {
        return jdkDownloaders.computeIfAbsent(
                ImmutableJdkDownloadersCacheKey.builder()
                        .project(project)
                        .jdkDistributionName(jdkDistributionName)
                        .build(),
                _ignored -> new JdkDownloader(
                        project,
                        jdkDistributionName,
                        jdksExtension
                                .jdkDistributionFor(jdkDistributionName)
                                .getBaseUrl()
                                .get()));
    }

    @Value.Immutable
    interface JdkDownloadersCacheKey {
        Project project();

        JdkDistributionName jdkDistributionName();
    }
}
