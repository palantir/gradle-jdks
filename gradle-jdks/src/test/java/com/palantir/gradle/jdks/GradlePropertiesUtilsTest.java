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
        void appends_missing_properties_to_empty_string() {
            String result = GradlePropertiesUtils.ensureProperties("", REQUIRED);
            assertThat(result)
                    .contains("org.gradle.java.installations.auto-detect=false")
                    .contains("org.gradle.java.installations.auto-download=false");
        }

        @Test
        void replaces_existing_property_with_wrong_value() {
            String input = "org.gradle.java.installations.auto-detect=true\n"
                    + "org.gradle.java.installations.auto-download=true\n";
            String result = GradlePropertiesUtils.ensureProperties(input, REQUIRED);
            assertThat(result)
                    .isEqualTo("org.gradle.java.installations.auto-detect=false\n"
                            + "org.gradle.java.installations.auto-download=false\n");
        }

        @Test
        void leaves_correct_values_unchanged() {
            String input = "org.gradle.java.installations.auto-detect=false\n"
                    + "org.gradle.java.installations.auto-download=false\n";
            String result = GradlePropertiesUtils.ensureProperties(input, REQUIRED);
            assertThat(result).isEqualTo(input);
        }

        @Test
        void preserves_comments_and_other_properties() {
            String input = "# This is a comment\n" + "some.other.property=value\n" + "\n" + "! another comment style\n";
            String result = GradlePropertiesUtils.ensureProperties(input, REQUIRED);
            assertThat(result)
                    .startsWith("# This is a comment\nsome.other.property=value\n\n! another comment style\n")
                    .contains("org.gradle.java.installations.auto-detect=false")
                    .contains("org.gradle.java.installations.auto-download=false");
        }

        @Test
        void replaces_in_place_preserving_line_order() {
            String input = "first.prop=a\n"
                    + "org.gradle.java.installations.auto-detect=true\n"
                    + "middle.prop=b\n"
                    + "org.gradle.java.installations.auto-download=true\n"
                    + "last.prop=c\n";
            String result = GradlePropertiesUtils.ensureProperties(input, REQUIRED);
            assertThat(result)
                    .isEqualTo("first.prop=a\n"
                            + "org.gradle.java.installations.auto-detect=false\n"
                            + "middle.prop=b\n"
                            + "org.gradle.java.installations.auto-download=false\n"
                            + "last.prop=c\n");
        }

        @Test
        void appends_only_missing_properties() {
            String input = "org.gradle.java.installations.auto-detect=false\n";
            String result = GradlePropertiesUtils.ensureProperties(input, REQUIRED);
            assertThat(result)
                    .as("existing property kept in place, missing one appended")
                    .startsWith("org.gradle.java.installations.auto-detect=false\n")
                    .contains("org.gradle.java.installations.auto-download=false");
        }

        @Test
        void preserves_trailing_newline() {
            String input = "some.property=value\n";
            String result = GradlePropertiesUtils.ensureProperties(input, REQUIRED);
            assertThat(result).as("output should end with a newline").endsWith("\n");
        }

        @Test
        void preserves_no_trailing_newline_when_input_has_none() {
            String input = "org.gradle.java.installations.auto-detect=false\n"
                    + "org.gradle.java.installations.auto-download=false";
            String result = GradlePropertiesUtils.ensureProperties(input, REQUIRED);
            assertThat(result)
                    .as("output should not add a trailing newline when input had none")
                    .isEqualTo("org.gradle.java.installations.auto-detect=false\n"
                            + "org.gradle.java.installations.auto-download=false");
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
