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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

public final class JarResources {

    public static void extractJar(File jarFile, Path sourceDirectory) {
        try (JarInputStream jarInputStream = new JarInputStream(new FileInputStream(jarFile))) {
            JarEntry jarEntry;
            while ((jarEntry = jarInputStream.getNextJarEntry()) != null) {
                File outputFile = new File(sourceDirectory.toFile(), jarEntry.getName());
                if (jarEntry.isDirectory()) {
                    outputFile.mkdirs();
                } else {
                    outputFile.getParentFile().mkdirs();
                    try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
                        jarInputStream.transferTo(outputStream);
                    }
                }
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void createJarFromDirectory(File sourceDir, File outputFile) {
        try (FileOutputStream fos = new FileOutputStream(outputFile);
                JarOutputStream jos = new JarOutputStream(fos)) {
            addDirectoryToJar(jos, sourceDir, "");
        } catch (IOException e) {
            throw new RuntimeException("Failed to create Jar from dir", e);
        }
    }

    private static void addDirectoryToJar(JarOutputStream jarOutputStream, File dir, String relativePath)
            throws IOException {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                String newRelativePath = relativePath + file.getName() + "/";
                addDirectoryToJar(jarOutputStream, file, newRelativePath);
            } else {
                String entryName = relativePath + file.getName();
                JarEntry entry = new JarEntry(entryName);
                jarOutputStream.putNextEntry(entry);
                try (InputStream in = new FileInputStream(file)) {
                    in.transferTo(jarOutputStream);
                }
                jarOutputStream.closeEntry();
            }
        }
    }

    private JarResources() {}
}
