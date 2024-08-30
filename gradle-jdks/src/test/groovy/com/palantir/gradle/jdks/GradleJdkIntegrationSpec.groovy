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

package com.palantir.gradle.jdks

import com.palantir.gradle.jdks.setup.common.CommandRunner
import nebula.test.IntegrationSpec
import org.apache.commons.lang3.tuple.Pair

import java.nio.file.Path

abstract class GradleJdkIntegrationSpec extends IntegrationSpec {

    static final List<String> GRADLE_TEST_VERSIONS = [GradleJdkTestUtils.GRADLE_7_6_VERSION, GradleJdkTestUtils.GRADLE_8_8_VERSION]

    abstract Path workingDir();

    def setupJdksHardcodedVersions() {
x        GradleJdkTestUtils.setupJdksHardcodedVersions(buildFile)
    }

    def setupJdksHardcodedVersions(String daemonTarget) {
        GradleJdkTestUtils.setupJdksHardcodedVersions(buildFile, daemonTarget)
    }

    def applyApplicationPlugin() {
        GradleJdkTestUtils.applyApplicationPlugin(buildFile)
    }

    def applyBaselineJavaVersions() {
        GradleJdkTestUtils.applyBaselineJavaVersions(buildFile)
    }

    def applyJdksPlugins() {
        GradleJdkTestUtils.applyJdksPlugins(settingsFile, buildFile)
    }

    def applyPalantirCaPlugin() {
        GradleJdkTestUtils.applyPalantirCaPlugin(buildFile)
    }

    String runGradlewTasksSuccessfully(String... tasks) {
        String output = runGradlewTasks(tasks)
        assert output.contains("BUILD SUCCESSFUL")
        return output
    }

    String runGradlewTasksWithFailure(String... tasks) {
        String output = runGradlewTasks(tasks)
        assert output.contains("BUILD FAILED")
        return output
    }

    private String runGradlewTasks(String... tasks) {
        ProcessBuilder processBuilder = getProcessBuilder(tasks)
        Process process = processBuilder.start()
        String output = CommandRunner.readAllInput(process.getInputStream())
        return output
    }

    private ProcessBuilder getProcessBuilder(String... tasks) {
        List<String> arguments = ["./gradlew"]
        Arrays.asList(tasks).forEach(arguments::add)
        ProcessBuilder processBuilder = new ProcessBuilder()
                .command(arguments)
                .directory(projectDir).redirectErrorStream(true)
        processBuilder.environment().put("GRADLE_USER_HOME", workingDir().toAbsolutePath().toString())
        return processBuilder
    }

    private static final int BYTECODE_IDENTIFIER = (int) 0xCAFEBABE

    // See http://illegalargumentexception.blogspot.com/2009/07/java-finding-class-versions.html
    static Pair readBytecodeVersion(File file) {
        try (InputStream stream = new FileInputStream(file)
             DataInputStream dis = new DataInputStream(stream)) {
            int magic = dis.readInt()
            if (magic != BYTECODE_IDENTIFIER) {
                throw new IllegalArgumentException("File " + file + " does not appear to be java bytecode")
            }
            int minorBytecodeVersion = dis.readUnsignedShort()
            int majorBytecodeVersion = dis.readUnsignedShort()
            return Pair.of(minorBytecodeVersion, majorBytecodeVersion)
        }
    }
}
