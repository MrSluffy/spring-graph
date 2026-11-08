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
package io.springgraph.intellij.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import io.springgraph.intellij.SpringGraphReports;
import io.springgraph.intellij.analysis.BundledProcessor;
import io.springgraph.intellij.analysis.SpringGraphAnalyzer;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.Nls;

/** Settings | Tools | SpringGraph. */
public final class SpringGraphConfigurable implements Configurable {

    private final Project project;
    private ComboBox<SpringGraphSettings.Source> source;
    private ComboBox<String> mode;
    private JBTextArea boundaries;
    private JBCheckBox analyzeAfterBuild;

    public SpringGraphConfigurable(Project project) {
        this.project = project;
    }

    @Override
    public @Nls String getDisplayName() {
        return "SpringGraph";
    }

    @Override
    public JComponent createComponent() {
        source = new ComboBox<>(SpringGraphSettings.Source.values());
        source.setRenderer(SimpleListCellRenderer.create("", SpringGraphSettings.Source::label));
        mode = new ComboBox<>(SpringGraphSettings.MODES.toArray(String[]::new));
        boundaries = new JBTextArea(5, 40);
        boundaries.getAccessibleContext().setAccessibleName("Runtime boundaries");
        analyzeAfterBuild = new JBCheckBox("Run the IDE analysis after every build");

        String processor;
        try {
            processor = "io.github.mrsluffy:springgraph-processor:"
                    + BundledProcessor.load().version();
        } catch (RuntimeException ex) {
            processor = "not available (" + ex.getMessage() + ")";
        }

        JPanel panel = FormBuilder.createFormBuilder()
                .addLabeledComponent("Show reports from:", source)
                .addTooltip("The IDE analysis runs the bundled processor on the module's sources, with the settings"
                        + " below. It never changes the Gradle or Maven build.")
                .addVerticalGap(8)
                .addLabeledComponent("IDE analysis mode:", mode)
                .addTooltip("Advisory reports findings as warnings; strict as errors. The graph is the same.")
                .addLabeledComponent("Runtime boundaries:", new JBScrollPane(boundaries), true)
                .addTooltip("Types Spring supplies at runtime (for example java.time.Clock), one per line or comma"
                        + " separated. Typos are reported as SG009.")
                .addComponent(analyzeAfterBuild)
                .addVerticalGap(8)
                .addComponent(new JBLabel(
                        "Bundled processor: " + processor, UIUtil.ComponentStyle.SMALL, UIUtil.FontColor.BRIGHTER))
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
        reset();
        return panel;
    }

    private SpringGraphSettings.Data edited() {
        SpringGraphSettings.Data data = new SpringGraphSettings.Data();
        data.source = ((SpringGraphSettings.Source) source.getSelectedItem()).name();
        data.mode = (String) mode.getSelectedItem();
        // Stored normalized (comma separated), so editing only the layout is not a modification.
        data.runtimeBoundaries = SpringGraphSettings.normalizeBoundaries(boundaries.getText());
        data.analyzeAfterBuild = analyzeAfterBuild.isSelected();
        return data;
    }

    @Override
    public boolean isModified() {
        SpringGraphSettings.Data stored =
                SpringGraphSettings.getInstance(project).getState().copy();
        stored.runtimeBoundaries = SpringGraphSettings.normalizeBoundaries(stored.runtimeBoundaries);
        return !edited().equals(stored);
    }

    @Override
    public void apply() {
        SpringGraphSettings settings = SpringGraphSettings.getInstance(project);
        SpringGraphSettings.Data before = settings.getState().copy();
        SpringGraphSettings.Data after = edited();
        settings.loadState(after);

        boolean analysisChanged = !java.util.Objects.equals(before.mode, after.mode)
                || !SpringGraphSettings.normalizeBoundaries(before.runtimeBoundaries)
                        .equals(after.runtimeBoundaries);
        if (analysisChanged && settings.source() != SpringGraphSettings.Source.BUILD) {
            // New mode or boundaries only take effect in a new analysis; it reloads the reports when done.
            SpringGraphAnalyzer.getInstance(project).analyze(false);
        } else {
            SpringGraphReports.getInstance(project).reload();
        }
    }

    @Override
    public void reset() {
        SpringGraphSettings.Data data = SpringGraphSettings.getInstance(project).getState();
        source.setSelectedItem(SpringGraphSettings.Source.parse(data.source));
        mode.setSelectedItem(SpringGraphSettings.MODES.contains(data.mode) ? data.mode : "advisory");
        boundaries.setText(
                SpringGraphSettings.normalizeBoundaries(data.runtimeBoundaries).replace(",", "\n"));
        analyzeAfterBuild.setSelected(data.analyzeAfterBuild);
    }

    @Override
    public void disposeUIResources() {
        source = null;
        mode = null;
        boundaries = null;
        analyzeAfterBuild = null;
    }
}
