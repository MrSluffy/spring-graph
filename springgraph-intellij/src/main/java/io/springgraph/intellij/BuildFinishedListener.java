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

import com.intellij.openapi.project.Project;
import com.intellij.task.ProjectTaskListener;
import com.intellij.task.ProjectTaskManager;
import org.jetbrains.annotations.NotNull;

/**
 * Reloads the reports after Build / Rebuild from the IDE, including builds delegated to Gradle or Maven. Successful or
 * not: a failed strict build still writes its report.
 */
public final class BuildFinishedListener implements ProjectTaskListener {

    private final Project project;

    public BuildFinishedListener(Project project) {
        this.project = project;
    }

    @Override
    public void finished(@NotNull ProjectTaskManager.Result result) {
        SpringGraphReports.getInstance(project).reload();
    }
}
