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

package com.palantir.gradle.jdks.toolchains;

import java.nio.file.Path;
import java.util.Optional;
import org.gradle.jvm.toolchain.JavaToolchainDownload;
import org.gradle.jvm.toolchain.JavaToolchainRequest;
import org.gradle.jvm.toolchain.JavaToolchainResolver;

public abstract class JavaToolchainResolverImplementation implements JavaToolchainResolver {

    @Override
    public Optional<JavaToolchainDownload> resolve(JavaToolchainRequest request) {
        return Optional.of(JavaToolchainDownload.fromUri(
                Path.of("/Users/crogoz/.gradle/gradle-jdks/amazon-corretto-17.0.3.6.1-todo-crogoz")
                        .toUri()));
    }
}
