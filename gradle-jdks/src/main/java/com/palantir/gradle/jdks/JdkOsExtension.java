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

import com.palantir.gradle.jdks.JdkRelease.Arch;
import com.palantir.gradle.utils.lazilyconfiguredmapping.LazilyConfiguredMapping;
import java.util.Optional;
import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

public abstract class JdkOsExtension {
    public abstract Property<String> getJdkVersion();

    @Inject
    protected abstract ObjectFactory getObjectFactory();

    private final LazilyConfiguredMapping<Arch, JdkOsArchExtension, Void> jdkOsArchExtensions =
            new LazilyConfiguredMapping<>(() -> {
                JdkOsArchExtension extension = getObjectFactory().newInstance(JdkOsArchExtension.class);
                extension.getJdkVersion().set(getJdkVersion());
                return extension;
            });

    final JdkOsArchExtension jdkFor(Arch arch) {
        return jdkOsArchExtensions.get(arch, null);
    }

    public final void arch(Arch arch, Action<JdkOsArchExtension> action) {
        jdkOsArchExtensions.put(arch, action);
    }
}
