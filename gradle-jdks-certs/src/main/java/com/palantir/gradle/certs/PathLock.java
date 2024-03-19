/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.certs;

import com.google.common.io.Closer;
import com.google.common.util.concurrent.Striped;
import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.locks.Lock;

/**
 * Abstraction around locking access to a file or directory, by creating another file with a
 * matching name, and '.lock' extension. Note that the underlying file locking mechanism is
 * left to the filesystem, so we must be careful to work within its bounds. For example,
 * POSIX file locks apply to a process, so within the process we must ensure synchronization
 * separately.
 */
public final class PathLock implements Closeable {
    private static final Striped<Lock> JVM_LOCKS = Striped.lock(16);
    private final Closer closer;

    public PathLock(Path path) throws IOException {
        this.closer = Closer.create();
        try {
            Lock jvmLock = JVM_LOCKS.get(path);
            jvmLock.lock();
            closer.register(jvmLock::unlock);
            Files.createDirectories(path.getParent());
            FileChannel channel = closer.register(FileChannel.open(
                    path.getParent().resolve(path.getFileName() + ".lock"),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE));
            FileLock fileLock = channel.lock();
            closer.register(fileLock::close);
        } catch (Throwable t) {
            closer.close();
            throw t;
        }
    }

    @Override
    public void close() throws IOException {
        closer.close();
    }
}
