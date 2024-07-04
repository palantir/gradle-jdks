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

import com.palantir.gradle.jdks.setup.common.Arch;
import com.palantir.gradle.jdks.setup.common.Os;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

public final class JdkDistributionConfigurator {

    private static Logger logger = Logging.getLogger(JdkDistributionConfigurator.class);
    private static final JavaLanguageVersion MINIMUM_SUPPORTED_JAVA_VERSION = JavaLanguageVersion.of(11);

    public static Map<JavaLanguageVersion, List<JdkDistributionConfig>> getJavaVersionToJdkDistros(
            Project project, JdkDistributions jdkDistributions, JdksExtension jdksExtension) {
        Set<JavaLanguageVersion> javaVersions = Arrays.stream(JavaVersion.values())
                .map(javaVersion -> JavaLanguageVersion.of(javaVersion.getMajorVersion()))
                .filter(javaLanguageVersion -> javaLanguageVersion.canCompileOrRun(MINIMUM_SUPPORTED_JAVA_VERSION))
                .collect(Collectors.toSet());
        return javaVersions.stream()
                .collect(Collectors.toMap(
                        javaVersion -> javaVersion,
                        javaVersion ->
                                getJdkDistributionConfigs(project, jdkDistributions, javaVersion, jdksExtension)));
    }

    private static List<JdkDistributionConfig> getJdkDistributionConfigs(
            Project project,
            JdkDistributions jdkDistributions,
            JavaLanguageVersion javaVersion,
            JdksExtension jdksExtension) {
        return Arrays.stream(Os.values())
                .flatMap(os -> Arrays.stream(Arch.values())
                        .flatMap(arch -> getJdkDistributionConfig(
                                project, jdkDistributions, os, arch, javaVersion, jdksExtension)))
                .collect(Collectors.toList());
    }

    private static Stream<JdkDistributionConfig> getJdkDistributionConfig(
            Project project,
            JdkDistributions jdkDistributions,
            Os os,
            Arch arch,
            JavaLanguageVersion javaVersion,
            JdksExtension jdksExtension) {
        Optional<JdkExtension> jdkExtension = jdksExtension.jdkFor(javaVersion, project);
        if (jdkExtension.isEmpty()) {
            logger.debug("Skipping JDK distribution for javaVersion={} as it is not configured", javaVersion);
            return Stream.empty();
        }
        Optional<String> jdkVersion = Optional.ofNullable(
                jdkExtension.get().jdkFor(os).jdkFor(arch).getJdkVersion().getOrNull());
        if (jdkVersion.isEmpty()) {
            logger.debug(
                    "Skipping JDK distribution for os={} arch={} javaVersion={} as it is not configured",
                    os,
                    arch,
                    javaVersion);
            return Stream.empty();
        }
        JdkDistributionName jdkDistributionName =
                jdkExtension.get().getDistributionName().get();
        JdkRelease jdkRelease =
                JdkRelease.builder().arch(arch).os(os).version(jdkVersion.get()).build();
        JdkSpec jdkSpec = JdkSpec.builder()
                .distributionName(jdkDistributionName)
                .release(jdkRelease)
                .caCerts(CaCerts.from(jdksExtension.getCaCerts().get()))
                .build();
        JdkDistributionConfig jdkDistribution = project.getObjects().newInstance(JdkDistributionConfig.class);
        jdkDistribution.getConsistentHash().set(jdkSpec.consistentShortHash());
        jdkDistribution.getArch().set(arch);
        jdkDistribution.getOs().set(os);
        JdkPath jdkPath = jdkDistributions.get(jdkDistributionName).path(jdkRelease);
        jdkDistribution
                .getDownloadUrl()
                .set(String.format(
                        "%s/%s.%s",
                        jdksExtension
                                .jdkDistributionFor(jdkDistributionName)
                                .getBaseUrl()
                                .get(),
                        jdkPath.filename(),
                        jdkPath.extension()));
        jdkDistribution
                .getLocalPath()
                .set(String.format(
                        "%s-%s-%s",
                        jdkDistributionName,
                        jdkVersion.get(),
                        jdkDistribution.getConsistentHash().get()));
        return Stream.of(jdkDistribution);
    }

    private JdkDistributionConfigurator() {}
}
