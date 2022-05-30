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

import java.util.Optional;

enum Libc {
    GLIBC,
    MUSL;

    public static Optional<Libc> forOs(Os os) {
        if (!os.equals(Os.LINUX)) {
            return Optional.empty();
        }

        return Optional.of(isGlibc() ? GLIBC : MUSL);
    }

    private static boolean isGlibc() {
        try {
            GlibcProbe.gnu_get_libc_version();
            return true;
        } catch (UnsatisfiedLinkError e) {
            return false;
        }
    }

    private static final class GlibcProbe {
        static {
            System.loadLibrary("c");
        }

        public static native String gnu_get_libc_version();
    }
}
