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

import java.io.File;
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
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.jar.JarEntry;

public final class GradleJdksConfigsUtils {

    public static Path copyResourceToPath(Path targetDir, String resourceName) {
        try {
            URL installJdksResource =
                    GradleJdksConfigsUtils.class.getClassLoader().getResource(resourceName);
            Path installationScript = targetDir.resolve(resourceName);
            URLConnection urlConnection = installJdksResource.openConnection();
            if (urlConnection instanceof JarURLConnection) {
                writeResourceAsJarEntryToFile((JarURLConnection) urlConnection, resourceName, installationScript);
            } else {
                writeResourceAsStreamToFile(resourceName, installationScript.toFile());
            }
            return installationScript;
        } catch (IOException e) {
            throw new RuntimeException(String.format("Failed to write the %s script", resourceName), e);
        }
    }

    public static void writeResourceAsJarEntryToFile(
            JarURLConnection connection, String resourceName, Path installationScript) {
        try {
            JarEntry jarEntry = connection.getJarFile().getJarEntry(resourceName);
            try (InputStream is = connection.getJarFile().getInputStream(jarEntry);
                    OutputStream os = new FileOutputStream(installationScript.toFile())) {
                is.transferTo(os);
            }
        } catch (IOException e) {
            throw new RuntimeException(String.format("Failed to write the %s script", resourceName), e);
        }
    }

    public static void writeResourceAsStreamToFile(String resource, File outputFile) {
        try (InputStream inputStream =
                GenerateGradleJdksConfigsTask.class.getClassLoader().getResourceAsStream(resource)) {
            if (inputStream == null) {
                throw new RuntimeException(String.format("Resource not found: %s:", resource));
            }
            try (OutputStream outputStream = new FileOutputStream(outputFile)) {
                inputStream.transferTo(outputStream);
            }
        } catch (IOException e) {
            throw new RuntimeException(String.format("Failed to write %s to %s", resource, outputFile.toPath()), e);
        }
    }

    public static void writeConfigurationFile(Path pathFile, String content) {
        try {
            if (!Files.exists(pathFile)) {
                Files.createFile(pathFile);
            }
            // The content of the configuration files should always end with a newline character to ensure the file can
            // be
            // read by {see: resources/gradle-jdks-functions.sh#read_value }
            String contentWithLineEnding = content + "\n";
            Files.write(pathFile, contentWithLineEnding.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(String.format("Failed to write configuration file %s", pathFile), e);
        }
    }

    public static void setExecuteFilePermissions(Path path) {
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

    public static void createDirectories(Path directory) {
        if (Files.exists(directory)) {
            return;
        }
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new RuntimeException(String.format("Failed to create directory %s", directory), e);
        }
    }

    private GradleJdksConfigsUtils() {}
}
