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
package io.springgraph.processor.internal.discovery;

import io.springgraph.processor.internal.diagnostic.Code;
import io.springgraph.processor.internal.diagnostic.Diagnostics;
import io.springgraph.processor.internal.model.Component;
import java.util.ArrayList;
import java.util.List;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;

/** Turns a stereotype class into a {@link Component}, applying the scope and constructor rules (SG004, SG005). */
public final class ComponentFactory {

    private final Diagnostics diagnostics;

    public ComponentFactory(Diagnostics diagnostics) {
        this.diagnostics = diagnostics;
    }

    public Component create(TypeElement type) {
        String name = type.getQualifiedName().toString();
        String conditional = Annotations.conditionalMarker(type);

        String scope = Annotations.scope(type);
        if (!Annotations.isSingletonScope(scope)) {
            diagnostics.finding(
                    Code.SG004,
                    type,
                    "Unsupported scope '" + scope + "' on " + name
                            + ". Only singleton components are validated; this component is excluded from the graph.");
            return Component.excluded(type, conditional, "scope '" + scope + "' is not supported");
        }

        // Constructor rule: exactly one non-private constructor (Lombok-generated constructors count).
        List<ExecutableElement> constructors = accessibleConstructors(type);
        if (constructors.size() != 1) {
            diagnostics.finding(
                    Code.SG005,
                    type,
                    name + " has " + constructors.size()
                            + " accessible constructors. Exactly one is supported; this component is excluded from the graph.");
            return Component.excluded(
                    type, conditional, constructors.size() + " accessible constructors (exactly one is supported)");
        }
        return Component.analysed(type, conditional, constructors.get(0));
    }

    private static List<ExecutableElement> accessibleConstructors(TypeElement type) {
        List<ExecutableElement> result = new ArrayList<>();
        for (ExecutableElement constructor : ElementFilter.constructorsIn(type.getEnclosedElements())) {
            if (!constructor.getModifiers().contains(Modifier.PRIVATE)) {
                result.add(constructor);
            }
        }
        return result;
    }
}
