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

package com.palantir.gradle.jdks.setup

import com.google.common.base.Splitter
import com.google.common.collect.Iterables
import com.palantir.gradle.jdks.AmazonCorrettoJdkDistribution
import com.palantir.gradle.jdks.Arch
import com.palantir.gradle.jdks.CurrentArch
import com.palantir.gradle.jdks.CurrentOs
import com.palantir.gradle.jdks.JdkPath
import com.palantir.gradle.jdks.JdkRelease
import com.palantir.gradle.jdks.Os
import nebula.test.IntegrationSpec
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class GradleJdkPatcherIntegrationTest extends IntegrationSpec {

    private static final List<String> GRADLE_VERSIONS = List.of("7.6.2", "8.4")
    private static final AmazonCorrettoJdkDistribution CORRETTO_JDK_DISTRIBUTION = new AmazonCorrettoJdkDistribution();
    private static final String CORRETTO_DISTRIBUTION_URL_ENV = "CORRETTO_DISTRIBUTION_URL";
    private static final String JDK_17_VERSION = "17.0.9.8.1";
    private static final String AMAZON_ROOT_CA_1_SERIAL = "143266978916655856878034712317230054538369994"
    private static final String AMAZON_CERT_ALIAS = "AmazonRootCA1Test";

    @TempDir
    private Path workingDir;

    def setup() {
        // language=gradle
        buildFile << '''
            repositories {
                mavenLocal()
                mavenCentral() { metadataSources { mavenPom(); ignoreGradleMetadataRedirection() } }
            }

            apply plugin: 'com.palantir.jdks'
            
        '''.stripIndent(true)


        // language=gradle
        def subprojectDir = addSubproject 'subproject', '''
            apply plugin: 'java-library'
            
            task printJavaVersion(type: JavaExec) {
                classpath = sourceSets.main.runtimeClasspath
                mainClass = 'foo.PrintJavaVersion'
                logging.captureStandardOutput LogLevel.LIFECYCLE
                logging.captureStandardError LogLevel.LIFECYCLE
            }
        '''.stripIndent(true)

        // language=java
        writeJavaSourceFile '''
            package foo;

            public final class PrintJavaVersion {
                public static void main(String... args) {
                    System.out.printf(
                            "version: %s, vendor: %s%n",
                            System.getProperty("java.version"),
                            System.getProperty("java.vendor"));
                }
            }
        '''.stripIndent(true), subprojectDir
    }


    def '#gradleVersionNumber: patches gradleWrapper to set up JDK'() {

        file('gradle.properties') << 'gradle.jdk.setup.enabled=true'

        gradleVersion = gradleVersionNumber
        populateGradleFiles(JDK_17_VERSION)

        when:
        def output = runTasksSuccessfully('wrapper')
        ProcessBuilder processBuilder = new ProcessBuilder()
                .command(List.of(String.format("./gradlew", "wrapper", "-v")))
                .directory(projectDir).redirectErrorStream(true);
        processBuilder.environment().put("GRADLE_USER_HOME", workingDir.toAbsolutePath().toString());
        Process process = processBuilder.start();
        String wrapperResult = CommandRunner.readAllInput(process.getInputStream());
        process.waitFor();

        then:
        output.standardOutput.contains("Gradle JDK setup is enabled, patching the gradle wrapper files")
        Path gradleJdksPath = workingDir.resolve("gradle-jdks")
        String expectedLocalPath = gradleJdksPath.resolve(getLocalFilename(JDK_17_VERSION))
        wrapperResult.contains(String.format("Successfully installed JDK distribution, setting JAVA_HOME to %s", expectedLocalPath))
        wrapperResult.contains(String.format("JAVA_HOME=%s", expectedLocalPath))
        file("gradlew").text.contains("source gradle/gradle-jdks-setup.sh")

        where:
        gradleVersionNumber << GRADLE_VERSIONS
    }


    private void populateGradleFiles(String jdkVersion) {
        String jdkMajorVersion = Iterables.get(Splitter.on('.').split(jdkVersion), 0);

        file('gradle/gradle-jdk-major-version') << jdkMajorVersion + "\n"
        directory('gradle/jdks')

        Files.copy(
                Path.of(String.format(
                        "../gradle-jdks-setup/build/libs/gradle-jdks-setup-%s.jar",
                        System.getenv().get("PROJECT_VERSION"))),
                projectDir.toPath().resolve("gradle/jdks/gradle-jdks-setup.jar"));

        Files.copy(
                Path.of("../gradle-jdks-setup/src/main/resources/gradle-jdks-setup.sh"),
                projectDir.toPath().resolve("gradle/gradle-jdks-setup.sh"));

        Os os = CurrentOs.get();
        Arch arch = CurrentArch.get();

        directory(String.format('gradle/jdks/%s/%s/%s', jdkMajorVersion, os, arch))

        JdkPath jdkPath = CORRETTO_JDK_DISTRIBUTION.path(
                JdkRelease.builder().version(jdkVersion).os(os).arch(arch).build());
        String correttoDistributionUrl = Optional.ofNullable(System.getenv(CORRETTO_DISTRIBUTION_URL_ENV))
                .orElseGet(CORRETTO_JDK_DISTRIBUTION::defaultBaseUrl);
        String downloadUrl = String.format(
                String.format("%s/%s.%s\n", correttoDistributionUrl, jdkPath.filename(), jdkPath.extension()))
        String localFilename = getLocalFilename(jdkVersion);
        file(String.format('gradle/jdks/%s/%s/%s/download-url', jdkMajorVersion, CurrentOs.get(), CurrentArch.get())) << downloadUrl
        file(String.format('gradle/jdks/%s/%s/%s/local-path', jdkMajorVersion, CurrentOs.get(), CurrentArch.get())) <<  localFilename

        directory('gradle/certs')
        file(String.format('gradle/certs/%s.serial-number', AMAZON_CERT_ALIAS)) << AMAZON_ROOT_CA_1_SERIAL
    }

    private String getLocalFilename(String jdkVersion) {
       return  String.format("amazon-corretto-%s-jdkPluginIntegrationTest\n", jdkVersion);
    }
}
