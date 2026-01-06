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
import com.palantir.platform.OperatingSystem;

public final class GraalVmCeDistribution implements JdkDistribution {
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

    private static String os(OperatingSystem os) {
        return switch (os) {
            case MACOS -> "macos";
            case LINUX_GLIBC, LINUX_MUSL -> "linux";
            case WINDOWS -> "windows";
            default -> throw new UnsupportedOperationException("Case " + os + " not implemented");
        };
    }

    private static String arch(Arch arch) {
        return switch (arch) {
            case X86, X86_64 -> "x64";
            case AARCH64 -> "aarch64";
            default -> throw new UnsupportedOperationException("Case " + arch + " not implemented");
        };
    }

    private static Extension extension(OperatingSystem operatingSystem) {
        return switch (operatingSystem) {
            case LINUX_GLIBC, LINUX_MUSL, MACOS -> Extension.TARGZ;
            case WINDOWS -> Extension.ZIP;
            default -> throw new UnsupportedOperationException("Unknown OS: " + operatingSystem);
        };
    }
}
