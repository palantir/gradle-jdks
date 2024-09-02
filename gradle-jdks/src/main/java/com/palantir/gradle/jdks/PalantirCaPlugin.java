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

import com.palantir.gradle.jdks.setup.CaResources;
import java.util.Map;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

public final class PalantirCaPlugin implements Plugin<Project> {

    private Project rootProject;
    private PalantirCaExtension extension;

    @Override
    public void apply(Project possibleRootProject) {
        if (possibleRootProject.getRootProject() != possibleRootProject) {
            throw new IllegalArgumentException(
                    "com.palantir.jdks.palantir-ca must be applied to the root project only");
        }

        rootProject = possibleRootProject;

        extension = rootProject.getExtensions().create("palantirCa", PalantirCaExtension.class);

        rootProject.getPluginManager().apply(JdksPlugin.class);

        JdksExtension jdksExtension = rootProject.getExtensions().getByType(JdksExtension.class);

        jdksExtension
                .getCaAliasesToSerialNumbers()
                .putAll(Map.of(CaResources.PALANTIR_3RD_GEN_ALIAS, CaResources.PALANTIR_3RD_GEN_SERIAL));
    }
}
