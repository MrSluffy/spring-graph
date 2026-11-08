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

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import io.springgraph.intellij.report.ReportModel;
import io.springgraph.intellij.report.ReportSnapshot;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A gutter icon on every constructor parameter of an analysed component, showing how the last build classified it.
 * Clicking a validated or ambiguous dependency navigates to the chosen bean or the candidates.
 *
 * <p>Nothing is shown when the constructor no longer matches the report (the source changed since the build), so the
 * icons are never attached to the wrong parameter. Toggle under Settings | Editor | General | Gutter Icons.
 */
public final class DependencyLineMarkerProvider extends LineMarkerProviderDescriptor {

    @Override
    public @NotNull String getName() {
        return "SpringGraph dependency";
    }

    @Override
    public @Nullable Icon getIcon() {
        return Presentation.icon(ReportModel.Resolution.STATIC);
    }

    @Override
    public @Nullable LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        // Line markers must be attached to leaf elements: the parameter's name.
        if (!(element instanceof PsiIdentifier) || !(element.getParent() instanceof PsiParameter parameter)) {
            return null;
        }
        if (parameter.getNameIdentifier() != element
                || !(parameter.getDeclarationScope() instanceof PsiMethod constructor)
                || !constructor.isConstructor()) {
            return null;
        }
        PsiClass type = constructor.getContainingClass();
        String name = type == null ? null : type.getQualifiedName();
        if (name == null) {
            return null;
        }
        Project project = element.getProject();
        ReportSnapshot snapshot = SpringGraphReports.getInstance(project).snapshot();
        ReportSnapshot.Entry entry = snapshot.component(name);
        if (entry == null || !PsiLookup.matches(constructor, entry.component())) {
            return null;
        }
        int index = constructor.getParameterList().getParameterIndex(parameter);
        ReportModel.Dependency dependency = entry.component().dependencies().get(index);
        ReportModel.Resolution resolution = dependency.resolution();
        Icon icon = Presentation.icon(resolution);
        String tooltip = tooltip(entry, dependency, snapshot.findings(entry, index));

        if (resolution == ReportModel.Resolution.STATIC || resolution == ReportModel.Resolution.AMBIGUOUS) {
            List<String> targets = dependency.targets();
            return NavigationGutterIconBuilder.create(icon)
                    .setTargets(NotNullLazyValue.lazy(() -> resolve(project, targets)))
                    .setTooltipText(tooltip)
                    .setPopupTitle("SpringGraph Candidates")
                    .setAlignment(GutterIconRenderer.Alignment.LEFT)
                    .createLineMarkerInfo(element);
        }
        return new LineMarkerInfo<>(
                element,
                element.getTextRange(),
                icon,
                e -> tooltip,
                null,
                GutterIconRenderer.Alignment.LEFT,
                () -> "SpringGraph: " + Presentation.describe(dependency));
    }

    private static List<PsiClass> resolve(Project project, List<String> names) {
        List<PsiClass> classes = new ArrayList<>();
        for (String target : names) {
            PsiClass resolved = PsiLookup.findClass(project, target);
            if (resolved != null) {
                classes.add(resolved);
            }
        }
        return classes;
    }

    private static String tooltip(
            ReportSnapshot.Entry entry, ReportModel.Dependency dependency, List<ReportModel.Finding> findings) {
        // Every value comes from a file on disk and goes into Swing HTML, so all of it is escaped. A crafted graph.json
        // must not inject markup (for example an <img> that loads a remote URL on hover).
        StringBuilder html = new StringBuilder("<html><b>SpringGraph</b> (")
                .append(StringUtil.escapeXmlEntities(
                        String.valueOf(entry.report().model().mode)))
                .append(entry.report().isIdeAnalysis() ? ", IDE analysis)<br>" : " build)<br>")
                .append(StringUtil.escapeXmlEntities(Presentation.requested(dependency)))
                .append(": ")
                .append(StringUtil.escapeXmlEntities(Presentation.describe(dependency)));
        for (ReportModel.Finding finding : findings) {
            html.append("<br>")
                    .append(StringUtil.escapeXmlEntities(finding.code))
                    .append(": ")
                    .append(StringUtil.escapeXmlEntities(finding.summary()));
        }
        return html.append("</html>").toString();
    }
}
