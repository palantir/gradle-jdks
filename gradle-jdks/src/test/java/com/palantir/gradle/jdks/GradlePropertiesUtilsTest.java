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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GradlePropertiesUtilsTest {

    private static final Map<String, String> REQUIRED = Map.of(
            "org.gradle.java.installations.auto-detect", "false",
            "org.gradle.java.installations.auto-download", "false");

    @Nested
    class EnsureProperties {

        @Test
        void appends_missing_properties_to_empty_list() {
            List<String> lines = new ArrayList<>();
            GradlePropertiesUtils.ensureProperties(lines, REQUIRED);
            assertThat(lines)
                    .contains(
                            "org.gradle.java.installations.auto-detect=false",
                            "org.gradle.java.installations.auto-download=false");
        }

        @Test
        void replaces_existing_property_with_wrong_value() {
            List<String> lines = new ArrayList<>(List.of(
                    "org.gradle.java.installations.auto-detect=true",
                    "org.gradle.java.installations.auto-download=true"));
            GradlePropertiesUtils.ensureProperties(lines, REQUIRED);
            assertThat(lines)
                    .containsExactly(
                            "org.gradle.java.installations.auto-detect=false",
                            "org.gradle.java.installations.auto-download=false");
        }

        @Test
        void leaves_correct_values_unchanged() {
            List<String> lines = new ArrayList<>(List.of(
                    "org.gradle.java.installations.auto-detect=false",
                    "org.gradle.java.installations.auto-download=false"));
            GradlePropertiesUtils.ensureProperties(lines, REQUIRED);
            assertThat(lines)
                    .containsExactly(
                            "org.gradle.java.installations.auto-detect=false",
                            "org.gradle.java.installations.auto-download=false");
        }

        @Test
        void preserves_comments_and_other_properties() {
            List<String> lines = new ArrayList<>(
                    List.of("# This is a comment", "some.other.property=value", "", "! another comment style"));
            GradlePropertiesUtils.ensureProperties(lines, REQUIRED);
            assertThat(lines)
                    .startsWith("# This is a comment", "some.other.property=value", "", "! another comment style")
                    .contains(
                            "org.gradle.java.installations.auto-detect=false",
                            "org.gradle.java.installations.auto-download=false");
        }

        @Test
        void replaces_in_place_preserving_line_order() {
            List<String> lines = new ArrayList<>(List.of(
                    "first.prop=a",
                    "org.gradle.java.installations.auto-detect=true",
                    "middle.prop=b",
                    "org.gradle.java.installations.auto-download=true",
                    "last.prop=c"));
            GradlePropertiesUtils.ensureProperties(lines, REQUIRED);
            assertThat(lines)
                    .containsExactly(
                            "first.prop=a",
                            "org.gradle.java.installations.auto-detect=false",
                            "middle.prop=b",
                            "org.gradle.java.installations.auto-download=false",
                            "last.prop=c");
        }

        @Test
        void appends_only_missing_properties() {
            List<String> lines = new ArrayList<>(List.of("org.gradle.java.installations.auto-detect=false"));
            GradlePropertiesUtils.ensureProperties(lines, REQUIRED);
            assertThat(lines)
                    .as("existing property kept in place, missing one appended")
                    .startsWith("org.gradle.java.installations.auto-detect=false")
                    .contains("org.gradle.java.installations.auto-download=false");
        }
    }

    @Nested
    class ExtractPropertyKey {

        @Test
        void extracts_key_from_simple_pair() {
            assertThat(GradlePropertiesUtils.extractPropertyKey("key=value")).isEqualTo("key");
        }

        @Test
        void extracts_key_with_dots() {
            assertThat(GradlePropertiesUtils.extractPropertyKey("org.gradle.java.installations.auto-detect=false"))
                    .isEqualTo("org.gradle.java.installations.auto-detect");
        }

        @Test
        void returns_null_for_comment_lines() {
            assertThat(GradlePropertiesUtils.extractPropertyKey("# comment")).isNull();
            assertThat(GradlePropertiesUtils.extractPropertyKey("! comment")).isNull();
        }

        @Test
        void returns_null_for_blank_lines() {
            assertThat(GradlePropertiesUtils.extractPropertyKey("")).isNull();
            assertThat(GradlePropertiesUtils.extractPropertyKey("   ")).isNull();
        }

        @Test
        void returns_null_for_lines_without_equals() {
            assertThat(GradlePropertiesUtils.extractPropertyKey("no-equals-sign"))
                    .isNull();
        }

        @Test
        void trims_whitespace_around_key() {
            assertThat(GradlePropertiesUtils.extractPropertyKey("  key  = value"))
                    .isEqualTo("key");
        }
    }
}
