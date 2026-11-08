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

import com.intellij.notification.Notification;
import com.intellij.notification.Notifications;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.NotNull;

/** The IDE analysis runs project-controlled code, so it must never start in an untrusted project. */
public class SpringGraphAnalyzerTrustTest extends BasePlatformTestCase {

    public void testUntrustedProjectIsNotAnalysed() {
        List<Notification> shown = new ArrayList<>();
        getProject()
                .getMessageBus()
                .connect(getTestRootDisposable())
                .subscribe(Notifications.TOPIC, new Notifications() {
                    @Override
                    public void notify(@NotNull Notification notification) {
                        shown.add(notification);
                    }
                });
        AtomicInteger checks = new AtomicInteger();
        SpringGraphAnalyzer analyzer = new SpringGraphAnalyzer(getProject());
        analyzer.setTrustCheckForTests(project -> {
            checks.incrementAndGet();
            return false;
        });

        analyzer.analyze(true);

        assertEquals(1, checks.get());
        assertEquals(1, shown.size());
        assertTrue(shown.get(0).getContent(), shown.get(0).getContent().contains("untrusted project"));
        // No background task was started.
        assertFalse(ProgressManager.getInstance().hasProgressIndicator());
    }
}
