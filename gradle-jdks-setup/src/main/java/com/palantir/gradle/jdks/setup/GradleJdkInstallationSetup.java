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

package com.palantir.gradle.jdks.setup;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;

public final class GradleJdkInstallationSetup {

    public static void main(String[] args) throws IOException {
        StdLogger logger = new StdLogger();
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "Expected two arguments: destinationJdkInstallationDirectory and certsDirectory");
        }
        Path destinationJdkInstallationDirectory = Path.of(args[0]);
        Path certsDirectory = Path.of(args[1]);
        atomicCopyJdkInstallationDirectory(logger, destinationJdkInstallationDirectory);
        Map<String, String> certNamesToSerialNumbers = extractCertsSerialNumbers(certsDirectory);
        CaResources.maybeImportCertsInJdk(logger, destinationJdkInstallationDirectory, certNamesToSerialNumbers);
    }

    private static void atomicCopyJdkInstallationDirectory(ILogger logger, Path destinationJdkInstallationDirectory)
            throws IOException {
        Path currentJavaHome = Path.of(System.getProperty("java.home"));
        Path jdksInstallationDirectory = destinationJdkInstallationDirectory.getParent();
        if (!jdksInstallationDirectory.toFile().exists()) {
            Files.createDirectories(jdksInstallationDirectory);
        }
        Path lockFile = jdksInstallationDirectory.resolve(destinationJdkInstallationDirectory.getFileName() + ".lock");
        try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            channel.lock();
            // double-check, now that we hold the lock
            if (Files.exists(destinationJdkInstallationDirectory)) {
                logger.log(String.format("Distribution URL %s already exists", destinationJdkInstallationDirectory));
                return;
            }
            logger.log(
                    String.format("Copying JDK from %s into %s", currentJavaHome, destinationJdkInstallationDirectory));
            FileUtils.copyDirectory(currentJavaHome, destinationJdkInstallationDirectory);
        } catch (IOException e) {
            throw new RuntimeException("Unable to acquire locks, won't move the JDK installation directory", e);
        }
    }

    private static Map<String, String> extractCertsSerialNumbers(Path certsDirectory) throws IOException {
        if (!Files.exists(certsDirectory)) {
            return Map.of();
        }
        Map<String, String> certSerialNumbersToAliases = new HashMap<>();
        Files.walkFileTree(certsDirectory, new FileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path _dir, BasicFileAttributes _attrs) throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes _attrs) throws IOException {
                String certAlias = file.getFileName().toString().split("\\.")[0];
                String certSerialNumber = Files.readString(file).trim();
                certSerialNumbersToAliases.put(certSerialNumber, certAlias);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path _file, IOException exc) throws IOException {
                throw new RuntimeException("Unable to read file", exc);
            }

            @Override
            public FileVisitResult postVisitDirectory(Path _dir, IOException _exc) throws IOException {
                return FileVisitResult.CONTINUE;
            }
        });
        return certSerialNumbersToAliases;
    }

    public GradleJdkInstallationSetup() {}
}
