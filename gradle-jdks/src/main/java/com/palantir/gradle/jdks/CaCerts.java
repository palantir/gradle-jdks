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

import java.util.Comparator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import org.immutables.value.Value;

@Value.Immutable
public interface CaCerts {
    NavigableMap<String, String> caCerts();

    default String combinedInSortedOrder() {
        StringBuilder stringBuilder = new StringBuilder();

        caCerts().forEach((alias, caCert) -> {
            stringBuilder.append(alias).append(": ").append(caCert).append('\n');
        });

        return stringBuilder.toString();
    }

    class Builder extends ImmutableCaCerts.Builder {}

    static Builder builder() {
        return new Builder();
    }

    static CaCerts from(Map<String, String> caCerts) {
        NavigableMap<String, String> sortedMap = new TreeMap<>(Comparator.naturalOrder());
        sortedMap.putAll(caCerts);
        return builder().caCerts(sortedMap).build();
    }
}
