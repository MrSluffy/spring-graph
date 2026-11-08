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
package io.springgraph.processor.internal.report;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class JsonReportStringTest {

    @Test
    void escapesQuotesBackslashesAndControlCharacters() {
        assertEquals("\"a\\\"b\\\\c\\nd\\re\\tf\\u0001\"", JsonReport.string("a\"b\\c\nd\re\tf\u0001"));
    }

    @Test
    void nullBecomesJsonNull() {
        assertEquals("null", JsonReport.string(null));
    }

    @Test
    void escapesNonAsciiSoTheFileIsEncodingIndependent() {
        assertEquals("\"Gr\\u00f6\\u00dfe \\u2192 \\u007f\"", JsonReport.string("Größe → \u007f"));
    }

    @Test
    void keepsSurrogatePairsAndUnpairedSurrogatesAsEscapes() {
        assertEquals("\"\\ud83d\\ude00 \\ud800\"", JsonReport.string("\uD83D\uDE00 \uD800"));
    }

    @Test
    void cannotBreakOutOfTheString() {
        String hostile = "x\", \"injected\": true, \"y\": \"";
        assertEquals("\"x\\\", \\\"injected\\\": true, \\\"y\\\": \\\"\"", JsonReport.string(hostile));
    }
}
