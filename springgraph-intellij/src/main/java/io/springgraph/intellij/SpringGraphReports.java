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
import io.springgraph.intellij.report.ReportSnapshot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.TestOnly;

/**
 * Holds the SpringGraph reports of the last build. Reports are reloaded after every build started from the IDE
 * ({@link BuildFinishedListener}), on Refresh, and lazily on first use. Loading runs in the background; listeners on
 * {@link #TOPIC} and the editor gutters are refreshed when it finishes.
 *
 * <p>The plugin never analyses code: everything shown comes from these files, so it always matches what the build
 * reported.
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
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            if (project.isDisposed()) {
                return;
            }
            Set<Path> candidates =
                    ReadAction.compute(() -> project.isDisposed() ? Set.of() : ReportLocator.candidates(project));
            update(load(candidates));
        });
    }

    private ReportSnapshot load(Set<Path> candidates) {
        List<ReportSnapshot.Loaded> reports = new ArrayList<>();
        List<ReportSnapshot.Problem> problems = new ArrayList<>();
        for (Path file : candidates) {
            if (!Files.isRegularFile(file)) {
                continue;
            }
            try {
                reports.add(
                        new ReportSnapshot.Loaded(file, ReportLocator.label(project, file), ReportReader.read(file)));
            } catch (IOException | RuntimeException ex) {
                LOG.info("Could not read SpringGraph report " + file, ex);
                problems.add(new ReportSnapshot.Problem(file, ex.getMessage()));
            }
        }
        reports.sort(Comparator.comparing(ReportSnapshot.Loaded::label));
        return ReportSnapshot.of(reports, problems);
    }

    private void update(ReportSnapshot loaded) {
        snapshot = loaded;
        ApplicationManager.getApplication()
                .invokeLater(
                        () -> {
                            // Re-run the gutter providers with the new reports; the reason shows in daemon logs.
                            DaemonCodeAnalyzer.getInstance(project).restart("SpringGraph reports reloaded");
                            project.getMessageBus().syncPublisher(TOPIC).reportsChanged(loaded);
                        },
                        project.getDisposed());
    }

    @TestOnly
    public void setSnapshotForTests(ReportSnapshot loaded) {
        requested.set(true);
        snapshot = loaded;
    }
}
