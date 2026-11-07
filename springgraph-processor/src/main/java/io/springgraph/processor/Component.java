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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

/**
 * A discovered stereotype component.
 *
 * <p>An <em>excluded</em> component (unsupported scope or constructor) is not analysed, but it still counts as a candidate
 * when other components' dependencies are resolved, so one unsupported component does not cascade into SG001 elsewhere.
 * A <em>conditional</em> component ({@code @Profile}, {@code @Conditional*}) is analysed, but never used to resolve a
 * dependency.
 */
final class Component {

    private final String name;
    private final TypeElement type;
    private final String conditional;
    private final ExecutableElement constructor;
    private final String exclusion;
    private final List<Dependency> dependencies = new ArrayList<>();

    private Component(
            String name, TypeElement type, String conditional, ExecutableElement constructor, String exclusion) {
        this.name = name;
        this.type = type;
        this.conditional = conditional;
        this.constructor = constructor;
        this.exclusion = exclusion;
    }

    static Component analysed(TypeElement type, String conditional, ExecutableElement constructor) {
        return new Component(type.getQualifiedName().toString(), type, conditional, constructor, null);
    }

    static Component excluded(TypeElement type, String conditional, String reason) {
        return new Component(type.getQualifiedName().toString(), type, conditional, null, reason);
    }

    /** Fully qualified class name; also the component's identity in the graph. */
    String name() {
        return name;
    }

    TypeElement type() {
        return type;
    }

    /** E.g. "@Profile", or null when the component is unconditional. */
    String conditional() {
        return conditional;
    }

    boolean isConditional() {
        return conditional != null;
    }

    /** The selected constructor; null for excluded components. */
    ExecutableElement constructor() {
        return constructor;
    }

    boolean isExcluded() {
        return exclusion != null;
    }

    /** Why the component is excluded, or null. */
    String exclusion() {
        return exclusion;
    }

    List<Dependency> dependencies() {
        return Collections.unmodifiableList(dependencies);
    }

    void addDependency(Dependency dependency) {
        dependencies.add(dependency);
    }
}
