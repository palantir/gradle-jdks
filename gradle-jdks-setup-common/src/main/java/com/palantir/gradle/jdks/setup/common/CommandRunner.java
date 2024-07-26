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

package com.palantir.gradle.jdks.setup.common;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class CommandRunner {

    public static String runWithOutputCollection(ProcessBuilder processBuilder) {
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try {
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
                                + "Failed with exit code %d.\nError output:\n\n%s\n\nStandard Output:\n\n%s",
                        String.join(" ", processBuilder.command()), exitCode, errorOutput, output));
            }
            return output;
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(
                    String.format("Failed to run command '%s'. ", String.join(" ", processBuilder.command())), e);
        } catch (ExecutionException e) {
            throw new RuntimeException(
                    String.format(
                            "Failed to get output for the command '%s'. ", String.join(" ", processBuilder.command())),
                    e);
        } finally {
            executorService.shutdown();
        }
    }

    public static void runWithLogger(
            ProcessBuilder processBuilder,
            Function<InputStream, Void> stdOutputWriter,
            Function<InputStream, Void> stdErrWriter) {
        ExecutorService executorService = Executors.newFixedThreadPool(3);
        try {
            Process process = processBuilder.start();
            CompletableFuture<Void> stdOutputFuture =
                    CompletableFuture.runAsync(() -> stdOutputWriter.apply(process.getInputStream()), executorService);
            CompletableFuture<Void> stdErrFuture =
                    CompletableFuture.runAsync(() -> stdErrWriter.apply(process.getErrorStream()), executorService);
            stdOutputFuture.get();
            stdErrFuture.get();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException(String.format(
                        "Command '%s' failed with exit code %d.",
                        String.join(" ", processBuilder.command()), exitCode));
            }
        } catch (IOException | InterruptedException | ExecutionException e) {
            throw new RuntimeException(
                    String.format("Failed to run command '%s'. ", String.join(" ", processBuilder.command())), e);
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

    public static Void processStream(InputStream inputStream, Function<String, Void> logFunction) {
        try (BufferedReader bufferedReader =
                new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                logFunction.apply(line);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to write inputStream", e);
        }
        return null;
    }

    private CommandRunner() {}
}
