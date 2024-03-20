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

package com.palantir.gradle.jdks.setup;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public final class GradleJdkInstallationSetupTest {

    public static Path tempDir;

    @BeforeAll
    public static void beforeAll() throws IOException {
        tempDir = Files.createTempDirectory("testing-jdk-installation");
    }

    @AfterAll
    public static void afterAll() throws IOException {
        FileUtils.deleteDirectory(tempDir.toFile());
    }

    @Test
    public void can_copy_jdk_installation_with_no_certs() throws IOException {
        String distribution =
                Path.of(System.getProperty("java.home")).getFileName().toString();
        Path certsDir = Files.createDirectories(tempDir.resolve("certs"));
        GradleJdkInstallationSetup.main(new String[] {
            tempDir.resolve(distribution).toAbsolutePath().toString(),
            certsDir.toAbsolutePath().toString()
        });
        Path destDistribution = tempDir.resolve(distribution);
        Path destJavaHome = destDistribution.resolve("bin/java");
        assertThat(destDistribution).exists();
        assertThat(destJavaHome).exists();
    }

    @Test
    public void can_copy_jdk_installation_with_palantir_certs() throws IOException {
        String distribution =
                Path.of(System.getProperty("java.home")).getFileName().toString();
        Path certsDir = Files.createDirectories(tempDir.resolve("certs"));
        Path palantirRootCa = Files.createFile(certsDir.resolve("Palantir3rdGenRootCaTest.crt"));
        Files.write(palantirRootCa, "18126334688741185161\n".getBytes(StandardCharsets.UTF_8));
        GradleJdkInstallationSetup.main(new String[] {
            tempDir.resolve(distribution).toAbsolutePath().toString(),
            certsDir.toAbsolutePath().toString()
        });
        Path destDistribution = tempDir.resolve(distribution);
        Path destJavaHome = destDistribution.resolve("bin/java");
        assertThat(destDistribution).exists();
        assertThat(destJavaHome).exists();
        checkCaIsImportedIfExistingInSystemKeytool(destDistribution, "Palantir3rdGenRootCaTest");
    }

    private static void checkCaIsImportedIfExistingInSystemKeytool(Path jdkPath, String certAlias) {
        CaResources.readPalantirRootCaFromSystemTruststore(new StdLogger())
                .ifPresent(_ignored -> checkCaIsImported(jdkPath, certAlias));
    }

    private static void checkCaIsImported(Path jdkPath, String certAlias) {
        CommandRunner.run(List.of(
                jdkPath.resolve("bin/keytool").toString(),
                "-list",
                "-storepass",
                "changeit",
                "-alias",
                certAlias,
                "-keystore",
                jdkPath.resolve("lib/security/cacerts").toString()));
    }
}
