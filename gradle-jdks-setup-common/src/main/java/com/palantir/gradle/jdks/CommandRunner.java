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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class CommandRunner {

    public static String run(List<String> commandArguments) {
        return run(commandArguments, Optional.empty());
    }

    public static String run(List<String> commandArguments, Optional<File> directory) {
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try {
            ProcessBuilder processBuilder = new ProcessBuilder().command(commandArguments);
            if (directory.isPresent()) {
                processBuilder.directory(directory.get());
            }
            Process process = processBuilder.start();
            CompletableFuture<String> outputFuture =
                    CompletableFuture.supplyAsync(() -> readAllInput(process.getInputStream()), executorService);
            CompletableFuture<String> errorOutputFuture =
                    CompletableFuture.supplyAsync(() -> readAllInput(process.getErrorStream()), executorService);
            String output = outputFuture.get();
            String errorOutput = errorOutputFuture.get();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException(String.format(
                        "Failed to run command '%s'. "
                                + "Failed with exit code %d.Error output:\n\n%s\n\nStandard Output:\n\n%s",
                        String.join(" ", commandArguments), exitCode, errorOutput, output));
            }
            return output;
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(
                    String.format("Failed to run command '%s'. ", String.join(" ", commandArguments)), e);
        } catch (ExecutionException e) {
            throw new RuntimeException(
                    String.format("Failed to get output for the command '%s'. ", String.join(" ", commandArguments)),
                    e);
        } finally {
            executorService.shutdown();
        }
    }

    public static void runWithInheritIO(List<String> commandArguments, File directory) {
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try {
            ProcessBuilder processBuilder = new ProcessBuilder().command(commandArguments);
            processBuilder.directory(directory);
            processBuilder.inheritIO();
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException(String.format(
                        "Failed to run command '%s'. " + "Failed with exit code %d.",
                        String.join(" ", commandArguments), exitCode));
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(
                    String.format("Failed to run command '%s'. ", String.join(" ", commandArguments)), e);
        } finally {
            executorService.shutdown();
        }
    }

    public static String readAllInput(InputStream inputStream) {
        try (Stream<String> lines =
                new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)).lines()) {
            return lines.collect(Collectors.joining("\n"));
        }
    }

    private CommandRunner() {}
}
