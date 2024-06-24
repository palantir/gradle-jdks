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
import java.nio.charset.StandardCharsets;
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

    enum Command {
        JDK_SETUP("jdkSetup"),
        DAEMON_SETUP("daemonSetup");

        private final String label;

        Command(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }

        public static Command fromLabel(String label) {
            for (Command e : values()) {
                if (e.label.equals(label)) {
                    return e;
                }
            }
            throw new RuntimeException(String.format("Cannot convert %s to a Command", label));
        }
    }

    public static void main(String[] args) throws IOException {
        StdLogger logger = new StdLogger();
        CaResources caResources = new CaResources(logger);
        if (args.length < 1) {
            throw new IllegalArgumentException("Expected at least an argument: jdkSetup or daemonSetup");
        }
        Command command = Command.fromLabel(args[0]);
        switch (command) {
            case JDK_SETUP:
                setupJdk(logger, caResources, args);
                break;
            case DAEMON_SETUP:
                setupDaemon(args);
                break;
        }
    }

    private static void setupDaemon(String[] args) {
        // [Intelij specific] Set the gradle java.home in .gradle/config.properties. This is the value for the Intelij
        // env variable GRADLE_LOCAL_JAVA_HOME
        if (args.length != 3) {
            throw new IllegalArgumentException("Expected 2 arguments: daemonSetup <projectDir> <gradleDaemonJavaHome>");
        }
        Path projectDir = Path.of(args[1]);
        Path gradleDaemonJavaHome = Path.of(args[2]);
        try {
            Files.createDirectories(projectDir.resolve(".gradle"));
            Files.write(
                    projectDir.resolve(".gradle/config.properties"),
                    String.format("java.home=%s", gradleDaemonJavaHome).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("Unable to set the java.home value in .gradle/config.properties.", e);
        }
    }

    private static void setupJdk(StdLogger logger, CaResources caResources, String[] args) {
        if (args.length != 3) {
            throw new IllegalArgumentException(
                    "Expected 3 arguments: jdkSetup <destinationJdkInstallationDir> <certsDir>");
        }
        Path destinationJdkInstallationDir = Path.of(args[1]);
        Path certsDir = Path.of(args[2]);
        copy(logger, destinationJdkInstallationDir);
        Map<String, String> certSerialNumbersToNames = extractCertsSerialNumbers(logger, certsDir);
        caResources.maybeImportCertsInJdk(destinationJdkInstallationDir, certSerialNumbersToNames);
    }

    private static void copy(ILogger logger, Path destinationJdkInstallationDirectory) {
        Path currentJavaHome = Path.of(System.getProperty("java.home"));
        Path jdksInstallationDirectory = destinationJdkInstallationDirectory.getParent();
        FileUtils.createDirectories(jdksInstallationDirectory);
        Path lockFile = jdksInstallationDirectory.resolve(destinationJdkInstallationDirectory.getFileName() + ".lock");
        try (FileChannel channel = FileChannel.open(
                lockFile, StandardOpenOption.READ, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
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
    private static Map<String, String> extractCertsSerialNumbers(ILogger logger, Path certsDirectory) {
        if (!Files.exists(certsDirectory)) {
            logger.log("No `certs` directory found, no certificates will be imported");
            return Map.of();
        }
        try (Stream<Path> stream = Files.list(certsDirectory)) {
            return stream.collect(Collectors.toMap(
                    GradleJdkInstallationSetup::readSerialNumber,
                    certFile -> certFile.getFileName().toString().split("\\.")[0]));
        } catch (IOException e) {
            throw new RuntimeException("Unable to list the certificates in the certs directory", e);
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
