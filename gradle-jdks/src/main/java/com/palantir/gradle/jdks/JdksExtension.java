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

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

public abstract class JdksExtension {
    protected abstract MapProperty<JdkDistributionName, JdkDistributionExtension> getJdkDistributions();

    protected abstract MapProperty<JavaLanguageVersion, JdkExtension> getJdks();

    public abstract MapProperty<String, String> getCaCerts();

    public abstract DirectoryProperty getJdkStorageLocation();

    @Inject
    protected abstract ProviderFactory getProviderFactory();

    @Inject
    protected abstract ObjectFactory getObjectFactory();

    public final void jdk(JavaLanguageVersion javaLanguageVersion, Action<JdkExtension> action) {
        JdkExtension jdkExtension = getJdks()
                .getting(javaLanguageVersion)
                .orElse(getProviderFactory().provider(() -> {
                    JdkExtension newJdkExtension = getObjectFactory().newInstance(JdkExtension.class);
                    getJdks().put(javaLanguageVersion, newJdkExtension);
                    return newJdkExtension;
                }))
                .get();

        action.execute(jdkExtension);
    }

    @SuppressWarnings("RawTypes")
    public final void jdk(int javaLanguageVersion, @DelegatesTo(JdkExtension.class) Closure closure) {
        jdk(JavaLanguageVersion.of(javaLanguageVersion), ConfigureUtil.toAction(closure));
    }

    public final void jdkDistribution(
            JdkDistributionName jdkDistributionName, Action<JdkDistributionExtension> action) {
        action.execute(getJdkDistributions().getting(jdkDistributionName).get());
    }

    @SuppressWarnings("RawTypes")
    public final void jdkDistribution(
            String distributionName, @DelegatesTo(JdkDistributionExtension.class) Closure closure) {
        jdkDistribution(JdkDistributionName.fromStringThrowing(distributionName), ConfigureUtil.toAction(closure));
    }
}
