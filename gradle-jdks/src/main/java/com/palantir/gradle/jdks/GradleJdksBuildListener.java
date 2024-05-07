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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.palantir.baseline.plugins.javaversions.BaselineJavaVersionExtension;
import com.palantir.baseline.plugins.javaversions.BaselineJavaVersionsExtension;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.gradle.BuildListener;
import org.gradle.BuildResult;
import org.gradle.api.Project;
import org.gradle.api.initialization.Settings;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

public final class GradleJdksBuildListener implements BuildListener {

    private static final Logger log = Logging.getLogger(GradleJdksBuildListener.class);
    private final JdkDistributions jdkDistributions;

    public GradleJdksBuildListener(JdkDistributions jdkDistributions) {
        this.jdkDistributions = jdkDistributions;
    }

    @Override
    public void projectsEvaluated(Gradle gradle) {
        /* the evaluation changes iif
           - javaVersions extension is changed
           - jdks extension is changed (or jdks-latest is changed which in turn changes the jdks)
        */
        // TODO(crogoz): if the files have changed, then we should throw and ask the user to re-run the build
        log.info("Projects were evaluated");
        BaselineJavaVersionsExtension baselineJavaVersionsExtension =
                gradle.getRootProject().getExtensions().getByType(BaselineJavaVersionsExtension.class);
        Set<JavaLanguageVersion> javaVersions = getJavaVersions(gradle, baselineJavaVersionsExtension);
        Path gradleDir = gradle.getRootProject()
                .getLayout()
                .getProjectDirectory()
                .getAsFile()
                .toPath()
                .resolve("gradle");
        Path gradleJdksDir = gradleDir.resolve("jdks");
        Set<Path> expectedJdkPaths = javaVersions.stream()
                .map(javaVersion -> gradleJdksDir.resolve(javaVersion.toString()))
                .collect(Collectors.toSet());
        deleteUnexpectedJdkFiles(gradleJdksDir, expectedJdkPaths);

        JdksExtension jdksExtension = gradle.getRootProject().getExtensions().getByType(JdksExtension.class);
        javaVersions.forEach(javaVersion ->
                createGradleJdkFiles(gradle.getRootProject(), gradleJdksDir, javaVersion, jdksExtension));

        try {
            writeToFile(
                    gradleDir.resolve("gradle-jdk-major-version").toFile(),
                    baselineJavaVersionsExtension.getDaemonTarget().get().toString());
        } catch (IOException e) {
            throw new RuntimeException("Unable to write gradle-jdk-major-version", e);
        }
    }

    private static void deleteUnexpectedJdkFiles(Path gradleJdksDir, Set<Path> expectedJdkPaths) {
        try (Stream<Path> pathStream = Files.list(gradleJdksDir)) {
            Set<Path> existingJdkPaths = pathStream.collect(Collectors.toSet());
            Sets.difference(expectedJdkPaths, existingJdkPaths).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    throw new RuntimeException("Unable to delete gradle jdk files", e);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException("Unable to delete gradle jdk files", e);
        }
    }

    @Override
    public void buildFinished(BuildResult result) {}

    @Override
    public void settingsEvaluated(Settings settings) {}

    @Override
    public void projectsLoaded(Gradle gradle) {}

    private static Set<JavaLanguageVersion> getJavaVersions(
            Gradle gradle, BaselineJavaVersionsExtension baselineJavaVersionsExtension) {
        ImmutableSet.Builder<JavaLanguageVersion> javaVersions = ImmutableSet.builder();
        javaVersions.addAll(List.of(
                baselineJavaVersionsExtension.getDaemonTarget().get(),
                baselineJavaVersionsExtension.libraryTarget().get(),
                baselineJavaVersionsExtension.distributionTarget().get().javaLanguageVersion(),
                baselineJavaVersionsExtension.runtime().get().javaLanguageVersion()));
        gradle.allprojects(project -> {
            BaselineJavaVersionExtension baselineJavaVersionExtension =
                    project.getExtensions().getByType(BaselineJavaVersionExtension.class);
            javaVersions.addAll(List.of(
                    baselineJavaVersionExtension.runtime().get().javaLanguageVersion(),
                    baselineJavaVersionExtension.target().get().javaLanguageVersion()));
        });
        return javaVersions.build();
    }

    private void createGradleJdkFiles(
            Project rootProject, Path gradleJdksDir, JavaLanguageVersion javaVersion, JdksExtension jdksExtension) {
        for (Os os : Os.values()) {
            for (Arch arch : Arch.values()) {
                try {
                    createGradleJdkFiles(rootProject, gradleJdksDir, os, arch, javaVersion, jdksExtension);
                } catch (IOException e) {
                    throw new RuntimeException("Unable to create gradle jdk files", e);
                }
            }
        }
    }

    private void createGradleJdkFiles(
            Project rootProject,
            Path gradleJdksDir,
            Os os,
            Arch arch,
            JavaLanguageVersion javaVersion,
            JdksExtension jdksExtension)
            throws IOException {
        // TODO(CROGOZ): actually get the project
        Optional<JdkExtension> jdkExtension = jdksExtension.jdkFor(javaVersion, rootProject);
        if (jdkExtension.isEmpty()) {
            log.warn(
                    "Could not find a JDK with major version {} in project '{}'. "
                            + "Please ensure that you have configured JDKs properly for "
                            + "gradle-jdks as per the readme: "
                            + "https://github.com/palantir/gradle-jdks#usage",
                    javaVersion.toString(),
                    rootProject.getPath());
            return;
        }
        Path outputDir = gradleJdksDir
                .resolve(javaVersion.toString())
                .resolve(os.uiName())
                .resolve(arch.uiName());
        Files.createDirectories(outputDir);
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
        File downloadUrlFile = outputDir.resolve("download-url").toFile();
        writeToFile(downloadUrlFile, downloadUrl);
        String localFileName = String.format(
                "%s-%s-%s-crogoz", jdkDistributionName.uiName(), jdkVersion, jdkSpec.consistentShortHash());
        File localPath = outputDir.resolve("local-path").toFile();
        writeToFile(localPath, localFileName);
    }

    private static void writeToFile(File file, String content) throws IOException {
        if (!file.exists()) {
            file.createNewFile();
        }
        String contentWithLineEnding = content + "\n";
        Files.write(file.toPath(), contentWithLineEnding.getBytes(StandardCharsets.UTF_8));
    }
}
