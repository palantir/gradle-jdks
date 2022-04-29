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

import java.util.Locale;
import java.util.Set;
import org.immutables.value.Value;

@Value.Immutable
interface JdkRelease {
    String version();

    @Value.Default
    default Os os() {
        String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (osName.startsWith("mac")) {
            return Os.MACOS;
        }
        if (osName.startsWith("linux")) {
            return Os.LINUX;
        }

        if (osName.startsWith("windows")) {
            return Os.WINDOWS;
        }

        throw new UnsupportedOperationException("Cannot get platform for operating system " + osName);
    }

    @Value.Default
    default Arch arch() {
        String osArch = System.getProperty("os.arch");

        if (Set.of("x64", "amd64").contains(osArch)) {
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

    enum Os {
        MACOS,
        LINUX,
        WINDOWS
    }

    enum Arch {
        X86,
        X86_64,
        AARCH64
    }

    class Builder extends ImmutableJdkRelease.Builder {}

    static Builder builder() {
        return new Builder();
    }
}
