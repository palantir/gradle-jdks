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

import com.palantir.gradle.jdks.json.JdksInfoJson;
import com.palantir.gradle.utils.lazilyconfiguredmapping.LazilyConfiguredMapping;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import java.util.Optional;
import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

public abstract class JdksExtension {
    private final LazilyConfiguredMapping<JdkDistributionName, JdkDistributionExtension, Void> jdkDistributions;
    private final LazilyConfiguredMapping<JavaLanguageVersion, JdkExtension, Project> jdks;
    private final SetProperty<JavaLanguageVersion> jdksInUse;
    private final ListProperty<JavaLanguageVersion> configuredJavaVersions;
    private final MapProperty<String, String> caCerts;
    private final DirectoryProperty jdkStorageLocation;
    private final Property<JavaLanguageVersion> daemonTarget;

    @Inject
    protected abstract ObjectFactory getObjectFactory();

    public JdksExtension(Project project) {
        this.jdkDistributions =
                new LazilyConfiguredMapping<>(() -> getObjectFactory().newInstance(JdkDistributionExtension.class));
        this.jdks = new LazilyConfiguredMapping<>(() -> getObjectFactory().newInstance(JdkExtension.class));
        this.jdksInUse = getObjectFactory().setProperty(JavaLanguageVersion.class);
        this.configuredJavaVersions = getObjectFactory().listProperty(JavaLanguageVersion.class);
        this.jdksInUse.convention(project.provider(configuredJavaVersions::get));
        // there is an extremely subtle race condition whereby a property can be mid finalization in one
        // worker thread, and being read from another worker thread, and if the finalization happens just
        // as the other thread is already reading, it can cause very infrequent build failure. While
        // the build failure itself is extremely rare, on very large builds with larger worker pools, these
        // errors occur enough times that it is significant
        // tracing the source of this race condition is difficult, so to just mitigate the problem,
        // these two properties have had their methods synchronized (which arguably gradle should probably
        // do since it's known that Property access is racy)
        this.caCerts = SynchronizedInterface.synchronizeAllInterfaceMethods(
                MapProperty.class, getObjectFactory().mapProperty(String.class, String.class));
        this.jdkStorageLocation = SynchronizedInterface.synchronizeAllInterfaceMethods(
                DirectoryProperty.class, getObjectFactory().directoryProperty());
        this.daemonTarget = project.getObjects().property(JavaLanguageVersion.class);

        this.getCaCerts().finalizeValueOnRead();
        this.getJdkStorageLocation().finalizeValueOnRead();
        this.getDaemonTarget().finalizeValueOnRead();
        this.getJdksInUse().finalizeValueOnRead();
    }

    public final Property<JavaLanguageVersion> getDaemonTarget() {
        return daemonTarget;
    }

    public final void setDaemonTarget(String value) {
        getDaemonTarget().set(JavaLanguageVersion.of(value));
    }

    public final void setDaemonTarget(int value) {
        getDaemonTarget().set(JavaLanguageVersion.of(value));
    }

    public final MapProperty<String, String> getCaCerts() {
        return caCerts;
    }

    public final DirectoryProperty getJdkStorageLocation() {
        return jdkStorageLocation;
    }

    public final void jdks(LazyJdks lazyJdks) {
        jdks.put(lazyJdks::configureJdkFor);
    }

    public final SetProperty<JavaLanguageVersion> getJdksInUse() {
        return jdksInUse;
    }

    public final void jdk(JavaLanguageVersion javaLanguageVersion, Action<JdkExtension> action) {
        jdks.put(javaLanguageVersion, action);
        configuredJavaVersions.add(javaLanguageVersion);
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

    public final void fromJson(JdksInfoJson jdksInfo) {
        jdksInfo.jdksPerJavaVersion().forEach((javaVersionAsString, jdkInfo) -> {
            jdk(JavaLanguageVersion.of(javaVersionAsString), jdkExtension -> jdkExtension.fromJson(jdkInfo));
        });
    }

    public final JdkDistributionExtension jdkDistributionFor(JdkDistributionName jdkDistributionName) {
        return jdkDistributions
                .get(jdkDistributionName, null)
                .orElseThrow(() -> new RuntimeException(
                        String.format("No configuration for JdkDistribution " + jdkDistributionName)));
    }

    final Optional<JdkExtension> jdkFor(JavaLanguageVersion javaLanguageVersion, Project project) {
        return jdks.get(javaLanguageVersion, project);
    }

    public interface LazyJdkDistributions {
        Optional<Action<JdkDistributionExtension>> configureJdkDistributionFor(JdkDistributionName jdkDistributionName);
    }

    public interface LazyJdks {
        Optional<Action<JdkExtension>> configureJdkFor(JavaLanguageVersion javaLanguageVersion, Project project);
    }
}
