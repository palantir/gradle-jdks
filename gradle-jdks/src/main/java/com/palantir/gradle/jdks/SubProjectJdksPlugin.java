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

import com.palantir.baseline.plugins.javaversions.BaselineJavaVersion;
import com.palantir.baseline.plugins.javaversions.BaselineJavaVersionExtension;
import com.palantir.baseline.plugins.javaversions.ChosenJavaVersion;
import com.palantir.gradle.jdks.GenerateGradleJdkConfigs.GenerateGradleJdkConfigsTask;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SubProjectJdksPlugin implements Plugin<Project> {

    private static final Logger log = LoggerFactory.getLogger(SubProjectJdksPlugin.class);

    @Override
    public void apply(Project project) {
        if (project == project.getRootProject()) {
            return;
        }
        JdkDistributions jdkDistributions = new JdkDistributions();
        JdksExtension jdksExtension = project.getRootProject().getExtensions().getByType(JdksExtension.class);

        project.getPluginManager().apply(BaselineJavaVersion.class);
        BaselineJavaVersionExtension projectVersions =
                project.getExtensions().getByType(BaselineJavaVersionExtension.class);
        project.getRootProject()
                .getTasks()
                .withType(GenerateGradleJdkConfigsTask.class)
                .configureEach(task -> task.getJavaVersionToJdkDistros().putAll(project.provider(() -> Stream.of(
                                projectVersions.target().map(ChosenJavaVersion::javaLanguageVersion),
                                projectVersions.runtime().map(ChosenJavaVersion::javaLanguageVersion))
                        .map(Provider::get)
                        .distinct()
                        .collect(Collectors.toMap(
                                javaVersion -> javaVersion,
                                javaVersion -> JdksPlugin.getJdkDistributions(
                                        project, jdkDistributions, javaVersion, jdksExtension))))));
    }
}
