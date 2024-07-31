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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;

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
        writeConfigurationFile(jdkOsArchDir.resolve("download-url"), resolveDownloadUrl(baseUrl, jdkSpec));
        writeConfigurationFile(jdkOsArchDir.resolve("local-path"), resolveLocalPath(jdkSpec));
    }

    private static void writeConfigurationFile(Path pathFile, String content) {
        try {
            Files.createDirectories(pathFile.getParent());
            // The content of the configuration files should always end with a newline character to ensure the file can
            // be read by {see: resources/install-jdks.sh#read_value }
            String contentWithLineEnding = content + "\n";
            Files.write(
                    pathFile, contentWithLineEnding.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create directories for file: " + pathFile, e);
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
     * Writes the installation script to the target directory.
     *
     * @param targetDir the target directory where the installation scripts should be written to.
     */
    public static void writeInstallationScripts(Path targetDir) {
        Path scriptsDir = targetDir.resolve("scripts");
        maybeCreateDirectories(scriptsDir);
        Path functionsScript = copyResourceToPath(scriptsDir, "gradle-jdks-functions.sh");
        setExecuteFilePermissions(functionsScript);
        Path installationScript = copyResourceToPath(scriptsDir, "install-jdks.sh");
        setExecuteFilePermissions(installationScript);
        copyResourceToPath(scriptsDir, "gradle-jdks-setup.jar");
    }

    private static void maybeCreateDirectories(Path directory) {
        if (Files.exists(directory)) {
            return;
        }
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new RuntimeException(String.format("Failed to create directory %s", directory), e);
        }
    }

    private static Path copyResourceToPath(Path targetDir, String resourceName) {
        try {
            URL installJdksResource =
                    GradleJdksConfigurator.class.getClassLoader().getResource(resourceName);
            Path installationScript = targetDir.resolve(resourceName);
            URLConnection urlConnection = installJdksResource.openConnection();
            if (urlConnection instanceof JarURLConnection) {
                JarURLConnection connection = (JarURLConnection) installJdksResource.openConnection();
                JarEntry jarEntry = connection.getJarFile().getJarEntry(resourceName);
                try (InputStream is = connection.getJarFile().getInputStream(jarEntry);
                        OutputStream os = new FileOutputStream(installationScript.toFile())) {
                    is.transferTo(os);
                }
            } else {
                writeResourceStreamToFile(installationScript, resourceName);
            }
            return installationScript;
        } catch (IOException e) {
            throw new RuntimeException(String.format("Failed to write the %s script", resourceName), e);
        }
    }

    private static void writeResourceStreamToFile(Path targetPath, String resource) {
        try (InputStream inputStream =
                GenerateGradleJdksConfigsTask.class.getClassLoader().getResourceAsStream(resource)) {
            if (inputStream == null) {
                throw new RuntimeException(String.format("Resource not found: %s:", resource));
            }
            try (OutputStream outputStream = new FileOutputStream(targetPath.toFile())) {
                inputStream.transferTo(outputStream);
            }
        } catch (IOException e) {
            throw new RuntimeException(String.format("Failed to write %s to %s", resource, targetPath), e);
        }
    }

    private static void setExecuteFilePermissions(Path path) {
        try {
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(path);
            perms.addAll(Set.of(
                    PosixFilePermission.OWNER_EXECUTE,
                    PosixFilePermission.GROUP_EXECUTE,
                    PosixFilePermission.OTHERS_EXECUTE));
            Files.setPosixFilePermissions(path, perms);
        } catch (IOException e) {
            throw new RuntimeException(String.format("Failed to set execute permissions to path %s", path), e);
        }
    }

    private GradleJdksConfigurator() {}
}
