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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.palantir.gradle.jdks.setup.common.CommandRunner;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

public class CommandRunnerTest {

    @Test
    public void command_runs_successfully() {
        assertThat(CommandRunner.runWithOutputCollection(new ProcessBuilder().command("echo", "my message")))
                .contains("my message");
    }

    @Test
    public void command_fails() {
        assertThatThrownBy(
                        () -> CommandRunner.runWithOutputCollection(new ProcessBuilder().command("nonexistingcommand")))
                .hasMessageContaining("Failed to run command 'nonexistingcommand'");
    }

    @Test
    public void command_runs_with_logger() {
        CommandRunner.runWithLogger(
                new ProcessBuilder().command("echo", "my message"), CommandRunnerTest::assertOutput, _unused -> null);
    }

    private static Void assertOutput(InputStream inputStream) {
        return CommandRunner.processStream(inputStream, line -> {
            assertThat(line).contains("my message");
            return null;
        });
    }
}
