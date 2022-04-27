/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.gradle.jdks;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JdkDistributionNameTest {
    @Test
    void parses_names_correctly() {
        assertThat(JdkDistributionName.fromStringThrowing("azul-zulu")).isEqualTo(JdkDistributionName.AZUL_ZULU);
    }
}
