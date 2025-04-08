/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.jdks.flow;

import com.palantir.gradle.jdks.GradleJdksConfigsUtils;
import com.palantir.gradle.jdks.enablement.GradleJdksEnablement;
import javax.inject.Inject;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.flow.FlowProviders;
import org.gradle.api.flow.FlowScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ToolchainFailureFlowActionsPlugin implements Plugin<Project> {

    private static final Logger log = LoggerFactory.getLogger(ToolchainFailureFlowActionsPlugin.class);

    @Inject
    protected abstract FlowScope getFlowScope();

    @Inject
    protected abstract FlowProviders getFlowProviders();

    @Override
    public final void apply(Project project) {
        if (!GradleJdksEnablement.isGradleJdkSetupEnabled(
                project.getRootProject().getProjectDir().toPath())) {
            throw new RuntimeException(
                    "Cannot apply `ToolchainFailureFlowActionsPlugin` without enabling palantir.jdk.setup.enabled");
        }
        getFlowScope().always(ToolchainFlowAction.class, spec -> {
            spec.getParameters().getBuildResult().set(getFlowProviders().getBuildWorkResult());
            spec.getParameters()
                    .getConfiguredJavaMajorVersions()
                    .set(project.provider(() -> GradleJdksConfigsUtils.getConfiguredJavaMajorVersions(
                            project.getRootProject().file("gradle/jdks").toPath())));
        });
    }
}
