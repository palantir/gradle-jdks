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
import com.palantir.baseline.plugins.javaversions.ChosenJavaVersion;
import com.palantir.gradle.jdks.GradleJdkConfigs.GradleJdkConfigsTask;
import com.palantir.gradle.jdks.GradleWrapperPatcher.GradleWrapperPatcherTask;
import java.util.List;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.wrapper.Wrapper;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

public final class ToolchainsPlugin extends JdkDistributionConfigurator implements Plugin<Project> {

    @Override
    public void apply(Project rootProject) {
        if (!JdksPlugin.getEnableGradleJdkProperty(rootProject)) {
            throw new RuntimeException("Cannot apply ToolchainsJdksPlugin without enabling gradle.jdk.setup.enabled");
        }

        rootProject
                .getLogger()
                .info("Gradle JDK automanagement is enabled. The JDKs used for all subprojects "
                        + "are managed by the configured custom toolchains.");
        JdkDistributions jdkDistributions = new JdkDistributions();

        Provider<JdkDistributionsService> jdkDistributionsService = rootProject
                .getGradle()
                .getSharedServices()
                .registerIfAbsent("jdkDistributionsService", JdkDistributionsService.class, spec -> {});

        JdksExtension jdksExtension = JdksPlugin.extension(rootProject, jdkDistributions);
        BaselineJavaVersionsExtension baselineJavaVersionsExtension =
                rootProject.getExtensions().getByType(BaselineJavaVersionsExtension.class);

        baselineJavaVersionsExtension.getSetupJdkToolchains().set(false);

        TaskProvider<GradleJdkConfigsTask> generateGradleJdkConfigs = rootProject
                .getTasks()
                .register("generateGradleJdkConfigs", GradleJdkConfigsTask.class, task -> {
                    configureGenerateJdkConfigs(
                            task, rootProject, baselineJavaVersionsExtension, jdksExtension, jdkDistributionsService);
                    task.getGenerate().set(true);
                    task.getOutputGradleDirectory()
                            .set(rootProject.getLayout().dir(rootProject.provider(() -> rootProject.file("gradle"))));
                });
        TaskProvider<GradleJdkConfigsTask> checkGradleJdkConfigs = rootProject
                .getTasks()
                .register("checkGradleJdkConfigs", GradleJdkConfigsTask.class, task -> {
                    configureGenerateJdkConfigs(
                            task, rootProject, baselineJavaVersionsExtension, jdksExtension, jdkDistributionsService);
                    task.getGenerate().set(false);
                    task.getOutputGradleDirectory()
                            .set(rootProject.getLayout().getBuildDirectory().dir("gradleConfigs"));
                });
        rootProject
                .getTasks()
                .named(LifecycleBasePlugin.CHECK_TASK_NAME)
                .configure(check -> check.dependsOn(checkGradleJdkConfigs));

        TaskProvider<Wrapper> wrapperTask = rootProject.getTasks().named("wrapper", Wrapper.class);
        TaskProvider<GradleWrapperPatcherTask> wrapperPatcherTask = rootProject
                .getTasks()
                .register("wrapperJdkPatcher", GradleWrapperPatcherTask.class, task -> {
                    task.getOriginalGradlewScript().set(wrapperTask.map(Wrapper::getScriptFile));
                    // TODO(crogoz): changeMe + below
                    task.getPatchedGradlewScript()
                            .set(rootProject.file(
                                    rootProject.getRootDir().toPath().resolve("gradlew")));
                    task.getOriginalGradleWrapperJar().set(wrapperTask.map(Wrapper::getJarFile));
                    task.getPatchedGradleWrapperJar()
                            .set(rootProject.file(
                                    rootProject.getRootDir().toPath().resolve("gradle/wrapper/gradle-wrapper.jar")));
                    task.getBuildDir().set(task.getTemporaryDir());
                    task.getGradleJdksSetupJar()
                            .set(generateGradleJdkConfigs
                                    .map(GradleJdkConfigsTask::getOutputGradleDirectory)
                                    .flatMap(dirProp -> dirProp.getAsFile().map(file -> file.toPath()
                                            .resolve("gradle-jdks-setup.jar")
                                            .toFile())));
                });
        wrapperTask.configure(task -> task.finalizedBy(wrapperPatcherTask));
        rootProject.allprojects(proj -> proj.getPluginManager().withPlugin("java", unused -> {
            proj.getPluginManager().apply(ProjectToolchainsPlugin.class);
        }));
    }

    private static void configureGenerateJdkConfigs(
            GradleJdkConfigsTask task,
            Project rootProject,
            BaselineJavaVersionsExtension baselineJavaVersionsExtension,
            JdksExtension jdksExtension,
            Provider<JdkDistributionsService> jdkDistributionsService) {
        task.getJdkDistributions().set(jdkDistributionsService);
        task.usesService(jdkDistributionsService);
        task.getGradleDirectory()
                .set(rootProject.getLayout().getProjectDirectory().dir("gradle"));
        task.getDaemonJavaVersion().set(jdksExtension.getDaemonTarget());
        task.getJavaVersionToJdkDistros()
                .putAll(rootProject.provider(() -> getJavaVersionToJdkDistros(
                        rootProject,
                        List.of(
                                baselineJavaVersionsExtension.libraryTarget(),
                                jdksExtension.getDaemonTarget(),
                                baselineJavaVersionsExtension
                                        .distributionTarget()
                                        .map(ChosenJavaVersion::javaLanguageVersion),
                                baselineJavaVersionsExtension.runtime().map(ChosenJavaVersion::javaLanguageVersion)),
                        jdksExtension)));
        task.getCaCerts().putAll(jdksExtension.getCaCerts());
    }
}
