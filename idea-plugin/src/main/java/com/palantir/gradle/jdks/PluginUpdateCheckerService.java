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
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.util.text.VersionComparatorUtil;
import java.util.Optional;
import java.util.Set;

@Service(Service.Level.PROJECT)
public final class PluginUpdateCheckerService {

    private final Logger logger = Logger.getInstance(PluginUpdateCheckerService.class);
    private final Project project;

    public PluginUpdateCheckerService(Project project) {
        this.project = project;
    }

    public void checkPluginVersion() {
        PluginId pluginId = PluginId.getId("palantir-gradle-jdks");
        IdeaPluginDescriptor pluginDescriptor = PluginManagerCore.getPlugin(pluginId);
        if (pluginDescriptor == null) {
            logger.info("Plugin " + pluginId + " not found");
            return;
        }
        Optional<String> maybeMinVersion =
                ExternalDependenciesManager.getInstance(project).getDependencies(DependencyOnPlugin.class).stream()
                        .filter(dependencyOnPlugin ->
                                dependencyOnPlugin.getPluginId().equals(pluginId.getIdString()))
                        .map(DependencyOnPlugin::getMinVersion)
                        .filter(version -> Optional.ofNullable(version).isPresent())
                        .max(VersionComparatorUtil::compare);
        boolean isPluginUpToDate = maybeMinVersion
                .map(minVersion -> VersionComparatorUtil.compare(pluginDescriptor.getVersion(), minVersion) > 0)
                .orElse(true);

        if (!isPluginUpToDate) {
            NotificationGroupManager.getInstance()
                    .getNotificationGroup("Update Palantir plugins")
                    .createNotification(
                            "Update palantir-gradle-jdks plugin",
                            String.format(
                                    "Please update the plugin in the Settings window to a version higher than '%s'",
                                    maybeMinVersion.get()),
                            NotificationType.ERROR)
                    .notify(project);
            Runnable runnable = () -> {
                ShowSettingsUtil.getInstance()
                        .showSettingsDialog(
                                ProjectUtil.currentOrDefaultProject(project),
                                PluginManagerConfigurable.class,
                                _configurable -> PluginManagerConfigurable.showPluginConfigurableAndEnable(
                                        project, Set.of(pluginDescriptor)));
            };
            ApplicationManager.getApplication().invokeLater(runnable);
        }
    }
}
