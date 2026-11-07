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

import java.util.List;
import javax.lang.model.element.VariableElement;

/**
 * One constructor parameter and its classification.
 *
 * @param parameter  the constructor parameter (diagnostics are attached to it)
 * @param type       erased, fully qualified requested type
 * @param resolution classification
 * @param targets    the resolved component for {@link Resolution#STATIC}, the candidates for {@link Resolution#AMBIGUOUS},
 *                   otherwise empty; component names in sorted order
 */
public record Dependency(VariableElement parameter, String type, Resolution resolution, List<String> targets) {

    public Dependency {
        targets = List.copyOf(targets);
    }

    public static Dependency of(VariableElement parameter, String type, Resolution resolution) {
        return new Dependency(parameter, type, resolution, List.of());
    }

    /** The resolved component name; only valid for {@link Resolution#STATIC}. */
    public String target() {
        if (resolution != Resolution.STATIC) {
            throw new IllegalStateException("No single target for " + resolution);
        }
        return targets.get(0);
    }
}
