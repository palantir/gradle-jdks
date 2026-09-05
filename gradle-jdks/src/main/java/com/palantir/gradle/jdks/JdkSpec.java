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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.immutables.value.Value;

@Value.Immutable
public interface JdkSpec {
    JdkDistributionName distributionName();

    JdkRelease release();

    CaCerts caCerts();

    default String consistentShortHash() {
        String infoBlock = String.format(
                String.join("\n", "Distribution: %s", "Version: %s", "Os: %s", "Arch: %s", "CaCerts: %s"),
                distributionName().uiName(),
                release().version(),
                release().os(),
                release().arch(),
                caCerts().combinedInSortedOrder());

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(infoBlock.getBytes(StandardCharsets.UTF_8));
            // We only want a short hash, so just parse the first part of the digest into a hex string
            return Long.toHexString(ByteBuffer.wrap(bytes).getLong());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Could not find SHA-256 hash algorithm", e);
        }
    }

    class Builder extends ImmutableJdkSpec.Builder {}

    static Builder builder() {
        return new Builder();
    }
}
