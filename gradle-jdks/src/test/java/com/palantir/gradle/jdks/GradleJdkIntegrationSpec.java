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
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

abstract class GradleJdkIntegrationSpec {

    private static final int BYTECODE_IDENTIFIER = 0xCAFEBABE;

    abstract Path workingDir();

    String runGradlewTasksSuccessfully(File projectDir, String... tasks) {
        String output = runGradlewTasks(projectDir, tasks);
        if (!output.contains("BUILD SUCCESSFUL")) {
            throw new AssertionError("Expected build to succeed but it failed:\n" + output);
        }
        return output;
    }

    String runGradlewTasksWithFailure(File projectDir, String... tasks) {
        String output = runGradlewTasks(projectDir, tasks);
        if (!output.contains("BUILD FAILED")) {
            throw new AssertionError("Expected build to fail but it succeeded:\n" + output);
        }
        return output;
    }

    private String runGradlewTasks(File projectDir, String... tasks) {
        try {
            ProcessBuilder processBuilder = getProcessBuilder(projectDir, tasks);
            Process process = processBuilder.start();
            return CommandRunner.readAllInput(process.getInputStream());
        } catch (IOException e) {
            throw new RuntimeException("Failed to run Gradle tasks", e);
        }
    }

    private ProcessBuilder getProcessBuilder(File projectDir, String... tasks) {
        List<String> arguments = new java.util.ArrayList<>();
        arguments.add("./gradlew");
        arguments.addAll(Arrays.asList(tasks));
        ProcessBuilder processBuilder =
                new ProcessBuilder().command(arguments).directory(projectDir).redirectErrorStream(true);
        processBuilder
                .environment()
                .put("GRADLE_USER_HOME", workingDir().toAbsolutePath().toString());
        return processBuilder;
    }

    // See http://illegalargumentexception.blogspot.com/2009/07/java-finding-class-versions.html
    static Pair<Integer, Integer> readBytecodeVersion(File file) {
        try (InputStream stream = new FileInputStream(file);
                DataInputStream dis = new DataInputStream(stream)) {
            int magic = dis.readInt();
            if (magic != BYTECODE_IDENTIFIER) {
                throw new IllegalArgumentException("File " + file + " does not appear to be java bytecode");
            }
            int minorBytecodeVersion = dis.readUnsignedShort();
            int majorBytecodeVersion = dis.readUnsignedShort();
            return Pair.of(minorBytecodeVersion, majorBytecodeVersion);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read bytecode version", e);
        }
    }
}
