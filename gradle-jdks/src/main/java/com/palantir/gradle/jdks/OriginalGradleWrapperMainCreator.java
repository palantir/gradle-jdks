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

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public final class OriginalGradleWrapperMainCreator {

    private static final String OLD_CLASS_NAME = "org.gradle.wrapper.GradleWrapperMain";
    private static final String NEW_CLASS_NAME = "org.gradle.wrapper.OriginalGradleWrapperMain";

    public static void create(Path destinationDir) {
        try {
            ClassReader classReader =
                    new ClassReader(new FileInputStream(getClassPath(destinationDir, OLD_CLASS_NAME)));
            ClassWriter classWriter = new ClassWriter(classReader, 0);
            ClassVisitor renamingClassVisitor = new RenamingClassVisitor(classWriter);
            classReader.accept(renamingClassVisitor, 0);
            byte[] modifiedClassBytecode = classWriter.toByteArray();
            try (FileOutputStream outputStream = new FileOutputStream(getClassPath(destinationDir, NEW_CLASS_NAME))) {
                outputStream.write(modifiedClassBytecode);
            }
        } catch (IOException e) {
            throw new RuntimeException(
                    "Error renaming GradleWrapperMain.class into OriginalGradleWrapperMain.class", e);
        }
    }

    static class RenamingClassVisitor extends ClassVisitor {

        RenamingClassVisitor(ClassVisitor classVisitor) {
            super(Opcodes.ASM9, classVisitor);
        }

        @Override
        public void visit(
                int version, int access, String name, String signature, String superName, String[] interfaces) {
            super.visit(version, access, NEW_CLASS_NAME.replace('.', '/'), signature, superName, interfaces);
        }

        @Override
        public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
            return new MethodVisitor(api, methodVisitor) {
                @Override
                public void visitMethodInsn(
                        int opcode, String owner, String name, String descriptor, boolean isInterface) {
                    if (owner.equals(OLD_CLASS_NAME.replace('.', '/'))) {
                        super.visitMethodInsn(opcode, NEW_CLASS_NAME.replace('.', '/'), name, descriptor, isInterface);
                    } else {
                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                    }
                }

                @Override
                public void visitLdcInsn(Object value) {
                    if (value instanceof Type) {
                        Type type = (Type) value;
                        if ((type.getSort() == Type.OBJECT)
                                && type.getClassName().equals(OLD_CLASS_NAME)) {
                            Type newType = Type.getObjectType(NEW_CLASS_NAME.replace('.', '/'));
                            super.visitLdcInsn(newType);
                        } else {
                            super.visitLdcInsn(value);
                        }
                    } else {
                        super.visitLdcInsn(value);
                    }
                }
            };
        }
    }

    private static String getClassPath(Path buildDir, String className) {
        return buildDir.resolve(className.replace('.', '/') + ".class")
                .toAbsolutePath()
                .toString();
    }

    private OriginalGradleWrapperMainCreator() {}
}
