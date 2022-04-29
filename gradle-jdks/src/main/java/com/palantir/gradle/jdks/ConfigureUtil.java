/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
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

import groovy.lang.Closure;
import org.gradle.api.Action;

final class ConfigureUtil {
    @SuppressWarnings("RawTypes")
    public static <T> Action<T> toAction(Closure closure) {
        return item -> {
            closure.setDelegate(item);
            closure.run();
        };
    }

    private ConfigureUtil() {}
}
