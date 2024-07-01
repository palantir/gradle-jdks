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

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import javax.inject.Inject;
import org.apache.tools.ant.util.TeeOutputStream;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;

/**
 * Actually triggering the patched `./gradlew` script (that configures and installs the JDKs) and runs `javaToolchains`
 * to output the JDKs that Gradle will use.
 */
public abstract class SetupJdksTask extends DefaultTask {

    private static final Logger logger = Logging.getLogger(SetupJdksTask.class);

    @InputFile
    public abstract RegularFileProperty getGradlewScript();

    @Inject
    protected abstract ExecOperations getExecOperations();

    @TaskAction
    public final void exec() {
        if (CurrentOs.get().equals(Os.WINDOWS)) {
            logger.debug("Windows gradleJdk setup is not yet supported.");
            return;
        }
        ByteArrayOutputStream inMemoryOutput = new ByteArrayOutputStream();
        OutputStream logOutput = new TeeOutputStream(System.out, inMemoryOutput);

        ExecResult execResult = getExecOperations().exec(execSpec -> {
            execSpec.setIgnoreExitValue(true);
            execSpec.setStandardOutput(logOutput);
            execSpec.setErrorOutput(logOutput);
            execSpec.commandLine(getGradlewScript().get().getAsFile().toPath(), "-q", "javaToolchains", "--stacktrace");
        });

        if (execResult.getExitValue() != 0) {
            String output = inMemoryOutput.toString(StandardCharsets.UTF_8);
            if (output.contains("UnsupportedClassVersionError")) {
                throw new RuntimeException(
                        "The Gradle JDK setup has failed. The Gradle Daemon major version might be incorrectly set."
                                + " Update the Gradle JDK major version using `jdks.daemonTargetVersion` in your"
                                + " `build.gradle` and the `gradle/gradle-daemon-jdk-version` entry");
            }
            throw new RuntimeException(String.format("The Gradle JDK setup has failed. Error: %s", output));
        }
    }
}
