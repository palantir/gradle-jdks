/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.gradle.jdks;

import groovy.lang.Closure;
import org.gradle.api.Action;

final class ConfigureUtil {
    public static <T> Action<T> toAction(Closure closure) {
        return item -> {
            closure.setDelegate(item);
            closure.run();
        };
    }

    private ConfigureUtil() {}
}
