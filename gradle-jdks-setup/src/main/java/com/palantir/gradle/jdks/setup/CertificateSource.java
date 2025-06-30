/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.jdks.setup;

import com.palantir.gradle.jdks.setup.common.CommandRunner;
import com.palantir.gradle.jdks.setup.common.CurrentOs;
import com.palantir.gradle.jdks.setup.common.Os;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CertificateSource {
    public static Optional<byte[]> systemCertificates(ILogger logger) {
        Os os = CurrentOs.get();
        switch (os) {
            case MACOS:
                return Optional.of(CertificateSource.macosSystemCertificates());
            case LINUX_MUSL:
            case LINUX_GLIBC:
                return Optional.of(CertificateSource.linuxSystemCertificates());
            case WINDOWS:
                logger.logError(String.format(
                        "Not attempting to read Palantir CA from system truststore "
                                + "as OS type '%s' does not yet support this",
                        os.uiName()));
                return Optional.empty();
        }
        throw new IllegalStateException("Unreachable code; all Os enum values should be handled");
    }

    public static byte[] macosSystemCertificates() {
        return Stream.of("/Library/Keychains/System.keychain")
                .map(Paths::get)
                .filter(Files::exists)
                .map(CertificateSource::macosSystemCertificates)
                .collect(Collectors.joining("\n"))
                .getBytes(StandardCharsets.UTF_8);
    }

    private static String macosSystemCertificates(Path keyChainPath) {
        return CommandRunner.runWithOutputCollection(new ProcessBuilder()
                .command(
                        "security",
                        "export",
                        "-t",
                        "certs",
                        "-f",
                        "pemseq",
                        "-k",
                        keyChainPath.toAbsolutePath().toString()));
    }

    public static byte[] linuxSystemCertificates() {
        List<Path> possibleCaCertificatePaths = List.of(
                // Ubuntu/debian
                Paths.get("/etc/ssl/certs/ca-certificates.crt"),
                // Red hat/centos
                Paths.get("/etc/ssl/certs/ca-bundle.crt"));

        return possibleCaCertificatePaths.stream()
                .filter(Files::exists)
                .map(caCertificatePath -> {
                    try {
                        return Files.readString(caCertificatePath);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to read CA certs from " + caCertificatePath, e);
                    }
                })
                .collect(Collectors.joining("\n"))
                .getBytes(StandardCharsets.UTF_8);
    }
}
