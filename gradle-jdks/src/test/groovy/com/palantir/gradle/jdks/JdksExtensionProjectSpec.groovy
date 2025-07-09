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

import com.palantir.gradle.plugintesting.GradleTestVersions
import nebula.test.IntegrationSpec
import spock.lang.Unroll

@Unroll
final class JdksExtensionProjectSpec extends IntegrationSpec {
    def setup() {
        // language=Gradle
        buildFile << '''
            import com.palantir.gradle.jdks.*
            
            buildscript {
                repositories {
                    mavenCentral() { metadataSources { mavenPom(); ignoreGradleMetadataRedirection() } }
                    gradlePluginPortal() { metadataSources { mavenPom(); ignoreGradleMetadataRedirection() } }
                }

                dependencies {
                    classpath "com.palantir.gradle.utils:platform:0.13.0"
                }
            }

            extensions.create('jdks', JdksExtension)
        '''.stripIndent(true)
    }

    def '#gradleVersionNumber: correctly handles multi level version overrides'() {
        gradleVersion = gradleVersionNumber
        // language=Gradle
        buildFile << '''
            import com.palantir.gradle.jdks.setup.common.Arch
            import com.palantir.platform.OperatingSystem

            jdks {
                jdk(11) {
                    distribution = 'amazon-corretto'
                    jdkVersion = '11.1'
                    
                    os('linux-glibc') {
                        jdkVersion = '11.2'
                        
                        arch('x86-64') {
                            jdkVersion = '11.3'
                        }
                    }
                }
            }
            
            def jdkVersionFor = { os, arch ->
                jdks.jdkFor(JavaLanguageVersion.of(11), project).get().jdkFor(os).jdkFor(arch).jdkVersion.get()
            }
            
            println('jdkVersion macos aarch64: ' + jdkVersionFor(OperatingSystem.MACOS, Arch.AARCH64))
            println('jdkVersion linux-glibc aarch64: ' + jdkVersionFor(OperatingSystem.LINUX_GLIBC, Arch.AARCH64))
            println('jdkVersion linux-glibc x64: ' + jdkVersionFor(OperatingSystem.LINUX_GLIBC, Arch.X86_64))
        '''.stripIndent(true)

        when:
        def stdout = runTasksSuccessfully('help').standardOutput

        then:
        stdout.contains('jdkVersion macos aarch64: 11.1')
        stdout.contains('jdkVersion linux-glibc aarch64: 11.2')
        stdout.contains('jdkVersion linux-glibc x64: 11.3')
        
        where:
        gradleVersionNumber << GradleTestVersions.getGradleVersionsForTests()
    }
}
