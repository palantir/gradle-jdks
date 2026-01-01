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

package com.palantir.gradle.jdks;

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.gradle.jdks.setup.common.CommandRunner;
import com.palantir.gradle.testing.files.gradle.GradleFile;
import com.palantir.gradle.testing.project.RootProject;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Utility class providing helper methods for Gradle JDK integration tests.
 * This class replaces the abstract GradleJdkIntegrationSpec from the Nebula framework.
 */
public final class GradleJdkIntegrationTest {

    private static final int BYTECODE_IDENTIFIER = (int) 0xCAFEBABE;

    public static GradleFile setupJdksHardcodedVersions(RootProject rootProject) {
        GradleJdkTestUtils.setupJdksHardcodedVersions(
                rootProject.settingsGradle().path().toFile(),
                rootProject.buildGradle().path().toFile());

        return rootProject.buildGradle();
    }

    public static GradleFile setupJdksHardcodedVersions(RootProject rootProject, String daemonTarget) {
        GradleJdkTestUtils.setupJdksHardcodedVersions(
                rootProject.settingsGradle().path().toFile(),
                rootProject.buildGradle().path().toFile(),
                daemonTarget);

        return rootProject.buildGradle();
    }

    public static GradleFile applyApplicationPlugin(RootProject rootProject) {
        GradleJdkTestUtils.applyApplicationPlugin(
                rootProject.buildGradle().path().toFile());

        return rootProject.buildGradle();
    }

    public static GradleFile applyBaselineJavaVersions(RootProject rootProject) {
        GradleJdkTestUtils.applyBaselineJavaVersions(
                rootProject.buildGradle().path().toFile());

        return rootProject.buildGradle();
    }

    public static void applyJdksPlugins(RootProject rootProject) {
        GradleJdkTestUtils.applyJdksPlugins(
                rootProject.settingsGradle().path().toFile(),
                rootProject.buildGradle().path().toFile());
    }

    public static String runGradlewTasksSuccessfully(RootProject rootProject, Path workingDir, String... tasks) {
        String output = runGradlewTasks(rootProject, workingDir, tasks);
        assertThat(output).contains("BUILD SUCCESSFUL");
        return output;
    }

    public static String runGradlewTasksWithFailure(RootProject rootProject, Path workingDir, String... tasks) {
        String output = runGradlewTasks(rootProject, workingDir, tasks);
        assertThat(output).contains("BUILD FAILED");
        return output;
    }

    private static String runGradlewTasks(RootProject rootProject, Path workingDir, String... tasks) {
        ProcessBuilder processBuilder = getProcessBuilder(rootProject, workingDir, tasks);
        try {
            Process process = processBuilder.start();
            String output = CommandRunner.readAllInput(process.getInputStream());
            return output;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to start process", e);
        }
    }

    private static ProcessBuilder getProcessBuilder(RootProject rootProject, Path workingDir, String... tasks) {
        List<String> arguments = new ArrayList<>();
        arguments.add("./gradlew");
        arguments.addAll(Arrays.asList(tasks));

        ProcessBuilder processBuilder = new ProcessBuilder()
                .command(arguments)
                .directory(rootProject.path().toFile())
                .redirectErrorStream(true);

        processBuilder
                .environment()
                .put("GRADLE_USER_HOME", workingDir.toAbsolutePath().toString());

        return processBuilder;
    }

    // See http://illegalargumentexception.blogspot.com/2009/07/java-finding-class-versions.html
    public static Pair<Integer, Integer> readBytecodeVersion(Path file) {
        try (InputStream stream = new FileInputStream(file.toFile());
                DataInputStream dis = new DataInputStream(stream)) {
            int magic = dis.readInt();
            if (magic != BYTECODE_IDENTIFIER) {
                throw new IllegalArgumentException("File " + file + " does not appear to be java bytecode");
            }
            int minorBytecodeVersion = dis.readUnsignedShort();
            int majorBytecodeVersion = dis.readUnsignedShort();
            return Pair.of(minorBytecodeVersion, majorBytecodeVersion);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read bytecode version from " + file, e);
        }
    }

    private GradleJdkIntegrationTest() {}
}
