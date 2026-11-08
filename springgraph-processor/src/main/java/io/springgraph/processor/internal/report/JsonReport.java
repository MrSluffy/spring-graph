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
import io.springgraph.processor.internal.diagnostic.Finding;
import io.springgraph.processor.internal.model.Component;
import io.springgraph.processor.internal.model.Dependency;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The machine-readable report written to {@link SpringGraphProcessor#JSON_REPORT_PATH}, for tools such as the IntelliJ
 * plugin. It carries the same graph as {@link GraphReport} plus every coded finding, tied to its component and
 * constructor parameter. The layout is documented in docs/reference/report-json.md and locked by SnapshotTest.
 *
 * <p>Compatibility: {@link #SCHEMA_VERSION} changes only when a field is removed or its meaning changes; new fields may be
 * added within a version, so readers must ignore unknown fields. Output is deterministic: components sorted by name,
 * dependencies in parameter order, findings in emission order. Absent values are written as {@code null}.
 */
public final class JsonReport {

    public static final int SCHEMA_VERSION = 1;

    private JsonReport() {}

    /**
     * @param graph                components sorted by name
     * @param configurationClasses {@code @Configuration} classes, sorted
     * @param findings             findings in emission order
     */
    public static String render(
            Mode mode,
            Map<String, Component> graph,
            Set<String> configurationClasses,
            List<Finding> findings,
            GraphReport summary) {
        List<String> components = new ArrayList<>();
        List<String> excluded = new ArrayList<>();
        for (Component component : graph.values()) {
            if (component.isExcluded()) {
                excluded.add(object(
                        3,
                        field("name", string(component.name())),
                        field("reason", string(component.exclusion())),
                        field("configuration", "false")));
                continue;
            }
            List<String> dependencies = new ArrayList<>();
            List<Dependency> all = component.dependencies();
            for (int i = 0; i < all.size(); i++) {
                Dependency dependency = all.get(i);
                List<String> targets = new ArrayList<>();
                for (String target : dependency.targets()) {
                    targets.add(string(target));
                }
                dependencies.add(object(
                        5,
                        field("index", Integer.toString(i)),
                        field(
                                "parameter",
                                string(dependency.parameter().getSimpleName().toString())),
                        field("type", string(dependency.type())),
                        field("qualifier", string(dependency.qualifier())),
                        field("resolution", string(dependency.resolution().name())),
                        field("targets", inlineArray(targets)),
                        field("reason", string(dependency.reason()))));
            }
            components.add(object(
                    3,
                    field("name", string(component.name())),
                    field("conditional", string(component.conditional())),
                    field("dependencies", array(4, dependencies))));
        }
        for (String configuration : configurationClasses) {
            excluded.add(object(
                    3,
                    field("name", string(configuration)),
                    field("reason", string("@Configuration is not interpreted (its @Bean methods are unknown)")),
                    field("configuration", "true")));
        }

        List<String> findingObjects = new ArrayList<>();
        for (Finding finding : findings) {
            findingObjects.add(object(
                    3,
                    field("code", string(finding.code().name())),
                    field("severity", string(finding.kind().name())),
                    field("component", string(finding.component())),
                    field(
                            "parameter",
                            finding.parameter() == null
                                    ? "null"
                                    : finding.parameter().toString()),
                    field("message", string(finding.message()))));
        }

        return object(
                        1,
                        field("schemaVersion", Integer.toString(SCHEMA_VERSION)),
                        field("mode", string(mode.label())),
                        field(
                                "coverage",
                                object(
                                        2,
                                        field("validated", Integer.toString(summary.validated())),
                                        field("total", Integer.toString(summary.total())))),
                        field("components", array(2, components)),
                        field("excluded", array(2, excluded)),
                        field("findings", array(2, findingObjects)))
                + "\n";
    }

    private static String field(String name, String value) {
        return string(name) + ": " + value;
    }

    /** An object whose fields are indented to {@code depth} levels of two spaces. */
    private static String object(int depth, String... fields) {
        String inner = "  ".repeat(depth);
        String outer = "  ".repeat(depth - 1);
        return "{\n" + inner + String.join(",\n" + inner, fields) + "\n" + outer + "}";
    }

    /** An array whose elements are indented to {@code depth} levels; {@code []} when empty. */
    private static String array(int depth, List<String> elements) {
        if (elements.isEmpty()) {
            return "[]";
        }
        String inner = "  ".repeat(depth);
        String outer = "  ".repeat(depth - 1);
        return "[\n" + inner + String.join(",\n" + inner, elements) + "\n" + outer + "]";
    }

    private static String inlineArray(List<String> elements) {
        return "[" + String.join(", ", elements) + "]";
    }

    /**
     * A JSON string literal, or {@code null}. Escapes quotes, backslashes, control characters and every non-ASCII
     * character, so the file is pure ASCII: the Filer writes with the compiler's encoding, which is not UTF-8 on every
     * platform (javac 17 on Windows), and unpaired surrogates survive as escapes instead of becoming {@code ?}.
     */
    static String string(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20 || c > 0x7e) {
                        String hex = Integer.toHexString(c);
                        sb.append("\\u").append("0000", hex.length(), 4).append(hex);
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
