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
import java.util.List;
import java.util.Optional;

@SuppressWarnings("BanSystemOut")
public final class GradleWrapperMain {

    private GradleWrapperMain() {}

    public static void main(String[] args) throws Exception {
        configureJdkAutomanagement();

        // Delegate back to the original GradleWrapperMain implementation
        Class<?> originalGradleWrapperMainClass = Class.forName("org.gradle.wrapper.OriginalGradleWrapperMain");
        Method method = originalGradleWrapperMainClass.getMethod("main", String[].class);
        method.invoke(null, new Object[] {args});
    }

    private static void configureJdkAutomanagement() throws IOException {
        // Delegate to gradle-jdks-setup.sh to install the JDK
        // TODO(crogoz): maybe use java to do the setup
        Path projectHome = projectHome();
        // TODO(crogoz): if invoked from ./gradle then we don't need to do this
        CommandRunner.run(List.of("./gradle/gradle-jdks-setup.sh"), Optional.of(projectHome.toFile()));

        // Set the daemon Java Home
        Path jdkMajorVersionPath = projectHome.resolve("gradle/gradle-jdk-major-version");
        String majorVersion = Files.readString(jdkMajorVersionPath).trim();
        Path localPathFile = projectHome
                .resolve("gradle/jdks")
                .resolve(majorVersion)
                .resolve(CurrentOs.get().uiName())
                .resolve(CurrentArch.get().uiName())
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
}
