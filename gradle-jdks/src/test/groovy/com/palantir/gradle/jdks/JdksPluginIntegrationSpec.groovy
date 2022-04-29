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

package com.palantir.gradle.jdks

import nebula.test.IntegrationSpec
import nebula.test.functional.ExecutionResult


class JdksPluginIntegrationSpec extends IntegrationSpec {
    def 'can download + run an Azul Zulu JDK'() {
        // language=gradle
        buildFile << '''
            apply plugin: 'com.palantir.jdks'
            
            jdks {                
                jdk(11) {
                    distribution = 'azul-zulu'
                    jdkVersion = '11.54.25-11.0.14.1'    
                }
            }
            
            javaVersions {
                libraryTarget = 11
            }
            
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
                    System.out.println(String.format(
                            "version: %s, vendor: %s",
                            System.getProperty("java.version"),
                            System.getProperty("java.vendor")));
                }
            }
        '''.stripIndent(true)

        when:

        def stdout = runTasksSuccessfully(
                'printJavaVersion',
                // TODO: avoid resolving from root configuration somehow, removed in Gradle 8
                '--warning-mode=none')
                .standardOutput

        then:
        stdout.contains 'version: 11.0.14.1, vendor: Azul Systems, Inc.'
    }

    @Override
    ExecutionResult runTasksSuccessfully(String... tasks) {
        def result = super.runTasks(tasks)

        if (result.failure) {
            println result.standardOutput
            println result.standardError
            result.rethrowFailure()
        }

        return result
    }
}
