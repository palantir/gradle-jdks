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

import com.palantir.gradle.jdks.setup.common.Arch;
import com.palantir.gradle.testing.project.RootProject;
import com.palantir.platform.OperatingSystem;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.assertj.core.api.AbstractListAssert;
import org.assertj.core.api.ObjectAssert;

final class JdkDirectoriesAssert
        extends AbstractListAssert<
                JdkDirectoriesAssert,
                List<JdkDirectoriesAssert.JdkDirectory>,
                JdkDirectoriesAssert.JdkDirectory,
                ObjectAssert<JdkDirectoriesAssert.JdkDirectory>> {

    record JdkDirectory(String version, Path path) {
        Path platformPath(OperatingSystem os, Arch arch) {
            return path.resolve(os.uiName()).resolve(arch.uiName());
        }
    }

    private JdkDirectoriesAssert(List<JdkDirectory> actual) {
        super(actual, JdkDirectoriesAssert.class);
    }

    static JdkDirectoriesAssert assertThatJdkDirectories(RootProject rootProject) {
        return assertThatJdkDirectories(rootProject.path().resolve("gradle/jdks"));
    }

    static JdkDirectoriesAssert assertThatJdkDirectories(Path jdksRoot) {
        try (Stream<Path> paths = Files.list(jdksRoot)) {
            List<JdkDirectory> directories = paths.filter(Files::isDirectory)
                    .map(path -> new JdkDirectory(path.getFileName().toString(), path))
                    .collect(Collectors.toList());
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

    @Override
    protected ObjectAssert<JdkDirectory> toAssert(JdkDirectory value, String description) {
        return new ObjectAssert<>(value).as(description);
    }

    @Override
    protected JdkDirectoriesAssert newAbstractIterableAssert(Iterable<? extends JdkDirectory> iterable) {
        List<JdkDirectory> list = new ArrayList<>();
        iterable.forEach(list::add);
        return new JdkDirectoriesAssert(list);
    }
}
