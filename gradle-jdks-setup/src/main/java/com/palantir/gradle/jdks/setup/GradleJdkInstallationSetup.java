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

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Properties;

/**
 * Class responsible for  installing the current JDK into {@code destinationJdkInstallationDir} and,
 * in the case of Hotspot, importing the system certificates into the JDK's truststore.
 * The class will be called by the Gradle setup script in
 * <a href="file:../resources/gradle-jdks-setup.sh">resources/gradle-jdks-setup.sh</a>.
 */
public final class GradleJdkInstallationSetup {

    public interface Labeled {
        String getLabel();
    }

    public enum Command implements Labeled {
        JDK_SETUP("jdkSetup"),
        DAEMON_SETUP("daemonSetup");

        private final String label;

        Command(String label) {
            this.label = label;
        }

        @Override
        public String getLabel() {
            return label;
        }
    }

    public enum JvmCompiler implements Labeled {
        HOTSPOT("hotspot"),
        GRAAL("graal");

        private final String label;

        JvmCompiler(String label) {
            this.label = label;
        }

        @Override
        public String getLabel() {
            return label;
        }
    }

    public static void main(String[] args) {
        StdErrLogger logger = new StdErrLogger();
        CaResources caResources = new CaResources(logger);
        if (args.length < 1) {
            throw new IllegalArgumentException("Expected at least an argument: jdkSetup or daemonSetup");
        }
        Command command = fromLabel(Command.class, args[0]);
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
            Path gradleConfigFile = projectDir.resolve(".gradle/config.properties");
            gradleConfigFile.toFile().createNewFile();
            Properties gradleProperties = new Properties();
            gradleProperties.load(new FileInputStream(gradleConfigFile.toFile()));
            gradleProperties.setProperty("java.home", gradleDaemonJavaHome.toString());
            gradleProperties.store(Files.newBufferedWriter(gradleConfigFile, StandardCharsets.UTF_8), null);
        } catch (IOException e) {
            throw new RuntimeException("Unable to set the java.home value in .gradle/config.properties.", e);
        }
    }

    private static void setupJdk(StdErrLogger logger, CaResources caResources, String[] args) {
        if (args.length != 3) {
            throw new IllegalArgumentException(
                    "Expected 3 arguments: jdkSetup <destinationJdkInstallationDir> <jdksType>");
        }
        Path destinationJdkInstallationDir = Path.of(args[1]);
        JvmCompiler jvmCompiler = fromLabel(JvmCompiler.class, args[2]);
        boolean wasCopied = copy(logger, destinationJdkInstallationDir);
        // Only adding certificates to the Hotspot Jdks.
        // If the JDK was not copied by the current process - which means that we waited for the lock while another
        // process set up the JDK - then we shouldn't try to add the certificate because the certificate was already
        // added.
        if (wasCopied && jvmCompiler.equals(JvmCompiler.HOTSPOT)) {
            caResources.importAllSystemCerts(destinationJdkInstallationDir);
        }
    }

    private static boolean copy(ILogger logger, Path destinationJdkInstallationDirectory) {
        Path currentJavaHome = Path.of(System.getProperty("java.home"));
        Path jdksInstallationDirectory = destinationJdkInstallationDirectory.getParent();
        FileUtils.createDirectories(jdksInstallationDirectory);
        Path lockFile = jdksInstallationDirectory.resolve(destinationJdkInstallationDirectory.getFileName() + ".lock");
        try (FileChannel channel = FileChannel.open(
                lockFile, StandardOpenOption.READ, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            channel.lock();
            // double-check, now that we hold the lock
            if (Files.exists(destinationJdkInstallationDirectory)
                    && Files.exists(destinationJdkInstallationDirectory.resolve("bin/java"))) {
                logger.log(String.format("Distribution URL %s already exists", destinationJdkInstallationDirectory));
                return false;
            }
            logger.log(
                    String.format("Copying JDK from %s into %s", currentJavaHome, destinationJdkInstallationDirectory));

            // same filesystem for the temporary directory as the destinationDirectory to ensure an atomic move
            Path tempCopyDir = jdksInstallationDirectory.resolve(
                    String.format("tmp-%s", destinationJdkInstallationDirectory.getFileName()));
            // Ensuring that the JDK installation directory won't be partially copied.
            // 1. Copying the currentJavaHome to a temporary directory inside the jdksInstallationDirectory
            // 2. Atomic move of the temporary directory to the destination directory
            try {
                Files.createDirectories(tempCopyDir);
                FileUtils.copyDirectory(currentJavaHome, tempCopyDir);
                Files.move(
                        tempCopyDir,
                        destinationJdkInstallationDirectory,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(tempCopyDir, destinationJdkInstallationDirectory, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                FileUtils.delete(tempCopyDir);
            }
            return true;
        } catch (IOException e) {
            throw new RuntimeException(
                    String.format(
                            "Failed to copy the JDK installation to path= %s", destinationJdkInstallationDirectory),
                    e);
        }
    }

    private static <T extends Enum<T> & Labeled> T fromLabel(Class<T> enumType, String label) {
        for (T e : enumType.getEnumConstants()) {
            if (e.getLabel().equals(label)) {
                return e;
            }
        }
        throw new IllegalArgumentException(String.format("Cannot convert %s to %s", label, enumType.getSimpleName()));
    }

    private GradleJdkInstallationSetup() {}
}
