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

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.Topic;
import io.springgraph.intellij.report.ReportReader;
import io.springgraph.intellij.report.ReportSelection;
import io.springgraph.intellij.report.ReportSnapshot;
import io.springgraph.intellij.settings.SpringGraphSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.TestOnly;

/**
 * Holds the reports shown in the tool window and gutters: those written by the project's build, and those written by
 * the IDE analysis ({@link io.springgraph.intellij.analysis.SpringGraphAnalyzer}), chosen per module by the source
 * setting ({@link ReportSelection}). Reports are reloaded after every build started from the IDE
 * ({@link BuildFinishedListener}), after each IDE analysis, on Refresh, and lazily on first use. Loading runs in the
 * background; listeners on {@link #TOPIC} and the editor gutters are refreshed when it finishes.
 *
 * <p>Either way the processor did the analysis: everything shown comes from its {@code graph.json} files.
 */
@Service(Service.Level.PROJECT)
public final class SpringGraphReports {

    public interface Listener {
        void reportsChanged(ReportSnapshot snapshot);
    }

    public static final Topic<Listener> TOPIC = Topic.create("SpringGraph reports", Listener.class);

    private static final Logger LOG = Logger.getInstance(SpringGraphReports.class);

    private final Project project;
    private final AtomicBoolean requested = new AtomicBoolean();
    private final Object reloadLock = new Object();
    private long generation;
    private volatile ReportSnapshot snapshot = ReportSnapshot.EMPTY;

    public SpringGraphReports(Project project) {
        this.project = project;
    }

    public static SpringGraphReports getInstance(Project project) {
        return project.getService(SpringGraphReports.class);
    }

    /** The current reports; the first call starts loading and returns an empty snapshot until it is done. */
    public ReportSnapshot snapshot() {
        if (requested.compareAndSet(false, true)) {
            reload();
        }
        return snapshot;
    }

    /** Re-reads every report in the background. */
    public void reload() {
        requested.set(true);
        long requestGeneration;
        synchronized (reloadLock) {
            requestGeneration = ++generation;
        }
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            if (project.isDisposed()) {
                return;
            }
            Locations locations = locations();
            if (locations == null) {
                return;
            }
            List<ReportSelection.Candidate> candidates = ReportSelection.select(
                    SpringGraphSettings.getInstance(project).source(),
                    locations.build(),
                    locations.ide(),
                    Files::isRegularFile,
                    file -> ReportLocator.label(project, file));
            update(requestGeneration, load(candidates));
        });
    }

    /** Modules whose build wrote a report; the IDE analysis skips them in AUTO. Call off the UI thread. */
    public Set<String> modulesWithBuildReports() {
        Locations locations = locations();
        return locations == null
                ? Set.of()
                : ReportSelection.modulesWithBuildReports(locations.build(), Files::isRegularFile);
    }

    private record Locations(Map<String, Set<Path>> build, Map<String, Path> ide) {}

    private Locations locations() {
        return ReadAction.compute(() -> project.isDisposed()
                ? null
                : new Locations(ReportLocator.buildCandidates(project), ReportLocator.ideReports(project)));
    }

    private ReportSnapshot load(List<ReportSelection.Candidate> candidates) {
        List<ReportSnapshot.Loaded> reports = new ArrayList<>();
        List<ReportSnapshot.Problem> problems = new ArrayList<>();
        for (ReportSelection.Candidate candidate : candidates) {
            Path file = candidate.file();
            try {
                reports.add(new ReportSnapshot.Loaded(
                        file, candidate.label(), ReportReader.read(file), candidate.origin()));
            } catch (IOException | RuntimeException ex) {
                LOG.info("Could not read SpringGraph report " + file, ex);
                problems.add(new ReportSnapshot.Problem(file, ex.getMessage()));
            }
        }
        reports.sort(Comparator.comparing(ReportSnapshot.Loaded::label));
        return ReportSnapshot.of(reports, problems);
    }

    private void update(long requestGeneration, ReportSnapshot loaded) {
        synchronized (reloadLock) {
            if (project.isDisposed() || requestGeneration != generation) {
                return;
            }
            snapshot = loaded;
            ApplicationManager.getApplication()
                    .invokeLater(
                            () -> {
                                synchronized (reloadLock) {
                                    if (project.isDisposed() || requestGeneration != generation) {
                                        return;
                                    }
                                    // Re-run the gutter providers with the new reports.
                                    DaemonCodeAnalyzer.getInstance(project).restart("SpringGraph reports reloaded");
                                    project.getMessageBus().syncPublisher(TOPIC).reportsChanged(loaded);
                                }
                            },
                            project.getDisposed());
        }
    }

    @TestOnly
    public void setSnapshotForTests(ReportSnapshot loaded) {
        requested.set(true);
        snapshot = loaded;
    }
}
