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

/**
 * A simple logger that logs to stdout and stderr.
 */
@SuppressWarnings({"BanSystemOut", "BanSystemErr"})
public final class StdLogger implements ILogger {

    @Override
    public void log(String message) {
        System.out.println(message);
    }

    @Override
    public void logError(String errorMessae) {
        System.err.println(errorMessae);
    }
}
