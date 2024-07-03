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

import nebula.test.IntegrationSpec

final class JdksExtensionProjectSpec extends IntegrationSpec {
    def setup() {
        // language=Gradle
        buildFile << '''
            import com.palantir.gradle.jdks.*

            extensions.create('jdks', JdksExtension)
        '''.stripIndent(true)
    }

    def 'correctly handles multi level version overrides'() {
        // language=Gradle
        buildFile << '''
            import com.palantir.gradle.jdks.setup.common.Arch
            import com.palantir.gradle.jdks.setup.common.Os
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
            
            println('jdkVersion macos aarch64: ' + jdkVersionFor(Os.MACOS, Arch.AARCH64))
            println('jdkVersion linux-glibc aarch64: ' + jdkVersionFor(Os.LINUX_GLIBC, Arch.AARCH64))
            println('jdkVersion linux-glibc x64: ' + jdkVersionFor(Os.LINUX_GLIBC, Arch.X86_64))
        '''.stripIndent(true)

        when:
        def stdout = runTasksSuccessfully('help').standardOutput

        then:
        stdout.contains('jdkVersion macos aarch64: 11.1')
        stdout.contains('jdkVersion linux-glibc aarch64: 11.2')
        stdout.contains('jdkVersion linux-glibc x64: 11.3')
    }
}
