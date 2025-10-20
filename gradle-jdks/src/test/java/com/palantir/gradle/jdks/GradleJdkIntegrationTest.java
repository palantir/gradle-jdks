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

import com.palantir.gradle.jdks.setup.common.CommandRunner;
import com.palantir.gradle.plugintesting.GradleTestVersions;
import com.palantir.gradle.testing.files.ProjectFile;
import com.palantir.gradle.testing.files.gradle.GradleFile;
import com.palantir.gradle.testing.files.gradle.SettingsGradleFile;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

@GradlePluginTests
abstract class GradleJdkIntegrationTest {

    // Make constants public for use in tests
    public static final String DAEMON_MAJOR_VERSION_17 = "17";
    public static final String SIMPLIFIED_JDK_11_VERSION = "11.0.14";
    public static final String SIMPLIFIED_JDK_17_VERSION = "17.0.3";
    public static final String SIMPLIFIED_JDK_21_VERSION = "21.0.2";
    
    static final List<String> GRADLE_TEST_VERSIONS = GradleTestVersions.getGradleVersionsForTests();

    protected RootProject rootProject;

    @BeforeEach
    void setup(RootProject rootProject) {
        this.rootProject = rootProject;
    }
    
    abstract Path workingDir();

    void setupJdksHardcodedVersions() {
        GradleFile buildGradle = rootProject.buildGradle();
        SettingsGradleFile settingsGradle = rootProject.settingsGradle();
        GradleJdkTestUtils.setupJdksHardcodedVersions(settingsGradle.path().toFile(), buildGradle.path().toFile());
    }

    void setupJdksHardcodedVersions(String daemonTarget) {
        GradleFile buildGradle = rootProject.buildGradle();
        SettingsGradleFile settingsGradle = rootProject.settingsGradle();
        GradleJdkTestUtils.setupJdksHardcodedVersions(settingsGradle.path().toFile(), buildGradle.path().toFile(), daemonTarget);
    }

    void applyApplicationPlugin() {
        GradleFile buildGradle = rootProject.buildGradle();
        GradleJdkTestUtils.applyApplicationPlugin(buildGradle.path().toFile());
    }

    void applyBaselineJavaVersions() {
        GradleFile buildGradle = rootProject.buildGradle();
        GradleJdkTestUtils.applyBaselineJavaVersions(buildGradle.path().toFile());
    }

    void applyJdksPlugins() {
        GradleFile buildGradle = rootProject.buildGradle();
        SettingsGradleFile settingsGradle = rootProject.settingsGradle();
        GradleJdkTestUtils.applyJdksPlugins(settingsGradle.path().toFile(), buildGradle.path().toFile());
    }
    
    ProjectFile<?> createFile(String path) {
        try {
            Path filePath = rootProject.path().resolve(path);
            Files.createDirectories(filePath.getParent());
            return rootProject.propertiesFile(path);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create file: " + path, e);
        }
    }

    @SuppressWarnings("BadAssert")
    String runGradlewTasksSuccessfully(String... tasks) throws IOException {
        String output = runGradlewTasks(tasks);
        assert output.contains("BUILD SUCCESSFUL");
        return output;
    }

    @SuppressWarnings("BadAssert")
    String runGradlewTasksWithFailure(String... tasks) throws IOException {
        String output = runGradlewTasks(tasks);
        assert output.contains("BUILD FAILED");
        return output;
    }

    private String runGradlewTasks(String... tasks) throws IOException {
        ProcessBuilder processBuilder = getProcessBuilder(tasks);
        Process process = processBuilder.start();
        return CommandRunner.readAllInput(process.getInputStream());
    }

    private ProcessBuilder getProcessBuilder(String... tasks) {
        List<String> arguments = Arrays.asList("./gradlew");
        Arrays.asList(tasks).forEach(arguments::add);
        ProcessBuilder processBuilder = new ProcessBuilder()
                .command(arguments)
                .directory(rootProject.path().toFile())
                .redirectErrorStream(true);
        processBuilder.environment().put("GRADLE_USER_HOME", workingDir().toAbsolutePath().toString());
        return processBuilder;
    }

    private static final int BYTECODE_IDENTIFIER = 0xCAFEBABE;

    // See http://illegalargumentexception.blogspot.com/2009/07/java-finding-class-versions.html
    static Pair<Integer, Integer> readBytecodeVersion(File file) throws IOException {
        try (InputStream stream = new FileInputStream(file);
             DataInputStream dis = new DataInputStream(stream)) {
            int magic = dis.readInt();
            if (magic != BYTECODE_IDENTIFIER) {
                throw new IllegalArgumentException("File " + file + " does not appear to be java bytecode");
            }
            int minorBytecodeVersion = dis.readUnsignedShort();
            int majorBytecodeVersion = dis.readUnsignedShort();
            return Pair.of(minorBytecodeVersion, majorBytecodeVersion);
        }
    }
}