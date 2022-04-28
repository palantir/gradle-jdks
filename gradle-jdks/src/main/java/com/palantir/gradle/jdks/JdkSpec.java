/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.gradle.jdks;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.immutables.value.Value;

@Value.Immutable
interface JdkSpec {
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
