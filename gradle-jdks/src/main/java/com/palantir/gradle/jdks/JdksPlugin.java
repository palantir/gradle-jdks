/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
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

import com.palantir.baseline.plugins.javaversions.BaselineJavaVersions;
import com.palantir.baseline.plugins.javaversions.BaselineJavaVersionsExtension;
import com.palantir.baseline.plugins.javaversions.ChosenJavaVersion;
import com.palantir.gradle.jdks.GenerateGradleJdkConfigs.GenerateGradleJdkConfigsTask;
import com.palantir.gradle.jdks.GenerateGradleJdkConfigs.JdkDistributionConfig;
import com.palantir.gradle.jdks.GradleWrapperPatcher.GradleWrapperPatcherTask;
import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.toolchain.JavaInstallationMetadata;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JdksPlugin implements Plugin<Project> {

    private static final String ENABLE_GRADLE_JDK_SETUP = "gradle.jdk.setup.enabled";
    private static final Logger log = LoggerFactory.getLogger(JdksPlugin.class);

    @Override
    public void apply(Project rootProject) {
        if (rootProject.getRootProject() != rootProject) {
            throw new IllegalArgumentException("com.palantir.jdks must be applied to the root project only");
        }
        rootProject.getPluginManager().apply(BaselineJavaVersions.class);

        JdkDistributions jdkDistributions = new JdkDistributions();

        JdksExtension jdksExtension = extension(rootProject, jdkDistributions);

        BaselineJavaVersionsExtension baselineJavaVersionsExtension =
                rootProject.getExtensions().getByType(BaselineJavaVersionsExtension.class);

        TaskProvider<GenerateGradleJdkConfigsTask> generateGradleJdkConfigs = rootProject
                .getTasks()
                .register("generateGradleJdkConfigs", GenerateGradleJdkConfigsTask.class, task -> {
                    configureGenerateJdkConfigs(
                            task, rootProject, baselineJavaVersionsExtension, jdksExtension, jdkDistributions);
                    task.getFix().set(true);
                    // TODO(crogoz): changeMe, add an option
                    task.getOutputGradleDirectory()
                            .set(rootProject.getLayout().dir(rootProject.provider(() -> rootProject.file("gradle"))));
                });
        TaskProvider<GenerateGradleJdkConfigsTask> checkGradleJdkConfigs = rootProject
                .getTasks()
                .register("checkGradleJdkConfigs", GenerateGradleJdkConfigsTask.class, task -> {
                    configureGenerateJdkConfigs(
                            task, rootProject, baselineJavaVersionsExtension, jdksExtension, jdkDistributions);
                    task.getFix().set(false);
                    task.getOutputGradleDirectory()
                            .set(rootProject.getLayout().getBuildDirectory().dir("gradleConfigs"));
                });

        if (getEnableGradleJdkProperty(rootProject)) {
            rootProject
                    .getLogger()
                    .info("Gradle JDK automanagement is enabled. The JDKs used for all subprojects "
                            + "are managed by the configured custom toolchains.");
            baselineJavaVersionsExtension.getSetupJdkToolchains().set(false);

            rootProject
                    .getTasks()
                    .named(LifecycleBasePlugin.CHECK_TASK_NAME)
                    .configure(check -> check.dependsOn(checkGradleJdkConfigs));

        } else {
            JdkManager jdkManager = new JdkManager(
                    jdksExtension.getJdkStorageLocation(), jdkDistributions, new JdkDownloaders(jdksExtension));

            rootProject
                    .getExtensions()
                    .getByType(BaselineJavaVersionsExtension.class)
                    .jdks((javaLanguageVersion, project) -> {
                        JdkExtension jdkExtension = jdksExtension
                                .jdkFor(javaLanguageVersion, project)
                                .orElseThrow(() -> new RuntimeException(String.format(
                                        "Could not find a JDK with major version %s in project '%s'. "
                                                + "Please ensure that you have configured JDKs properly for "
                                                + "gradle-jdks as per the readme: "
                                                + "https://github.com/palantir/gradle-jdks#usage",
                                        javaLanguageVersion.toString(), project.getPath())));

                        return Optional.of(javaInstallationForLanguageVersion(
                                project, jdksExtension, jdkExtension, jdkManager, javaLanguageVersion));
                    });
        }

        TaskProvider<GradleWrapperPatcherTask> wrapperPatcherTask = rootProject
                .getTasks()
                .register("wrapperJdkPatcher", GradleWrapperPatcherTask.class, task -> {
                    task.onlyIf(t -> getEnableGradleJdkProperty(rootProject));
                    Path gradlewPath = rootProject.getRootDir().toPath().resolve("gradlew");
                    task.getOriginalGradlewScript().set(rootProject.file(gradlewPath.toAbsolutePath()));
                    task.getPatchedGradlewScript().set(rootProject.file(gradlewPath.toAbsolutePath()));
                    task.getOriginalGradleWrapperJar()
                            .set(rootProject.file(
                                    rootProject.getRootDir().toPath().resolve("gradle/wrapper/gradle-wrapper.jar")));
                    task.getPatchedGradleWrapperJar()
                            .set(rootProject.file(
                                    rootProject.getRootDir().toPath().resolve("gradle/wrapper/gradle-wrapper.jar")));
                    File gradleJdksSetupJar = rootProject
                            .getRootDir()
                            .toPath()
                            .resolve("gradle/gradle-jdks-setup.jar")
                            .toFile();
                    task.getBuildDir().set(task.getTemporaryDir());
                    task.getGradleJdksSetupJar().set(gradleJdksSetupJar.exists() ? gradleJdksSetupJar : null);
                    task.dependsOn(generateGradleJdkConfigs);
                });
        rootProject.getTasks().named("wrapper").configure(wrapperTask -> {
            wrapperTask.finalizedBy(wrapperPatcherTask);
        });
        rootProject.allprojects(proj -> proj.getPluginManager().withPlugin("java", unused -> {
            proj.getPluginManager().apply(SubProjectJdksPlugin.class);
        }));
    }

    public boolean getEnableGradleJdkProperty(Project project) {
        return !CurrentOs.get().equals(Os.WINDOWS)
                && Optional.ofNullable(project.findProperty(ENABLE_GRADLE_JDK_SETUP))
                        .map(prop -> Boolean.parseBoolean(((String) prop)))
                        .orElse(false);
    }

    private JdksExtension extension(Project rootProject, JdkDistributions jdkDistributions) {
        JdksExtension jdksExtension = rootProject.getExtensions().create("jdks", JdksExtension.class);

        jdksExtension
                .getJdkStorageLocation()
                .set(rootProject
                        .getLayout()
                        .dir(rootProject.provider(
                                () -> new File(System.getProperty("user.home"), ".gradle/gradle-jdks"))));

        Arrays.stream(JdkDistributionName.values()).forEach(jdkDistributionName -> {
            jdksExtension.jdkDistribution(jdkDistributionName, jdkDistributionExtension -> {
                jdkDistributionExtension
                        .getBaseUrl()
                        .set(jdkDistributions.get(jdkDistributionName).defaultBaseUrl());
            });
        });

        return jdksExtension;
    }

    private JavaInstallationMetadata javaInstallationForLanguageVersion(
            Project project,
            JdksExtension jdksExtension,
            JdkExtension jdkExtension,
            JdkManager jdkManager,
            JavaLanguageVersion javaLanguageVersion) {

        Os currentOs = CurrentOs.get();
        Arch currentArch = CurrentArch.get();

        String version = jdkExtension
                .jdkFor(currentOs)
                .jdkFor(currentArch)
                .getJdkVersion()
                .get();

        JdkDistributionName jdkDistributionName =
                jdkExtension.getDistributionName().get();

        Provider<Directory> installationPath = project.getLayout().dir(project.provider(() -> jdkManager
                .jdk(
                        project,
                        JdkSpec.builder()
                                .distributionName(jdkDistributionName)
                                .release(JdkRelease.builder()
                                        .version(version)
                                        .os(currentOs)
                                        .arch(currentArch)
                                        .build())
                                .caCerts(CaCerts.from(jdksExtension.getCaCerts().get()))
                                .build())
                .toFile()));

        return GradleJdksJavaInstallationMetadata.create(
                javaLanguageVersion, version, version, jdkDistributionName.uiName(), installationPath);
    }

    static List<JdkDistributionConfig> getJdkDistributions(
            Project project,
            JdkDistributions jdkDistributions,
            JavaLanguageVersion javaVersion,
            JdksExtension jdksExtension) {
        return Arrays.stream(Os.values())
                .flatMap(os -> Arrays.stream(Arch.values())
                        .map(arch ->
                                getJdkDistribution(project, jdkDistributions, os, arch, javaVersion, jdksExtension)))
                .collect(Collectors.toList());
    }

    private static JdkDistributionConfig getJdkDistribution(
            Project project,
            JdkDistributions jdkDistributions,
            Os os,
            Arch arch,
            JavaLanguageVersion javaVersion,
            JdksExtension jdksExtension) {
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
        JdkPath jdkPath = jdkDistributions.get(jdkDistributionName).path(jdkRelease);
        JdkSpec jdkSpec = JdkSpec.builder()
                .distributionName(jdkDistributionName)
                .release(jdkRelease)
                .caCerts(CaCerts.from(jdksExtension.getCaCerts().get()))
                .build();
        String downloadUrl = String.format(
                "%s/%s.%s",
                jdkDistributions.get(jdkDistributionName).defaultBaseUrl(), jdkPath.filename(), jdkPath.extension());
        String localFileName =
                String.format("%s-%s-%s", jdkDistributionName.uiName(), jdkVersion, jdkSpec.consistentShortHash());
        JdkDistributionConfig jdkDistribution = project.getObjects().newInstance(JdkDistributionConfig.class);
        jdkDistribution.getDownloadUrl().set(downloadUrl);
        jdkDistribution.getLocalPath().set(localFileName);
        jdkDistribution.getArch().set(arch);
        jdkDistribution.getOs().set(os);
        return jdkDistribution;
    }

    private static void configureGenerateJdkConfigs(
            GenerateGradleJdkConfigsTask task,
            Project rootProject,
            BaselineJavaVersionsExtension baselineJavaVersionsExtension,
            JdksExtension jdksExtension,
            JdkDistributions jdkDistributions) {
        task.getGradleDirectory()
                .set(rootProject.getLayout().getProjectDirectory().dir("gradle"));
        task.getDaemonJavaVersion().set(baselineJavaVersionsExtension.getDaemonTarget());
        task.getJavaVersionToJdkDistros().putAll(rootProject.provider(() -> Stream.of(
                        baselineJavaVersionsExtension.libraryTarget(),
                        baselineJavaVersionsExtension.getDaemonTarget(),
                        baselineJavaVersionsExtension.distributionTarget().map(ChosenJavaVersion::javaLanguageVersion),
                        baselineJavaVersionsExtension.runtime().map(ChosenJavaVersion::javaLanguageVersion))
                .map(Provider::get)
                .distinct()
                .collect(Collectors.toMap(
                        javaVersion -> javaVersion,
                        javaVersion ->
                                getJdkDistributions(rootProject, jdkDistributions, javaVersion, jdksExtension)))));
        task.getCaCerts().putAll(jdksExtension.getCaCerts());
    }
}
