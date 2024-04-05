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

import com.palantir.gradle.jdks.common.CommandRunner;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;

@SuppressWarnings("BanSystemOut")
public final class GradleWrapperMain {

    private GradleWrapperMain() {}

    public static void main(String[] args) throws Exception {
        configureJdkAutomanagement();

        // Delegate back to the original GradleWrapperMain implementation
        ClassLoader loader = new OriginalGradleWrapperLoader();
        Class<?> origGradleWrapperMainClass = loader.loadClass("org.gradle.wrapper.OrigGradleWrapper");
        Method method = origGradleWrapperMainClass.getMethod("main", String[].class);
        method.invoke(null, new Object[] {args});
    }

    private static void configureJdkAutomanagement() throws IOException {
        // Delegate to gradle-jdks-setup.sh to install the JDK
        // TODO(crogoz): maybe use java to do the setup
        Path projectHome = projectHome();
        CommandRunner.run(List.of("sh", "./gradle/gradle-jdks-setup.sh"), Optional.of(projectHome.toFile()));

        // Set the daemon Java Home
        Path jdkMajorVersionPath = projectHome.resolve("gradle/gradle-jdk-major-version");
        String majorVersion = Files.readString(jdkMajorVersionPath).trim();
        Path localPathFile = projectHome
                .resolve("gradle/jdks")
                .resolve(majorVersion)
                .resolve(getOs())
                .resolve(getArch())
                .resolve("local-path");
        String localJdkFileName = Files.readString(localPathFile).trim();
        Path jdkInstallationPath = getGradleJdksPath().resolve(localJdkFileName);
        System.out.println("Setting daemon Java Home to " + jdkInstallationPath.toAbsolutePath());
        System.setProperty(
                "org.gradle.java.home", jdkInstallationPath.toAbsolutePath().toString());

        // Disable auto-download and auto-detect of JDKs
        // System.setProperty("org.gradle.java.installations.auto-download", "false");
        // System.setProperty("org.gradle.java.installations.auto-detect", "false");

        // TODO(crogoz): read all jdks installations & set them up
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
        } catch (URISyntaxException var3) {
            throw new RuntimeException(var3);
        }

        if (!location.getScheme().equals("file")) {
            throw new RuntimeException(
                    String.format("Cannot determine classpath for wrapper Jar from codebase '%s'.", location));
        } else {
            try {
                return Paths.get(location);
            } catch (NoClassDefFoundError var2) {
                return Paths.get(location.getPath());
            }
        }
    }

    private static Path getGradleJdksPath() {
        return Path.of(Optional.ofNullable(System.getenv("GRADLE_USER_HOME"))
                        .orElseGet(() -> System.getProperty("user.home") + "/.gradle"))
                .resolve("gradle-jdks");
    }

    public static String getArch() {
        String osArch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);

        if (Set.of("x86_64", "x64", "amd64").contains(osArch)) {
            return "x86-64";
        }

        if (Set.of("arm", "arm64", "aarch64").contains(osArch)) {
            return "aarch64";
        }

        if (Set.of("x86", "i686").contains(osArch)) {
            return "x86";
        }

        throw new UnsupportedOperationException("Cannot get architecture for " + osArch);
    }

    public static String getOs() {
        String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);

        if (osName.startsWith("mac")) {
            return "macos";
        }

        if (osName.startsWith("windows")) {
            return "windows";
        }

        if (osName.startsWith("linux")) {
            return linuxLibcFromLdd(UnaryOperator.identity());
        }

        throw new UnsupportedOperationException("Cannot get platform for operating system " + osName);
    }

    static String linuxLibcFromLdd(UnaryOperator<List<String>> argTransformer) {
        try {
            Process process = new ProcessBuilder()
                    .command(argTransformer.apply(List.of("ldd", "--version")))
                    .start();

            // Extremely frustratingly, musl `ldd` exits with code 1 on --version, and prints to stderr, unlike the more
            // reasonable glibc, which exits with code 0 and prints to stdout. So we concat stdout and stderr together,
            // check the output for the correct strings, then fail if we can't find it.
            String lowercaseOutput = (CommandRunner.readAllInput(process.getInputStream()) + "\n"
                            + CommandRunner.readAllInput(process.getErrorStream()))
                    .toLowerCase(Locale.ROOT);

            int secondsToWait = 5;
            if (!process.waitFor(secondsToWait, TimeUnit.SECONDS)) {
                throw new RuntimeException(
                        "ldd failed to run within " + secondsToWait + " seconds. Output: " + lowercaseOutput);
            }

            if (lowercaseOutput.contains("glibc") || lowercaseOutput.contains("gnu libc")) {
                return "linux-glibc";
            }

            if (lowercaseOutput.contains("musl")) {
                return "linux-musl";
            }

            if (!Set.of(0, 1).contains(process.exitValue())) {
                throw new RuntimeException(String.format(
                        "Failed to run ldd - exited with exit code %d. Output: %s.",
                        process.exitValue(), lowercaseOutput));
            }

            throw new UnsupportedOperationException(
                    "Cannot work out libc used by this OS. ldd output was: " + lowercaseOutput);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static final class OriginalGradleWrapperLoader extends ClassLoader {
        @Override
        public Class<?> findClass(String name) {
            byte[] bytes = loadClassFromFile(name);
            return defineClass(name, bytes, 0, bytes.length);
        }

        private byte[] loadClassFromFile(String fileName) {
            try (InputStream inputStream =
                    getClass().getClassLoader().getResourceAsStream(fileName.replace('.', '/') + ".class")) {
                ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
                int nextValue;
                while ((nextValue = inputStream.read()) != -1) {
                    byteStream.write(nextValue);
                }
                byteStream.flush();
                return byteStream.toByteArray();
            } catch (IOException e) {
                throw new RuntimeException("Failed to load class from file", e);
            }
        }
    }
}
