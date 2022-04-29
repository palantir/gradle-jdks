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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import org.immutables.value.Value;

@Value.Immutable
interface CaCerts {
    SortedMap<String, File> caCerts();

    default String combinedInSortedOrder() {
        StringBuilder stringBuilder = new StringBuilder();

        caCerts().forEach((alias, caCertFile) -> {
            stringBuilder.append(alias);
            stringBuilder.append(": ");
            try {
                stringBuilder.append(Files.readString(caCertFile.toPath()));
            } catch (IOException e) {
                throw new RuntimeException("Failed to read file " + caCertFile, e);
            }
            stringBuilder.append('\n');
        });

        return stringBuilder.toString();
    }

    class Builder extends ImmutableCaCerts.Builder {}

    static Builder builder() {
        return new Builder();
    }

    static CaCerts from(Map<String, File> caCerts) {
        SortedMap<String, File> sortedMap = new TreeMap<>(Comparator.naturalOrder());
        sortedMap.putAll(caCerts);
        return builder().caCerts(sortedMap).build();
    }
}
