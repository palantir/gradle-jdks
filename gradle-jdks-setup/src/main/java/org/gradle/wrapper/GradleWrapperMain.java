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

package org.gradle.wrapper;

import com.palantir.gradle.jdks.CommandRunner;
import com.palantir.gradle.jdks.CurrentArch;
import com.palantir.gradle.jdks.CurrentOs;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings("BanSystemOut")
public final class GradleWrapperMain {

    private static final String RUNNING_FROM_GRADLEW_PROP = "running.from.gradlew";

    private GradleWrapperMain() {}

    public static void main(String[] args) throws Exception {
        List<String> toolchains = getAndConfigureJdkAutomanagement();

        // Delegate back to the original GradleWrapperMain implementation
        Class<?> originalGradleWrapperMainClass = Class.forName("org.gradle.wrapper.OriginalGradleWrapperMain");
        Method method = originalGradleWrapperMainClass.getMethod("main", String[].class);
        String[] extraArgs = {
            // Disable auto-download and auto-detect of JDKs
            "-Porg.gradle.java.installations.auto-download=false",
            "-Porg.gradle.java.installations.auto-detect=false",
            // Set the custom toolchains locations
            "-Porg.gradle.java.installations.paths=" + String.join(",", toolchains)
        };
        String[] combinedArgs =
                Stream.concat(Arrays.stream(args), Arrays.stream(extraArgs)).toArray(String[]::new);
        method.invoke(null, new Object[] {combinedArgs});
    }

    private static List<String> getAndConfigureJdkAutomanagement() {
        // Delegate to gradle-jdks-setup.sh to install the JDK if it hasn't been installed yet
        Path projectHome = projectHome();
        if (!isRunningFromGradlew()) {
            CommandRunner.run(List.of("./gradle/gradle-jdks-setup.sh"), Optional.of(projectHome.toFile()));
        }

        // Set the daemon Java Home
        String osName = CurrentOs.get().uiName();
        String archName = CurrentArch.get().uiName();
        Path daemonJdkMajorVersionPath = projectHome.resolve("gradle/gradle-daemon-jdk-version");
        String majorVersion = readFile(daemonJdkMajorVersionPath);
        Path localPathFile = projectHome
                .resolve("gradle/jdks")
                .resolve(majorVersion)
                .resolve(osName)
                .resolve(archName)
                .resolve("local-path");
        String localJdkFileName = readFile(localPathFile);
        Path gradleJdksInstallationDir = getGradleJdksPath();
        Path jdkInstallationPath = gradleJdksInstallationDir.resolve(localJdkFileName);
        System.out.println("Setting daemon Java Home to " + jdkInstallationPath.toAbsolutePath());
        System.setProperty(
                "org.gradle.java.home", jdkInstallationPath.toAbsolutePath().toString());

        List<String> toolchainsInstallationPaths = getAllInstallationsPaths(
                projectHome.resolve("gradle/jdks"), gradleJdksInstallationDir, osName, archName);
        System.out.println("Setting custom toolchains locations to " + toolchainsInstallationPaths);
        return toolchainsInstallationPaths;
    }

    private static List<String> getAllInstallationsPaths(
            Path gradleJdksConfigurationPath, Path gradleJdksInstallationDir, String osName, String archName) {
        try (Stream<Path> gradleJdkConfigurationPath =
                Files.walk(gradleJdksConfigurationPath, 1).filter(Files::isDirectory)) {
            return gradleJdkConfigurationPath
                    .flatMap(jdkPath -> getInstallationPath(jdkPath, gradleJdksInstallationDir, osName, archName))
                    .collect(Collectors.toList());

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Stream<String> getInstallationPath(
            Path gradleJdkConfigurationPath, Path gradleJdksInstallationDir, String osName, String archName) {
        Path localPathFile =
                gradleJdkConfigurationPath.resolve(osName).resolve(archName).resolve("local-path");
        if (!localPathFile.toFile().exists()) {
            System.out.println(String.format(
                    "Couldn't find a valid JDK version %s installation for os=%s and arch=%s in %s. Skipping ...",
                    gradleJdkConfigurationPath.getFileName(),
                    osName,
                    archName,
                    gradleJdkConfigurationPath.toAbsolutePath()));
            return Stream.empty();
        }
        String localJdkFileName = readFile(localPathFile);
        return Stream.of(gradleJdksInstallationDir
                .resolve(localJdkFileName)
                .toAbsolutePath()
                .toString());
    }

    private static String readFile(Path path) {
        try {
            return Files.readString(path).trim();
        } catch (IOException e) {
            throw new RuntimeException("Unable to read file", e);
        }
    }

    private static boolean isRunningFromGradlew() {
        return Boolean.parseBoolean(System.getProperty(RUNNING_FROM_GRADLEW_PROP, "false"));
    }

    private static Path projectHome() {
        return wrapperJar().toAbsolutePath().getParent().getParent().getParent();
    }

    private static Path wrapperJar() {
        URI location;
        try {
            location = GradleWrapperMain.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        if (!location.getScheme().equals("file")) {
            throw new RuntimeException(
                    String.format("Cannot determine classpath for wrapper Jar from codebase '%s'.", location));
        }
        try {
            return Paths.get(location);
        } catch (NoClassDefFoundError e) {
            return Paths.get(location.getPath());
        }
    }

    private static Path getGradleJdksPath() {
        return Path.of(Optional.ofNullable(System.getenv("GRADLE_USER_HOME"))
                        .orElseGet(() -> System.getProperty("user.home") + "/.gradle"))
                .resolve("gradle-jdks");
    }
}
