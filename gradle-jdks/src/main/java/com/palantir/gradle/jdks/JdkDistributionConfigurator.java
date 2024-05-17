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

import com.palantir.gradle.jdks.GradleJdkConfigs.JdkDistributionConfig;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

public abstract class JdkDistributionConfigurator {

    public static Map<JavaLanguageVersion, List<JdkDistributionConfig>> getJavaVersionToJdkDistros(
            Project project, List<Provider<JavaLanguageVersion>> javaVersions, JdksExtension jdksExtension) {
        return javaVersions.stream()
                .map(Provider::get)
                .distinct()
                .collect(Collectors.toMap(
                        javaVersion -> javaVersion,
                        javaVersion -> getJdkDistributionConfigs(project, javaVersion, jdksExtension)));
    }

    private static List<JdkDistributionConfig> getJdkDistributionConfigs(
            Project project, JavaLanguageVersion javaVersion, JdksExtension jdksExtension) {
        return Arrays.stream(Os.values())
                .flatMap(os -> Arrays.stream(Arch.values())
                        .map(arch -> getJdkDistributionConfig(project, os, arch, javaVersion, jdksExtension)))
                .collect(Collectors.toList());
    }

    private static JdkDistributionConfig getJdkDistributionConfig(
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
