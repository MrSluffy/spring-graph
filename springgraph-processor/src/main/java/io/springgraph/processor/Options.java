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
package io.springgraph.processor;

import java.util.Collections;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * Processor configuration from {@code -A} options.
 *
 * @param mode       validation policy
 * @param boundaries declared runtime boundary types, sorted so diagnostics are deterministic
 */
record Options(Mode mode, SortedSet<String> boundaries) {

    /**
     * Parses the javac options. Returns null when the processor is not enabled (no mode option) or the mode is invalid;
     * an invalid mode is reported through {@code onError}.
     */
    static Options parse(Map<String, String> javacOptions, Consumer<String> onError) {
        String rawMode = javacOptions.get(SpringGraphProcessor.OPT_MODE);
        if (rawMode == null) {
            return null;
        }
        Mode mode = Mode.parse(rawMode);
        if (mode == null) {
            onError.accept(
                    "-A" + SpringGraphProcessor.OPT_MODE + " must be 'advisory' or 'strict', was '" + rawMode + "'.");
            return null;
        }
        return new Options(mode, parseList(javacOptions.get(SpringGraphProcessor.OPT_BOUNDARIES)));
    }

    /** Comma-separated, trimmed, blanks ignored. */
    private static SortedSet<String> parseList(String value) {
        SortedSet<String> result = new TreeSet<>();
        if (value != null) {
            for (String part : value.split(",")) {
                if (!part.isBlank()) {
                    result.add(part.trim());
                }
            }
        }
        return Collections.unmodifiableSortedSet(result);
    }
}
