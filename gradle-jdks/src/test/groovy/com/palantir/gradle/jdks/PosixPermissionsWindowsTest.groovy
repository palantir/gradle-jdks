/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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

import spock.lang.Specification
import spock.lang.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission

class PosixPermissionsWindowsTest extends Specification {

    @TempDir Path tempDir

    def 'setExecuteFilePermissions works on both POSIX and non-POSIX filesystems'() {
        given:
        Path p = Files.createFile(tempDir.resolve("x.sh"))
        boolean isPosixSupported = Files.getFileStore(p).supportsFileAttributeView("posix")

        when:
        GradleJdksConfigsUtils.setExecuteFilePermissions(p)

        then:
        noExceptionThrown()

        and:
        if (isPosixSupported) {
            def perms = Files.getPosixFilePermissions(p)
            assert perms.contains(PosixFilePermission.OWNER_EXECUTE)
            assert perms.contains(PosixFilePermission.GROUP_EXECUTE)
            assert perms.contains(PosixFilePermission.OTHERS_EXECUTE)
        } else {
            assert Files.isExecutable(p) || p.toFile().canExecute()
        }
    }
}