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
import com.palantir.gradle.jdks.GenerateGradleJdkConfigs.GenerateGradleJdkConfigsTask;
import com.palantir.gradle.jdks.GenerateGradleJdkConfigs.JdkDistributionConfig;
import com.palantir.gradle.jdks.GradleWrapperPatcher.GradleWrapperPatcherTask;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.wrapper.Wrapper;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

public final class ToolchainsJdksPlugin implements Plugin<Project> {

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

        TaskProvider<GenerateGradleJdkConfigsTask> generateGradleJdkConfigs = rootProject
                .getTasks()
                .register("generateGradleJdkConfigs", GenerateGradleJdkConfigsTask.class, task -> {
                    configureGenerateJdkConfigs(
                            task,
                            rootProject,
                            baselineJavaVersionsExtension,
                            jdksExtension,
                            jdkDistributions,
                            jdkDistributionsService);
                    task.getFix().set(true);
                    task.getOutputGradleDirectory()
                            .set(rootProject.getLayout().dir(rootProject.provider(() -> rootProject.file("gradle"))));
                });
        TaskProvider<GenerateGradleJdkConfigsTask> checkGradleJdkConfigs = rootProject
                .getTasks()
                .register("checkGradleJdkConfigs", GenerateGradleJdkConfigsTask.class, task -> {
                    configureGenerateJdkConfigs(
                            task,
                            rootProject,
                            baselineJavaVersionsExtension,
                            jdksExtension,
                            jdkDistributions,
                            jdkDistributionsService);
                    task.getFix().set(false);
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
                    task.getGradleJdksSetupJar().set(rootProject.provider(() -> generateGradleJdkConfigs
                            .map(GenerateGradleJdkConfigsTask::getOutputGradleDirectory)
                            .map(dir -> dir.getAsFile().get().toPath().resolve("gradle-jdks-setup.jar"))));
                });
        rootProject.allprojects(proj -> proj.getPluginManager().withPlugin("java", unused -> {
            proj.getPluginManager().apply(SubProjectJdksPlugin.class);
        }));
    }

    private static void configureGenerateJdkConfigs(
            GenerateGradleJdkConfigsTask task,
            Project rootProject,
            BaselineJavaVersionsExtension baselineJavaVersionsExtension,
            JdksExtension jdksExtension,
            JdkDistributions jdkDistributions,
            Provider<JdkDistributionsService> jdkDistributionsService) {
        task.getJdkDistributions().set(jdkDistributionsService);
        task.usesService(jdkDistributionsService);
        task.getGradleDirectory()
                .set(rootProject.getLayout().getProjectDirectory().dir("gradle"));
        task.getDaemonJavaVersion().set(jdksExtension.getDaemonTarget());
        task.getJavaVersionToJdkDistros().putAll(rootProject.provider(() -> Stream.of(
                        baselineJavaVersionsExtension.libraryTarget(),
                        jdksExtension.getDaemonTarget(),
                        baselineJavaVersionsExtension.distributionTarget().map(ChosenJavaVersion::javaLanguageVersion),
                        baselineJavaVersionsExtension.runtime().map(ChosenJavaVersion::javaLanguageVersion))
                .map(Provider::get)
                .distinct()
                .collect(Collectors.toMap(
                        javaVersion -> javaVersion,
                        javaVersion -> getJdkDistributions(rootProject, javaVersion, jdksExtension)))));
        task.getCaCerts().putAll(jdksExtension.getCaCerts());
    }

    public static List<JdkDistributionConfig> getJdkDistributions(
            Project project, JavaLanguageVersion javaVersion, JdksExtension jdksExtension) {
        return Arrays.stream(Os.values())
                .flatMap(os -> Arrays.stream(Arch.values())
                        .map(arch -> getJdkDistribution(project, os, arch, javaVersion, jdksExtension)))
                .collect(Collectors.toList());
    }

    private static JdkDistributionConfig getJdkDistribution(
            Project project, Os os, Arch arch, JavaLanguageVersion javaVersion, JdksExtension jdksExtension) {
        Optional<JdkExtension> jdkExtension = jdksExtension.jdkFor(javaVersion, project);
        if (jdkExtension.isEmpty()) {
            throw new RuntimeException(String.format(
                    "Could not find a JDK with major version %s in project '%s'. "
                            + "Please ensure that you have configured JDKs properly for "
                            + "gradle-jdks as per the readme: "
                            + "https://github.com/palantir/gradle-jdks#usage",
                    javaVersion.toString(), project.getPath()));
        }
        String jdkVersion =
                jdkExtension.get().jdkFor(os).jdkFor(arch).getJdkVersion().get();
        JdkDistributionName jdkDistributionName =
                jdkExtension.get().getDistributionName().get();
        JdkRelease jdkRelease =
                JdkRelease.builder().arch(arch).os(os).version(jdkVersion).build();
        JdkSpec jdkSpec = JdkSpec.builder()
                .distributionName(jdkDistributionName)
                .release(jdkRelease)
                .caCerts(CaCerts.from(jdksExtension.getCaCerts().get()))
                .build();
        JdkDistributionConfig jdkDistribution = project.getObjects().newInstance(JdkDistributionConfig.class);
        jdkDistribution.getDistributionName().set(jdkDistributionName.uiName());
        jdkDistribution.getConsistentHash().set(jdkSpec.consistentShortHash());
        jdkDistribution.getVersion().set(jdkVersion);
        jdkDistribution.getArch().set(arch);
        jdkDistribution.getOs().set(os);
        return jdkDistribution;
    }
}
