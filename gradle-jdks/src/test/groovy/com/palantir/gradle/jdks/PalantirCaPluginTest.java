/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
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

import java.io.IOException;
import org.junit.jupiter.api.Test;

class PalantirCaPluginTest {
    @Test
    void does_not_explode_when_given_certs_with_duplicate_extensions() throws IOException {
        byte[] certWithDuplicateExtensions = getClass()
                .getClassLoader()
                .getResourceAsStream("apple-kdc-cert-with-duplicate-extension.pem")
                .readAllBytes();

        assertThat(PalantirCaPlugin.parseCerts(certWithDuplicateExtensions)).isEmpty();
    }
}
