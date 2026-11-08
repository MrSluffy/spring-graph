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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.pom.Navigatable;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.tree.TreeUtil;
import io.springgraph.intellij.Presentation;
import io.springgraph.intellij.PsiLookup;
import io.springgraph.intellij.SpringGraphReports;
import io.springgraph.intellij.report.ReportModel;
import io.springgraph.intellij.report.ReportSnapshot;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.Icon;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import org.jetbrains.annotations.NotNull;

/**
 * The SpringGraph tool window: one node per report (module), with its findings, analysed components and their
 * dependencies, and excluded classes. Double-click or Enter navigates to the source.
 */
public final class SpringGraphPanel extends SimpleToolWindowPanel {

    /** A tree row. {@code target} is resolved only when the user navigates. */
    record Node(String text, String detail, Icon icon, Supplier<Navigatable> target) {
        @Override
        public String toString() {
            return detail == null ? text : text + " " + detail; // used by speed search
        }
    }

    private final Project project;
    private final Tree tree = new Tree(new DefaultTreeModel(new DefaultMutableTreeNode()));

    public SpringGraphPanel(Project project, Disposable parent) {
        super(true, true);
        this.project = project;

        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new Renderer());
        tree.getEmptyText()
                .setText("No SpringGraph report found")
                .appendLine("Build the project with -Aspringgraph.mode=advisory, then refresh");
        TreeSpeedSearch.installOn(tree);
        new DoubleClickListener() {
            @Override
            protected boolean onDoubleClick(@NotNull MouseEvent event) {
                return navigateToSelection();
            }
        }.installOn(tree);
        tree.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.VK_ENTER && navigateToSelection()) {
                    event.consume();
                }
            }
        });
        setContent(ScrollPaneFactory.createScrollPane(tree));

        ActionToolbar toolbar = ActionManager.getInstance()
                .createActionToolbar(
                        "SpringGraph",
                        (ActionGroup) ActionManager.getInstance().getAction("SpringGraph.ToolWindowToolbar"),
                        true);
        toolbar.setTargetComponent(this);
        setToolbar(toolbar.getComponent());

        project.getMessageBus().connect(parent).subscribe(SpringGraphReports.TOPIC, this::show);
        show(SpringGraphReports.getInstance(project).snapshot());
    }

    private void show(ReportSnapshot snapshot) {
        tree.setModel(new DefaultTreeModel(build(project, snapshot)));
        TreeUtil.expand(tree, 2);
    }

    /** The tree for a snapshot; separate from Swing state so it can be tested. */
    static DefaultMutableTreeNode build(Project project, ReportSnapshot snapshot) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode();
        for (ReportSnapshot.Loaded report : snapshot.reports()) {
            root.add(reportNode(project, snapshot, report));
        }
        for (ReportSnapshot.Problem problem : snapshot.problems()) {
            root.add(leaf(new Node(
                    problem.file().toString(),
                    "could not be read: " + problem.message(),
                    AllIcons.General.Error,
                    null)));
        }
        return root;
    }

    private static DefaultMutableTreeNode reportNode(
            Project project, ReportSnapshot snapshot, ReportSnapshot.Loaded report) {
        ReportModel model = report.model();
        String coverage = model.coverage == null
                ? ""
                : ", " + model.coverage.validated + " of " + model.coverage.total + " dependencies validated";
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(
                new Node(report.label(), model.mode + coverage, AllIcons.Nodes.Module, null));

        List<ReportModel.Finding> findings = model.findings();
        DefaultMutableTreeNode findingsNode = group("Findings", findings.size());
        for (ReportModel.Finding finding : findings) {
            ReportSnapshot.Entry entry = snapshot.component(finding.component);
            ReportModel.Component component = entry == null ? null : entry.component();
            findingsNode.add(leaf(new Node(
                    finding.code + (finding.component == null ? "" : " " + finding.component),
                    finding.summary(),
                    Presentation.icon(finding),
                    finding.component == null
                            ? null
                            : () -> PsiLookup.target(project, finding.component, component, finding.parameter))));
        }
        node.add(findingsNode);

        List<ReportModel.Component> components = model.components();
        DefaultMutableTreeNode componentsNode = group("Components", components.size());
        for (ReportModel.Component component : components) {
            DefaultMutableTreeNode componentNode = new DefaultMutableTreeNode(new Node(
                    component.name,
                    component.conditional == null
                            ? null
                            : "conditional (" + component.conditional + "), never used to resolve a dependency",
                    AllIcons.Nodes.Class,
                    () -> PsiLookup.target(project, component.name, null, null)));
            for (ReportModel.Dependency dependency : component.dependencies()) {
                componentNode.add(leaf(new Node(
                        Presentation.requested(dependency),
                        Presentation.describe(dependency),
                        Presentation.icon(dependency.resolution()),
                        () -> PsiLookup.target(project, component.name, component, dependency.index))));
            }
            componentsNode.add(componentNode);
        }
        node.add(componentsNode);

        List<ReportModel.Excluded> excluded = model.excluded();
        DefaultMutableTreeNode excludedNode = group("Excluded", excluded.size());
        for (ReportModel.Excluded entry : excluded) {
            excludedNode.add(leaf(new Node(
                    entry.name,
                    entry.reason,
                    entry.configuration ? AllIcons.Nodes.Class : AllIcons.Nodes.ExceptionClass,
                    () -> PsiLookup.target(project, entry.name, null, null))));
        }
        node.add(excludedNode);
        return node;
    }

    private static DefaultMutableTreeNode group(String name, int size) {
        return new DefaultMutableTreeNode(new Node(name, "(" + size + ")", AllIcons.Nodes.Folder, null));
    }

    private static DefaultMutableTreeNode leaf(Node node) {
        return new DefaultMutableTreeNode(node, false);
    }

    private boolean navigateToSelection() {
        Object selected = tree.getLastSelectedPathComponent();
        if (!(selected instanceof DefaultMutableTreeNode treeNode)
                || !(treeNode.getUserObject() instanceof Node node)
                || node.target() == null) {
            return false;
        }
        if (DumbService.isDumb(project)) {
            return false;
        }
        Navigatable target = ReadAction.compute(() -> node.target().get());
        if (target == null || !target.canNavigate()) {
            return false;
        }
        target.navigate(true);
        return true;
    }

    private static final class Renderer extends ColoredTreeCellRenderer {
        @Override
        public void customizeCellRenderer(
                @NotNull JTree tree,
                Object value,
                boolean selected,
                boolean expanded,
                boolean leaf,
                int row,
                boolean hasFocus) {
            if (value instanceof DefaultMutableTreeNode treeNode && treeNode.getUserObject() instanceof Node node) {
                setIcon(node.icon());
                append(node.text());
                if (node.detail() != null) {
                    append("  " + node.detail(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
                }
            }
        }
    }
}
