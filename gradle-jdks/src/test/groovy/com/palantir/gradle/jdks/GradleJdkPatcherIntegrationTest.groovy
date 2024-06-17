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
import java.util.stream.Stream

import static org.junit.jupiter.api.Assertions.assertTrue

class GradleJdkPatcherIntegrationTest extends GradleJdkIntegrationTest {

    @TempDir
    Path workingDir

    def setup() {
        setupJdksHardcodedVersions()
    }

    def '#gradleVersionNumber: successfully generates Gradle JDK setup files'() {
        file('gradle.properties') << 'palantir.jdk.setup.enabled=true'
        gradleVersion = gradleVersionNumber
        String gitignoreContent = """
            #Intelij
            .idea/
            
            # My custom setup
            something else
            another thing
        """.stripIndent(true)
        file(".gitignore") << gitignoreContent

        when: 'running wrapper task'
        def wrapperResult = runTasksSuccessfully('wrapper')

        then: './gradlew file is patched'
        wrapperResult.wasExecuted(':wrapperJdkPatcher')
        !wrapperResult.wasSkipped(':wrapperJdkPatcher')
        wrapperResult.standardOutput.contains("Gradle JDK setup is enabled, patching the gradle wrapper files")
        file("gradlew").text.contains("gradle/gradle-jdks-setup.sh")
        file("gradlew").text.findAll(GradleJdkPatchHelper.PATCH_HEADER).size() == 1
        file("gradlew").text.findAll(GradleJdkPatchHelper.PATCH_FOOTER).size() == 1

        and: './gradlew.bat file is patched'
        file("gradlew.bat").text.contains(Path.of("src/main/resources/batch-patch.bat").text)
        file("gradlew.bat").text.findAll("@rem # >>> Gradle JDK setup >>>").size() == 1
        file("gradlew.bat").text.findAll("@rem # <<< Gradle JDK setup <<<").size() == 1

        and: 'the `gradle/` configuration files are generated'
        wrapperResult.wasExecuted(':generateGradleJdkConfigs')
        !wrapperResult.wasSkipped(':generateGradleJdkConfigs')
        checkJdksVersions(projectDir, Set.of("11", "17", "21"))
        Files.readString(projectDir.toPath().resolve("gradle/gradle-daemon-jdk-version")).trim() == "11"
        /*Path jarInProject = projectDir.toPath().resolve("gradle/gradle-jdks-setup.jar");
        Path originalJar = Path.of("build/resources/main/gradle-jdks-setup.jar");
        jarInProject.text == originalJar.text*/
        Path scriptPath = projectDir.toPath().resolve("gradle/gradle-jdks-setup.sh");
        Files.isExecutable(scriptPath)
        Path certFile = projectDir.toPath().resolve("gradle/certs/Palantir3rdGenRootCa.serial-number")
        Optional<AliasContentCert> maybePalantirCerts = new CaResources(new StdLogger()).readPalantirRootCaFromSystemTruststore()
        if (maybePalantirCerts.isPresent()) {
            Files.readString(certFile).trim() == "18126334688741185161"
        } else {
            !Files.exists(certFile)
        }

        when: 'we are running the patched ./gradlew script. Any task can be used. The setup should be done by the script.'
        def output = runGradlewTasksSuccessfully('setupJdks')

        then: '.idea files are generated'
        output.contains("Gradle JDK setup is enabled, patching the gradle wrapper files")
        Path jdkPropertiesFile = projectDir.toPath().resolve(".idea")
        Path ideaConfigurations = Path.of("../gradle-jdks-setup/src/main/resources/ideaConfigurations")
        try (Stream<Path> gradleJdkConfigurationPath =
                Files.list(ideaConfigurations).filter(Files::isRegularFile)) {
            assertTrue(gradleJdkConfigurationPath.allMatch(
                    path -> Files.exists(jdkPropertiesFile.resolve(path.relativize(ideaConfigurations)))))
        } catch (IOException e) {
            throw new RuntimeException("Could not list the ideaConfigurations files", e);
        }

        then: 'gradle.properties files contain the jdk properties'
        Properties properties = new Properties();
        properties.load(new FileInputStream(projectDir.toPath().resolve("gradle.properties").toFile()))
        properties.getProperty("org.gradle.java.installations.paths") == "jdk-11,jdk-17,jdk-21"

        and: '.gitignore ignores the .idea directory and the jdk-* symlinks'
        String expectedGitignoreContent = """
            #Intelij
            .idea/*
            
            # My custom setup
            something else
            another thing
            # >>> Gradle JDK setup >>>
            .idea/*
            !.idea/startup.xml
            !.idea/runConfigurations/IntelijGradleJdkSetup.xml
            jdk-*
            # <<< Gradle JDK setup <<<""".stripIndent(true)
        Files.readString(projectDir.toPath().resolve(".gitignore")) == expectedGitignoreContent

        and: 'jdk symlinks are created'
        ProcessBuilder jdkProcessBuilder = new ProcessBuilder(projectDir.toPath().resolve("jdk-21/bin/java").toString(), "-version").redirectErrorStream(true)

        then:
        CommandRunner.readAllInput(jdkProcessBuilder.start().getInputStream()).contains(JDK_21_VERSION)

        when: 'we trigger the generation of the gradle JDK setup files'
        runGradlewTasksSuccessfully('setupJdks')

        then: '.gitignore is not modified'
        Files.readString(projectDir.toPath().resolve(".gitignore")) == expectedGitignoreContent

        where:
        gradleVersionNumber << [GRADLE_7VERSION, GRADLE_8VERSION]
    }

    def '#gradleVersionNumber: sets up the right distributionUrl for download-urls'() {
        file('gradle.properties') << 'palantir.jdk.setup.enabled=true'
        gradleVersion = gradleVersionNumber
        // language=groovy
        buildFile << '''
            import com.palantir.gradle.jdks.JdkDistributionName
            import com.palantir.gradle.jdks.JdksExtension
            JdksExtension jdksExtension = rootProject.getExtensions().getByType(JdksExtension.class);
            jdksExtension.jdkDistribution(JdkDistributionName.AZUL_ZULU, azulZulu -> {
                azulZulu.getBaseUrl().set("https://myCustomValue");
            });
        '''

        when: 'running wrapper task'
        runTasksSuccessfully('wrapper')

        then:
        String osName = CurrentOs.get().uiName();
        String archName = CurrentArch.get().uiName();
        projectDir.toPath().resolve("gradle/jdks/11/${osName}/${archName}/download-url").text.startsWith("https://myCustomValue");

        where:
        gradleVersionNumber << [GRADLE_7VERSION, GRADLE_8VERSION]

    }

    def 'no gradleWrapper patch if palantir.jdk.setup.enabled == false'() {
        when:
        def output = runTasksSuccessfully('wrapper')

        then:
        !output.wasExecuted('wrapperJdkPatcher')
        !file("gradlew").text.contains("gradle-jdks-setup.sh")
    }

    private static void checkJdksVersions(File projectDir, Set<String> versions) {
        assert Files.list(projectDir.toPath().resolve("gradle/jdks")).filter(Files::isDirectory)
                .map(path -> path.getFileName().toString())
                .collect(Collectors.toSet()) == versions
        String osName = CurrentOs.get().uiName();
        String archName = CurrentArch.get().uiName();
        versions.stream().findFirst().ifPresent(version -> {
            assert Files.exists(projectDir.toPath().resolve("gradle/jdks/${version}/${osName}/${archName}/download-url"))
            assert Files.exists(projectDir.toPath().resolve("gradle/jdks/${version}/${osName}/${archName}/local-path"))
        })
    }

    @Override
    Path workingDir() {
        return workingDir
    }
}
