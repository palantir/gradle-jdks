/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.gradle.jdks;

import com.palantir.baseline.extensions.BaselineJavaVersionsExtension;
import com.palantir.baseline.plugins.BaselineJavaVersions;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.jvm.toolchain.JavaInstallationMetadata;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

public final class JdksPlugin implements Plugin<Project> {
    @Override
    public void apply(Project rootProject) {
        if (rootProject.getRootProject() != rootProject) {
            throw new IllegalArgumentException("com.palantir.jdks must be applied to the root project only");
        }

        JdkDistributions jdkDistributions = new JdkDistributions();

        JdksExtension jdksExtension = extension(rootProject, jdkDistributions);

        JdkManager jdkManager = new JdkManager(
                rootProject.getProject(),
                rootProject
                        .getLayout()
                        .getBuildDirectory()
                        .dir("jdks")
                        .get()
                        .getAsFile()
                        .toPath(),
                jdkDistributions,
                new JdkDownloaders(rootProject, jdksExtension));

        rootProject.getPluginManager().apply(BaselineJavaVersions.class);

        rootProject
                .getExtensions()
                .getByType(BaselineJavaVersionsExtension.class)
                .getJdks()
                .putAll(rootProject.provider(() -> {
                    Map<JavaLanguageVersion, JavaInstallationMetadata> ret = new HashMap<>();
                    jdksExtension.getJdks().get().forEach((javaLanguageVersion, jdkExtension) -> {
                        ret.put(
                                javaLanguageVersion,
                                javaInstallationForLanguageVersion(
                                        rootProject, jdksExtension, jdkExtension, jdkManager, javaLanguageVersion));
                    });

                    return ret;
                }));
    }

    private JdksExtension extension(Project rootProject, JdkDistributions jdkDistributions) {
        JdksExtension jdksExtension = rootProject.getExtensions().create("jdks", JdksExtension.class);

        Arrays.stream(JdkDistributionName.values()).forEach(jdkDistributionName -> {
            JdkDistributionExtension jdkDistributionExtension =
                    rootProject.getObjects().newInstance(JdkDistributionExtension.class);

            jdkDistributionExtension
                    .getBaseUrl()
                    .set(jdkDistributions.get(jdkDistributionName).defaultBaseUrl());

            jdksExtension.getJdkDistributions().put(jdkDistributionName, jdkDistributionExtension);
        });

        return jdksExtension;
    }

    private GradleJdksJavaInstallationMetadata javaInstallationForLanguageVersion(
            Project rootProject,
            JdksExtension jdksExtension,
            JdkExtension jdkExtension,
            JdkManager jdkManager,
            JavaLanguageVersion javaLanguageVersion) {

        String version = jdkExtension.getJdkVersion().get();
        JdkDistributionName jdkDistributionName =
                jdkExtension.getDistributionName().get();

        Path jdk = jdkManager.jdk(JdkSpec.builder()
                .distributionName(jdkDistributionName)
                .release(JdkRelease.builder().version(version).build())
                .caCerts(CaCerts.from(jdksExtension.getCaCerts().get()))
                .build());

        return GradleJdksJavaInstallationMetadata.builder()
                .installationPath(rootProject
                        .getLayout()
                        .dir(rootProject.provider(jdk::toFile))
                        .get())
                .javaRuntimeVersion(version)
                .languageVersion(javaLanguageVersion)
                .jvmVersion(version)
                .vendor(jdkDistributionName.uiName())
                .build();
    }
}
