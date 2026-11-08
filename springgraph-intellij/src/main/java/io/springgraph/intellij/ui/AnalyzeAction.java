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
package io.springgraph.intellij.ui;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import io.springgraph.intellij.analysis.SpringGraphAnalyzer;
import io.springgraph.intellij.settings.SpringGraphSettings;
import org.jetbrains.annotations.NotNull;

/** Runs the IDE analysis with the bundled processor. It waits for indexing itself, so it is available in dumb mode. */
public final class AnalyzeAction extends DumbAwareAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        if (event.getProject() != null) {
            SpringGraphAnalyzer.getInstance(event.getProject()).analyze(true);
        }
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        boolean available = event.getProject() != null
                && SpringGraphSettings.getInstance(event.getProject()).source() != SpringGraphSettings.Source.BUILD;
        event.getPresentation().setEnabled(available);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
