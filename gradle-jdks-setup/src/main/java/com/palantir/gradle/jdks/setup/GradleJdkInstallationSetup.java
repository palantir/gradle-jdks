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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Class responsible for installing the current JDK into {@code destinationJdkInstallationDir} and importing the
 * certificates specified by their serialNumbers and alias name from {@code certsDir} into the JDK's truststore.
 * A certificate will be imported iff the serial number already exists in the truststore.
 * The class will be called by the Gradle setup script in
 * <a href="file:../resources/gradle-jdks-setup.sh">resources/gradle-jdks-setup.sh</a>.
 */
public final class GradleJdkInstallationSetup {

    public static void main(String[] args) throws IOException {
        StdLogger logger = new StdLogger();
        CaResources caResources = new CaResources(logger);
        if (args.length != 2) {
            throw new IllegalArgumentException("Expected two arguments: destinationJdkInstallationDir and certsDir");
        }
        Path destinationJdkInstallationDir = Path.of(args[0]);
        Path certsDir = Path.of(args[1]);
        copy(logger, destinationJdkInstallationDir);
        Map<String, String> certSerialNumbersToNames = extractCertsSerialNumbers(logger, certsDir);
        caResources.maybeImportCertsInJdk(destinationJdkInstallationDir, certSerialNumbersToNames);
    }

    private static void copy(ILogger logger, Path destinationJdkInstallationDirectory) throws IOException {
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

    @SuppressWarnings("StringSplitter")
    private static Map<String, String> extractCertsSerialNumbers(ILogger logger, Path certsDirectory)
            throws IOException {
        if (!Files.exists(certsDirectory)) {
            logger.log("No `certs` directory found, no certificates will be imported");
            return Map.of();
        }
        try (Stream<Path> stream = Files.list(certsDirectory)) {
            return stream.collect(Collectors.toMap(
                    GradleJdkInstallationSetup::readSerialNumber,
                    certFile -> certFile.getFileName().toString().split("\\.")[0]));
        }
    }

    private static String readSerialNumber(Path certFile) {
        try {
            return Files.readString(certFile).trim();
        } catch (IOException e) {
            throw new RuntimeException("Unable to read serial number from " + certFile, e);
        }
    }

    private GradleJdkInstallationSetup() {}
}
