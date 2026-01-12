/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;
import javax.inject.Inject;
import org.apache.tools.ant.util.TeeOutputStream;
import org.gradle.api.DefaultTask;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;

public abstract class RunExecTask extends DefaultTask {

    @Inject
    protected abstract ExecOperations getExecOperations();

    public final void runCommandWithFailureHandling(List<String> command, Consumer<String> errorHandler) {
        ByteArrayOutputStream inMemoryOutput = new ByteArrayOutputStream();
        OutputStream logOutput = new TeeOutputStream(System.out, inMemoryOutput);

        ExecResult execResult = getExecOperations().exec(execSpec -> {
            execSpec.setIgnoreExitValue(true);
            execSpec.setStandardOutput(logOutput);
            execSpec.setErrorOutput(logOutput);
            execSpec.commandLine(command);
        });
        if (execResult.getExitValue() != 0) {
            errorHandler.accept(inMemoryOutput.toString(StandardCharsets.UTF_8));
        }
    }
}
