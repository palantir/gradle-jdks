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
import com.palantir.gradle.jdks.setup.common.Arch;
import com.palantir.gradle.jdks.setup.common.Os;
import java.util.Arrays;
import java.util.List;
import org.immutables.value.Value;

final class AzulZuluJdkDistribution implements JdkDistribution {
    // See https://docs.azul.com/core/zulu-openjdk/versioning-and-naming for flag details.
    private static final ImmutableSet<String> BUNDLE_FLAGS =
            ImmutableSet.of("embvm", "cp1", "cp2", "cp3", "c2", "criu", "cr", "crac");
    /**
     * According to the Zulu version documentation, the -ea bundle flag is separate from the license availability flag.
     * Empirically release contain one, or the other.
     */
    private static final ImmutableSet<String> AVAILABILITY_FLAGS = ImmutableSet.of("ea", "ca", "sa");

    private static final ImmutableSet<String> ALL_FLAGS = ImmutableSet.<String>builder()
            .addAll(BUNDLE_FLAGS)
            .addAll(AVAILABILITY_FLAGS)
            .build();

    @Override
    public String defaultBaseUrl() {
        return "https://cdn.azul.com/zulu/bin";
    }

    @Override
    public JdkPath path(JdkRelease jdkRelease) {
        ZuluVersionSplit zuluVersionSplit = splitCombinedVersion(jdkRelease.version());

        String filename = String.format(
                "zulu%s-jdk%s-%s_%s",
                maybeAddAvailabilitySuffix(zuluVersionSplit.zuluVersion()),
                zuluVersionSplit.javaVersion(),
                os(jdkRelease.os()),
                arch(jdkRelease.arch()));

        return JdkPath.builder()
                .filename(filename)
                .extension(extension(jdkRelease.os()))
                .build();
    }

    /**
     * In general, we prefer to use the community availability release of Zulu. If the user specifies a subscriber
     * release or early access release flag, we will attempt to download that.
     */
    private static String maybeAddAvailabilitySuffix(String zuluVersion) {
        if (AVAILABILITY_FLAGS.stream().map(flag -> "-" + flag).anyMatch(zuluVersion::contains)) {
            return zuluVersion;
        } else {
            return zuluVersion + "-ca";
        }
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
            case LINUX_GLIBC:
            case LINUX_MUSL:
                return Extension.TARGZ;
            case MACOS:
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
        while (endOfFlags < split.size() && ALL_FLAGS.contains(split.get(endOfFlags))) {
            endOfFlags++;
        }

        if (endOfFlags == split.size()) {
            throw new IllegalArgumentException(
                    String.format("Expected %s to split into two versions, split into one.", combinedVersion));
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
