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
package io.springgraph.intellij.report;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The reports loaded after the last build, immutable. Components are indexed by qualified name; when two modules report
 * the same class, the first report wins.
 *
 * @param reports  loaded reports, sorted by label
 * @param problems files that could not be read, with the reason
 */
public record ReportSnapshot(List<Loaded> reports, List<Problem> problems, Map<String, Entry> index) {

    public static final ReportSnapshot EMPTY = of(List.of(), List.of());

    /** A report and where it came from. {@code label} is the output directory relative to the project. */
    public record Loaded(Path file, String label, ReportModel model) {}

    public record Problem(Path file, String message) {}

    /** A component and the report it came from. */
    public record Entry(Loaded report, ReportModel.Component component) {}

    public ReportSnapshot {
        reports = List.copyOf(reports);
        problems = List.copyOf(problems);
        index = Map.copyOf(index);
    }

    public static ReportSnapshot of(List<Loaded> reports, List<Problem> problems) {
        Map<String, Entry> index = new HashMap<>();
        for (Loaded report : reports) {
            for (ReportModel.Component component : report.model().components()) {
                if (component.name != null) {
                    index.putIfAbsent(component.name, new Entry(report, component));
                }
            }
        }
        return new ReportSnapshot(reports, problems, index);
    }

    public boolean isEmpty() {
        return reports.isEmpty() && problems.isEmpty();
    }

    /** The analysed component with this qualified name, or null. */
    public Entry component(String qualifiedName) {
        // Findings without a class (SG009) have no component; immutable maps reject null lookups.
        return qualifiedName == null ? null : index.get(qualifiedName);
    }

    /** Findings attached to one constructor parameter of a component, from the component's own report. */
    public List<ReportModel.Finding> findings(Entry entry, int parameter) {
        List<ReportModel.Finding> result = new ArrayList<>();
        for (ReportModel.Finding finding : entry.report().model().findings()) {
            if (entry.component().name.equals(finding.component)
                    && finding.parameter != null
                    && finding.parameter == parameter) {
                result.add(finding);
            }
        }
        return result;
    }
}
