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
import java.util.Map;
import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

public abstract class JdksExtension {
    private final DelayedConfigurableMap<JdkDistributionName, JdkDistributionExtension> jdkDistributions;
    private final DelayedConfigurableMap<JavaLanguageVersion, JdkExtension> jdks;

    public JdksExtension() {
        this.jdkDistributions = new DelayedConfigurableMap<>(
                getProviderFactory(), () -> getObjectFactory().newInstance(JdkDistributionExtension.class));
        this.jdks = new DelayedConfigurableMap<>(
                getProviderFactory(), () -> getObjectFactory().newInstance(JdkExtension.class));
    }

    public abstract MapProperty<String, String> getCaCerts();

    public abstract DirectoryProperty getJdkStorageLocation();

    @Inject
    protected abstract ProviderFactory getProviderFactory();

    @Inject
    protected abstract ObjectFactory getObjectFactory();

    public final void jdks(Provider<Map<JavaLanguageVersion, Action<JdkExtension>>> actions) {
        jdks.configureLater(actions);
    }

    public final void jdk(JavaLanguageVersion javaLanguageVersion, Action<JdkExtension> action) {
        jdks.configure(javaLanguageVersion, action);
    }

    @SuppressWarnings("RawTypes")
    public final void jdk(int javaLanguageVersion, @DelegatesTo(JdkExtension.class) Closure closure) {
        jdk(JavaLanguageVersion.of(javaLanguageVersion), ConfigureUtil.toAction(closure));
    }

    public final void jdkDistributions(Provider<Map<JdkDistributionName, Action<JdkDistributionExtension>>> actions) {
        jdkDistributions.configureLater(actions);
    }

    public final void jdkDistribution(
            JdkDistributionName jdkDistributionName, Action<JdkDistributionExtension> action) {
        jdkDistributions.configure(jdkDistributionName, action);
    }

    @SuppressWarnings("RawTypes")
    public final void jdkDistribution(
            String distributionName, @DelegatesTo(JdkDistributionExtension.class) Closure closure) {
        jdkDistribution(JdkDistributionName.fromStringThrowing(distributionName), ConfigureUtil.toAction(closure));
    }

    public final Provider<Map<JdkDistributionName, JdkDistributionExtension>> getJdkDistributions() {
        return jdkDistributions.getAsProvider();
    }

    public final Provider<Map<JavaLanguageVersion, JdkExtension>> getJdks() {
        return jdks.getAsProvider();
    }
}
