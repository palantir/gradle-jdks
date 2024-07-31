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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class GradleJdksConfigurator {

    private static final JdkDistributions jdkDistributions = new JdkDistributions();

    /**
     * Writes the jdk configuration files for a given jdk distribution.
     *
     * @param targetDir the target directory where the configuration files should be written to.
     * @param baseUrl the base url where the jdk distributions are hosted e.g. `https://corretto.aws`
     * @param jdkDistributionName the name of the jdk distribution e.g. corretto.
     * @param os the operating system of the jdk distribution e.g. linux.
     * @param arch the architecture of the jdk distribution e.g. x64.
     * @param javaVersion the major java version e.g. 8.
     * @param jdkVersion the full version of the jdkVersion e.g. 8.282.08.1.
     * @param caCerts the list of certificates that need to be added to the jdk. This influences the hash and the
     * local installation directory name.
     */
    public static void writeGradleJdkInstallationConfigs(
            Path targetDir,
            String baseUrl,
            JdkDistributionName jdkDistributionName,
            Os os,
            Arch arch,
            String javaVersion,
            String jdkVersion,
            Map<String, String> caCerts) {
        JdkSpec jdkSpec = JdkSpec.builder()
                .distributionName(jdkDistributionName)
                .release(JdkRelease.builder()
                        .arch(arch)
                        .os(os)
                        .version(jdkVersion)
                        .build())
                .caCerts(CaCerts.from(caCerts))
                .build();

        Path jdksDir = targetDir.resolve("jdks");
        Path jdkOsArchDir = jdksDir.resolve(javaVersion).resolve(os.uiName()).resolve(arch.uiName());
        try {
            Files.createDirectories(jdkOsArchDir);
            GradleJdksConfigsUtils.writeConfigurationFile(
                    jdkOsArchDir.resolve("download-url"), resolveDownloadUrl(baseUrl, jdkSpec));
            GradleJdksConfigsUtils.writeConfigurationFile(
                    jdkOsArchDir.resolve("local-path"), resolveLocalPath(jdkSpec));
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format("Failed to crate jdk configuration files in dir %s", jdkOsArchDir), e);
        }
    }

    private static String resolveLocalPath(JdkSpec jdkSpec) {
        return String.format(
                "%s-%s-%s", jdkSpec.distributionName(), jdkSpec.release().version(), jdkSpec.consistentShortHash());
    }

    private static String resolveDownloadUrl(String baseUrl, JdkSpec jdkSpec) {
        JdkPath jdkPath = jdkDistributions.get(jdkSpec.distributionName()).path(jdkSpec.release());
        return String.format("%s/%s.%s", baseUrl, jdkPath.filename(), jdkPath.extension());
    }

    /**
     * Writes the installation scripts & the jars to the target directory.
     * @param targetDir the target directory where the installation scripts should be written to.
     */
    public static void writeInstallationScripts(Path targetDir) {
        Path scriptsDir = targetDir.resolve("scripts");
        GradleJdksConfigsUtils.createDirectories(scriptsDir);
        Path functionsScript = GradleJdksConfigsUtils.copyResourceToPath(scriptsDir, "gradle-jdks-functions.sh");
        GradleJdksConfigsUtils.setExecuteFilePermissions(functionsScript);
        Path installationScript = GradleJdksConfigsUtils.copyResourceToPath(scriptsDir, "install-jdks.sh");
        GradleJdksConfigsUtils.setExecuteFilePermissions(installationScript);
        GradleJdksConfigsUtils.copyResourceToPath(scriptsDir, "gradle-jdks-setup.jar");
    }

    private GradleJdksConfigurator() {}
}
