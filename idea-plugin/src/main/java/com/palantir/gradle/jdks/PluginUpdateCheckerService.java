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

import com.intellij.externalDependencies.DependencyOnPlugin;
import com.intellij.externalDependencies.ExternalDependenciesManager;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerConfigurable;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.util.text.VersionComparatorUtil;
import java.util.Optional;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;

public final class PluginUpdateCheckerService implements ProjectActivity {

    @Override
    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        PluginId pluginId = PluginId.getId("palantir-gradle-jdks");
        IdeaPluginDescriptor pluginDescriptor = PluginManagerCore.getPlugin(pluginId);
        if (pluginDescriptor == null) {
            return continuation;
        }
        Optional<String> maybeMinVersion =
                ExternalDependenciesManager.getInstance(project).getDependencies(DependencyOnPlugin.class).stream()
                        .filter(dependencyOnPlugin ->
                                dependencyOnPlugin.getPluginId().equals(pluginId.getIdString()))
                        .map(DependencyOnPlugin::getMinVersion)
                        .filter(version -> Optional.ofNullable(version).isPresent())
                        .max((d1, d2) -> VersionComparatorUtil.compare(d1, d2));
        boolean isPluginUpToDate = maybeMinVersion
                .map(minVersion -> VersionComparatorUtil.compare(pluginDescriptor.getVersion(), minVersion) > 0)
                .orElse(true);

        if (!isPluginUpToDate) {
            NotificationGroupManager.getInstance()
                    .getNotificationGroup("Update Palantir plugins")
                    .createNotification(
                            "Update palantir gradle JDK intellij plugin",
                            String.format(
                                    "Project requires a version of palantir-gradle-jdks higher than %s. "
                                            + "Please update the Intellij plugin in the Settings window.",
                                    maybeMinVersion.get()),
                            NotificationType.ERROR)
                    .notify(project);
            ApplicationManager.getApplication().invokeLater(() -> {
                ShowSettingsUtil.getInstance()
                        .showSettingsDialog(
                                ProjectUtil.currentOrDefaultProject(project),
                                PluginManagerConfigurable.class,
                                configurable -> configurable.showPluginConfigurable(project, pluginDescriptor));
            });
        }
        return continuation;
    }
}
