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

package com.palantir.gradle.jdks


import com.palantir.gradle.jdks.setup.AliasContentCert
import com.palantir.gradle.jdks.setup.CaResources
import com.palantir.gradle.jdks.setup.StdLogger
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors

class GenerateGradleJdkConfigsIntegrationTest extends GradleJdkIntegrationTest {

    @TempDir
    Path workingDir

    def '#gradleVersionNumber: generates the latest jdk configs'() {
        setupJdksLatest()
        applyBaselineJavaVersions()

        file('gradle.properties') << 'palantir.jdk.setup.enabled=true'
        gradleVersion = gradleVersionNumber

        buildFile << '''
            javaVersions {
                libraryTarget = '11'
                distributionTarget = '17'
                runtime = '17'
            }
        '''.stripIndent(true)

        when:
        runTasksSuccessfully("wrapper", '--info')

        then:
        checkJdksVersions(projectDir, Set.of("11", "17", "21"))
        Files.exists(projectDir.toPath().resolve("gradle/gradle-daemon-jdk-version"))
        Path jarInProject = projectDir.toPath().resolve("gradle/gradle-jdks-setup.jar");
        Path originalJar = Path.of("build/resources/main/gradle-jdks-setup.jar");
        jarInProject.text == originalJar.text
        Path scriptPath = projectDir.toPath().resolve("gradle/gradle-jdks-setup.sh");
        Files.isExecutable(scriptPath)
        Path certFile = projectDir.toPath().resolve("gradle/certs/Palantir3rdGenRootCa.serial-number")
        Optional<AliasContentCert> maybePalantirCerts = new CaResources(new StdLogger()).readPalantirRootCaFromSystemTruststore()
        if (maybePalantirCerts.isPresent()) {
            Files.readString(certFile).trim() == "18126334688741185161"
        } else {
            !Files.exists(certFile)
        }

        when:
        def checkResult = runGradlewTasksSuccessfully('check', '--info')

        then:
        !checkResult.contains(':checkWrapperPatcher UP-TO-DATE')
        !checkResult.contains(':checkGradleJdkConfigs UP-TO-DATE')

        when:
        String os = CurrentOs.get().uiName()
        String arch = CurrentArch.get().uiName()
        String jdk17Path = "gradle/jdks/17/${os}/${arch}/local-path"
        Files.write(projectDir.toPath().resolve(jdk17Path), "new-path\n".getBytes())

        def checkGradleJdkConfigs = runGradlewTasksWithFailure('check', '--info')
        def notUpToDateGenerate = runGradlewTasksSuccessfully('generateGradleJdkConfigs', '--info')

        then:
        !checkGradleJdkConfigs.contains("checkGradleJdkConfigs UP-TO-DATE")
        checkGradleJdkConfigs.contains("Gradle JDK configuration file `${jdk17Path}` is out of date")
        !notUpToDateGenerate.contains(':generateGradleJdkConfigs UP-TO-DATE')

        where:
        gradleVersionNumber << [GRADLE_7VERSION, GRADLE_8VERSION]
    }

    def '#gradleVersionNumber: fails if the jdk version is not configured'() {
        setupJdksHardcodedVersions()
        applyBaselineJavaVersions()

        gradleVersion = gradleVersionNumber

        buildFile << '''
            javaVersions {
                distributionTarget = '15'
            }
        '''.stripIndent(true)
        writeHelloWorld(projectDir)
        file('gradle.properties') << 'palantir.jdk.setup.enabled=true'
        runTasksSuccessfully("wrapper")

        when:
        def result = runGradlewTasksWithFailure("compileJava")

        then:
        switch (gradleVersionNumber) {
            case GRADLE_7VERSION:
                result.contains("No compatible toolchains found for request specification: {languageVersion=15, vendor=any, implementation=vendor-specific} (auto-detect false, auto-download false).")
                break;
            case GRADLE_8VERSION:
                result.contains(" No matching toolchains found for requested specification: {languageVersion=15, vendor=any, implementation=vendor-specific}")
                        && result.contains("No locally installed toolchains match and toolchain auto-provisioning is not enabled.")
                break;
            default:
                throw new RuntimeException(String.format("Unexpected gradleVersionNumber", gradleVersionNumber))
        }

        where:
        gradleVersionNumber << [GRADLE_7VERSION, GRADLE_8VERSION]

    }

    private static void checkJdksVersions(File projectDir, Set<String> versions) {
        assert Files.list(projectDir.toPath().resolve("gradle/jdks")).filter(Files::isDirectory)
                .map(path -> path.getFileName().toString())
                .collect(Collectors.toSet()) == versions
        versions.stream().findFirst().ifPresent(version -> {
            assert Files.exists(projectDir.toPath().resolve(String.format("gradle/jdks/%s/%s/%s/download-url", version, CurrentOs.get().uiName(), CurrentArch.get().uiName())))
            assert Files.exists(projectDir.toPath().resolve(String.format("gradle/jdks/%s/%s/%s/local-path", version, CurrentOs.get().uiName(), CurrentArch.get().uiName())))
        })
    }


    @Override
    Path workingDir() {
        return workingDir
    }
}
