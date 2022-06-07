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

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class SynchronizedInterface {
    public static <T> T synchronizeAllInterfaceMethods(Class<T> returnInterface, T original) {
        Object sync = new Object();
        return returnInterface.cast(Proxy.newProxyInstance(
                original.getClass().getClassLoader(),
                allInterfaces(original.getClass()).distinct().toArray(Class[]::new),
                (_proxy, method, args) -> {
                    synchronized (sync) {
                        return method.invoke(original, args);
                    }
                }));
    }

    private static Stream<Class<?>> allInterfaces(Class<?> clazz) {
        Set<Class<?>> superclasses = Stream.concat(
                        Optional.ofNullable(clazz.getSuperclass()).stream(), Arrays.stream(clazz.getInterfaces()))
                .collect(Collectors.toSet());

        return Stream.concat(
                superclasses.stream().filter(Class::isInterface),
                superclasses.stream().flatMap(SynchronizedInterface::allInterfaces));
    }

    private SynchronizedInterface() {}
}
