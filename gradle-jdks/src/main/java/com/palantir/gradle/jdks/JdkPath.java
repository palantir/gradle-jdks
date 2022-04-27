/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.gradle.jdks;

import org.immutables.value.Value;

@Value.Immutable
interface JdkPath {
    String filename();

    Extension extension();

    enum Extension {
        ZIP("zip"),
        TARGZ("tar.gz");

        private final String extension;

        Extension(String extension) {
            this.extension = extension;
        }

        @Override
        public String toString() {
            return extension;
        }
    }

    class Builder extends ImmutableJdkPath.Builder {}

    static Builder builder() {
        return new Builder();
    }
}
