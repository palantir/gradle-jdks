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

package com.palantir.gradle.jdks;

import com.palantir.gradle.testing.project.RootProject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.assertj.core.api.AbstractObjectAssert;

final class JdkDirectoriesAssert
        extends AbstractObjectAssert<JdkDirectoriesAssert, Set<JdkDirectoriesAssert.JdkDirectory>> {

    record JdkDirectory(String version, Path path) {}

    private JdkDirectoriesAssert(Set<JdkDirectory> actual) {
        super(actual, JdkDirectoriesAssert.class);
    }

    static JdkDirectoriesAssert assertThatJdkDirectories(RootProject rootProject) {
        return assertThatJdkDirectories(rootProject.path().resolve("gradle/jdks"));
    }

    static JdkDirectoriesAssert assertThatJdkDirectories(Path jdksRoot) {
        try (Stream<Path> paths = Files.list(jdksRoot)) {
            Set<JdkDirectory> directories = paths.filter(Files::isDirectory)
                    .map(path -> new JdkDirectory(path.getFileName().toString(), path))
                    .collect(Collectors.toSet());
            return new JdkDirectoriesAssert(directories);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    JdkDirectoriesAssert containsExactJdks(int... versions) {
        isNotNull();
        Set<String> expected =
                Arrays.stream(versions).mapToObj(Integer::toString).collect(Collectors.toSet());
        Set<String> actualVersions = actual.stream().map(JdkDirectory::version).collect(Collectors.toSet());
        if (!actualVersions.equals(expected)) {
            failWithMessage(
                    "Expected JDK directories to contain exactly versions %s but found directories: %s",
                    expected, actual);
        }
        return this;
    }

    JdkDirectoriesAssert containsJdk(int... versions) {
        isNotNull();
        Set<String> expected =
                Arrays.stream(versions).mapToObj(Integer::toString).collect(Collectors.toSet());
        Set<String> actualVersions = actual.stream().map(JdkDirectory::version).collect(Collectors.toSet());
        if (!actualVersions.containsAll(expected)) {
            failWithMessage(
                    "Expected JDK directories to contain versions %s but found directories: %s", expected, actual);
        }
        return this;
    }
}
