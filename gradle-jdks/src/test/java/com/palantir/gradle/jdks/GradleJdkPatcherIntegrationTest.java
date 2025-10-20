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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.palantir.gradle.jdks.setup.common.CurrentArch;
import com.palantir.gradle.jdks.setup.common.GradleJdksPatchHelper;
import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.execution.InvocationResult;
import com.palantir.gradle.testing.project.RootProject;
import com.palantir.platform.OperatingSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class GradleJdkPatcherIntegrationTest extends GradleJdkIntegrationTest {

    @TempDir
    Path workingDir;

    @Override
    Path workingDir() {
        return workingDir;
    }

    @ParameterizedTest
    @MethodSource("GRADLE_TEST_VERSIONS")
    void successfullyGeneratesGradleJdkSetupFiles(String gradleVersionNumber, GradleInvoker gradle) throws IOException {
        setupJdksHardcodedVersions();
        
        InvocationResult wrapperResult = gradle.withArgs("wrapper").buildsSuccessfully();
        createFile("gradle.properties").overwrite("palantir.jdk.setup.enabled=true");

        // Running setupJdks
        InvocationResult result = gradle.withArgs("setupJdks").buildsSuccessfully();

        // Verify it triggers the execution of Gradle JDK setup tasks
        assertTrue(result.output().contains("wrapperJdkPatcher"));
        assertTrue(result.output().contains("generateGradleJdkConfigs"));

        // Verify ./gradlew file is patched
        String gradlewText = Files.readString(rootProject.path().resolve("gradlew"));
        assertTrue(gradlewText.contains("gradle/gradle-jdks-setup.sh"));
        assertEquals(1, gradlewText.split(GradleJdksPatchHelper.PATCH_HEADER).length - 1);
        assertEquals(1, gradlewText.split(GradleJdksPatchHelper.PATCH_FOOTER).length - 1);

        // Verify the gradle/ configuration files are generated
        checkJdksVersions(rootProject.path().toFile(), Set.of("11", "17", "21"));
        assertEquals(
                DAEMON_MAJOR_VERSION_17, 
                Files.readString(rootProject.path().resolve("gradle/gradle-daemon-jdk-version")).trim());
        
        Path scriptPath = rootProject.path().resolve("gradle/gradle-jdks-setup.sh");
        assertTrue(Files.isExecutable(scriptPath));
        
        Path functionsPath = rootProject.path().resolve("gradle/gradle-jdks-functions.sh");
        assertTrue(Files.isExecutable(functionsPath));

        // Verify old gradle jdk paths are removed
        Path oldPath = rootProject.path().resolve("gradle/certs");
        assertFalse(Files.exists(oldPath));

        // Verify .gradle/config.properties configures java.home
        assertTrue(Files.readString(rootProject.path().resolve(".gradle/config.properties")).contains("java.home"));

        // Run check
        InvocationResult checkResult = gradle.withArgs("check").buildsSuccessfully();
        
        // Verify check tasks
        assertTrue(checkResult.output().contains("checkGradleJdkConfigs"));
        assertFalse(checkResult.output().contains(":checkGradleJdkConfigs UP-TO-DATE"));
        assertTrue(checkResult.output().contains("checkWrapperJdkPatcher"));
        assertFalse(checkResult.output().contains(":checkWrapperJdkPatcher UP-TO-DATE"));

        // Run second check
        InvocationResult secondCheckResult = gradle.withArgs("check").buildsSuccessfully();
        
        // Verify second check tasks
        assertTrue(secondCheckResult.output().contains(":checkGradleJdkConfigs UP-TO-DATE"));
        assertTrue(secondCheckResult.output().contains(":checkWrapperJdkPatcher UP-TO-DATE"));
    }

    @ParameterizedTest
    @MethodSource("GRADLE_TEST_VERSIONS")
    void failsIfGradleJdkConfigurationIsWrong(String gradleVersionNumber, GradleInvoker gradle) {
        setupJdksHardcodedVersions("15");
        createFile("gradle.properties").overwrite("palantir.jdk.setup.enabled=true");
        
        // Run setupJdks expecting failure
        InvocationResult result = gradle.withArgs("setupJdks").buildsWithFailure();
        
        // Verify generateGradleJdkConfigs fails
        assertTrue(result.output().contains("generateGradleJdkConfigs"));
        assertFalse(result.output().contains("wrapperJdkPatcher"));
        assertTrue(result.output().contains("Gradle daemon JDK version is `15` but no JDK configured for that version."));
    }

    @ParameterizedTest
    @MethodSource("GRADLE_TEST_VERSIONS")
    void failsIfNoJdksWereConfigured(String gradleVersionNumber, GradleInvoker gradle) {
        applyJdksPlugins();
        
        rootProject.buildGradle().append("""
            jdks {
               daemonTarget = 11
            }
        """);
        
        gradle.withArgs("wrapper").buildsSuccessfully();
        createFile("gradle.properties").overwrite("palantir.jdk.setup.enabled=true");

        // Run setupJdks expecting failure
        InvocationResult result = gradle.withArgs("setupJdks").buildsWithFailure();
        
        // Verify generateGradleJdkConfigs fails
        assertTrue(result.output().contains("generateGradleJdkConfigs"));
        assertFalse(result.output().contains("wrapperJdkPatcher"));
        assertTrue(result.output().contains("No JDKs were configured for the gradle setup"));
    }

    @ParameterizedTest
    @MethodSource("GRADLE_TEST_VERSIONS")
    void checkGradleJdkConfigsFailsIfRunBeforeSetupJdks(String gradleVersionNumber, GradleInvoker gradle) {
        setupJdksHardcodedVersions();
        
        gradle.withArgs("wrapper").buildsSuccessfully();
        createFile("gradle.properties").overwrite("palantir.jdk.setup.enabled=true");
        
        // Run check expecting failure
        InvocationResult checkResult = gradle.withArgs("check").buildsWithFailure();
        
        assertTrue(checkResult.output().contains("checkGradleJdkConfigs"));
        assertTrue(checkResult.output().contains("is out of date, please run `./gradlew setupJdks`"));
    }

    @ParameterizedTest
    @MethodSource("GRADLE_TEST_VERSIONS")
    void noGradleWrapperPatchIfSetupDisabled(String gradleVersionNumber, GradleInvoker gradle) throws IOException {
        setupJdksHardcodedVersions();
        
        InvocationResult output = gradle.withArgs("wrapper").buildsSuccessfully();
        
        assertFalse(output.output().contains("wrapperJdkPatcher"));
        assertFalse(Files.readString(rootProject.path().resolve("gradlew")).contains("gradle-jdks-setup.sh"));
    }

    private static void checkJdksVersions(File projectDir, Set<String> versions) throws IOException {
        Set<String> jdkDirs;
        try (Stream<Path> stream = Files.list(projectDir.toPath().resolve("gradle/jdks"))) {
            jdkDirs = stream
                    .filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toSet());
        }
                
        assertEquals(versions, jdkDirs);
        
        String osName = OperatingSystem.get().uiName();
        String archName = CurrentArch.get().uiName();
        
        versions.stream().findFirst().ifPresent(version -> {
            assertTrue(Files.exists(projectDir.toPath().resolve(String.format("gradle/jdks/%s/%s/%s/download-url", version, osName, archName))));
            assertTrue(Files.exists(projectDir.toPath().resolve(String.format("gradle/jdks/%s/%s/%s/local-path", version, osName, archName))));
        });
    }
}