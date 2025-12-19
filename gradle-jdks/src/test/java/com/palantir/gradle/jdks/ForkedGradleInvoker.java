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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A Gradle invoker that runs Gradle via the wrapper script using ProcessBuilder.
 * This provides true forked execution, unlike TestKit which runs in-process or in a shared daemon.
 *
 * <p>Use this when you need behavior that matches running ./gradlew directly, such as:
 * <ul>
 *   <li>JDK detection via org.gradle.java.installations.paths instead of "Current JVM"</li>
 *   <li>Testing wrapper script modifications</li>
 *   <li>Testing daemon JDK setup behavior</li>
 * </ul>
 */
public final class ForkedGradleInvoker {

    private static final String GRADLE_USER_HOME_DIR = ".gradle-user-home";

    private final Path projectDir;
    private final Path gradleUserHome;

    /**
     * Creates a ForkedGradleInvoker using a subdirectory of the project as GRADLE_USER_HOME.
     */
    public ForkedGradleInvoker(Path projectDir) {
        this(projectDir, projectDir.resolve(GRADLE_USER_HOME_DIR));
    }

    public ForkedGradleInvoker(Path projectDir, Path gradleUserHome) {
        this.projectDir = projectDir;
        this.gradleUserHome = gradleUserHome;
    }

    /**
     * Returns the GRADLE_USER_HOME directory used by this invoker.
     * Useful for constructing expected paths in assertions, e.g.:
     * {@code gradleUserHome().resolve("gradle-jdks").resolve(localPathContent)}
     */
    public Path gradleUserHome() {
        return gradleUserHome;
    }

    /**
     * Runs ./gradlew with the given tasks and returns the output.
     * Asserts that the build succeeds.
     */
    public String runTasksSuccessfully(String... tasks) {
        String output = runTasks(tasks);
        assertThat(output)
                .as("Expected build to succeed but got output:\n%s", output)
                .contains("BUILD SUCCESSFUL");
        return output;
    }

    /**
     * Runs ./gradlew with the given tasks and returns the output.
     * Asserts that the build fails.
     */
    public String runTasksWithFailure(String... tasks) {
        String output = runTasks(tasks);
        assertThat(output)
                .as("Expected build to fail but got output:\n%s", output)
                .contains("BUILD FAILED");
        return output;
    }

    /**
     * Runs ./gradlew with the given tasks and returns the output.
     * Does not assert on success or failure.
     */
    public String runTasks(String... tasks) {
        ProcessBuilder processBuilder = createProcessBuilder(tasks);
        return CommandRunner.runWithOutputCollection(processBuilder);
    }

    private ProcessBuilder createProcessBuilder(String... tasks) {
        List<String> arguments = new ArrayList<>();
        arguments.add("./gradlew");
        arguments.addAll(Arrays.asList(tasks));
        // Use --no-daemon to ensure a fresh JVM that detects JDKs via installations.paths
        arguments.add("--no-daemon");

        ProcessBuilder processBuilder = new ProcessBuilder()
                .command(arguments)
                .directory(projectDir.toFile())
                .redirectErrorStream(true);

        processBuilder.environment().put("GRADLE_USER_HOME", gradleUserHome.toAbsolutePath().toString());

        return processBuilder;
    }
}
