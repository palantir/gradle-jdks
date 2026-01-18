package com.palantir.gradle.jdks

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class PosixPermissionsWindowsTest extends Specification {

    @TempDir Path tempDir

    def 'setExecuteFilePermissions crashes on Windows when POSIX is not supported'() {
        given:
        Path p = Files.createFile(tempDir.resolve("x.sh"))

        when:
        GradleJdksConfigsUtils.setExecuteFilePermissions(p)

        then:
        thrown(RuntimeException) 
    }
}
