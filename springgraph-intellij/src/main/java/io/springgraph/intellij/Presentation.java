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
package io.springgraph.intellij;

import com.intellij.icons.AllIcons;
import io.springgraph.intellij.report.ReportModel;
import javax.swing.Icon;

/** Shared wording and icons, so the gutter and the tool window describe a dependency the same way. */
public final class Presentation {

    private Presentation() {}

    /** One line, e.g. {@code validated: p.DefaultGateway (via @Primary)}. Matches the wording of graph.txt. */
    public static String describe(ReportModel.Dependency dependency) {
        return switch (dependency.resolution()) {
            case STATIC ->
                "validated: " + String.join(", ", dependency.targets())
                        + (dependency.reason != null ? " (via " + dependency.reason + ")" : "");
            case BOUNDARY -> "Spring runtime boundary, not statically validated";
            case REPOSITORY -> "Spring Data repository, created at runtime, not statically validated";
            case VALUE -> "@Value property or expression, not a bean";
            case MISSING -> "missing: no static binding found";
            case AMBIGUOUS ->
                "ambiguous" + ("@Primary".equals(dependency.reason) ? " (several @Primary)" : "") + ": "
                        + String.join(", ", dependency.targets());
            case UNSUPPORTED -> "unsupported, not validated";
            case UNKNOWN -> "unknown classification '" + dependency.resolution + "' (update the SpringGraph plugin)";
        };
    }

    /** The requested type with its qualifier, e.g. {@code p.Gateway @Qualifier("x")}. */
    public static String requested(ReportModel.Dependency dependency) {
        return dependency.qualifier == null
                ? dependency.type
                : dependency.type + " @Qualifier(\"" + dependency.qualifier + "\")";
    }

    /**
     * Validated: a check. Not validated by design (boundary, repository, @Value): information. Unsupported: warning, as
     * the build reports SG006/SG008. Unresolved (missing, ambiguous): error.
     */
    public static Icon icon(ReportModel.Resolution resolution) {
        return switch (resolution) {
            case STATIC -> AllIcons.General.InspectionsOK;
            case BOUNDARY, REPOSITORY, VALUE -> AllIcons.General.Information;
            case UNSUPPORTED, UNKNOWN -> AllIcons.General.Warning;
            case MISSING, AMBIGUOUS -> AllIcons.General.Error;
        };
    }

    public static Icon icon(ReportModel.Finding finding) {
        return finding.isError() ? AllIcons.General.Error : AllIcons.General.Warning;
    }
}
