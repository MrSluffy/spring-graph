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
package io.springgraph.processor.internal.diagnostic;

/** Diagnostic codes. Documented in docs/reference/diagnostics.md; the names are part of the user-facing contract. */
public enum Code {
    /** Missing binding inside a closed static boundary. */
    SG001,
    /** Reserved: conflicting binding identity. Not emitted. */
    SG002,
    /** Eager constructor dependency cycle. */
    SG003,
    /** Unsupported scope. */
    SG004,
    /** Unsupported constructor selection. */
    SG005,
    /** Unsupported dependency-resolution feature. */
    SG006,
    /** Ambiguous binding. */
    SG007,
    /** Unsupported qualifier. */
    SG008,
    /** Configuration problem (options, runtime boundary declarations, report output). */
    SG009
}
