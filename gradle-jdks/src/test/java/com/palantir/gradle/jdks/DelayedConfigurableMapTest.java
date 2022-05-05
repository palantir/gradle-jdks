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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.concurrent.Callable;
import org.gradle.api.Action;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DelayedConfigurableMapTest {
    private final ProviderFactory providerFactory = mock(ProviderFactory.class);

    private final DelayedConfigurableMap<String, Extension> delayedConfigurableMap =
            new DelayedConfigurableMap<>(providerFactory, () -> new Extension(0));

    @BeforeEach
    void beforeEach() {
        when(providerFactory.provider(any())).thenAnswer(invocation -> {
            Callable<?> callable = invocation.getArgument(0);
            Provider<?> provider = mock(Provider.class);
            when(provider.get()).thenAnswer(_ignored -> callable.call());
            return provider;
        });
    }

    @Test
    void setting_one_value() {
        delayedConfigurableMap.configure("abc", extension -> extension.number = 4);
        assertThat(delayedConfigurableMap.get()).containsExactly(Map.entry("abc", new Extension(4)));
    }

    @Test
    void setting_one_value_multiple_times() {
        delayedConfigurableMap.configure("abc", extension -> extension.number = 4);
        delayedConfigurableMap.configure("abc", extension -> extension.number = 5);

        assertThat(delayedConfigurableMap.get()).containsExactly(Map.entry("abc", new Extension(5)));
    }

    @Test
    void setting_multiple_values() {
        delayedConfigurableMap.configure("abc", extension -> extension.number = 4);
        delayedConfigurableMap.configure("xyz", extension -> extension.number = 5);

        assertThat(delayedConfigurableMap.get())
                .containsExactlyInAnyOrderEntriesOf(Map.of("abc", new Extension(4), "xyz", new Extension(5)));
    }

    @Test
    void setting_multiple_values_multiple_times() {
        delayedConfigurableMap.configure("abc", extension -> extension.number = 1);
        delayedConfigurableMap.configure("xyz", extension -> extension.number = 8);

        delayedConfigurableMap.configure("abc", extension -> extension.number = 2);
        delayedConfigurableMap.configure("xyz", extension -> extension.number = 9);

        assertThat(delayedConfigurableMap.get())
                .containsExactlyInAnyOrderEntriesOf(Map.of("abc", new Extension(2), "xyz", new Extension(9)));
    }

    @Test
    void lazily_providing_multiple_values() {
        Provider<Map<String, Action<Extension>>> foo =
                providerFactory.provider(() -> Map.of("not this!", extension -> extension.number = 12));

        delayedConfigurableMap.configureLater(foo);

        when(foo.get())
                .thenReturn(Map.of(
                        "yes this",
                        extension -> extension.number = 4,
                        "really this",
                        extension -> extension.number = 5));

        assertThat(delayedConfigurableMap.get())
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of("yes this", new Extension(4), "really this", new Extension(5)));
    }

    @Test
    void lazily_providing_multiple_values_at_multiple_times() {
        delayedConfigurableMap.configureLater(providerFactory.provider(
                () -> Map.of("aaa", extension -> extension.number = 3, "bbb", extension -> extension.number = 4)));

        delayedConfigurableMap.configureLater(providerFactory.provider(
                () -> Map.of("ccc", extension -> extension.number = 6, "bbb", extension -> extension.number = 5)));

        assertThat(delayedConfigurableMap.get())
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of("aaa", new Extension(3), "bbb", new Extension(5), "ccc", new Extension(6)));
    }

    @Test
    void errors_out_if_you_try_to_configure_after_setting() {
        assertThat(delayedConfigurableMap.get()).isEmpty();

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> {
            delayedConfigurableMap.configure("hey", extension -> extension.number = 3);
        });

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> {
            delayedConfigurableMap.configureLater(
                    providerFactory.provider(() -> Map.of("hey", extension -> extension.number = 3)));
        });

        assertThat(delayedConfigurableMap.get()).isEmpty();
    }

    private static final class Extension {
        public int number;

        Extension(int number) {
            this.number = number;
        }

        @Override
        @SuppressWarnings("checkstyle:EqualsHashCode")
        public boolean equals(Object obj) {
            return number == ((Extension) obj).number;
        }

        @Override
        public String toString() {
            return Integer.toString(number);
        }
    }
}
