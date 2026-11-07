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
package io.springgraph.processor.internal.model;

/** How a constructor dependency was classified. Only {@link #STATIC} counts as statically validated. */
public enum Resolution {
    /** Exactly one unconditional component satisfies the dependency. */
    STATIC,
    /** No component satisfies it, and the type is a declared runtime boundary. */
    BOUNDARY,
    /**
     * An interface extending Spring Data's {@code Repository} (and not {@code @NoRepositoryBean}). Spring Data creates the
     * bean at runtime if repository scanning covers it, which the processor cannot verify.
     */
    REPOSITORY,
    /** A {@code @Value} property or expression; not a bean dependency. */
    VALUE,
    /** No component satisfies it and it is not declared or recognized: SG001. */
    MISSING,
    /** Several unconditional components satisfy it: SG007. */
    AMBIGUOUS,
    /** The processor does not model the resolution semantics (SG006/SG008); not validated, never guessed. */
    UNSUPPORTED;

    public boolean isValidated() {
        return this == STATIC;
    }
}
