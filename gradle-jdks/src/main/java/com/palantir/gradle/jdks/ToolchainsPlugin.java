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
import java.util.stream.Collectors;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.wrapper.Wrapper;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

public final class ToolchainsPlugin implements Plugin<Project> {

    private static final String GRADLE_JDK_GROUP = "Gradle JDK";
    private static final JavaLanguageVersion MINIMUM_SUPPORTED_JAVA_VERSION = JavaLanguageVersion.of(11);

    @Override
    public void apply(Project rootProject) {
        if (!JdksPlugin.getEnableGradleJdkProperty(rootProject)) {
            throw new RuntimeException("Cannot apply ToolchainsJdksPlugin without enabling palantir.jdk.setup.enabled");
        }

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

        TaskProvider<GenerateGradleJdkConfigsTask> generateGradleJdkConfigs = rootProject
                .getTasks()
                .register("generateGradleJdkConfigs", GenerateGradleJdkConfigsTask.class, task -> {
                    task.getOutputGradleDirectory()
                            .set(rootProject.getLayout().getProjectDirectory().dir("gradle"));
                    task.dependsOn(wrapperTask);
                });
        TaskProvider<CheckGradleJdkConfigsTask> checkGradleJdkConfigs = rootProject
                .getTasks()
                .register("checkGradleJdkConfigs", CheckGradleJdkConfigsTask.class, task -> {
                    task.getInputGradleDirectory()
                            .set(generateGradleJdkConfigs
                                    .get()
                                    .getOutputGradleDirectory()
                                    .getLocationOnly()
                                    .get());
                    task.getDummyOutputFile()
                            .set(rootProject.getLayout().getBuildDirectory().file("checkGradleJdkConfigs"));
                });
        rootProject.getTasks().withType(GradleJdkConfigs.class).configureEach(task -> {
            task.getDaemonJavaVersion().set(jdksExtension.getDaemonTarget());
            task.getJavaVersionToJdkDistros()
                    .putAll(rootProject.provider(() -> JdkDistributionConfigurator.getJavaVersionToJdkDistros(
                            rootProject,
                            jdkDistributions,
                            jdksExtension.getConfiguredJavaVersions().get().stream()
                                    .filter(javaLanguageVersion ->
                                            javaLanguageVersion.canCompileOrRun(MINIMUM_SUPPORTED_JAVA_VERSION))
                                    .collect(Collectors.toSet()),
                            jdksExtension)));
            task.getCaCerts().putAll(jdksExtension.getCaCerts());
        });
        TaskProvider<GradleWrapperPatcherTask> wrapperPatcherTask = rootProject
                .getTasks()
                .register("wrapperJdkPatcher", GradleWrapperPatcherTask.class, task -> {
                    task.getOriginalGradlewScript().fileProvider(wrapperTask.map(Wrapper::getScriptFile));
                    task.getOriginalGradleWrapperJar().fileProvider(wrapperTask.map(Wrapper::getJarFile));
                    task.getBuildDir().set(task.getTemporaryDir());
                    task.getGradleJdksSetupJar()
                            .fileProvider(generateGradleJdkConfigs
                                    .map(GenerateGradleJdkConfigsTask::getOutputGradleDirectory)
                                    .flatMap(dirProp -> dirProp.getAsFile().map(file -> file.toPath()
                                            .resolve(GradleJdkConfigs.GRADLE_JDKS_SETUP_JAR)
                                            .toFile())));
                    task.getPatchedGradlewScript()
                            .set(rootProject.file(
                                    rootProject.getRootDir().toPath().resolve("gradlew")));
                    task.getPatchedGradleWrapperJar()
                            .set(rootProject.file(
                                    rootProject.getRootDir().toPath().resolve("gradle/wrapper/gradle-wrapper.jar")));
                    task.getGenerate().set(true);
                });
        wrapperTask.configure(task -> task.finalizedBy(wrapperPatcherTask));

        TaskProvider<GradleWrapperPatcherTask> checkWrapperPatcher = rootProject
                .getTasks()
                .register("checkWrapperPatcher", GradleWrapperPatcherTask.class, task -> {
                    // Using providers to avoid implicit dependency on wrapperPatcherTask and generateGradleJdkConfigs
                    task.getOriginalGradlewScript().fileProvider(rootProject.provider(() -> wrapperPatcherTask
                            .get()
                            .getPatchedGradlewScript()
                            .getAsFile()
                            .get()));
                    task.getOriginalGradleWrapperJar().fileProvider(rootProject.provider(() -> wrapperPatcherTask
                            .get()
                            .getPatchedGradleWrapperJar()
                            .getAsFile()
                            .get()));
                    task.getBuildDir().set(task.getTemporaryDir());
                    task.getGradleJdksSetupJar().fileProvider(rootProject.provider(() -> generateGradleJdkConfigs
                            .get()
                            .getOutputGradleDirectory()
                            .file(GradleJdkConfigs.GRADLE_JDKS_SETUP_JAR)
                            .get()
                            .getAsFile()));
                    task.getPatchedGradlewScript()
                            .set(rootProject.getLayout().getBuildDirectory().file("checkWrapperPatcher/gradlew"));
                    task.getPatchedGradleWrapperJar()
                            .set(rootProject
                                    .getLayout()
                                    .getBuildDirectory()
                                    .file("checkWrapperPatcher/gradle-wrapper.jar"));
                    task.getGenerate().set(false);
                });

        rootProject
                .getTasks()
                .named(LifecycleBasePlugin.CHECK_TASK_NAME)
                .configure(check -> check.dependsOn(checkGradleJdkConfigs, checkWrapperPatcher));

        rootProject.getTasks().register("setupJdks", setupJdksTask -> {
            setupJdksTask.setDescription("Configures the gradle JDK setup.");
            setupJdksTask.setGroup(GRADLE_JDK_GROUP);
            setupJdksTask.dependsOn(generateGradleJdkConfigs, wrapperPatcherTask);
        });
    }
}
