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

import com.palantir.gradle.jdks.json.JdksInfoJson;
import com.palantir.gradle.jdks.setup.common.Arch;
import com.palantir.gradle.jdks.setup.common.Os;
import java.nio.file.Files;
import java.nio.file.Path;

public final class GradleJdksConfigurator {

    private static final JdkDistributions jdkDistributions = new JdkDistributions();

    /**
     * Writes the jdk configuration files for a given jdk distribution.
     * @param targetDir the target directory where the configuration files should be written to.
     * @param jdksInfoJson jdk info json object which will be rendered as a directory structure in the target directory.
     * @param baseUrl the base url fron where the jdk distributions can be downloaded e.g. `https://corretto.aws`
     */
    public static void renderJdkInstallationConfigurations(Path targetDir, JdksInfoJson jdksInfoJson, String baseUrl) {
        writeGradleJdkConfigurations(targetDir, jdksInfoJson, baseUrl);
        writeInstallationScripts(targetDir);
    }

    private static void writeGradleJdkConfigurations(Path targetDir, JdksInfoJson jdksInfoJson, String baseUrl) {
        jdksInfoJson.jdksPerJavaVersion().forEach((javaVersion, jdkInfoJson) -> {
            jdkInfoJson.os().forEach((os, jdkOsInfoJson) -> {
                jdkOsInfoJson.arch().forEach((arch, jdkOsArchInfoJson) -> {
                    writeJdkInstallationConfiguration(
                            jdkInfoJson.distribution(),
                            baseUrl,
                            javaVersion,
                            jdkOsArchInfoJson.version(),
                            os,
                            arch,
                            targetDir);
                });
            });
        });
    }

    private static void writeJdkInstallationConfiguration(
            JdkDistributionName jdkDistributionName,
            String baseUrl,
            String javaVersion,
            String jdkVersion,
            Os os,
            Arch arch,
            Path targetDir) {
        JdkRelease jdkRelease =
                JdkRelease.builder().arch(arch).os(os).version(jdkVersion).build();

        Path jdksDir = targetDir.resolve("jdks");
        Path jdkOsArchDir = jdksDir.resolve(javaVersion).resolve(os.uiName()).resolve(arch.uiName());
        try {
            Files.createDirectories(jdkOsArchDir);
            GradleJdksConfigsUtils.writeConfigurationFile(
                    jdkOsArchDir.resolve("download-url"), resolveDownloadUrl(baseUrl, jdkDistributionName, jdkRelease));
            GradleJdksConfigsUtils.writeConfigurationFile(
                    jdkOsArchDir.resolve("local-path"), resolveLocalPath(jdkDistributionName, jdkRelease));
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format("Failed to crate jdk configuration files in dir %s", jdkOsArchDir), e);
        }
    }

    /**
     * Writes the installation scripts & the jars to the target directory.
     * @param targetDir the target directory where the installation scripts should be written to.
     */
    private static void writeInstallationScripts(Path targetDir) {
        Path scriptsDir = targetDir.resolve("scripts");
        GradleJdksConfigsUtils.createDirectories(scriptsDir);
        Path functionsScript = GradleJdksConfigsUtils.copyResourceToPath(scriptsDir, "gradle-jdks-functions.sh");
        GradleJdksConfigsUtils.setExecuteFilePermissions(functionsScript);
        Path installationScript = GradleJdksConfigsUtils.copyResourceToPath(scriptsDir, "install-jdks.sh");
        GradleJdksConfigsUtils.setExecuteFilePermissions(installationScript);
        GradleJdksConfigsUtils.copyResourceToPath(scriptsDir, "gradle-jdks-setup.jar");
    }

    private static String resolveLocalPath(JdkDistributionName jdkDistributionName, JdkRelease jdkRelease) {
        return String.format("%s-%s", jdkDistributionName, jdkRelease.version());
    }

    private static String resolveDownloadUrl(
            String baseUrl, JdkDistributionName jdkDistributionName, JdkRelease jdkRelease) {
        JdkPath jdkPath = jdkDistributions.get(jdkDistributionName).path(jdkRelease);
        return String.format("%s/%s.%s", baseUrl, jdkPath.filename(), jdkPath.extension());
    }

    private GradleJdksConfigurator() {}
}
