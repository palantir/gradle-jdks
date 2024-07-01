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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import java.io.File;
import java.nio.file.Path;
import java.util.Optional;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

public class GradleJdksStartupActivity implements ProjectActivity {

    @Nullable
    @Override
    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> _continuation) {
        for (GradleProjectSettings projectSettings :
                GradleSettings.getInstance(project).getLinkedProjectsSettings()) {
            File gradleConfigFile = Path.of(projectSettings.getExternalProjectPath(), ".gradle/config.properties")
                    .toFile();
            if (!gradleConfigFile.exists() || isGradleJdkConfigurationAlreadySetUp(projectSettings)) {
                continue;
            }
            project.getService(GradleJdksProjectService.class).setupGradleJdks();
        }
        return project;
    }

    public static boolean isGradleJdkConfigurationAlreadySetUp(GradleProjectSettings projectSettings) {
        return Optional.ofNullable(projectSettings.getGradleJvm())
                .map(gradleJvm -> gradleJvm.equals("#GRADLE_LOCAL_JAVA_HOME"))
                .orElse(false);
    }
}
