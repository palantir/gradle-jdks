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

import org.gradle.api.internal.properties.GradleProperties;
import org.gradle.api.internal.provider.DefaultValueSourceProviderFactory;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.isolation.IsolatableFactory;
import org.gradle.internal.service.ServiceLookup;
import org.gradle.process.ExecOperations;

public class NewValueSourceProviderFactory extends DefaultValueSourceProviderFactory {

    public NewValueSourceProviderFactory(
            ListenerManager listenerManager,
            InstantiatorFactory instantiatorFactory,
            IsolatableFactory isolatableFactory,
            GradleProperties gradleProperties,
            ExecOperations execOperations,
            ServiceLookup services) {
        super(listenerManager, instantiatorFactory, isolatableFactory, gradleProperties, execOperations, services);
    }
}
