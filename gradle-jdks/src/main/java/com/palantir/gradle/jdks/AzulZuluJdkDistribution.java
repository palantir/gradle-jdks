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

import com.google.common.collect.ImmutableSet;
import com.palantir.gradle.jdks.JdkPath.Extension;
import com.palantir.gradle.jdks.JdkRelease.Arch;
import java.util.Arrays;
import java.util.List;
import org.immutables.value.Value;

final class AzulZuluJdkDistribution implements JdkDistribution {
    // See https://docs.azul.com/core/zulu-openjdk/versioning-and-naming for flag details.
    private static final ImmutableSet<String> BUNDLE_FLAGS =
            ImmutableSet.of("ea", "embvm", "cp1", "cp2", "cp3", "c2", "criu", "cr", "crac");

    @Override
    public String defaultBaseUrl() {
        return "https://cdn.azul.com/zulu/bin";
    }

    @Override
    public JdkPath path(JdkRelease jdkRelease) {
        ZuluVersionSplit zuluVersionSplit = splitCombinedVersion(jdkRelease.version());

        String filename = String.format(
                "zulu%s-ca-jdk%s-%s_%s",
                zuluVersionSplit.zuluVersion(),
                zuluVersionSplit.javaVersion(),
                os(jdkRelease.os()),
                arch(jdkRelease.arch()));

        return JdkPath.builder()
                .filename(filename)
                .extension(extension(jdkRelease.os()))
                .build();
    }

    private static String os(Os os) {
        switch (os) {
            case MACOS:
                return "macosx";
            case LINUX_GLIBC:
                return "linux";
            case LINUX_MUSL:
                return "linux_musl";
            case WINDOWS:
                return "win";
        }

        throw new UnsupportedOperationException("Case " + os + " not implemented");
    }

    private static String arch(Arch arch) {
        switch (arch) {
            case X86:
                return "i686";
            case X86_64:
                return "x64";
            case AARCH64:
                return "aarch64";
        }

        throw new UnsupportedOperationException("Case " + arch + " not implemented");
    }

    private static Extension extension(Os operatingSystem) {
        switch (operatingSystem) {
            case MACOS:
            case LINUX_GLIBC:
            case LINUX_MUSL:
                return Extension.TARGZ;
            case WINDOWS:
                return Extension.ZIP;
        }
        throw new UnsupportedOperationException("Unknown OS: " + operatingSystem);
    }

    static ZuluVersionSplit splitCombinedVersion(String combinedVersion) {
        List<String> split = Arrays.asList(combinedVersion.split("-", -1));

        if (split.size() < 2) {
            throw new IllegalArgumentException(String.format(
                    "Expected %s to split into at least two parts, split into %d", combinedVersion, split.size()));
        }

        int endOfFlags = 1;
        while (endOfFlags < split.size() && BUNDLE_FLAGS.contains(split.get(endOfFlags))) {
            endOfFlags++;
        }

        if (endOfFlags == split.size()) {
            throw new IllegalArgumentException(
                    String.format("Expected %s to split into two versions, split into one."));
        }

        return ZuluVersionSplit.builder()
                .zuluVersion(String.join("-", split.subList(0, endOfFlags)))
                .javaVersion(String.join("-", split.subList(endOfFlags, split.size())))
                .build();
    }

    @Value.Immutable
    interface ZuluVersionSplit {
        String zuluVersion();

        String javaVersion();

        class Builder extends ImmutableZuluVersionSplit.Builder {}

        static Builder builder() {
            return new Builder();
        }
    }
}
