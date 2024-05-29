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


import spock.lang.TempDir

import java.nio.file.Path
import java.util.regex.Matcher
import java.util.regex.Pattern

class GradleJdkToolchainsIntegrationTest extends GradleJdkIntegrationTest {

    private static String JDK_11_VERSION = "11.54.25-11.0.14.1"
    private static String JDK_17_VERSION = "17.0.3.6.1"
    private static String JDK_21_VERSION = "21.0.2.13.1"
    private static final int JAVA_17_BYTECODE = 61
    private static final int ENABLE_PREVIEW_BYTECODE = 65535

    @TempDir
    Path workingDir


    def '#gradleVersionNumber: javaToolchains correctly set-up'() {
        gradleVersion = gradleVersionNumber
        setupJdksHardcodedVersions()
        applyApplicationPlugin()

        file('gradle.properties') << 'gradle.jdk.setup.enabled=true'
        file('src/main/java/Circle.java') << java17Code

        // language=Groovy
        buildFile << """
            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(17)
                }
            }
        """.stripIndent(true)

        //language=groovy
        def subprojectLib = addSubproject 'subprojectLib', '''
            apply plugin: 'java-library'
            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(21)
                }
            }
        '''.stripIndent(true)
        writeHelloWorld(subprojectLib)

        def subprojectLib1 = addSubproject 'subprojectLib1', '''
            apply plugin: 'java-library'
            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(11)
                }
            }
        '''.stripIndent(true)
        writeHelloWorld(subprojectLib1)
        runTasksSuccessfully('wrapper', '--info')

        runAndAssertToolchainsConfiguration()

        where:
        gradleVersionNumber << [GRADLE_7VERSION, GRADLE_8VERSION]
    }

    def '#gradleVersionNumber: javaToolchains correctly set-up with baseline-java'() {
        gradleVersion = gradleVersionNumber

        setupJdksHardcodedVersions()
        applyBaselineJavaVersions()
        applyApplicationPlugin()

        file('gradle.properties') << 'gradle.jdk.setup.enabled=true'
        file('src/main/java/Main.java') << java17PreviewCode

        // language=Groovy
        buildFile << """
            javaVersions {
                libraryTarget = '11'
                distributionTarget = '17_PREVIEW'
            }
        """.stripIndent(true)

        //language=groovy
        def subprojectLib = addSubproject 'subprojectLib', '''
            apply plugin: 'java-library'
            javaVersion {
               target = 21
            }
        '''.stripIndent(true)
        writeHelloWorld(subprojectLib)

        //language=groovy
        def subprojectLib1 = addSubproject 'subprojectLib1', '''
            apply plugin: 'java-library'
            javaVersion {
                library()
            }
        '''.stripIndent(true)
        writeHelloWorld(subprojectLib1)
        runTasksSuccessfully('wrapper', '--info')

        runAndAssertToolchainsConfiguration()

        where:
        gradleVersionNumber << [GRADLE_7VERSION, GRADLE_8VERSION]
    }

    def runAndAssertToolchainsConfiguration() {
        when:
        String output = runGradlewTasksSuccessfully("javaToolchains", "compileJava", "--info")
        String runOutput = runGradlewTasksSuccessfully("run", "--info")

        then:
        output.contains("Successfully installed JDK distribution in")
        output.contains("Auto-detection:     Disabled")
        output.contains("Auto-download:      Disabled")
        output.contains("JDK 11.0.14.1")
        output.contains("JDK 17.0.3")
        output.contains("JDK 21.0.2")
        Matcher matcher = Pattern.compile("Detected by:       (.*)").matcher(output)
        while (matcher.find()) {
            String detectedByPattern = matcher.group(1)
            detectedByPattern.contains("Gradle property 'org.gradle.java.installations.paths'")
        }
        Path gradleJdksPath = workingDir.resolve("gradle-jdks")
        Path expectedJdk11 = gradleJdksPath.resolve(String.format("azul-zulu-11.54.25-11.0.14.1-%s", getHashForDistribution(JdkDistributionName.AZUL_ZULU, JDK_11_VERSION)))
        output.contains(String.format("Compiling with toolchain '%s'", expectedJdk11.toFile().getCanonicalPath()))
        Path expectedJdk17 = gradleJdksPath.resolve(String.format("amazon-corretto-17.0.3.6.1-%s", getHashForDistribution(JdkDistributionName.AMAZON_CORRETTO, JDK_17_VERSION)))
        output.contains(String.format("Compiling with toolchain '%s'", expectedJdk17.toFile().getCanonicalPath()))
        Path expectedJdk21 = gradleJdksPath.resolve(String.format("amazon-corretto-21.0.2.13.1-%s", getHashForDistribution(JdkDistributionName.AMAZON_CORRETTO, JDK_21_VERSION)))
        output.contains(String.format("Compiling with toolchain '%s'", expectedJdk21.toFile().getCanonicalPath()))
        File compiledClass = new File(projectDir, "build/classes/java/main/Main.class")
        assertBytecodeVersion(compiledClass, JAVA_17_BYTECODE, ENABLE_PREVIEW_BYTECODE)
        runOutput.contains(expectedJdk17.toFile().getCanonicalPath())
    }

    def java17Code = '''
        abstract sealed class Shape permits Circle {
            public abstract double area();
        }
        
        // Circle.java - Subclass of Shape
        public final class Circle extends Shape {
            private final double radius;
            public Circle(double radius) {
                this.radius = radius;
            }
            public double area() {
                return Math.PI * radius * radius;
            }
        }
    '''

    def java17PreviewCode = '''
        public class Main {
            sealed interface MyUnion {
                record Foo(int number) implements MyUnion {}
            }
        
            public static void main(String[] args) {
                MyUnion myUnion = new MyUnion.Foo(1234);
                switch (myUnion) {
                    case MyUnion.Foo foo -> System.out.println("Java 17 pattern matching switch: " + foo.number);
                }
            }
        }
        '''

    @Override
    Path workingDir() {
        return workingDir
    }
}
