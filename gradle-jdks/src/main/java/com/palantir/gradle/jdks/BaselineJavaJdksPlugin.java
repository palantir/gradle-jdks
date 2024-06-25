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

import com.palantir.baseline.plugins.javaversions.BaselineJavaVersions;
import com.palantir.baseline.plugins.javaversions.BaselineJavaVersionsExtension;
import java.util.Optional;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.provider.Provider;
import org.gradle.jvm.toolchain.JavaInstallationMetadata;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

public final class BaselineJavaJdksPlugin implements Plugin<Project> {

    @Override
    public void apply(Project rootProject) {
        if (JdksPlugin.isGradleJdkSetupEnabled(rootProject)) {
            throw new RuntimeException("Cannot apply BaselineJavaJdksPlugin with palantir.jdk.setup.enabled");
        }
        rootProject.getPluginManager().apply(BaselineJavaVersions.class);

        JdkDistributions jdkDistributions = new JdkDistributions();

        JdksExtension jdksExtension = JdksPlugin.extension(rootProject, jdkDistributions);
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
