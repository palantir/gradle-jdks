/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.gradle.jdks;

import com.palantir.gradle.jdks.JdkPath.Extension;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.stream.Stream;
import org.gradle.api.Project;
import org.gradle.api.file.FileTree;
import org.gradle.process.ExecResult;

public final class JdkManager {
    private final Project project;
    private final Path storageLocation;
    private final JdkDistributions jdkDistributions;
    private final JdkDownloaders jdkDownloaders;

    JdkManager(
            Project project, Path storageLocation, JdkDistributions jdkDistributions, JdkDownloaders jdkDownloaders) {
        this.project = project;
        this.storageLocation = storageLocation;
        this.jdkDistributions = jdkDistributions;
        this.jdkDownloaders = jdkDownloaders;
    }

    public Path jdk(JdkSpec jdkSpec) {
        Path diskPath = storageLocation.resolve(String.format(
                "%s-%s-%s", jdkSpec.distributionName(), jdkSpec.release().version(), jdkSpec.consistentShortHash()));

        if (Files.exists(diskPath)) {
            return diskPath;
        }

        JdkPath jdkPath = jdkDistributions.get(jdkSpec.distributionName()).path(jdkSpec.release());
        Path jdkArchive =
                jdkDownloaders.jdkDownloaderFor(jdkSpec.distributionName()).downloadJdkPath(jdkPath);

        Path temporaryJdkPath = Paths.get(
                diskPath + ".in-progress-" + UUID.randomUUID().toString().substring(0, 8));
        try {
            project.copy(copy -> {
                copy.from(unpackTree(jdkPath.extension(), jdkArchive));
                copy.into(temporaryJdkPath);
            });

            Path javaHome = findJavaHome(temporaryJdkPath);

            jdkSpec.caCerts().caCerts().forEach(caCertFile -> addCaCert(javaHome, caCertFile));

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

    private void addCaCert(Path javaHome, Path caCertFile) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        ExecResult keytoolResult = project.exec(exec -> {
            exec.setCommandLine(
                    "bin/keytool",
                    "-import",
                    "-trustcacerts",
                    "-file",
                    caCertFile.toAbsolutePath(),
                    "-cacerts",
                    "-storepass",
                    "changeit",
                    "-noprompt");

            exec.environment("JAVA_HOME", javaHome);
            exec.setWorkingDir(javaHome);
            exec.setStandardOutput(output);
            exec.setErrorOutput(output);
            exec.setIgnoreExitValue(true);
        });

        if (keytoolResult.getExitValue() != 0) {
            throw new RuntimeException(String.format(
                    "Failed to add ca cert '%s' to java installation at '%s'. Keytool output:\n\n",
                    caCertFile.toAbsolutePath(), javaHome));
        }
    }

    private FileTree unpackTree(Extension extension, Path path) {
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
}
