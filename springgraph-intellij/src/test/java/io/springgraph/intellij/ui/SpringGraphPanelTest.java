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

import com.intellij.psi.PsiParameter;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import io.springgraph.intellij.RealJdk;
import io.springgraph.intellij.report.ReportReaderTest;
import io.springgraph.intellij.report.ReportSnapshot;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.tree.DefaultMutableTreeNode;
import org.jetbrains.annotations.NotNull;

public class SpringGraphPanelTest extends LightJavaCodeInsightFixtureTestCase {

    @Override
    protected @NotNull LightProjectDescriptor getProjectDescriptor() {
        return RealJdk.DESCRIPTOR;
    }

    public void testTreeShowsCoverageFindingsComponentsAndExclusions() throws Exception {
        ReportSnapshot snapshot = ReportSnapshot.of(
                List.of(new ReportSnapshot.Loaded(
                        Path.of("graph.json"), "app/build/classes/java/main", ReportReaderTest.processorSnapshot())),
                List.of(new ReportSnapshot.Problem(Path.of("broken/graph.json"), "not a valid SpringGraph report")));

        DefaultMutableTreeNode root = SpringGraphPanel.build(getProject(), snapshot);
        assertEquals(2, root.getChildCount());

        DefaultMutableTreeNode report = (DefaultMutableTreeNode) root.getChildAt(0);
        assertEquals("app/build/classes/java/main advisory, 4 of 15 dependencies validated", text(report));
        assertEquals(List.of("Findings (11)", "Components (13)", "Excluded (3)"), children(report));

        DefaultMutableTreeNode findings = (DefaultMutableTreeNode) report.getChildAt(0);
        assertEquals(
                "SG004 p.Proto Unsupported scope 'prototype' on p.Proto. Only singleton components are validated;"
                        + " this component is excluded from the graph.",
                text(findings.getChildAt(0)));

        DefaultMutableTreeNode components = (DefaultMutableTreeNode) report.getChildAt(1);
        DefaultMutableTreeNode orders = child(components, "p.OrderService");
        assertEquals("p.Gateway validated: p.DefaultGateway", text(orders.getChildAt(0)));
        assertEquals(
                "p.JpaStore conditional (@Profile), never used to resolve a dependency",
                text(child(components, "p.JpaStore")));

        DefaultMutableTreeNode excluded = (DefaultMutableTreeNode) report.getChildAt(2);
        assertEquals(
                "p.Config @Configuration is not interpreted (its @Bean methods are unknown)",
                text(excluded.getChildAt(2)));

        assertTrue(text(root.getChildAt(1)).endsWith("could not be read: not a valid SpringGraph report"));
    }

    public void testDependencyRowNavigatesToTheParameter() throws Exception {
        for (String type : List.of("Gateway", "Clock", "OrderRepo", "Missing")) {
            myFixture.addClass("package p; public interface " + type + " {}");
        }
        myFixture.addClass("""
                package p;
                public class OrderService {
                    public OrderService(Gateway gateway, Clock clock, OrderRepo repo, String value, Missing missing) {}
                }
                """);
        ReportSnapshot snapshot = ReportSnapshot.of(
                List.of(new ReportSnapshot.Loaded(Path.of("graph.json"), "app", ReportReaderTest.processorSnapshot())),
                List.of());
        DefaultMutableTreeNode components = (DefaultMutableTreeNode)
                SpringGraphPanel.build(getProject(), snapshot).getChildAt(0).getChildAt(1);
        DefaultMutableTreeNode clock =
                (DefaultMutableTreeNode) child(components, "p.OrderService").getChildAt(1);

        Object target = ((SpringGraphPanel.Node) clock.getUserObject()).target().get();
        assertInstanceOf(target, PsiParameter.class);
        assertEquals("clock", ((PsiParameter) target).getName());
    }

    private static String text(Object node) {
        return ((DefaultMutableTreeNode) node).getUserObject().toString();
    }

    private static List<String> children(DefaultMutableTreeNode node) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < node.getChildCount(); i++) {
            result.add(text(node.getChildAt(i)));
        }
        return result;
    }

    private static DefaultMutableTreeNode child(DefaultMutableTreeNode node, String text) {
        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
            if (((SpringGraphPanel.Node) child.getUserObject()).text().equals(text)) {
                return child;
            }
        }
        throw new AssertionError("no child '" + text + "' in " + children(node));
    }
}
