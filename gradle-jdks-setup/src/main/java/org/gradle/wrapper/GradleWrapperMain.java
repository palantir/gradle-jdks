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

package org.gradle.wrapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;

public final class GradleWrapperMain {

    private GradleWrapperMain() {}

    public static void main(String[] args) throws Exception {
        ClassLoader loader = new OriginalGradleWrapperLoader();
        Class<?> origGradleWrapperMainClass = loader.loadClass("org.gradle.wrapper.OrigGradleWrapper");
        Method method = origGradleWrapperMainClass.getMethod("main", String[].class);
        method.invoke(null, new Object[] {args});
    }

    private static final class OriginalGradleWrapperLoader extends ClassLoader {
        @Override
        public Class<?> findClass(String name) {
            byte[] bytes = loadClassFromFile(name);
            return defineClass(name, bytes, 0, bytes.length);
        }

        private byte[] loadClassFromFile(String fileName) {
            try (InputStream inputStream =
                    getClass().getClassLoader().getResourceAsStream(fileName.replace('.', '/') + ".class")) {
                ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
                int nextValue;
                while ((nextValue = inputStream.read()) != -1) {
                    byteStream.write(nextValue);
                }
                byteStream.flush();
                return byteStream.toByteArray();
            } catch (IOException e) {
                throw new RuntimeException("Failed to load class from file", e);
            }
        }
    }
}
