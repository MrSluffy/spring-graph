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
package io.springgraph.processor.internal.resolution;

import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;

/** Records, during resolution, which types were requested and which declared boundaries were applied (input to SG009). */
public final class BoundaryUsage {

    private final SortedSet<String> requested = new TreeSet<>();
    private final SortedSet<String> applied = new TreeSet<>();

    public void requested(String type) {
        requested.add(type);
    }

    public void applied(String boundary) {
        applied.add(boundary);
    }

    /** Every type requested by an analysed constructor parameter, sorted. */
    public SortedSet<String> requestedTypes() {
        return Collections.unmodifiableSortedSet(requested);
    }

    public boolean wasRequested(String type) {
        return requested.contains(type);
    }

    public boolean wasApplied(String boundary) {
        return applied.contains(boundary);
    }
}
