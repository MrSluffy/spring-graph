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

import io.springgraph.processor.SpringGraphProcessor;
import io.springgraph.processor.internal.config.Mode;
import io.springgraph.processor.internal.diagnostic.Code;
import io.springgraph.processor.internal.diagnostic.Diagnostics;
import io.springgraph.processor.internal.model.Component;
import io.springgraph.processor.internal.model.Dependency;
import io.springgraph.processor.internal.model.Resolution;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reports the graph-level findings once every dependency is resolved: SG001 missing, SG007 ambiguous (both with the
 * dependency path), and SG003 eager constructor cycles.
 */
public final class ResolutionReporter {

    private final Diagnostics diagnostics;

    public ResolutionReporter(Diagnostics diagnostics) {
        this.diagnostics = diagnostics;
    }

    public void reportUnresolved(Map<String, Component> graph) {
        DependencyPaths paths = new DependencyPaths(graph);
        for (Component component : graph.values()) {
            for (Dependency dependency : component.dependencies()) {
                switch (dependency.resolution()) {
                    case MISSING ->
                        diagnostics.finding(
                                Code.SG001,
                                dependency.parameter(),
                                missingMessage(dependency, paths.render(component.name(), dependency.type())));
                    case AMBIGUOUS ->
                        diagnostics.finding(
                                Code.SG007,
                                dependency.parameter(),
                                ambiguousMessage(dependency, paths.render(component.name(), dependency.type())));
                    default -> {}
                }
            }
        }
    }

    /** Only statically resolved edges are followed: a cycle through a boundary or unsupported edge is not claimed. */
    public void reportCycles(Map<String, Component> graph) {
        Map<String, List<String>> edges = new TreeMap<>();
        for (Component component : graph.values()) {
            List<String> targets = new ArrayList<>();
            for (Dependency dependency : component.dependencies()) {
                if (dependency.resolution() == Resolution.STATIC) {
                    targets.add(dependency.target());
                }
            }
            edges.put(component.name(), targets);
        }
        for (List<String> cycle : CycleDetector.findCycles(edges)) {
            List<String> shown = new ArrayList<>(cycle);
            shown.add(cycle.get(0));
            diagnostics.finding(
                    Code.SG003,
                    graph.get(cycle.get(0)).type(),
                    "Eager constructor dependency cycle:\n  " + String.join("\n    -> ", shown));
        }
    }

    private static String ambiguousMessage(Dependency dependency, String path) {
        String what = "@Primary".equals(dependency.reason())
                ? ": more than one candidate is @Primary"
                : (dependency.qualifier() != null ? " with @Qualifier(\"" + dependency.qualifier() + "\")" : "");
        return "Ambiguous binding for " + dependency.type() + what + ". Candidates: "
                + String.join(", ", dependency.targets()) + ".\n\nDependency path:\n" + path;
    }

    private String missingMessage(Dependency dependency, String path) {
        String policy = diagnostics.mode() == Mode.STRICT
                ? "This dependency is inside a closed static boundary."
                : "Advisory mode: this is a suspected missing binding, not a proven error.";
        String qualified = dependency.qualifier() != null ? " with @Qualifier(\"" + dependency.qualifier() + "\")" : "";
        String qualifierFix = dependency.qualifier() != null
                ? "  - Check the qualifier: it must equal a candidate's bean name or its class-level @Qualifier value.\n"
                : "";
        return "No static binding found for " + dependency.type() + qualified + ".\n\n"
                + "Dependency path:\n" + path + "\n\n"
                + "Graph policy:\n  " + policy + "\n\n"
                + "Possible fixes:\n"
                + qualifierFix
                + "  - Add a supported implementation.\n"
                + "  - Include an existing component in the graph.\n"
                + "  - Declare a runtime boundary (-A" + SpringGraphProcessor.OPT_BOUNDARIES + "=" + dependency.type()
                + ") if Spring supplies this dependency.";
    }
}
