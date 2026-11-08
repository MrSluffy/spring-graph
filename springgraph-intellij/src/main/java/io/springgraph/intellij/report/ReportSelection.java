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

import io.springgraph.intellij.settings.SpringGraphSettings.Source;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/** Which report files to show for each module, per the source setting. No IDE dependency, so it is unit tested. */
public final class ReportSelection {

    public record Candidate(Path file, ReportSnapshot.Origin origin, String label) {}

    private ReportSelection() {}

    /**
     * @param build     per module, in module order: where its build may have written a report
     * @param ide       per module: where the IDE analysis writes its report
     * @param exists    whether a file exists
     * @param label     display label of a build report
     */
    public static List<Candidate> select(
            Source source,
            Map<String, Set<Path>> build,
            Map<String, Path> ide,
            Predicate<Path> exists,
            Function<Path, String> label) {
        List<Candidate> result = new ArrayList<>();
        Set<Path> seen = new HashSet<>(); // Gradle source-set modules share one build directory
        for (Map.Entry<String, Set<Path>> module : build.entrySet()) {
            List<Path> existing = module.getValue().stream().filter(exists).toList();
            if (source != Source.IDE) {
                for (Path file : existing) {
                    if (seen.add(file)) {
                        result.add(new Candidate(file, ReportSnapshot.Origin.BUILD, label.apply(file)));
                    }
                }
            }
            boolean useIde = source == Source.IDE || (source == Source.AUTO && existing.isEmpty());
            Path ideReport = ide.get(module.getKey());
            if (useIde && ideReport != null && exists.test(ideReport) && seen.add(ideReport)) {
                result.add(new Candidate(ideReport, ReportSnapshot.Origin.IDE, module.getKey()));
            }
        }
        return result;
    }

    /** Modules whose build wrote a report: in AUTO, the IDE analysis skips them. */
    public static Set<String> modulesWithBuildReports(Map<String, Set<Path>> build, Predicate<Path> exists) {
        Set<String> modules = new java.util.LinkedHashSet<>();
        for (Map.Entry<String, Set<Path>> module : build.entrySet()) {
            if (module.getValue().stream().anyMatch(exists)) {
                modules.add(module.getKey());
            }
        }
        return modules;
    }
}
