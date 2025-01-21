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

import com.palantir.gradle.jdks.JdkPath.Extension;
import com.palantir.gradle.jdks.setup.common.Arch;
import com.palantir.gradle.jdks.setup.common.Os;

final class GraalVmCeDistribution implements JdkDistribution {
    @Override
    public String defaultBaseUrl() {
        return "https://github.com/graalvm/graalvm-ce-builds/releases/download";
    }

    @Override
    public JdkPath path(JdkRelease jdkRelease) {
        String filename = String.format(
                "jdk-%s/graalvm-community-jdk-%s_%s-%s_bin",
                jdkRelease.version(), jdkRelease.version(), os(jdkRelease.os()), arch(jdkRelease.arch()));

        return JdkPath.builder()
                .filename(filename)
                .extension(extension(jdkRelease.os()))
                .build();
    }

    private static String os(Os os) {
        switch (os) {
            case MACOS:
                return "macos";
            case LINUX_GLIBC:
            case LINUX_MUSL:
                return "linux";
            case WINDOWS:
                return "windows";
            default:
                throw new UnsupportedOperationException("Case " + os + " not implemented");
        }
    }

    private static String arch(Arch arch) {
        switch (arch) {
            case X86:
            case X86_64:
                return "x64";
            case AARCH64:
                return "aarch64";
            default:
                throw new UnsupportedOperationException("Case " + arch + " not implemented");
        }
    }

    private static Extension extension(Os operatingSystem) {
        switch (operatingSystem) {
            case LINUX_GLIBC:
            case LINUX_MUSL:
            case MACOS:
                return Extension.TARGZ;
            case WINDOWS:
                return Extension.ZIP;
            default:
                throw new UnsupportedOperationException("Unknown OS: " + operatingSystem);
        }
    }
}
