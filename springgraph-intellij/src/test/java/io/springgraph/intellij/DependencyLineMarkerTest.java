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

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.navigation.NavigationGutterIconRenderer;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiParameter;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import io.springgraph.intellij.report.ReportModel;
import io.springgraph.intellij.report.ReportReaderTest;
import io.springgraph.intellij.report.ReportSnapshot;
import java.nio.file.Path;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/** Gutter icons and navigation, driven by the processor's own snapshot report. */
public class DependencyLineMarkerTest extends LightJavaCodeInsightFixtureTestCase {

    private ReportSnapshot snapshot;

    @Override
    protected @NotNull LightProjectDescriptor getProjectDescriptor() {
        return RealJdk.DESCRIPTOR;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        ReportModel model = ReportReaderTest.processorSnapshot();
        snapshot =
                ReportSnapshot.of(List.of(new ReportSnapshot.Loaded(Path.of("graph.json"), "app", model)), List.of());
        SpringGraphReports.getInstance(getProject()).setSnapshotForTests(snapshot);
        for (String type : List.of("Gateway", "Clock", "OrderRepo", "Missing", "Sender", "Notifier")) {
            myFixture.addClass("package p; public interface " + type + " {}");
        }
        myFixture.addClass("package p; public class DefaultGateway implements Gateway {}");
        myFixture.addClass("package p; public class MailSender implements Sender {}");
        myFixture.addClass("package p; public class SmsSender implements Sender {}");
        myFixture.addClass("package p; public class PrimaryNotifier implements Notifier {}");
    }

    private static final String ORDER_SERVICE = """
            package p;
            public class OrderService {
                public OrderService(Gateway gateway, Clock clock, OrderRepo repo, String value, Missing missing) {}
            }
            """;

    public void testEveryParameterGetsTheBuildsClassification() {
        myFixture.configureByText("OrderService.java", ORDER_SERVICE);
        List<String> tooltips = springGraphTooltips();
        assertEquals(tooltips.toString(), 5, tooltips.size());
        assertContains(tooltips, "p.Gateway: validated: p.DefaultGateway");
        assertContains(tooltips, "p.Clock: Spring runtime boundary, not statically validated");
        assertContains(tooltips, "p.OrderRepo: Spring Data repository, created at runtime");
        assertContains(tooltips, "java.lang.String: @Value property or expression, not a bean");
        assertContains(
                tooltips,
                "p.Missing: missing: no static binding found<br>SG001: No static binding found for p.Missing.");
        assertContains(tooltips, "(advisory build)");
    }

    public void testShowsReasonAndAmbiguousCandidates() {
        myFixture.configureByText("Messaging.java", """
                package p;
                public class Messaging {
                    public Messaging(Sender sender, Sender smsSender, Notifier notifier) {}
                }
                """);
        List<String> tooltips = springGraphTooltips();
        assertContains(tooltips, "p.Sender: ambiguous: p.MailSender, p.SmsSender");
        assertContains(tooltips, "p.Sender: unsupported, not validated<br>SG006: Several beans match p.Sender");
        assertContains(tooltips, "p.Notifier: validated: p.PrimaryNotifier (via @Primary)");
    }

    public void testValidatedDependencyNavigatesToTheChosenBean() {
        myFixture.configureByText("OrderService.java", ORDER_SERVICE);
        assertEquals(List.of("p.DefaultGateway"), targetsOf("p.Gateway: validated"));
    }

    public void testAmbiguousDependencyOffersTheCandidates() {
        myFixture.configureByText("Messaging.java", """
                package p;
                public class Messaging {
                    public Messaging(Sender sender, Sender smsSender, Notifier notifier) {}
                }
                """);
        assertEquals(List.of("p.MailSender", "p.SmsSender"), targetsOf("ambiguous"));
    }

    public void testTooltipEscapesEveryValueFromTheReport() throws Exception {
        ReportModel crafted = io.springgraph.intellij.report.ReportReader.read(new java.io.StringReader("""
                {"schemaVersion": 1, "mode": "<img src='https://example.invalid/x.png'>",
                 "components": [{"name": "p.Victim", "dependencies": [
                   {"index": 0, "parameter": "g", "type": "p.Gateway", "qualifier": "<b>q</b>",
                    "resolution": "MISSING", "targets": []}]}],
                 "findings": [{"code": "<i>SG001</i>", "severity": "ERROR", "component": "p.Victim",
                   "parameter": 0, "message": "<script>x</script>"}]}
                """));
        SpringGraphReports.getInstance(getProject())
                .setSnapshotForTests(ReportSnapshot.of(
                        List.of(new ReportSnapshot.Loaded(Path.of("graph.json"), "app", crafted)), List.of()));
        myFixture.configureByText("Victim.java", """
                package p;
                public class Victim { public Victim(Gateway g) {} }
                """);
        List<String> tooltips = springGraphTooltips();
        assertEquals(tooltips.toString(), 1, tooltips.size());
        String tooltip = tooltips.get(0);
        for (String raw : List.of("<img", "<b>q", "<i>", "<script>")) {
            assertFalse("unescaped '" + raw + "' in " + tooltip, tooltip.contains(raw));
        }
        assertTrue(tooltip, tooltip.contains("&lt;img"));
    }

    public void testChangedConstructorShowsNothing() {
        myFixture.configureByText("OrderService.java", """
                package p;
                public class OrderService {
                    public OrderService(Gateway gateway, Clock clock) {}
                }
                """);
        assertEmpty(springGraphTooltips());
    }

    public void testChangedParameterTypeShowsNothing() {
        myFixture.configureByText("OrderService.java", """
                package p;
                public class OrderService {
                    public OrderService(Gateway gateway, Clock clock, OrderRepo repo, Integer value, Missing missing) {}
                }
                """);
        assertEmpty(springGraphTooltips());
    }

    public void testClassesNotInTheReportShowNothing() {
        myFixture.configureByText("Other.java", """
                package p;
                public class Other { public Other(Gateway gateway) {} }
                """);
        assertEmpty(springGraphTooltips());
    }

    public void testFindingNavigatesToTheParameter() {
        myFixture.configureByText("OrderService.java", ORDER_SERVICE);
        ReportSnapshot.Entry entry = snapshot.component("p.OrderService");
        Navigatable target = PsiLookup.target(getProject(), "p.OrderService", entry.component(), 4);
        assertInstanceOf(target, PsiParameter.class);
        assertEquals("missing", ((PsiParameter) target).getName());

        // An excluded class is only named; it navigates to the class.
        myFixture.addClass("package p; public class Proto {}");
        assertInstanceOf(PsiLookup.target(getProject(), "p.Proto", null, null), PsiClass.class);
        assertNull(PsiLookup.target(getProject(), "p.NotInProject", null, null));
    }

    private List<String> springGraphTooltips() {
        return myFixture.findAllGutters().stream()
                .map(GutterMark::getTooltipText)
                .filter(t -> t != null && t.contains("SpringGraph"))
                .toList();
    }

    private List<String> targetsOf(String tooltipPart) {
        for (GutterMark mark : myFixture.findAllGutters()) {
            String tooltip = mark.getTooltipText();
            if (tooltip != null
                    && tooltip.contains(tooltipPart)
                    && mark instanceof LineMarkerInfo.LineMarkerGutterIconRenderer<?> renderer
                    && renderer.getLineMarkerInfo().getNavigationHandler()
                            instanceof NavigationGutterIconRenderer navigation) {
                return navigation.getTargetElements().stream()
                        .map(e -> ((PsiClass) e).getQualifiedName())
                        .sorted()
                        .toList();
            }
        }
        fail("no navigable SpringGraph gutter icon containing '" + tooltipPart + "'");
        return List.of();
    }

    private static void assertContains(List<String> tooltips, String part) {
        assertTrue(
                "expected a tooltip containing '" + part + "' in " + tooltips,
                tooltips.stream().anyMatch(t -> t.contains(part)));
    }
}
