/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.gradle.jdks;

import java.util.Set;
import org.gradle.internal.os.OperatingSystem;
import org.immutables.value.Value;

@Value.Immutable
interface JdkRelease {
    String version();

    @Value.Default
    default Os os() {
        OperatingSystem operatingSystem = OperatingSystem.current();
        if (operatingSystem.isMacOsX()) {
            return Os.MACOS;
        }
        if (operatingSystem.isLinux()) {
            return Os.LINUX;
        }
        if (operatingSystem.isWindows()) {
            return Os.WINDOWS;
        }

        throw new UnsupportedOperationException("Cannot get platform for operating system " + operatingSystem);
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
