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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class OsTest {
    @Test
    void modern_glibc_system_is_identified() {
        assertThat(Os.linuxLibcFromLdd(execInDocker("ubuntu:20.04"))).isEqualTo(Os.LINUX_GLIBC);
    }

    @Test
    void old_glibc_system_is_identified() {
        assertThat(Os.linuxLibcFromLdd(execInDocker("centos:7"))).isEqualTo(Os.LINUX_GLIBC);
    }

    @Test
    void musl_system_is_identified() {
        assertThat(Os.linuxLibcFromLdd(execInDocker("alpine:3.16.0"))).isEqualTo(Os.LINUX_MUSL);
    }

    private UnaryOperator<List<String>> execInDocker(String dockerImage) {
        return args -> Stream.concat(Stream.of("docker", "run", "--rm", dockerImage), args.stream())
                .collect(Collectors.toList());
    }
}
