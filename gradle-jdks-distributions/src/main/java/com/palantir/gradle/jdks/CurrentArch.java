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

import java.util.Locale;
import java.util.Set;

public final class CurrentArch {
    public static Arch get() {
        String osArch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);

        if (Set.of("x86_64", "x64", "amd64").contains(osArch)) {
            return Arch.X86_64;
        }

        if (Set.of("arm", "arm64", "aarch64").contains(osArch)) {
            return Arch.AARCH64;
        }

        if (Set.of("x86", "i686").contains(osArch)) {
            return Arch.X86;
        }

        throw new UnsupportedOperationException("Cannot get architecture for " + osArch);
    }

    private CurrentArch() {}
}
