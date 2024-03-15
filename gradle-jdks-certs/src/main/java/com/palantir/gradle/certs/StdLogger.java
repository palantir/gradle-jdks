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

package com.palantir.gradle.certs;

import com.google.common.base.Strings;

/**
 * A simple logger that logs to stdout and stderr.
 */
public final class StdLogger implements ILogger {

    @Override
    public void log(String format, Object... args) {
        System.out.println(Strings.lenientFormat(format, args));
    }

    @Override
    public void logError(String format, Object... args) {
        System.err.println(Strings.lenientFormat(format, args));
    }
}
