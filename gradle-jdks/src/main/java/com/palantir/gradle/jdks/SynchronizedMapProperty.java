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

import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import javax.annotation.Nullable;
import org.gradle.api.Transformer;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Provider;

final class SynchronizedMapProperty<K, V> implements MapProperty<K, V> {
    private final MapProperty<K, V> delegate;

    SynchronizedMapProperty(MapProperty<K, V> delegate) {
        this.delegate = delegate;
    }

    @Override
    public synchronized MapProperty<K, V> empty() {
        return delegate.empty();
    }

    @Override
    public synchronized Provider<V> getting(K key) {
        return delegate.getting(key);
    }

    @Override
    public synchronized void set(@Nullable Map<? extends K, ? extends V> map) {
        delegate.set(map);
    }

    @Override
    public synchronized void set(Provider<? extends Map<? extends K, ? extends V>> provider) {
        delegate.set(provider);
    }

    @Override
    public synchronized MapProperty<K, V> value(@Nullable Map<? extends K, ? extends V> map) {
        return delegate.value(map);
    }

    @Override
    public synchronized MapProperty<K, V> value(Provider<? extends Map<? extends K, ? extends V>> provider) {
        return delegate.value(provider);
    }

    @Override
    public synchronized void put(K key, V value) {
        delegate.put(key, value);
    }

    @Override
    public synchronized void put(K key, Provider<? extends V> provider) {
        delegate.put(key, provider);
    }

    @Override
    public synchronized void putAll(Map<? extends K, ? extends V> map) {
        delegate.putAll(map);
    }

    @Override
    public synchronized void putAll(Provider<? extends Map<? extends K, ? extends V>> provider) {
        delegate.putAll(provider);
    }

    @Override
    public synchronized Provider<Set<K>> keySet() {
        return delegate.keySet();
    }

    @Override
    public synchronized MapProperty<K, V> convention(@Nullable Map<? extends K, ? extends V> map) {
        return delegate.convention(map);
    }

    @Override
    public synchronized MapProperty<K, V> convention(Provider<? extends Map<? extends K, ? extends V>> provider) {
        return delegate.convention(provider);
    }

    @Override
    public synchronized void finalizeValue() {
        delegate.finalizeValue();
    }

    @Override
    public synchronized Map<K, V> get() {
        return delegate.get();
    }

    @Override
    @Nullable
    public synchronized Map<K, V> getOrNull() {
        return delegate.getOrNull();
    }

    @Override
    public synchronized Map<K, V> getOrElse(Map<K, V> kvMap) {
        return delegate.getOrElse(kvMap);
    }

    @Override
    public synchronized <S> Provider<S> map(Transformer<? extends S, ? super Map<K, V>> transformer) {
        return delegate.map(transformer);
    }

    @Override
    public synchronized <S> Provider<S> flatMap(
            Transformer<? extends Provider<? extends S>, ? super Map<K, V>> transformer) {
        return delegate.flatMap(transformer);
    }

    @Override
    public synchronized boolean isPresent() {
        return delegate.isPresent();
    }

    @Override
    public synchronized Provider<Map<K, V>> orElse(Map<K, V> kvMap) {
        return delegate.orElse(kvMap);
    }

    @Override
    public synchronized Provider<Map<K, V>> orElse(Provider<? extends Map<K, V>> provider) {
        return delegate.orElse(provider);
    }

    @Override
    @Deprecated
    public synchronized Provider<Map<K, V>> forUseAtConfigurationTime() {
        return delegate.forUseAtConfigurationTime();
    }

    @Override
    public synchronized <B, R> Provider<R> zip(Provider<B> provider, BiFunction<Map<K, V>, B, R> biFunction) {
        return delegate.zip(provider, biFunction);
    }

    @Override
    public synchronized void finalizeValueOnRead() {
        delegate.finalizeValueOnRead();
    }

    @Override
    public synchronized void disallowChanges() {
        delegate.disallowChanges();
    }

    @Override
    public synchronized void disallowUnsafeRead() {
        delegate.disallowUnsafeRead();
    }
}
