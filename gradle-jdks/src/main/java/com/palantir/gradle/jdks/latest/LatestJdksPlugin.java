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
