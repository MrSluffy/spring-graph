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

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.ui.UIUtil;
import io.springgraph.intellij.report.ReportSnapshot;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SpringGraphReportsTest extends BasePlatformTestCase {

    public void testOlderCompletionCannotReplaceNewerSnapshot() throws Exception {
        SpringGraphReports reports = new SpringGraphReports(getProject());
        ReportSnapshot latest = snapshot("latest");
        advanceGeneration(reports, 2);
        complete(reports, 2, latest);
        complete(reports, 1, snapshot("older"));
        assertSame(latest, reports.snapshot());
        UIUtil.dispatchAllInvocationEvents();
    }

    public void testSupersededUiNotificationIsDiscarded() throws Exception {
        SpringGraphReports reports = new SpringGraphReports(getProject());
        List<ReportSnapshot> notifications = new ArrayList<>();
        getProject()
                .getMessageBus()
                .connect(getTestRootDisposable())
                .subscribe(SpringGraphReports.TOPIC, notifications::add);
        advanceGeneration(reports, 1);
        complete(reports, 1, snapshot("older"));
        advanceGeneration(reports, 2);
        ReportSnapshot latest = snapshot("latest");
        complete(reports, 2, latest);
        UIUtil.dispatchAllInvocationEvents();
        assertEquals(List.of(latest), notifications);
    }

    // Simulate request registration and completion deterministically, without racing disk IO or pooled threads.
    private static void advanceGeneration(SpringGraphReports reports, long generation) throws Exception {
        Field field = SpringGraphReports.class.getDeclaredField("generation");
        field.setAccessible(true);
        field.setLong(reports, generation);
        reports.setSnapshotForTests(ReportSnapshot.EMPTY);
    }

    private static void complete(SpringGraphReports reports, long generation, ReportSnapshot snapshot)
            throws Exception {
        Method method = SpringGraphReports.class.getDeclaredMethod("update", long.class, ReportSnapshot.class);
        method.setAccessible(true);
        method.invoke(reports, generation, snapshot);
    }

    private static ReportSnapshot snapshot(String label) {
        return ReportSnapshot.of(List.of(), List.of(new ReportSnapshot.Problem(Path.of(label), label)));
    }
}
