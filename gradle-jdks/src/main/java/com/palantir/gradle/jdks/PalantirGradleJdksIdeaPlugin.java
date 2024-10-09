/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.base.Preconditions;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

public class PalantirGradleJdksIdeaPlugin implements Plugin<Project> {

    private static final Logger logger = Logging.getLogger(ToolchainsPlugin.class);
    protected static final String MIN_IDEA_PLUGIN_VERSION = "0.44.0";

    @Override
    public final void apply(Project rootProject) {
        Preconditions.checkState(
                rootProject == rootProject.getRootProject(),
                "May only apply PalantirGradleJdkProviderPlugin to the root project");
        rootProject.getPluginManager().withPlugin("idea", ideaPlugin -> {
            configureIntelliJImport(rootProject);
        });
    }

    private static void configureIntelliJImport(Project project) {
        // Note: we tried using 'org.jetbrains.gradle.plugin.idea-ext' and afterSync triggers, but these are currently
        // very hard to manage as the tasks feel disconnected from the Sync operation, and you can't remove them once
        // you've added them. For that reason, we accept that we have to resolve this configuration at
        // configuration-time, but only do it when part of an IDEA import.
        if (!Boolean.getBoolean("idea.active")) {
            return;
        }
        project.getGradle().projectsEvaluated(gradle -> {
            ConfigureJdksIdeaPluginXml.updateIdeaXmlFile(
                    project.file(".idea/externalDependencies.xml"), MIN_IDEA_PLUGIN_VERSION, true);

            // Still configure legacy idea if using intellij import
            ConfigureJdksIdeaPluginXml.updateIdeaXmlFile(
                    project.file(project.getName() + ".ipr"), MIN_IDEA_PLUGIN_VERSION, false);
        });
    }
}
