/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.gradle.jdks;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import org.immutables.value.Value;

@Value.Immutable
interface CaCerts {
    Set<Path> caCerts();

    default String combinedInSortedOrder() {
        return caCerts().stream()
                // Must sort to keep hashes consistent!
                .sorted()
                .map(file -> {
                    try {
                        return Files.readString(file);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to read file " + file, e);
                    }
                })
                .collect(Collectors.joining("\n"));
    }

    class Builder extends ImmutableCaCerts.Builder {}

    static Builder builder() {
        return new Builder();
    }

    static CaCerts from(Collection<File> caCerts) {
        return builder()
                .caCerts(caCerts.stream().map(File::toPath).collect(Collectors.toSet()))
                .build();
    }
}
