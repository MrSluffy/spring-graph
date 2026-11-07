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

import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

/**
 * One analysis of the module, run once in the final processing round. The steps run in a fixed order, and diagnostics are
 * emitted in that order:
 * <ol>
 *   <li>discover components (SG004, SG005);</li>
 *   <li>resolve every constructor dependency (SG006, SG008);</li>
 *   <li>check declared runtime boundaries (SG009);</li>
 *   <li>report missing and ambiguous bindings (SG001, SG007), then cycles (SG003);</li>
 *   <li>write the report, with a note when coverage is low.</li>
 * </ol>
 */
final class GraphAnalysis {

    private final ProcessingEnvironment env;
    private final Options options;
    private final Diagnostics diagnostics;

    GraphAnalysis(ProcessingEnvironment env, Options options) {
        this.env = env;
        this.options = options;
        this.diagnostics = new Diagnostics(env.getMessager(), options.mode());
    }

    /** @param candidateNames qualified names of stereotype and {@code @Configuration} classes collected over all rounds */
    void run(Set<String> candidateNames) {
        Map<String, Component> graph = new TreeMap<>();
        SortedSet<String> configurationClasses = new TreeSet<>();
        discover(candidateNames, graph, configurationClasses);

        BoundaryUsage usage = new BoundaryUsage();
        resolve(graph, usage);

        new BoundaryChecker(env.getElementUtils(), diagnostics).check(options.boundaries(), usage);
        ResolutionReporter reporter = new ResolutionReporter(diagnostics);
        reporter.reportUnresolved(graph);
        reporter.reportCycles(graph);

        GraphReport report = GraphReport.render(options.mode(), graph, configurationClasses);
        if (report.isLowCoverage()) {
            diagnostics.note("SpringGraph: only " + report.validated() + " of " + report.total()
                    + " dependencies are statically validated; the rest are runtime boundaries, Spring Data repositories,"
                    + " @Value, or unsupported. See " + SpringGraphProcessor.REPORT_PATH + ".");
        }
        write(report);
    }

    private void discover(Set<String> candidateNames, Map<String, Component> graph, Set<String> configurationClasses) {
        ComponentFactory factory = new ComponentFactory(diagnostics);
        for (String name : candidateNames) {
            // Re-resolve by name: elements from earlier rounds must not be reused.
            TypeElement type = env.getElementUtils().getTypeElement(name);
            if (type == null || type.getModifiers().contains(Modifier.ABSTRACT)) {
                continue;
            }
            if (Annotations.has(type, SpringTypes.CONFIGURATION)) {
                configurationClasses.add(name);
            } else {
                graph.put(name, factory.create(type));
            }
        }
    }

    private void resolve(Map<String, Component> graph, BoundaryUsage usage) {
        DependencyResolver resolver = new DependencyResolver(
                env.getTypeUtils(), env.getElementUtils(), options.boundaries(), diagnostics, usage);
        for (Component component : graph.values()) {
            if (component.isExcluded()) {
                continue;
            }
            for (VariableElement parameter : component.constructor().getParameters()) {
                component.addDependency(resolver.resolve(parameter, graph));
            }
        }
    }

    private void write(GraphReport report) {
        try {
            FileObject file =
                    env.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", SpringGraphProcessor.REPORT_PATH);
            try (Writer writer = file.openWriter()) {
                writer.write(report.text());
            }
        } catch (IOException ex) {
            diagnostics.warning(
                    Code.SG009, "Could not write " + SpringGraphProcessor.REPORT_PATH + ": " + ex.getMessage());
        }
    }
}
