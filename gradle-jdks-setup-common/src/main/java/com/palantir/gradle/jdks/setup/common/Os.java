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

package com.palantir.gradle.jdks.setup.common;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Optional;

public enum Os {
    MACOS,
    LINUX_GLIBC,
    LINUX_MUSL,
    WINDOWS;

    @Override
    public String toString() {
        return uiName();
    }

    @JsonValue
    public final String uiName() {
        return UiNames.uiName(this);
    }

    public static Optional<Os> fromString(String osUiName) {
        return UiNames.fromString(values(), osUiName);
    }

    @JsonCreator
    public static Os fromStringThrowing(String osUiName) {
        return UiNames.fromStringThrowing(Os.class, values(), osUiName);
    }
}
