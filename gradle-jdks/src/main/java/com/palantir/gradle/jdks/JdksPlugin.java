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
import com.palantir.gradle.jdks.GradleWrapperPatcher.GradleWrapperPatcherTask;
import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.toolchain.JavaInstallationMetadata;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

public final class JdksPlugin implements Plugin<Project> {

    private static final String ENABLE_GRADLE_JDK_SETUP = "gradle.jdk.setup.enabled";

    @Override
    public void apply(Project rootProject) {
        if (rootProject.getRootProject() != rootProject) {
            throw new IllegalArgumentException("com.palantir.jdks must be applied to the root project only");
        }
        rootProject.getPluginManager().apply(BaselineJavaVersions.class);

        JdkDistributions jdkDistributions = new JdkDistributions();

        JdksExtension jdksExtension = extension(rootProject, jdkDistributions);

        if (getEnableGradleJdkProperty(rootProject)) {
            rootProject
                    .getLogger()
                    .info("Gradle JDK automanagement is enabled. The JDKs used for all subprojects "
                            + "are managed by the configured custom toolchains.");
            rootProject
                    .getExtensions()
                    .getByType(BaselineJavaVersionsExtension.class)
                    .getSetupJdkToolchains()
                    .set(false);
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
                });
        rootProject.getTasks().named("wrapper").configure(wrapperTask -> {
            wrapperTask.finalizedBy(wrapperPatcherTask);
        });
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
}
