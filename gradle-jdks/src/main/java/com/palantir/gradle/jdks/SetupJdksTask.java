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

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.Exec;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;

public abstract class SetupJdksTask extends Exec {

    private static final Logger logger = Logging.getLogger(SetupJdksTask.class);

    @InputFile
    public abstract RegularFileProperty getGradlewScript();

    @Internal
    public abstract RegularFileProperty getProjectDir();

    @Override
    @TaskAction
    protected final void exec() {
        switch (CurrentOs.get()) {
            case WINDOWS:
                logger.debug("Windows gradleJdk setup is not yet supported.");
                break;
            case LINUX_GLIBC:
            case LINUX_MUSL:
            case MACOS:
                setCommandLine(getGradlewScript().getAsFile().get().toPath(), "javaToolchains");
                break;
        }
        super.exec();
    }
}
