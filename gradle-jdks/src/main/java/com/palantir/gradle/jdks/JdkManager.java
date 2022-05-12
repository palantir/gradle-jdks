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

import com.palantir.gradle.jdks.JdkPath.Extension;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.stream.Stream;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileTree;
import org.gradle.api.provider.Provider;
import org.gradle.process.ExecResult;

public final class JdkManager {
    private final Provider<Directory> storageLocation;
    private final JdkDistributions jdkDistributions;
    private final JdkDownloaders jdkDownloaders;

    JdkManager(Provider<Directory> storageLocation, JdkDistributions jdkDistributions, JdkDownloaders jdkDownloaders) {
        this.storageLocation = storageLocation;
        this.jdkDistributions = jdkDistributions;
        this.jdkDownloaders = jdkDownloaders;
    }

    public Path jdk(Project project, JdkSpec jdkSpec) {
        Path diskPath = storageLocation
                .get()
                .getAsFile()
                .toPath()
                .resolve(String.format(
                        "%s-%s-%s",
                        jdkSpec.distributionName(), jdkSpec.release().version(), jdkSpec.consistentShortHash()));

        if (Files.exists(diskPath)) {
            return diskPath;
        }

        JdkPath jdkPath = jdkDistributions.get(jdkSpec.distributionName()).path(jdkSpec.release());
        Path jdkArchive = jdkDownloaders
                .jdkDownloaderFor(project, jdkSpec.distributionName())
                .downloadJdkPath(jdkPath);

        Path temporaryJdkPath = Paths.get(
                diskPath + ".in-progress-" + UUID.randomUUID().toString().substring(0, 8));
        try {
            project.copy(copy -> {
                copy.from(unpackTree(project, jdkPath.extension(), jdkArchive));
                copy.into(temporaryJdkPath);
            });

            Path javaHome = findJavaHome(temporaryJdkPath);

            jdkSpec.caCerts().caCerts().forEach((name, caCertFile) -> {
                addCaCert(project, javaHome, name, caCertFile);
            });

            try {
                Files.move(javaHome, diskPath, StandardCopyOption.ATOMIC_MOVE);
            } catch (FileAlreadyExistsException e) {
                // This means another process has successfully installed this JDK, and we can just use their one.
                return diskPath;
            } catch (IOException e) {
                throw new RuntimeException("Could not move java home", e);
            }

            return diskPath;
        } finally {
            project.delete(delete -> {
                delete.delete(temporaryJdkPath.toFile());
            });
        }
    }

    private FileTree unpackTree(Project project, Extension extension, Path path) {
        switch (extension) {
            case ZIP:
                return project.zipTree(path.toFile());
            case TARGZ:
                return project.tarTree(path.toFile());
        }

        throw new UnsupportedOperationException("Unknown case " + extension);
    }

    private Path findJavaHome(Path temporaryJdkPath) {
        try (Stream<Path> files = Files.walk(temporaryJdkPath)) {
            return files.filter(file -> Files.isRegularFile(file)
                            // macos JDKs have a `bin/java` symlink to `Contents/Home/bin/java`
                            && !Files.isSymbolicLink(file)
                            && file.endsWith(Paths.get("bin/java")))
                    .findFirst()
                    // JAVA_HOME/bin/java
                    .orElseThrow(() -> new RuntimeException("Failed to find java home in " + temporaryJdkPath))
                    // JAVA_HOME/bin
                    .getParent()
                    // JAVA_HOME
                    .getParent();
        } catch (IOException e) {
            throw new RuntimeException("Failed to find java home in " + temporaryJdkPath, e);
        }
    }

    private void addCaCert(Project project, Path javaHome, String alias, String caCert) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        ExecResult keytoolResult = project.exec(exec -> {
            exec.setCommandLine(
                    "bin/keytool",
                    "-import",
                    "-trustcacerts",
                    "-alias",
                    alias,
                    "-cacerts",
                    "-storepass",
                    "changeit",
                    "-noprompt");

            exec.environment("JAVA_HOME", javaHome);
            exec.setWorkingDir(javaHome);
            exec.setStandardInput(new ByteArrayInputStream(caCert.getBytes(StandardCharsets.UTF_8)));
            exec.setStandardOutput(output);
            exec.setErrorOutput(output);
            exec.setIgnoreExitValue(true);
        });

        if (keytoolResult.getExitValue() != 0) {
            throw new RuntimeException(String.format(
                    "Failed to add ca cert '%s' to java installation at '%s'. Keytool output: %s\n\n",
                    alias, javaHome, output.toString(StandardCharsets.UTF_8)));
        }
    }
}
