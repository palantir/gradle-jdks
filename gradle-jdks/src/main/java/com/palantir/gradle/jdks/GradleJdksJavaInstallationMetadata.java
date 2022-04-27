/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.gradle.jdks;

import org.gradle.jvm.toolchain.JavaInstallationMetadata;
import org.immutables.value.Value;

@Value.Immutable
abstract class GradleJdksJavaInstallationMetadata implements JavaInstallationMetadata {
    public static final class Builder extends ImmutableGradleJdksJavaInstallationMetadata.Builder {}

    public static Builder builder() {
        return new Builder();
    }
}
