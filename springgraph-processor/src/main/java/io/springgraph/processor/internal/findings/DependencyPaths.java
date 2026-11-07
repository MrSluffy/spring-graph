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
package io.springgraph.processor.internal.findings;

import io.springgraph.processor.internal.model.Component;
import io.springgraph.processor.internal.model.Dependency;
import io.springgraph.processor.internal.model.Resolution;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Renders the dependency path from a root component down to a requested type, following statically resolved edges
 * upwards. When a component has several dependents, the first in name order is used, so output is deterministic.
 */
public final class DependencyPaths {

    /** Component name to the names of components that statically depend on it. */
    private final Map<String, SortedSet<String>> dependents = new TreeMap<>();

    public DependencyPaths(Map<String, Component> graph) {
        for (Component component : graph.values()) {
            for (Dependency dependency : component.dependencies()) {
                if (dependency.resolution() == Resolution.STATIC) {
                    dependents
                            .computeIfAbsent(dependency.target(), k -> new TreeSet<>())
                            .add(component.name());
                }
            }
        }
    }

    /**
     * For example:
     * <pre>
     *   OrderController
     *     -> OrderService
     *       -> PaymentGateway
     * </pre>
     */
    public String render(String from, String requestedType) {
        Deque<String> chain = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        for (String current = from; current != null && seen.add(current); current = firstDependent(current)) {
            chain.addFirst(current);
        }
        StringBuilder sb = new StringBuilder();
        int indent = 2;
        for (String component : chain) {
            sb.append(" ".repeat(indent))
                    .append(indent == 2 ? "" : "-> ")
                    .append(component)
                    .append('\n');
            indent += 2;
        }
        return sb.append(" ".repeat(indent)).append("-> ").append(requestedType).toString();
    }

    private String firstDependent(String component) {
        SortedSet<String> parents = dependents.get(component);
        return parents == null ? null : parents.first();
    }
}
