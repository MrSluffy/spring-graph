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
package io.springgraph.intellij.analysis;

import com.intellij.ide.trustedProjects.TrustedProjects;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import io.springgraph.intellij.SpringGraphReports;
import io.springgraph.intellij.settings.SpringGraphSettings;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

/**
 * The IDE analysis: runs the bundled, published processor over each Spring module's main sources with the project's
 * {@link SpringGraphSettings}, in the background and cancellable. One run at a time; a request during a run is queued
 * once. Results are plain {@code graph.json} files, shown by {@link SpringGraphReports} like build reports.
 */
@Service(Service.Level.PROJECT)
public final class SpringGraphAnalyzer {

    private static final Logger LOG = Logger.getInstance(SpringGraphAnalyzer.class);
    private static final long TIMEOUT_SECONDS = 300;

    private final Project project;
    private Predicate<Project> trustCheck = TrustedProjects::isProjectTrusted;
    private boolean running;
    private boolean rerun;
    private boolean rerunNotify;

    public SpringGraphAnalyzer(Project project) {
        this.project = project;
    }

    public static SpringGraphAnalyzer getInstance(Project project) {
        return project.getService(SpringGraphAnalyzer.class);
    }

    /**
     * Starts an analysis of the modules the current source setting needs it for.
     *
     * @param notify show a summary when done; failures are always shown
     */
    public void analyze(boolean notify) {
        // The analysis runs the project's own annotation processors (from its processor path or classpath) and a
        // compiler over its sources: code the project controls. IntelliJ does not build untrusted (safe mode) projects
        // for the same reason, so neither does this.
        if (!trustCheck.test(project)) {
            notify(
                    NotificationType.WARNING,
                    "SpringGraph does not analyse an untrusted project, because the analysis"
                            + " runs the project's annotation processors. Trust the project first (the safe mode banner, or"
                            + " reopen it and choose Trust Project).");
            return;
        }
        synchronized (this) {
            if (running) {
                rerun = true;
                rerunNotify |= notify;
                return;
            }
            running = true;
        }
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "SpringGraph: analyzing", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                runAnalysis(indicator, notify);
            }

            @Override
            public void onFinished() {
                boolean again;
                boolean againNotify;
                synchronized (SpringGraphAnalyzer.this) {
                    running = false;
                    again = rerun;
                    againNotify = rerunNotify;
                    rerun = false;
                    rerunNotify = false;
                }
                SpringGraphReports.getInstance(project).reload();
                if (again && !project.isDisposed()) {
                    analyze(againNotify);
                }
            }
        });
    }

    private void runAnalysis(ProgressIndicator indicator, boolean notify) {
        SpringGraphSettings settings = SpringGraphSettings.getInstance(project);
        if (settings.source() == SpringGraphSettings.Source.BUILD) {
            if (notify) {
                notify(
                        NotificationType.INFORMATION,
                        "IDE analysis is off: SpringGraph shows build reports only."
                                + " Change it under Settings | Tools | SpringGraph.");
            }
            return;
        }
        Path processor;
        try {
            processor = BundledProcessor.load().extractTo(IdeReports.processorCache());
        } catch (IOException | RuntimeException ex) {
            LOG.warn("Bundled SpringGraph processor unavailable", ex);
            notify(NotificationType.ERROR, "The bundled processor could not be prepared: " + ex.getMessage());
            return;
        }

        indicator.setText("SpringGraph: waiting for indexing");
        Set<String> withBuildReport = SpringGraphReports.getInstance(project).modulesWithBuildReports();
        // Waits for indexing (finding Spring on a module's classpath needs it) and restarts if a write action
        // interrupts it, instead of blocking writes.
        List<ModuleAnalysisPlanner.Plan> plans = ReadAction.nonBlocking(() -> {
                    List<ModuleAnalysisPlanner.Plan> result = new ArrayList<>();
                    for (Module module : ModuleManager.getInstance(project).getModules()) {
                        if (settings.source() == SpringGraphSettings.Source.AUTO
                                && withBuildReport.contains(module.getName())) {
                            continue; // its build already runs the processor
                        }
                        result.add(ModuleAnalysisPlanner.plan(
                                project, module, processor, settings.mode(), settings.runtimeBoundaries()));
                    }
                    return result;
                })
                .inSmartMode(project)
                .wrapProgress(indicator)
                .executeSynchronously();

        List<ModuleAnalysisPlanner.Run> runs = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (ModuleAnalysisPlanner.Plan plan : plans) {
            if (plan instanceof ModuleAnalysisPlanner.Run run) {
                runs.add(run);
            } else if (plan instanceof ModuleAnalysisPlanner.Skip skip) {
                skipped.add(skip.module() + ": " + skip.reason());
            }
        }

        List<String> analysed = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        indicator.setIndeterminate(false);
        for (int i = 0; i < runs.size(); i++) {
            indicator.checkCanceled();
            ModuleAnalysisPlanner.Run run = runs.get(i);
            indicator.setText("SpringGraph: analyzing " + run.module());
            indicator.setFraction((double) i / runs.size());
            try {
                JavacRequest request = ModuleAnalysisPlanner.collectSources(run);
                if (request.sources().isEmpty()) {
                    skipped.add(run.module() + ": no .java files");
                    continue;
                }
                JavacRunner.Result result = JavacRunner.run(request, TIMEOUT_SECONDS, indicator::isCanceled);
                if (result.report() != null) {
                    analysed.add(run.module());
                } else {
                    LOG.info("SpringGraph analysis of " + run.module() + " wrote no report:\n" + result.output());
                    failed.add(run.module() + ": javac exited with " + result.exitCode() + "\n"
                            + StringUtil.first(result.output().strip(), 600, true));
                }
            } catch (InterruptedException ex) {
                if (indicator.isCanceled()) {
                    throw new ProcessCanceledException();
                }
                failed.add(run.module() + ": " + ex.getMessage());
            } catch (IOException | RuntimeException ex) {
                LOG.warn("SpringGraph analysis of " + run.module() + " failed", ex);
                failed.add(run.module() + ": " + ex.getMessage());
            }
        }

        if (!failed.isEmpty()) {
            notify(NotificationType.WARNING, "Analysis failed for " + String.join("\n\n", failed));
        }
        if (notify) {
            String summary = analysed.isEmpty()
                    ? "No module was analysed."
                    : "Analysed " + String.join(", ", analysed) + " with processor "
                            + BundledProcessor.load().version() + " (" + settings.mode() + ").";
            if (settings.source() == SpringGraphSettings.Source.AUTO && !withBuildReport.isEmpty()) {
                summary += "\nUsing the build's report for " + String.join(", ", withBuildReport) + ".";
            }
            if (!skipped.isEmpty()) {
                summary += "\nSkipped " + String.join("; ", skipped) + ".";
            }
            notify(analysed.isEmpty() ? NotificationType.WARNING : NotificationType.INFORMATION, summary);
        }
    }

    @TestOnly
    void setTrustCheckForTests(Predicate<Project> check) {
        trustCheck = check;
    }

    private void notify(NotificationType type, String message) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup("SpringGraph")
                .createNotification(
                        "SpringGraph", StringUtil.escapeXmlEntities(message).replace("\n", "<br>"), type)
                .notify(project);
    }
}
