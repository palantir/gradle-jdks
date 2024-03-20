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

package com.palantir.gradle.jdks.setup;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public class CaResourcesTest {

    @Test
    void handles_just_whitespace_truststore() {
        assertThat(CaResources.parseCerts("         \n      ".getBytes(StandardCharsets.UTF_8)))
                .isEmpty();
    }

    @Test
    void handles_whitespace_and_comments_between_certs() throws IOException {

        assertThat(CaResources.parseCerts(certsFromResources("amazon-cas-with-whitespace-between.pem")))
                .hasSize(3);
    }

    @Test
    void does_not_explode_when_given_certs_with_duplicate_extensions() throws IOException {
        assertThat(CaResources.parseCerts(certsFromResources("apple-kdc-cert-with-duplicate-extension.pem")))
                .isEmpty();
    }

    @Test
    void does_not_explode_when_given_certs_with_incorrect_vesion() throws IOException {
        assertThat(CaResources.parseCerts(certsFromResources("strongloop-cert-with-v3-extensions-but-v1-version.pem")))
                .isEmpty();
    }

    private byte[] certsFromResources(String name) throws IOException {
        return getClass().getClassLoader().getResourceAsStream(name).readAllBytes();
    }
}
