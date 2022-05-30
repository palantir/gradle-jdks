/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.immutables.value.Value;

enum Os {
    MACOS,
    LINUX_GLIBC,
    LINUX_MUSL,
    WINDOWS;

    @Value.Default
    public static Os current() {
        String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);

        if (osName.startsWith("mac")) {
            return Os.MACOS;
        }

        if (osName.startsWith("windows")) {
            return Os.WINDOWS;
        }

        if (osName.startsWith("linux")) {
            String lddVersionOutputLowercase = lddVersionOutput().toLowerCase(Locale.ROOT);

            if (lddVersionOutputLowercase.contains("glibc")) {
                return Os.LINUX_GLIBC;
            }

            if (lddVersionOutputLowercase.contains("musl")) {
                return Os.LINUX_MUSL;
            }

            throw new UnsupportedOperationException(
                    "Cannot work out libc used by this OS. ldd output was: " + lddVersionOutputLowercase);
        }

        throw new UnsupportedOperationException("Cannot get platform for operating system " + osName);
    }

    private static String lddVersionOutput() {
        try {
            Process process = new ProcessBuilder().command("ldd", "--version").start();

            String firstLine = new BufferedReader(new InputStreamReader(process.getInputStream())).readLine();

            int secondsToWait = 5;
            if (!process.waitFor(secondsToWait, TimeUnit.SECONDS)) {
                throw new RuntimeException("ldd failed to run within " + secondsToWait + " seconds");
            }

            if (process.exitValue() != 0) {
                throw new RuntimeException(String.format(
                        "Failed to run ldd - exited with exit code %d. Stderr: %s.",
                        process.exitValue(), readAllInput(process.getErrorStream())));
            }

            return firstLine;
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static String readAllInput(InputStream inputStream) {
        try (Stream<String> lines = new BufferedReader(new InputStreamReader(inputStream)).lines()) {
            return lines.collect(Collectors.joining("\n"));
        }
    }
}
