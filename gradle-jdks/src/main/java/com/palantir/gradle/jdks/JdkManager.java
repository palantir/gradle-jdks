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

import com.google.common.io.Closer;
import com.google.common.util.concurrent.Striped;
import com.palantir.gradle.jdks.JdkPath.Extension;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.stream.Stream;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DuplicatesStrategy;
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
        project.getLogger()
                .debug(
                        "Requested JDK {} {} ({})",
                        jdkSpec.distributionName(),
                        jdkSpec.release().version(),
                        jdkSpec.consistentShortHash());

        if (Files.exists(diskPath)) {
            project.getLogger()
                    .debug(
                            "JDK {} {} ({}) has already been unpacked",
                            jdkSpec.distributionName(),
                            jdkSpec.release().version(),
                            jdkSpec.consistentShortHash());
            return diskPath;
        }

        project.getLogger()
                .info(
                        "Preparing to install JDK {} {} ({}) into {}",
                        jdkSpec.distributionName(),
                        jdkSpec.release().version(),
                        jdkSpec.consistentShortHash(),
                        diskPath);

        JdkPath jdkPath = jdkDistributions.get(jdkSpec.distributionName()).path(jdkSpec.release());
        Path jdkArchive = jdkDownloaders
                .jdkDownloaderFor(project, jdkSpec.distributionName())
                .downloadJdkPath(jdkPath);

        Path temporaryJdkPath = diskPath.getParent()
                .resolve(diskPath.getFileName() + ".in-progress-"
                        + UUID.randomUUID().toString().substring(0, 8));
        try (PathLock ignored = new PathLock(diskPath)) {
            // double-check, now that we hold the lock
            if (Files.exists(diskPath)) {
                project.getLogger()
                        .info(
                                "JDK {} {} ({}) was installed while this task waited for the lock",
                                jdkSpec.distributionName(),
                                jdkSpec.release().version(),
                                jdkSpec.consistentShortHash());
                return diskPath;
            }
            project.getLogger()
                    .info(
                            "Unpacking JDK {} {} ({}) into {}",
                            jdkSpec.distributionName(),
                            jdkSpec.release().version(),
                            jdkSpec.consistentShortHash(),
                            temporaryJdkPath);
            project.copy(copy -> {
                copy.from(unpackTree(project, jdkPath.extension(), jdkArchive));
                copy.into(temporaryJdkPath);
                copy.setDuplicatesStrategy(DuplicatesStrategy.WARN);
            });

            Path javaHome = findJavaHome(temporaryJdkPath);

            jdkSpec.caCerts().caCerts().forEach((name, caCertFile) -> {
                project.getLogger()
                        .info(
                                "Installing certificate {} into JDK {} {} ({})",
                                name,
                                jdkSpec.distributionName(),
                                jdkSpec.release().version(),
                                jdkSpec.consistentShortHash());
                addCaCert(project, javaHome, name, caCertFile);
            });

            project.getLogger()
                    .info(
                            "Moving JDK {} {} ({}) home {} to {}",
                            jdkSpec.distributionName(),
                            jdkSpec.release().version(),
                            jdkSpec.consistentShortHash(),
                            javaHome,
                            diskPath);
            moveJavaHome(javaHome, diskPath);
            return diskPath;
        } catch (IOException e) {
            throw new RuntimeException("Locking failed", e);
        } finally {
            project.delete(delete -> {
                delete.delete(temporaryJdkPath.toFile());
            });
        }
    }

    private static void moveJavaHome(Path temporaryJavaHome, Path permanentJavaHome) {
        try {
            // Attempt an atomic move first to avoid broken partial states.
            // Failing that, we use the replace_existing option such that
            // the results of a successful move operation are consistent.
            // This provides a helpful property in a race where the slower
            // process doesn't risk attempting to use the jdk before it has
            // been fully unpacked.
            try {
                Files.move(
                        temporaryJavaHome,
                        permanentJavaHome,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporaryJavaHome, permanentJavaHome, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (FileAlreadyExistsException e) {
            // This means another process has successfully installed this JDK, and we can just use theirs.
            // Should be unreachable using REPLACE_EXISTING, however kept around to prevent issues with potential
            // future refactors.
        } catch (IOException e) {
            throw new RuntimeException("Could not move java home", e);
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
                            && file.endsWith(Paths.get("bin", SystemTools.javac())))
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
                    Paths.get("bin", SystemTools.keytool()).toString(),
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

    /**
     * Abstraction around locking access to a file or directory, by creating another file with a
     * matching name, and '.lock' extension. Note that the underlying file locking mechanism is
     * left to the filesystem, so we must be careful to work within its bounds. For example,
     * POSIX file locks apply to a process, so within the process we must ensure synchronization
     * separately.
     */
    private static final class PathLock implements Closeable {
        private static final Striped<Lock> JVM_LOCKS = Striped.lock(16);
        private final Closer closer;

        PathLock(Path path) throws IOException {
            this.closer = Closer.create();
            try {
                Lock jvmLock = JVM_LOCKS.get(path);
                jvmLock.lock();
                closer.register(jvmLock::unlock);
                Files.createDirectories(path.getParent());
                FileChannel channel = closer.register(FileChannel.open(
                        path.getParent().resolve(path.getFileName() + ".lock"),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE));
                FileLock fileLock = channel.lock();
                closer.register(fileLock::close);
            } catch (Throwable t) {
                closer.close();
                throw t;
            }
        }

        @Override
        public void close() throws IOException {
            closer.close();
        }
    }
}
