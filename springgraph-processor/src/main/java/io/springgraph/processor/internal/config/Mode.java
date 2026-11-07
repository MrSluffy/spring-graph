/*
 * Copyright 2026 John Andrew Camu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.springgraph.processor.internal.config;

import java.util.Locale;
import javax.tools.Diagnostic;

/** Validation policy, selected with {@code -Aspringgraph.mode}. */
public enum Mode {
    /** Findings are warnings; the build always passes. Missing bindings are suspected, not proven. */
    ADVISORY,
    /** Findings are errors; the module is treated as a closed static boundary. */
    STRICT;

    /** Severity used for findings whose severity depends on the mode. */
    public Diagnostic.Kind findingKind() {
        return this == STRICT ? Diagnostic.Kind.ERROR : Diagnostic.Kind.WARNING;
    }

    /** The option value, e.g. "strict". */
    public String label() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Parses an option value, ignoring case and surrounding whitespace; null if it is not a valid mode. */
    public static Mode parse(String value) {
        for (Mode mode : values()) {
            if (mode.name().equalsIgnoreCase(value.trim())) {
                return mode;
            }
        }
        return null;
    }
}
