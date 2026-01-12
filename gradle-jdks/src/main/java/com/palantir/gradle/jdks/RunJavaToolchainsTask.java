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

import com.palantir.gradle.utils.environmentvariables.EnvironmentVariables;
import com.palantir.platform.GradleOperatingSystem;
import com.palantir.platform.OperatingSystem;
import java.util.List;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.TaskAction;

/**
 * Actually runs the patched `./gradlew` task `javaToolchains` to output the JDKs that Gradle configured by the "setupJdks" task.
 */
public abstract class RunJavaToolchainsTask extends RunExecTask {

    private static final Logger logger = Logging.getLogger(SetupJdksTask.class);

    @InputFile
    public abstract RegularFileProperty getGradlewScript();

    @Nested
    protected abstract GradleOperatingSystem getOperatingSystem();

    @Nested
    abstract EnvironmentVariables getEnvironment();

    public RunJavaToolchainsTask() {}

    @TaskAction
    public final void exec() {
        if (getOperatingSystem().getOperatingSystem().get().equals(OperatingSystem.WINDOWS)) {
            logger.debug("Windows gradleJdk setup is not yet supported.");
            return;
        }

        boolean isRunningInAGradleTest = getEnvironment()
                .envVarOrFromTestingProperty("palantir.gradle.plugin.tests")
                .map(Boolean::parseBoolean)
                .orElse(false)
                .get();
        if (isRunningInAGradleTest) {
            logger.warn("Gradle jdks setup ran, skipping the ./gradlew javaToolchains call because we are running in a"
                    + " Gradle Plugin Test with @JdkAutomanagement enabled");
            return;
        }

        runCommandWithFailureHandling(
                List.of(getGradlewScript().get().getAsFile().getAbsolutePath(), "-q", "javaToolchains", "--stacktrace"),
                output -> {
                    if (output.contains("UnsupportedClassVersionError")) {
                        throw new RuntimeException(
                                "The Gradle JDK setup has failed. The Gradle Daemon major version might be"
                                        + " incorrectly set. Update the Gradle JDK major version using"
                                        + " `jdks.daemonTargetVersion` in your `build.gradle` and the"
                                        + " `gradle/gradle-daemon-jdk-version` entry");
                    }
                    throw new RuntimeException(String.format(
                            "Failed to run javaToolchains after setting up the JDK setup. Error: %s", output));
                });
    }
}
