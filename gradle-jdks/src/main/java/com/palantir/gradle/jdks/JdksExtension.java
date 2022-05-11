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

import com.palantir.baseline.plugins.javaversions.LazilyConfiguredMapping;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import java.util.Optional;
import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.MapProperty;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

public abstract class JdksExtension {
    private final LazilyConfiguredMapping<JdkDistributionName, JdkDistributionExtension, Void> jdkDistributions;
    private final LazilyConfiguredMapping<JavaLanguageVersion, JdkExtension, Project> jdks;

    public JdksExtension() {
        this.jdkDistributions =
                new LazilyConfiguredMapping<>(() -> getObjectFactory().newInstance(JdkDistributionExtension.class));
        this.jdks = new LazilyConfiguredMapping<>(() -> getObjectFactory().newInstance(JdkExtension.class));
    }

    public abstract MapProperty<String, String> getCaCerts();

    public abstract DirectoryProperty getJdkStorageLocation();

    @Inject
    protected abstract ObjectFactory getObjectFactory();

    public final void jdks(LazyJdks lazyJdks) {
        jdks.put(lazyJdks::configureJdkFor);
    }

    public final void jdk(JavaLanguageVersion javaLanguageVersion, Action<JdkExtension> action) {
        jdks.put(javaLanguageVersion, action);
    }

    @SuppressWarnings("RawTypes")
    public final void jdk(int javaLanguageVersion, @DelegatesTo(JdkExtension.class) Closure closure) {
        jdk(JavaLanguageVersion.of(javaLanguageVersion), ConfigureUtil.toAction(closure));
    }

    public final void jdkDistributions(LazyJdkDistributions lazyJdkDistributions) {
        jdkDistributions.put((jdkDistributionName, _ignored) ->
                lazyJdkDistributions.configureJdkDistributionFor(jdkDistributionName));
    }

    public final void jdkDistribution(
            JdkDistributionName jdkDistributionName, Action<JdkDistributionExtension> action) {
        jdkDistributions.put(jdkDistributionName, action);
    }

    @SuppressWarnings("RawTypes")
    public final void jdkDistribution(
            String distributionName, @DelegatesTo(JdkDistributionExtension.class) Closure closure) {
        jdkDistribution(JdkDistributionName.fromStringThrowing(distributionName), ConfigureUtil.toAction(closure));
    }

    public final JdkDistributionExtension jdkDistributionFor(JdkDistributionName jdkDistributionName) {
        return jdkDistributions
                .get(jdkDistributionName, null)
                .orElseThrow(() -> new RuntimeException(
                        String.format("No configuration for JdkDistribution " + jdkDistributionName)));
    }

    public final Optional<JdkExtension> jdkFor(JavaLanguageVersion javaLanguageVersion, Project project) {
        return jdks.get(javaLanguageVersion, project);
    }

    public interface LazyJdkDistributions {
        Optional<Action<JdkDistributionExtension>> configureJdkDistributionFor(JdkDistributionName jdkDistributionName);
    }

    public interface LazyJdks {
        Optional<Action<JdkExtension>> configureJdkFor(JavaLanguageVersion javaLanguageVersion, Project project);
    }
}
