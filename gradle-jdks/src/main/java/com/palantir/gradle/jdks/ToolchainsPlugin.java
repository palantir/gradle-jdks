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

import com.palantir.baseline.plugins.javaversions.BaselineJavaVersionExtension;
import com.palantir.baseline.plugins.javaversions.BaselineJavaVersionsExtension;
import com.palantir.baseline.plugins.javaversions.ChosenJavaVersion;
import com.palantir.gradle.ideaconfiguration.IdeaConfigurationExtension;
import com.palantir.gradle.ideaconfiguration.IdeaConfigurationPlugin;
import com.palantir.gradle.jdks.enablement.GradleJdksEnablement;
import com.palantir.gradle.jdks.flow.ToolchainFailureFlowActionsPlugin;
import com.palantir.gradle.utils.environmentvariables.EnvironmentVariables;
import com.palantir.platform.GradleOperatingSystem;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.wrapper.Wrapper;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.util.GradleVersion;

public abstract class ToolchainsPlugin implements Plugin<Project> {

    private static final String GRADLE_JDK_GROUP = "Gradle JDK";

    @Nested
    protected abstract GradleOperatingSystem getOperatingSystem();

    @Override
    public final void apply(Project rootProject) {
        if (!GradleJdksEnablement.isGradleJdkSetupEnabled(
                getOperatingSystem().getOperatingSystem().get(),
                rootProject.getProjectDir().toPath())) {
            throw new RuntimeException(
                    "Cannot apply `com.palantir.jdks.settings` without enabling palantir.jdk.setup.enabled");
        }
        if (!isGradleVersionSupported()) {
            throw new RuntimeException(String.format(
                    "Cannot apply `com.palantir.jdks.settings` with Gradle version < %s. Please upgrade to a higher "
                            + "Gradle version in order to use the JDK setup.",
                    GradleJdksEnablement.MINIMUM_SUPPORTED_GRADLE_VERSION));
        }
        if (areFlowActionsSupported()) {
            rootProject.getPluginManager().apply(ToolchainFailureFlowActionsPlugin.class);
        }
        rootProject.getPluginManager().apply(LifecycleBasePlugin.class);
        rootProject
                .getLogger()
                .info("Gradle JDK automanagement is enabled. The JDKs used for all subprojects "
                        + "are managed by the configured custom toolchains.");

        rootProject.getPluginManager().apply(IdeaConfigurationPlugin.class);
        IdeaConfigurationExtension extension = rootProject.getExtensions().getByType(IdeaConfigurationExtension.class);
        extension.getExternalDependencies().register("palantir-gradle-jdks", dep -> dep.atLeastVersion("0.44.0"));

        JdkDistributions jdkDistributions = new JdkDistributions();

        JdksExtension jdksExtension = JdksPlugin.extension(rootProject, jdkDistributions);

        rootProject.getPluginManager().withPlugin("com.palantir.baseline-java-versions", unused -> {
            BaselineJavaVersionsExtension baselineJavaVersionsExtension =
                    rootProject.getExtensions().getByType(BaselineJavaVersionsExtension.class);

            baselineJavaVersionsExtension.getSetupJdkToolchains().set(false);
            jdksExtension.jdkMajorVersionsToUse().add(jdksExtension.getDaemonTarget());

            try {
                // We use reflection here to avoid having to bump baseline to latest to get this out.
                // This is because there are quite a few stragglers and I'd rather not delay using this.
                // Once enough time has passed and baseline is generally high enough, we should just bump
                // the baseline dep and use the code directly.
                Method allJavaVersionsUsed =
                        BaselineJavaVersionsExtension.class.getDeclaredMethod("allJavaVersionsUsed");
                jdksExtension.jdkMajorVersionsToUse().addAll((Provider<Set<JavaLanguageVersion>>)
                        allJavaVersionsUsed.invoke(baselineJavaVersionsExtension));
            } catch (InvocationTargetException | IllegalAccessException e) {
                throw new RuntimeException(
                        "Failed to invoke BaselineJavaVersionsExtension#allJavaVersionsUsed despite it existing", e);
            } catch (NoSuchMethodException e) {
                gatherAllMajorVersionsUsedFromBaselineJavaVersions(
                        rootProject, jdksExtension.jdkMajorVersionsToUse(), baselineJavaVersionsExtension);
            }
        });

        TaskProvider<Wrapper> wrapperTask = rootProject.getTasks().named("wrapper", Wrapper.class);

        TaskProvider<GenerateGradleJdksConfigsTask> generateGradleJdkConfigs = rootProject
                .getTasks()
                .register("generateGradleJdkConfigs", GenerateGradleJdksConfigsTask.class, task -> {
                    task.getOutputGradleDirectory()
                            .set(rootProject.getLayout().getProjectDirectory().dir("gradle"));
                });
        TaskProvider<CheckGradleJdksConfigsTask> checkGradleJdkConfigs = rootProject
                .getTasks()
                .register("checkGradleJdkConfigs", CheckGradleJdksConfigsTask.class, task -> {
                    task.getInputGradleDirectory()
                            .set(generateGradleJdkConfigs
                                    .get()
                                    .getOutputGradleDirectory()
                                    .getLocationOnly()
                                    .get());
                    task.getDummyOutputFile()
                            .set(rootProject.getLayout().getBuildDirectory().file("checkGradleJdkConfigs"));
                });

        rootProject.getTasks().withType(GradleJdksConfigs.class).configureEach(task -> {
            task.getDaemonJavaVersion().set(jdksExtension.getDaemonTarget());
            task.getJavaVersionToJdkDistros()
                    .putAll(rootProject.provider(() -> JdkDistributionConfigurator.getJavaVersionToJdkDistros(
                            rootProject,
                            jdkDistributions,
                            jdksExtension,
                            task.getIncludeAllJdks().get(),
                            task.getIncludeJavaMajorVersions().get())));
            task.getCaCerts().putAll(jdksExtension.getCaCerts());
        });

        @SuppressWarnings("for-rollout:TaskDependsOn")
        TaskProvider<GradleWrapperPatcher> wrapperPatcherTask = rootProject
                .getTasks()
                .register("wrapperJdkPatcher", GradleWrapperPatcher.class, task -> {
                    task.getGenerate().set(true);
                    task.dependsOn(generateGradleJdkConfigs);
                });
        TaskProvider<GradleWrapperPatcher> checkWrapperPatcherTask = rootProject
                .getTasks()
                .register("checkWrapperJdkPatcher", GradleWrapperPatcher.class, task -> {
                    task.getGenerate().set(false);
                });

        rootProject.getTasks().withType(GradleWrapperPatcher.class).configureEach(task -> {
            task.getOriginalGradlewScript()
                    .fileProvider(rootProject.provider(() -> wrapperTask.get().getScriptFile()));
            task.getBuildDir().set(task.getTemporaryDir());
            task.getPatchedGradlewScript()
                    .set(rootProject.file(rootProject.getRootDir().toPath().resolve("gradlew")));
        });
        wrapperTask.configure(task -> {
            task.finalizedBy(wrapperPatcherTask);
        });

        TaskProvider<Task> checkJdksLifecycle = rootProject.getTasks().register("checkGradleJdks", Task.class, task -> {
            task.setDescription("Lifecycle task that checks the Gradle JDK configurations.");
            task.setGroup(GRADLE_JDK_GROUP);
            task.dependsOn(checkGradleJdkConfigs, checkWrapperPatcherTask);
        });

        rootProject
                .getTasks()
                .named(LifecycleBasePlugin.CHECK_TASK_NAME)
                .configure(check -> check.dependsOn(checkJdksLifecycle));

        registerSetupJdksTasks(rootProject, generateGradleJdkConfigs, wrapperPatcherTask, checkJdksLifecycle);
    }

    private static void registerSetupJdksTasks(
            Project rootProject,
            TaskProvider<GenerateGradleJdksConfigsTask> generateGradleJdkConfigs,
            TaskProvider<GradleWrapperPatcher> wrapperPatcherTask,
            TaskProvider<Task> checkJdksLifecycle) {
        EnvironmentVariables environmentVariables = rootProject.getObjects().newInstance(EnvironmentVariables.class);

        TaskProvider<EnsureGradlePropertiesTask> ensureGradleProperties = rootProject
                .getTasks()
                .register("ensureGradleJdkProperties", EnsureGradlePropertiesTask.class, task -> {
                    task.setDescription(
                            "Ensures gradle.properties has auto-detect and auto-download disabled for gradle-jdks.");
                    task.setGroup(GRADLE_JDK_GROUP);
                    task.getGradlePropertiesFile()
                            .set(rootProject.getLayout().getProjectDirectory().file("gradle.properties"));
                });

        @SuppressWarnings("TaskDependsOn")
        TaskProvider<SetupJdksTask> unused = rootProject
                .getTasks()
                .register("setupJdks", SetupJdksTask.class, setupJdksTask -> {
                    setupJdksTask.setDescription("Configures the gradle JDK setup.");
                    setupJdksTask.setGroup(GRADLE_JDK_GROUP);
                    setupJdksTask.dependsOn(ensureGradleProperties);
                    setupJdksTask
                            .getGradleJdksSetupScript()
                            .fileProvider(generateGradleJdkConfigs.map(task -> task.getOutputGradleDirectory()
                                    .file("gradle-jdks-setup.sh")
                                    .get()
                                    .getAsFile()));

                    if (!environmentVariables.isInTestMode().get()) {
                        setupJdksTask
                                .getGradlewScript()
                                .fileProvider(wrapperPatcherTask.map(task ->
                                        task.getPatchedGradlewScript().get().getAsFile()));
                    }
                });

        rootProject.getTasks().named("javaToolchains").configure(task -> {
            task.mustRunAfter(checkJdksLifecycle);
        });
    }

    private void gatherAllMajorVersionsUsedFromBaselineJavaVersions(
            Project rootProject,
            SetProperty<JavaLanguageVersion> jdkMajorVersionsToUse,
            BaselineJavaVersionsExtension baselineJavaVersionsExtension) {

        jdkMajorVersionsToUse.add(baselineJavaVersionsExtension.libraryTarget());
        jdkMajorVersionsToUse.add(asJavaLanguageVersion(baselineJavaVersionsExtension.runtime()));
        jdkMajorVersionsToUse.add(asJavaLanguageVersion(baselineJavaVersionsExtension.distributionTarget()));

        rootProject.subprojects(proj -> proj.getPluginManager()
                .withPlugin("com.palantir.baseline-java-version", unused -> {
                    BaselineJavaVersionExtension projectVersions =
                            proj.getExtensions().getByType(BaselineJavaVersionExtension.class);
                    jdkMajorVersionsToUse.add(asJavaLanguageVersion(projectVersions.target()));
                    jdkMajorVersionsToUse.add(asJavaLanguageVersion(projectVersions.runtime()));
                }));
    }

    private static boolean isGradleVersionSupported() {
        return GradleVersion.current()
                        .compareTo(GradleVersion.version(GradleJdksEnablement.MINIMUM_SUPPORTED_GRADLE_VERSION))
                >= 0;
    }

    private static boolean areFlowActionsSupported() {
        return GradleVersion.current().compareTo(GradleVersion.version("8.6")) >= 0;
    }

    private static Provider<JavaLanguageVersion> asJavaLanguageVersion(Property<ChosenJavaVersion> version) {
        return version.map(ChosenJavaVersion::javaLanguageVersion);
    }
}
