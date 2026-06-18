package com.origin.idea;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.KeyStroke;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class DeepProjectSearchToolWindowFactory implements ToolWindowFactory {
    @Override
    public boolean shouldBeAvailable(@NotNull Project project) {
        return true;
    }

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        DeepProjectSearchToolWindow searchToolWindow = new DeepProjectSearchToolWindow(project);
        Content content = ContentFactory.getInstance().createContent(searchToolWindow.getContent(), null, false);
        toolWindow.getContentManager().addContent(content);
    }

    private static final class DeepProjectSearchToolWindow {
        private static final String HISTORY_KEY = "DeepProjectSearch.history";
        private static final int MAX_HISTORY_ITEMS = 12;

        private final Project project;
        private final JComboBox<String> queryComboBox = new JComboBox<>();
        private final JTextField queryField;
        private final JComboBox<SearchScopeOption> scopeComboBox = new JComboBox<>(SearchScopeOption.values());
        private final JCheckBox caseSensitiveCheckBox = new JCheckBox(MyMessageBundle.message("toolwindow.DeepProjectSearch.caseSensitive"));
        private final DefaultListModel<DeepSearchResult> resultListModel = new DefaultListModel<>();
        private final JBList<DeepSearchResult> resultList = new JBList<>(resultListModel);
        private final JBLabel statusLabel = new JBLabel(MyMessageBundle.message("toolwindow.DeepProjectSearch.status.ready"));
        private final JBPanel<?> content = new JBPanel<>(new BorderLayout(0, 10));

        private DeepProjectSearchToolWindow(Project project) {
            this.project = project;
            queryComboBox.setEditable(true);
            loadSearchHistory();
            Component editorComponent = queryComboBox.getEditor().getEditorComponent();
            queryField = editorComponent instanceof JTextField textField ? textField : new JTextField();
            configureList();
            content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
            content.add(createSearchBar(), BorderLayout.NORTH);
            content.add(new JBScrollPane(resultList), BorderLayout.CENTER);
            content.add(statusLabel, BorderLayout.SOUTH);
        }

        private JComponent getContent() {
            return content;
        }

        private JComponent createSearchBar() {
            JBPanel<?> panel = new JBPanel<>(new BorderLayout(0, 8));
            queryField.setToolTipText(MyMessageBundle.message("toolwindow.DeepProjectSearch.query.tooltip"));
            queryField.addActionListener(event -> runSearch());
            queryField.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "runSearch");
            queryField.getActionMap().put("runSearch", new javax.swing.AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent event) {
                    runSearch();
                }
            });

            JBPanel<?> queryPanel = new JBPanel<>(new BorderLayout(6, 0));
            queryPanel.add(new JBLabel(MyMessageBundle.message("toolwindow.DeepProjectSearch.query.label")), BorderLayout.WEST);
            queryPanel.add(queryComboBox, BorderLayout.CENTER);
            panel.add(queryPanel, BorderLayout.NORTH);

            JBPanel<?> controls = new JBPanel<>(new GridLayout(0, 1, 0, 6));
            JBPanel<?> optionRow = new JBPanel<>(new FlowLayout(FlowLayout.LEFT, 8, 0));
            optionRow.add(scopeComboBox);
            optionRow.add(caseSensitiveCheckBox);
            controls.add(optionRow);

            JBPanel<?> actionRow = new JBPanel<>(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            JButton searchButton = new JButton(MyMessageBundle.message("toolwindow.DeepProjectSearch.search.button"));
            searchButton.addActionListener(event -> runSearch());
            JButton openButton = new JButton(MyMessageBundle.message("toolwindow.DeepProjectSearch.open.button"));
            openButton.addActionListener(event -> openSelectedResult());
            JButton copyPathButton = new JButton(MyMessageBundle.message("toolwindow.DeepProjectSearch.copyPath.button"));
            copyPathButton.addActionListener(event -> copySelectedPath());
            JButton clearButton = new JButton(MyMessageBundle.message("toolwindow.DeepProjectSearch.clear.button"));
            clearButton.addActionListener(event -> clearResults());
            actionRow.add(searchButton);
            actionRow.add(openButton);
            actionRow.add(copyPathButton);
            actionRow.add(clearButton);
            controls.add(actionRow);

            panel.add(controls, BorderLayout.CENTER);
            return panel;
        }

        private void configureList() {
            resultList.setCellRenderer(new SearchResultRenderer());
            resultList.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "openSelectedResult");
            resultList.getActionMap().put("openSelectedResult", new javax.swing.AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent event) {
                    openSelectedResult();
                }
            });
            resultList.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    if (event.getClickCount() >= 2) {
                        openSelectedResult();
                    }
                }
            });
        }

        private void runSearch() {
            String query = queryField.getText();
            SearchScopeOption scope = (SearchScopeOption) scopeComboBox.getSelectedItem();
            boolean caseSensitive = caseSensitiveCheckBox.isSelected();
            if (query == null || query.trim().isBlank() || scope == null) {
                resultListModel.clear();
                statusLabel.setText(MyMessageBundle.message("toolwindow.DeepProjectSearch.status.emptyQuery"));
                return;
            }

            rememberSearch(query.trim());
            statusLabel.setText(MyMessageBundle.message("toolwindow.DeepProjectSearch.status.searching"));
            ProgressManager.getInstance().run(new Task.Backgroundable(project, MyMessageBundle.message("toolwindow.DeepProjectSearch.progress.title"), false) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    List<DeepSearchResult> results = ApplicationManager.getApplication().runReadAction(
                            (com.intellij.openapi.util.Computable<List<DeepSearchResult>>) () ->
                                    DeepSearchService.search(project, query, scope, caseSensitive, indicator)
                    );
                    ApplicationManager.getApplication().invokeLater(() -> showResults(results));
                }
            });
        }

        private void showResults(List<DeepSearchResult> results) {
            resultListModel.clear();
            for (DeepSearchResult result : results) {
                resultListModel.addElement(result);
            }
            if (!results.isEmpty()) {
                resultList.setSelectedIndex(0);
                resultList.requestFocusInWindow();
            }
            statusLabel.setText(MyMessageBundle.message("toolwindow.DeepProjectSearch.status.results", results.size()));
        }

        private void openSelectedResult() {
            DeepSearchResult result = resultList.getSelectedValue();
            if (result == null || !result.getVirtualFile().isValid()) {
                statusLabel.setText(MyMessageBundle.message("toolwindow.DeepProjectSearch.status.invalidResult"));
                return;
            }
            if (result.getLineNumber() > 0) {
                new OpenFileDescriptor(
                        project,
                        result.getVirtualFile(),
                        result.getLineNumber() - 1,
                        Math.max(0, result.getColumnNumber())
                ).navigate(true);
                return;
            }
            FileEditorManager.getInstance(project).openFile(result.getVirtualFile(), true, true);
        }

        private void copySelectedPath() {
            DeepSearchResult result = resultList.getSelectedValue();
            if (result == null || !result.getVirtualFile().isValid()) {
                statusLabel.setText(MyMessageBundle.message("toolwindow.DeepProjectSearch.status.invalidResult"));
                return;
            }
            CopyPasteManager.getInstance().setContents(new StringSelection(result.getVirtualFile().getPath()));
            statusLabel.setText(MyMessageBundle.message("toolwindow.DeepProjectSearch.status.copiedPath"));
        }

        private void clearResults() {
            resultListModel.clear();
            statusLabel.setText(MyMessageBundle.message("toolwindow.DeepProjectSearch.status.ready"));
            queryField.requestFocusInWindow();
        }

        private void loadSearchHistory() {
            queryComboBox.removeAllItems();
            for (String item : getStoredHistory()) {
                queryComboBox.addItem(item);
            }
            queryComboBox.setToolTipText(MyMessageBundle.message("toolwindow.DeepProjectSearch.history.tooltip"));
        }

        private void rememberSearch(String query) {
            List<String> history = new ArrayList<>();
            history.add(query);
            for (String item : getStoredHistory()) {
                if (!item.equals(query)) {
                    history.add(item);
                }
                if (history.size() >= MAX_HISTORY_ITEMS) {
                    break;
                }
            }

            PropertiesComponent.getInstance(project).setValue(HISTORY_KEY, String.join("\n", history));
            queryComboBox.removeAllItems();
            for (String item : history) {
                queryComboBox.addItem(item);
            }
            queryField.setText(query);
        }

        private List<String> getStoredHistory() {
            String stored = PropertiesComponent.getInstance(project).getValue(HISTORY_KEY, "");
            if (stored.isBlank()) {
                return List.of();
            }
            return Arrays.stream(stored.split("\\R"))
                    .map(String::trim)
                    .filter(item -> !item.isBlank())
                    .distinct()
                    .limit(MAX_HISTORY_ITEMS)
                    .toList();
        }
    }

    private static final class SearchResultRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (component instanceof JLabel label && value instanceof DeepSearchResult result) {
                String preview = result.getPreview();
                String previewHtml = preview == null || preview.isBlank()
                        ? ""
                        : "<br><small><code>" + escapeHtml(preview) + "</code></small>";
                label.setText("<html><b>" + escapeHtml(result.getName()) + "</b> <small>"
                        + escapeHtml(result.getSourceType().name()) + "</small><br><small>"
                        + escapeHtml(result.getPath()) + "<br>"
                        + escapeHtml(result.getSource()) + "</small>"
                        + previewHtml + "</html>");
                label.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
            }
            return component;
        }

        private static String escapeHtml(String text) {
            return text.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;");
        }
    }
}
