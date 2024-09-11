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

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleConstants;

public final class GradleJdksExternalSystemTaskNotificationListener implements ExternalSystemTaskNotificationListener {

    @Override
    public void onStart(@NotNull ExternalSystemTaskId id, String _workingDir) {
        if (id.getProjectSystemId().equals(GradleConstants.SYSTEM_ID)
                && (id.getType() == ExternalSystemTaskType.RESOLVE_PROJECT
                        || id.getType() == ExternalSystemTaskType.EXECUTE_TASK)) {
            Project project = id.findProject();
            if (project != null) {
                project.getService(GradleJdksProjectService.class).maybeSetupGradleJdks();
            }
        }
    }

    @Override
    public void onStatusChange(@NotNull ExternalSystemTaskNotificationEvent _event) {}

    @Override
    public void onTaskOutput(@NotNull ExternalSystemTaskId _id, @NotNull String _text, boolean _stdOut) {}

    @Override
    public void onEnd(@NotNull ExternalSystemTaskId _id) {}

    @Override
    public void onSuccess(@NotNull ExternalSystemTaskId _id) {}

    @Override
    public void onFailure(@NotNull ExternalSystemTaskId _id, @NotNull Exception _exception) {}

    @Override
    public void beforeCancel(@NotNull ExternalSystemTaskId _id) {}

    @Override
    public void onCancel(@NotNull ExternalSystemTaskId _id) {}
}
