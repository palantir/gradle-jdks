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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.gradle.api.Action;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;

final class DelayedConfigurableMap<K, V> {
    private final ProviderFactory providerFactory;
    private final Supplier<V> valueFactory;
    private final List<Provider<Map<K, Action<V>>>> configurations = new ArrayList<>();
    private Optional<Map<K, V>> configuredValue = Optional.empty();

    DelayedConfigurableMap(ProviderFactory providerFactory, Supplier<V> valueFactory) {
        this.providerFactory = providerFactory;
        this.valueFactory = valueFactory;
    }

    public void configure(K key, Action<V> value) {
        checkNotFinialized();

        configurations.add(providerFactory.provider(() -> Map.of(key, value)));
    }

    public void configureLater(Provider<Map<K, Action<V>>> values) {
        checkNotFinialized();

        configurations.add(values);
    }

    private void checkNotFinialized() {
        if (configuredValue.isPresent()) {
            throw new IllegalStateException(String.format(
                    "This %s has been finialized by calling get(). It cannot be configured any more.",
                    DelayedConfigurableMap.class.getSimpleName()));
        }
    }

    public Map<K, V> get() {
        if (configuredValue.isPresent()) {
            return configuredValue.get();
        }

        Map<K, V> map = new HashMap<>();

        configurations.forEach(mapActionProvider -> {
            mapActionProvider.get().forEach((key, valueConfiguration) -> {
                V value = map.computeIfAbsent(key, _ignored -> valueFactory.get());
                valueConfiguration.execute(value);
            });
        });

        Map<K, V> unmodifiableMap = Collections.unmodifiableMap(map);

        configuredValue = Optional.of(unmodifiableMap);

        return unmodifiableMap;
    }

    public Provider<Map<K, V>> getAsProvider() {
        return providerFactory.provider(this::get);
    }
}
