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

import com.palantir.baseline.plugins.javaversions.BaselineJavaVersionsExtension;
import com.palantir.gradle.jdks.GradleWrapperPatcher.GradleWrapperPatcherTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.wrapper.Wrapper;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

public final class ToolchainsPlugin implements Plugin<Project> {

    private static final Logger logger = Logging.getLogger(ToolchainsPlugin.class);
    private static final String GRADLE_JDK_GROUP = "Gradle JDK";

    @Override
    public void apply(Project rootProject) {
        if (!JdksPlugin.isGradleJdkSetupEnabled(rootProject)) {
            throw new RuntimeException("Cannot apply ToolchainsJdksPlugin without enabling palantir.jdk.setup.enabled");
        }
        rootProject.getPluginManager().apply(LifecycleBasePlugin.class);
        rootProject.getPluginManager().apply("com.palantir.jdks.idea");
        rootProject
                .getLogger()
                .info("Gradle JDK automanagement is enabled. The JDKs used for all subprojects "
                        + "are managed by the configured custom toolchains.");
        JdkDistributions jdkDistributions = new JdkDistributions();

        JdksExtension jdksExtension = JdksPlugin.extension(rootProject, jdkDistributions);

        rootProject.getPluginManager().withPlugin("com.palantir.baseline-java-versions", unused -> {
            rootProject
                    .getExtensions()
                    .getByType(BaselineJavaVersionsExtension.class)
                    .getSetupJdkToolchains()
                    .set(false);
        });
        TaskProvider<Wrapper> wrapperTask = rootProject.getTasks().named("wrapper", Wrapper.class);

        TaskProvider<GenerateGradleJdksConfigsTask> generateGradleJdkConfigs = rootProject
                .getTasks()
                .register("generateGradleJdkConfigs", GenerateGradleJdksConfigsTask.class, task -> {
                    task.getOutputGradleDirectory()
                            .set(rootProject.getLayout().getProjectDirectory().dir("gradle"));
                });
        TaskProvider<CheckGradleJdksConfigsTask> checkGradleJdkConfigs = rootProject
                .getTasks()
                .register("checkGradleJdkConfigs", CheckGradleJdksConfigsTask.class, task -> {
                    task.getInputGradleDirectory()
                            .set(generateGradleJdkConfigs
                                    .get()
                                    .getOutputGradleDirectory()
                                    .getLocationOnly()
                                    .get());
                    task.getDummyOutputFile()
                            .set(rootProject.getLayout().getBuildDirectory().file("checkGradleJdkConfigs"));
                });

        rootProject.getTasks().withType(GradleJdksConfigs.class).configureEach(task -> {
            task.getDaemonJavaVersion().set(jdksExtension.getDaemonTarget());
            task.getJavaVersionToJdkDistros()
                    .putAll(rootProject.provider(() -> JdkDistributionConfigurator.getJavaVersionToJdkDistros(
                            rootProject, jdkDistributions, jdksExtension)));
            task.getCaCerts().putAll(jdksExtension.getCaCerts());
        });

        TaskProvider<GradleWrapperPatcherTask> wrapperPatcherTask = rootProject
                .getTasks()
                .register("wrapperJdkPatcher", GradleWrapperPatcherTask.class, task -> {
                    task.getGenerate().set(true);
                    task.dependsOn(generateGradleJdkConfigs);
                });
        TaskProvider<GradleWrapperPatcherTask> checkWrapperPatcherTask = rootProject
                .getTasks()
                .register("checkWrapperJdkPatcher", GradleWrapperPatcherTask.class, task -> {
                    task.getGenerate().set(false);
                });

        rootProject.getTasks().withType(GradleWrapperPatcherTask.class).configureEach(task -> {
            task.getOriginalGradlewScript()
                    .fileProvider(rootProject.provider(() -> wrapperTask.get().getScriptFile()));
            task.getBuildDir().set(task.getTemporaryDir());
            task.getPatchedGradlewScript()
                    .set(rootProject.file(rootProject.getRootDir().toPath().resolve("gradlew")));
        });
        wrapperTask.configure(task -> {
            task.finalizedBy(wrapperPatcherTask);
        });

        TaskProvider<Task> checkJdksLifecycle = rootProject.getTasks().register("checkJdks", Task.class, task -> {
            task.setDescription("Lifecycle task that checks the Gradle JDK configurations.");
            task.setGroup(GRADLE_JDK_GROUP);
            task.dependsOn(checkGradleJdkConfigs, checkWrapperPatcherTask);
        });

        rootProject
                .getTasks()
                .named(LifecycleBasePlugin.CHECK_TASK_NAME)
                .configure(check -> check.dependsOn(checkJdksLifecycle));

        rootProject.getTasks().register("setupJdks", SetupJdksTask.class, setupJdksTask -> {
            setupJdksTask.setDescription("Configures the gradle JDK setup.");
            setupJdksTask.setGroup(GRADLE_JDK_GROUP);
            setupJdksTask.getGradlewScript().set(wrapperPatcherTask.get().getPatchedGradlewScript());
            setupJdksTask.dependsOn(generateGradleJdkConfigs, wrapperPatcherTask);
        });

        rootProject.getTasks().named("javaToolchains").configure(task -> {
            task.mustRunAfter(checkJdksLifecycle);
        });
    }
}
