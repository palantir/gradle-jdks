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

import com.palantir.gradle.jdks.setup.ILogger;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;

public final class GradleLogger implements ILogger {

    private Logger gradleLogger;
    private LogLevel logLevel;

    public GradleLogger(Logger gradleLogger, LogLevel logLevel) {
        this.gradleLogger = gradleLogger;
        this.logLevel = logLevel;
    }

    @Override
    public void log(String message) {
        gradleLogger.log(logLevel, message);
    }

    @Override
    public void logError(String errorMessage) {
        log(errorMessage);
    }
}
