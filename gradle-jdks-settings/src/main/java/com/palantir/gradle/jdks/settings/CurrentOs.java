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

package com.palantir.gradle.jdks.settings;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;

public final class CurrentOs {
    public static Os get() {
        String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);

        if (osName.startsWith("mac")) {
            return Os.MACOS;
        }

        if (osName.startsWith("windows")) {
            return Os.WINDOWS;
        }

        if (osName.startsWith("linux")) {
            return linuxLibcFromLdd();
        }

        throw new UnsupportedOperationException("Cannot get platform for operating system " + osName);
    }

    private static Os linuxLibcFromLdd() {
        return linuxLibcFromLdd(UnaryOperator.identity());
    }

    // Visible for testing
    static Os linuxLibcFromLdd(UnaryOperator<List<String>> argTransformer) {
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
                return Os.LINUX_GLIBC;
            }

            if (lowercaseOutput.contains("musl")) {
                return Os.LINUX_MUSL;
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

    private CurrentOs() {}
}
