package com.ttl.internal.vn.tool.builder.component.input;

import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.GroupLayout;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableCellRenderer;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ttl.internal.vn.tool.builder.component.ISimpleComponent;
import com.ttl.internal.vn.tool.builder.component.input.Table.EllipsisMultilineCellRenderer;
import com.ttl.internal.vn.tool.builder.component.input.Table.RowSpanCellRenderer;
import com.ttl.internal.vn.tool.builder.git.GitCommit;
import com.ttl.internal.vn.tool.builder.git.GitRef;
import com.ttl.internal.vn.tool.builder.git.GitWalk;

public class GitTreeView extends JPanel implements ISimpleComponent {
    private final transient Logger logger = LogManager.getLogger(GitTreeView.class);
    private transient List<GitCommit> orderedCommits = new ArrayList<>();
    private final transient GitWalk gitWalk;
    private Table<GitCommit> table;
    private boolean diffToWorkingDirectory;

    // parent -> children commit hashes
    private Map<String, Set<String>> pendingEdges = new HashMap<>();

    public static class GitTreeViewGraphColumnRenderer extends JPanel implements TableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
                int row, int column) {
            if (column == 0 && row == 0) {
                table.setRowHeight(0, table.getRowHeight());
            }

            // cell backgroud color when selected
            if (isSelected) {
                setBackground(UIManager.getColor("Table.selectionBackground"));
            } else {
                setBackground(UIManager.getColor("Table.background"));
            }

            return this;
        }
    }

    public GitTreeView(GitWalk gitWalk) {
        super();
        this.gitWalk = gitWalk;
        initUI();
        registerListeners();
    }

    public void setDiffToWorkingDirectory(boolean diffToWorkingDirectory) {
        this.diffToWorkingDirectory = diffToWorkingDirectory;
        if (diffToWorkingDirectory) {
            table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        } else {
            table.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
        }
    }
    
    public boolean isDiffToWorkingDirectory() {
        return diffToWorkingDirectory;
    }

    @Override
    public void initUI() {
        this.table = new Table<>(Arrays.asList(
                "Graph",
                "Branch/tag",
                "Commit message",
                "Commit time",
                "SHA1")) {
            @Override
            public List<Object> convertToRow(GitCommit commit) {
                String[] refs = commit.getRefs().stream().map(GitRef::getShortName).filter(StringUtils::isNotBlank)
                        .toArray(String[]::new);
                return List.of("", refs, commit.getMessage(), commit.getAuthorTime().toString(), commit.getHash());
            }
        };
        table.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
        table.getColumnModel().getColumn(1).setCellRenderer(new EllipsisMultilineCellRenderer());
        table.getColumnModel().getColumn(0).setCellRenderer(new RowSpanCellRenderer());
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        JScrollPane scrollPane = new JScrollPane(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setViewportView(table);

        GroupLayout mainLayout = new GroupLayout(this);
        mainLayout.setAutoCreateContainerGaps(true);
        mainLayout.setAutoCreateGaps(true);
        setLayout(mainLayout);

        mainLayout.setHorizontalGroup(mainLayout.createParallelGroup().addComponent(scrollPane));
        mainLayout.setVerticalGroup(mainLayout.createSequentialGroup().addComponent(scrollPane));
    }

    public void walkAll() {
        GitCommit commit;
        orderedCommits.clear();
        while ((commit = gitWalk.walk()) != null) {
            orderedCommits.add(commit);
        }
        table.setDatas(orderedCommits);
    }

    public void initializeGraph() {
        // TODO: Implement something here
    }

    // NOTE: This is expensive so don't call this if you don't need to
    public void resetGraph() {
        orderedCommits.clear();
        table.clearDatas();
        walkAll();
        initializeGraph();
    }

    public void addListSelectionListener(ListSelectionListener l) {
        table.getSelectionModel().addListSelectionListener(l);
    }

    @Override
    public void refreshUI() {
        /** noop */
    }

    @Override
    public void registerListeners() {
        /** noop */
    }

    @Override
    public void handleException(Exception e) throws Exception {
        logger.error(e.getMessage(), e);
        // Rethrow exception, let the parent handle exception
        throw e;
    }

    @Override
    public void setComponentPopupMenu(JPopupMenu menu) {
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    menu.show(table, e.getX(), e.getY());
                }
            }
        });
    }

    public void clearSelection() {
        table.clearSelection();
    }

    public ListSelectionModel getSelectionModel() {
        return table.getSelectionModel();
    }

    public int[] getSelectedColumns() {
        return table.getSelectedColumns();
    }

    public int[] getSelectedRows() {
        return table.getSelectedRows();
    }

    public int getSelectedColumn() {
        return table.getSelectedColumn();
    }

    public GitCommit getCommit(int row) {
        return orderedCommits.get(row);
    }

    public int getSelectedRow() {
        return table.getSelectedRow();
    }

    private String filterString;
    private List<Integer> foundRows;
    private int currentSearchIndex;

    public void runFilter(String filterString) {
        // Type of filtering
        // + Commit hash start with filter string
        // + Refs short name start with filter string
        // + Commit hash contains filter string
        // + Refs short name contains filter string
        // + Message contains filter string

        if (!filterString.equals(this.filterString)) {
            this.filterString = filterString;

            // Populate found rows initially
            if (StringUtils.isNotBlank(filterString) && (foundRows == null || foundRows.isEmpty())) {
                currentSearchIndex = -1;
                foundRows = new ArrayList<>();
                for (int i = 0; i < orderedCommits.size(); i++) {
                    GitCommit commit = orderedCommits.get(i);
                    String lowerCaseFilterString = filterString.toLowerCase();
                    String commitHashLowerCase = commit.getHash().toLowerCase();
                    String lowerCaseCommentMsg = commit.getMessage().toLowerCase();
                    List<String> lowerCaseRefShortNames = commit.getRefs().stream().map(GitRef::getShortName)
                            .collect(Collectors.toList());
                    if (commitHashLowerCase.startsWith(lowerCaseFilterString) ||
                            lowerCaseRefShortNames.stream().anyMatch(ref -> ref.startsWith(lowerCaseFilterString)) ||
                            commitHashLowerCase.contains(lowerCaseFilterString) ||
                            lowerCaseRefShortNames.stream().anyMatch(ref -> ref.contains(lowerCaseFilterString)) ||
                            lowerCaseCommentMsg.contains(lowerCaseFilterString)) {
                        foundRows.add(i);
                    }
                }
            } else {
                currentSearchIndex = -1;
                foundRows = new ArrayList<>();
            }
        }

        // Scroll to result
        if (foundRows != null && !foundRows.isEmpty()) {
            currentSearchIndex = (currentSearchIndex + 1) % foundRows.size();

            int foundRow = foundRows.get(currentSearchIndex);

            table.getSelectionModel().setSelectionInterval(foundRow, foundRow);
            table.scrollRectToVisible(table.getCellRect(table.getSelectedRow(), 0, true));
        }
    }

    public void setGitBranch(List<String> selectedBranches) throws IOException {
        gitWalk.setGitBranch(selectedBranches);
        resetFilter();
    }
    
    public void setGitCommits(List<String> selectedCommits) throws IOException {
        gitWalk.setGitCommit(selectedCommits);
        resetFilter();
    }
    
    private void resetFilter() {
        filterString = null;
        foundRows = new ArrayList<>();
        currentSearchIndex = -1;
    }
}
