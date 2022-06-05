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
import java.util.function.BiFunction;
import javax.annotation.Nullable;
import org.gradle.api.Transformer;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;

final class SynchronizedDirectoryProperty implements DirectoryProperty {
    private final DirectoryProperty delegate;

    SynchronizedDirectoryProperty(DirectoryProperty delegate) {
        this.delegate = delegate;
    }

    @Override
    public synchronized FileTree getAsFileTree() {
        return delegate.getAsFileTree();
    }

    @Override
    public synchronized DirectoryProperty value(@Nullable Directory directory) {
        return delegate.value(directory);
    }

    @Override
    public synchronized DirectoryProperty value(Provider<? extends Directory> provider) {
        return delegate.value(provider);
    }

    @Override
    public synchronized DirectoryProperty fileValue(@Nullable File file) {
        return delegate.fileValue(file);
    }

    @Override
    public synchronized DirectoryProperty fileProvider(Provider<File> provider) {
        return delegate.fileProvider(provider);
    }

    @Override
    public synchronized DirectoryProperty convention(Directory directory) {
        return delegate.convention(directory);
    }

    @Override
    public synchronized DirectoryProperty convention(Provider<? extends Directory> provider) {
        return delegate.convention(provider);
    }

    @Override
    public synchronized Provider<Directory> dir(String s) {
        return delegate.dir(s);
    }

    @Override
    public synchronized Provider<Directory> dir(Provider<? extends CharSequence> provider) {
        return delegate.dir(provider);
    }

    @Override
    public synchronized Provider<RegularFile> file(String s) {
        return delegate.file(s);
    }

    @Override
    public synchronized Provider<RegularFile> file(Provider<? extends CharSequence> provider) {
        return delegate.file(provider);
    }

    @Override
    public synchronized FileCollection files(Object... objects) {
        return delegate.files(objects);
    }

    @Override
    public synchronized Provider<File> getAsFile() {
        return delegate.getAsFile();
    }

    @Override
    public synchronized void set(@Nullable File file) {
        delegate.set(file);
    }

    @Override
    public synchronized Provider<Directory> getLocationOnly() {
        return delegate.getLocationOnly();
    }

    @Override
    public synchronized void set(@Nullable Directory directory) {
        delegate.set(directory);
    }

    @Override
    public synchronized void set(Provider<? extends Directory> provider) {
        delegate.set(provider);
    }

    @Override
    public synchronized void finalizeValue() {
        delegate.finalizeValue();
    }

    @Override
    public synchronized Directory get() {
        return delegate.get();
    }

    @Override
    @Nullable
    public synchronized Directory getOrNull() {
        return delegate.getOrNull();
    }

    @Override
    public synchronized Directory getOrElse(Directory directory) {
        return delegate.getOrElse(directory);
    }

    @Override
    public synchronized <S> Provider<S> map(Transformer<? extends S, ? super Directory> transformer) {
        return delegate.map(transformer);
    }

    @Override
    public synchronized <S> Provider<S> flatMap(
            Transformer<? extends Provider<? extends S>, ? super Directory> transformer) {
        return delegate.flatMap(transformer);
    }

    @Override
    public synchronized boolean isPresent() {
        return delegate.isPresent();
    }

    @Override
    public synchronized Provider<Directory> orElse(Directory directory) {
        return delegate.orElse(directory);
    }

    @Override
    public synchronized Provider<Directory> orElse(Provider<? extends Directory> provider) {
        return delegate.orElse(provider);
    }

    @Override
    @Deprecated
    public synchronized Provider<Directory> forUseAtConfigurationTime() {
        return delegate.forUseAtConfigurationTime();
    }

    @Override
    public synchronized <B, R> Provider<R> zip(Provider<B> provider, BiFunction<Directory, B, R> biFunction) {
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
