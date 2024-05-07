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
import com.palantir.gradle.jdks.UpdateGradleJdks.UpdateGradleJdksTask;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
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

        // run `generateGradleJdksSetup` if build.gradle entry for jdk-latest is modified or whenever the extensions are
        // changed
        rootProject.afterEvaluate(project -> {
            log.info("After evaluate is called");
            Arch arch = CurrentArch.get();
            Os os = CurrentOs.get();
            Stream.of(
                            baselineJavaVersionsExtension.runtime().get().javaLanguageVersion(),
                            baselineJavaVersionsExtension.libraryTarget().get(),
                            baselineJavaVersionsExtension
                                    .distributionTarget()
                                    .get()
                                    .javaLanguageVersion(),
                            baselineJavaVersionsExtension.getDaemonTarget().get())
                    .distinct()
                    .forEach(javaVersion -> {
                        try {
                            UpdateGradleJdks.JdkDistribution jdkDistribution =
                                    getJdkDistro(rootProject, javaVersion, jdkDistributions, jdksExtension);
                            Path outputDir = project.getLayout()
                                    .getProjectDirectory()
                                    .getAsFile()
                                    .toPath()
                                    .resolve("gradle/jdks")
                                    .resolve(javaVersion.toString())
                                    .resolve(os.uiName())
                                    .resolve(arch.uiName());
                            Files.createDirectories(outputDir);
                            File downloadUrlFile =
                                    outputDir.resolve("download-url").toFile();
                            writeToFile(
                                    downloadUrlFile,
                                    jdkDistribution.getDownloadUrl().get());
                            File localPath = outputDir.resolve("local-path").toFile();
                            writeToFile(
                                    localPath, jdkDistribution.getLocalPath().get());
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
            try {
                writeToFile(
                        project.getLayout()
                                .getProjectDirectory()
                                .getAsFile()
                                .toPath()
                                .resolve("gradle/gradle-jdk-major-version")
                                .toFile(),
                        baselineJavaVersionsExtension.getDaemonTarget().get().toString());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        rootProject.getTasks().register("generateGradleJdksSetup", UpdateGradleJdksTask.class, task -> {
            task.getDaemonJavaVersion()
                    .set(rootProject.provider(() ->
                            baselineJavaVersionsExtension.getDaemonTarget().get()));
            task.getDaemonJdkFile().set(rootProject.provider(() -> rootProject
                    .getLayout()
                    .getProjectDirectory()
                    .dir("gradle")
                    .file("gradle-jdk-major-version")));
            task.getJavaVersionToJdkDistro().set(rootProject.provider(() -> Stream.of(
                            baselineJavaVersionsExtension.runtime().get().javaLanguageVersion(),
                            baselineJavaVersionsExtension.libraryTarget().get(),
                            baselineJavaVersionsExtension
                                    .distributionTarget()
                                    .get()
                                    .javaLanguageVersion(),
                            baselineJavaVersionsExtension.getDaemonTarget().get())
                    .distinct()
                    .collect(Collectors.toMap(
                            javaVersion -> javaVersion,
                            javaVersion -> getJdkDistro(rootProject, javaVersion, jdkDistributions, jdksExtension)))));
            task.getGradleJdkDirectories()
                    .set(rootProject.provider(() -> task.getJavaVersionToJdkDistro().get().keySet().stream()
                            .collect(Collectors.toMap(javaVersion -> javaVersion, javaVersion -> rootProject
                                    .getLayout()
                                    .getProjectDirectory()
                                    .dir("gradle/jdks")
                                    .dir(javaVersion.toString())))));
        });
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
                            .resolve("gradle/jdks/gradle-jdks-setup.jar")
                            .toFile();
                    task.getBuildDir().set(task.getTemporaryDir());
                    task.getGradleJdksSetupJar().set(gradleJdksSetupJar.exists() ? gradleJdksSetupJar : null);
                });
        rootProject.getTasks().named("wrapper").configure(wrapperTask -> {
            wrapperTask.finalizedBy(wrapperPatcherTask);
        });
    }

    private static UpdateGradleJdks.JdkDistribution getJdkDistro(
            Project rootProject,
            JavaLanguageVersion javaLanguageVersion,
            JdkDistributions jdkDistributions,
            JdksExtension jdksExtension) {

        JdkExtension jdkExtension = jdksExtension
                .jdkFor(javaLanguageVersion, rootProject)
                .orElseThrow(() -> new RuntimeException(String.format(
                        "Could not find a JDK with major version %s in project '%s'. "
                                + "Please ensure that you have configured JDKs properly for "
                                + "gradle-jdks as per the readme: "
                                + "https://github.com/palantir/gradle-jdks#usage",
                        javaLanguageVersion.toString(), rootProject.getPath())));
        Os currentOs = CurrentOs.get();
        Arch currentArch = CurrentArch.get();

        String jdkVersion = jdkExtension
                .jdkFor(currentOs)
                .jdkFor(currentArch)
                .getJdkVersion()
                .get();

        JdkDistributionName jdkDistributionName =
                jdkExtension.getDistributionName().get();

        JdkPath jdkPath = jdkDistributions
                .get(jdkDistributionName)
                .path(JdkRelease.builder()
                        .arch(currentArch)
                        .os(currentOs)
                        .version(jdkVersion)
                        .build());

        // consistentHash based on the caCerts (TODO)
        UpdateGradleJdks.JdkDistribution jdkDistribution =
                rootProject.getObjects().newInstance(UpdateGradleJdks.JdkDistribution.class);
        jdkDistribution
                .getDownloadUrl()
                .set(String.format(
                        "%s/%s.%s",
                        jdkDistributions.get(jdkDistributionName).defaultBaseUrl(),
                        jdkPath.filename(),
                        jdkPath.extension()));
        jdkDistribution
                .getLocalPath()
                .set(String.format("%s-%s-todo-crogoz", jdkDistributionName.uiName(), jdkVersion));
        return jdkDistribution;
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

    private static void writeToFile(File file, String content) throws IOException {
        if (!file.exists()) {
            file.createNewFile();
        }
        String contentWithLineEnding = content + "\n";
        Files.write(file.toPath(), contentWithLineEnding.getBytes(StandardCharsets.UTF_8));
    }
}
