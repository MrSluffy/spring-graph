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

import io.springgraph.processor.SpringGraphProcessor;
import io.springgraph.processor.internal.config.Mode;
import io.springgraph.processor.internal.model.Component;
import io.springgraph.processor.internal.model.Dependency;
import java.util.Map;
import java.util.Set;

/**
 * The deterministic coverage report written to {@link SpringGraphProcessor#REPORT_PATH}. Its layout is user-facing and
 * locked by SnapshotTest.
 *
 * @param text      the report
 * @param validated dependencies statically validated
 * @param total     dependencies of analysed components
 */
public record GraphReport(String text, int validated, int total) {

    /** Below this share of validated dependencies, the build prints a note pointing at the report. */
    private static final double LOW_COVERAGE = 0.5;

    public boolean isLowCoverage() {
        return total > 0 && validated < total * LOW_COVERAGE;
    }

    /**
     * @param graph                components sorted by name
     * @param configurationClasses {@code @Configuration} classes, sorted; listed as not interpreted
     */
    public static GraphReport render(Mode mode, Map<String, Component> graph, Set<String> configurationClasses) {
        StringBuilder sb = new StringBuilder();
        sb.append("SpringGraph report (mode=").append(mode.label()).append(")\n\n");

        int total = 0;
        int validated = 0;
        for (Component component : graph.values()) {
            if (component.isExcluded()) {
                continue;
            }
            sb.append(component.name());
            if (component.isConditional()) {
                sb.append(" [conditional: ")
                        .append(component.conditional())
                        .append("; never used to resolve a dependency]");
            }
            sb.append('\n');
            for (Dependency dependency : component.dependencies()) {
                total++;
                if (dependency.resolution().isValidated()) {
                    validated++;
                }
                sb.append("  ")
                        .append(dependency.type())
                        .append(" -> ")
                        .append(describe(dependency))
                        .append('\n');
            }
        }

        sb.append("\nExcluded components\n");
        boolean anyExcluded = false;
        for (Component component : graph.values()) {
            if (component.isExcluded()) {
                sb.append("  ")
                        .append(component.name())
                        .append(": ")
                        .append(component.exclusion())
                        .append('\n');
                anyExcluded = true;
            }
        }
        for (String configuration : configurationClasses) {
            sb.append("  ")
                    .append(configuration)
                    .append(": @Configuration is not interpreted (its @Bean methods are unknown)\n");
            anyExcluded = true;
        }
        if (!anyExcluded) {
            sb.append("  (none)\n");
        }

        sb.append("\nCoverage: ")
                .append(validated)
                .append(" of ")
                .append(total)
                .append(" dependencies statically validated\n");
        return new GraphReport(sb.toString(), validated, total);
    }

    private static String describe(Dependency dependency) {
        return switch (dependency.resolution()) {
            case STATIC -> dependency.target() + " [validated]";
            case BOUNDARY -> "Spring runtime boundary [not statically validated]";
            case REPOSITORY -> "Spring Data repository, created at runtime [not statically validated]";
            case VALUE -> "@Value property/expression [not a bean, not validated]";
            case MISSING -> "<missing> [unresolved]";
            case AMBIGUOUS -> "<ambiguous: " + String.join(", ", dependency.targets()) + "> [unresolved]";
            case UNSUPPORTED -> "[unsupported, not validated]";
        };
    }
}
