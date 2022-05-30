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
import org.immutables.value.Value;

enum Os {
    MACOS,
    LINUX_GLIBC,
    LINUX_MUSL,
    WINDOWS;

    @Value.Default
    public static Os current() {
        String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (osName.startsWith("mac")) {
            return Os.MACOS;
        }
        if (osName.startsWith("linux")) {
            return isGlibc() ? Os.LINUX_GLIBC : Os.LINUX_MUSL;
        }

        if (osName.startsWith("windows")) {
            return Os.WINDOWS;
        }

        throw new UnsupportedOperationException("Cannot get platform for operating system " + osName);
    }

    private static boolean isGlibc() {
        System.loadLibrary("libc");
        try {
            GlibcProbe.gnu_get_libc_version();
            return true;
        } catch (UnsatisfiedLinkError e) {
            return false;
        }
    }

    private static final class GlibcProbe {
        static {
            System.loadLibrary("libc");
        }

        public static native String gnu_get_libc_version();
    }
}
