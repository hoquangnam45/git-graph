package com.ttl.internal.vn.tool.builder.component;

import java.awt.Dimension;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.ItemEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.BoxLayout;
import javax.swing.GroupLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

import com.ttl.internal.vn.tool.builder.cli.CliBuildTool;
import com.ttl.internal.vn.tool.builder.component.input.*;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.helpers.MessageFormatter;

import com.ttl.internal.vn.tool.builder.git.GitCommit;
import com.ttl.internal.vn.tool.builder.git.GitRef;
import com.ttl.internal.vn.tool.builder.git.GitWalk;
import com.ttl.internal.vn.tool.builder.util.GitUtil;
import com.ttl.internal.vn.tool.builder.util.SwingGraphicUtil;

@Getter
public class BuildToolDashBoard extends JFrame implements ISimpleComponent {
    public static final String BRANCH_LABEL = "Branch";
    private static final String COMMIT_LABEL = "Working directory commit";
    private final transient Logger logger = LogManager.getLogger(BuildToolDashBoard.class);
    private final transient GitUtil gitUtil;
    private GitTreeView gitTreeView;
    private ComboBox<String> gitRefsComboBox;
    private List<String> branches;
    private TextField searchCommitTextField;
    private DiffView diffView;
    private ConfigDashBoard dashBoard;
    private Button checkOutButton;
    private Button fetchButton;
    private Button refreshButton;
    private JLabel currentCommitLabel;
    private final transient Session session;
    private CheckBox useWorkingDirectoryCheckbox;
    private TextField entryFilterField;
    private ProgressBar progressBar;

    public BuildToolDashBoard(Session session) throws GitAPIException, IOException {
        super();
        this.gitUtil = session.getGitUtil();
        this.session = session;
        session.setUseWorkingDirectory(true);
        initUI();
        registerListeners();
    }

    private String getWorkingDirectoryCommitLabel() throws IOException {
        return MessageFormatter.format("Commit {}", gitUtil.getHeadRef().getShortCommitHash()).getMessage();
    }

    private void refreshGitRelatedUI() throws IOException {
        if (!session.getUseWorkingDirectory()) {
            gitTreeView.setGitBranch(getSelectedBranches());
            gitRefsComboBox.setLabel(BRANCH_LABEL);
        } else {
            gitTreeView.setGitCommits(Collections.singletonList(gitUtil.getHeadRef().getCommitHash()));
            gitRefsComboBox.setEnabled(false);
            gitRefsComboBox.setEditable(true);
            gitRefsComboBox.setSelectedItem(gitUtil.getHeadRef().getCommitHash());
            gitRefsComboBox.setLabel(COMMIT_LABEL);
        }
        gitTreeView.resetGraph();
    }

    @Override
    public void initUI() throws GitAPIException, IOException {
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("Build tool dashboard");

        this.currentCommitLabel = new JLabel(getWorkingDirectoryCommitLabel());

        this.useWorkingDirectoryCheckbox = new CheckBox(session.getUseWorkingDirectory(), "Use working directory");

        List<GitRef> branchRefs = gitUtil.getBranchRefs(false).stream().map(GitRef::new).filter(it -> !it.isHead())
                .collect(Collectors.toList());
        this.branches = branchRefs.stream().map(GitRef::toString).collect(Collectors.toList());
        List<String> branchOptions = new ArrayList<>(branches);
        branchOptions.add(0, "All branches");
        gitRefsComboBox = new ComboBox<>("", branchOptions, null);
        gitRefsComboBox.setPreferredSize(new Dimension(300, 50));

        this.searchCommitTextField = new TextField("Search commit", 60, 120, true, BoxLayout.Y_AXIS, Collections.emptyList());
        this.entryFilterField = new TextField("Filter entry regex", 60, 120, true, BoxLayout.Y_AXIS, null);
        entryFilterField.setVisible(session.getUseWorkingDirectory());

        this.gitTreeView = new GitTreeView(new GitWalk(gitUtil));
        gitTreeView.setDiffToWorkingDirectory(session.getUseWorkingDirectory());
        refreshGitRelatedUI();

        this.diffView = new DiffView();
        diffView.setVisible(false);

        JPanel btnPanel = new JPanel();
        GroupLayout btnGroupLayout = new GroupLayout(btnPanel);
        btnGroupLayout.setAutoCreateContainerGaps(true);
        btnGroupLayout.setAutoCreateGaps(true);
        btnPanel.setLayout(btnGroupLayout);
        this.checkOutButton = new Button("Checkout commit");
        checkOutButton.setVisible(!session.getUseWorkingDirectory());
        this.fetchButton = new Button("Fetch");
        this.refreshButton = new Button("Sync git view");
        btnGroupLayout.setHorizontalGroup(btnGroupLayout.createSequentialGroup()
                .addComponent(refreshButton)
                .addComponent(fetchButton)
                .addComponent(checkOutButton));
        btnGroupLayout.setVerticalGroup(btnGroupLayout.createParallelGroup()
                .addComponent(refreshButton)
                .addComponent(fetchButton)
                .addComponent(checkOutButton));

        this.dashBoard = new ConfigDashBoard(session, this);

        JPanel gitViewPanel = new JPanel();
        GroupLayout gitGroupLayout = new GroupLayout(gitViewPanel);
        gitGroupLayout.setAutoCreateContainerGaps(true);
        gitGroupLayout.setAutoCreateGaps(true);
        gitViewPanel.setLayout(gitGroupLayout);
        gitGroupLayout.setHorizontalGroup(gitGroupLayout.createParallelGroup()
                .addComponent(currentCommitLabel, GroupLayout.Alignment.TRAILING)
                .addComponent(gitRefsComboBox)
                .addComponent(searchCommitTextField)
                .addComponent(entryFilterField)
                .addComponent(useWorkingDirectoryCheckbox)
                .addComponent(gitTreeView)
                .addComponent(diffView)
                .addComponent(btnPanel)
                .addGap(10));
        gitGroupLayout.setVerticalGroup(gitGroupLayout.createSequentialGroup()
                .addComponent(currentCommitLabel)
                .addComponent(gitRefsComboBox)
                .addComponent(searchCommitTextField)
                .addComponent(entryFilterField)
                .addComponent(useWorkingDirectoryCheckbox)
                .addComponent(gitTreeView)
                .addComponent(diffView)
                .addComponent(btnPanel)
                .addGap(10));

        JPanel container = new JPanel();
        GroupLayout containerGroupLayout = new GroupLayout(container);
        container.setLayout(containerGroupLayout);

        containerGroupLayout.setHorizontalGroup(containerGroupLayout.createSequentialGroup()
                .addComponent(gitViewPanel)
                .addComponent(dashBoard, 0, GroupLayout.PREFERRED_SIZE, Short.MAX_VALUE));
        containerGroupLayout.setVerticalGroup(containerGroupLayout.createParallelGroup()
                .addComponent(gitViewPanel)
                .addComponent(dashBoard));

        this.progressBar = new ProgressBar(GroupLayout.Alignment.LEADING);

        if (session.getUseWorkingDirectory()) {
            fetchButton.setVisible(false);
        }
        GroupLayout mainGroupLayout = new GroupLayout(getContentPane());
        mainGroupLayout.setAutoCreateContainerGaps(true);
        mainGroupLayout.setAutoCreateGaps(true);
        getContentPane().setLayout(mainGroupLayout);
        mainGroupLayout.setHorizontalGroup(mainGroupLayout.createParallelGroup()
                .addComponent(container)
                .addComponent(progressBar));
        mainGroupLayout.setVerticalGroup(mainGroupLayout.createSequentialGroup()
                .addComponent(container)
                .addComponent(progressBar));
        pack();
    }

    private List<String> getSelectedBranches() {
        if (gitRefsComboBox.getSelectedIndex() > 0) {
            return Collections.singletonList(gitRefsComboBox.getSelectedItem());
        }
        return branches;
    }

    @Override
    public void refreshUI() {
        /** noop */
    }

    @Override
    public void registerListeners() {
        gitRefsComboBox.addActionListener(e -> {
            SwingGraphicUtil.updateUI(() -> {
                try {
                    if (!session.getUseWorkingDirectory()) {
                        List<String> selectedBranches = getSelectedBranches();
                        gitTreeView.setGitBranch(selectedBranches);
                    } else {
                        gitTreeView.setGitCommits(Collections.singletonList(gitUtil.getHeadRef().getCommitHash()));
                    }
                } catch (IOException e1) {
                    handleException(e1);
                }
                gitTreeView.clearSelection();
                gitTreeView.resetGraph();
            });
        });
        searchCommitTextField.addActionListener(e -> {
            String searchRef = searchCommitTextField.getText();
            gitTreeView.runFilter(searchRef);
        });
        gitTreeView.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                try {
                    int[] selectedRows = gitTreeView.getSelectedRows();
                    if (selectedRows.length == 0) {
                        session.setBaseCommit(null);
                        session.setTargetCommit(null);
                        checkOutButton.setVisible(false);
                        diffView.setVisible(false);
                    } else if (selectedRows.length == 1) {
                        if (session.getUseWorkingDirectory()) {
                            diffView.setVisible(true);
                            GitCommit baseCommit = gitTreeView.getCommit(selectedRows[0]);
                            session.setBaseCommit(baseCommit);
                            session.setTargetCommit(null);
                            session.setEntryFilter(entryFilterField.getText());
                            checkOutButton.setVisible(false);
                            diffView.setLabel(MessageFormatter
                                    .format("Diff {} -> {}", "working directory", baseCommit.getShortHash())
                                    .getMessage());
                            session.getDiff().thenAccept(diffs -> SwingGraphicUtil.updateUI(() -> diffView.setDiffEntries(diffs)));
                        } else {
                            diffView.setVisible(true);
                            GitCommit targetCommit = gitTreeView.getCommit(selectedRows[0]);
                            // NOTE: Perform diff against first parent
                            // Justification here https://github.com/libgit2/pygit2/issues/907
                            GitCommit firstParentBaseCommit = gitUtil
                                    .fromHash(targetCommit.getParentHashs().get(0));
                            session.setBaseCommit(firstParentBaseCommit);
                            session.setTargetCommit(targetCommit);
                            session.setEntryFilter(null);
                            checkOutButton.setVisible(true);
                            checkOutButton.setText("Checkout " + targetCommit.getShortHash());
                            diffView.setLabel(MessageFormatter.format("Diff {} -> {}", targetCommit.getShortHash(),
                                    firstParentBaseCommit.getShortHash()).getMessage());
                            session.getDiff().thenAccept(diffs -> SwingGraphicUtil.updateUI(() -> diffView.setDiffEntries(diffs)));
                        }
                    } else {
                        diffView.setVisible(true);
                        GitCommit baseCommit = gitTreeView.getCommit(selectedRows[selectedRows.length - 1]);
                        GitCommit targetCommit = gitTreeView.getCommit(selectedRows[0]);
                        session.setBaseCommit(baseCommit);
                        session.setTargetCommit(targetCommit);
                        session.setEntryFilter(null);
                        checkOutButton.setVisible(true);
                        checkOutButton.setText("Checkout " + targetCommit.getShortHash());
                        diffView.setLabel(MessageFormatter
                                .format("Diff {} -> {}", targetCommit.getShortHash(), baseCommit.getShortHash())
                                .getMessage());
                        session.getDiff().thenAccept(diffs -> SwingGraphicUtil.updateUI(() -> diffView.setDiffEntries(diffs)));
                    }
                } catch (IOException ex) {
                    handleException(ex);
                }
            }
        });
        checkOutButton.addActionListener(e -> {
            try {
                checkOutButton.setEnabled(false);
                gitUtil.checkoutAndStash(session.getTargetCommit().getHash());
                currentCommitLabel.setText(
                        MessageFormatter.format("Commit {}", gitUtil.getHeadRef().getShortCommitHash()).getMessage());
                JOptionPane.showMessageDialog(this, "Checkout success", "Checkout", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e1) {
                handleException(e1);
            } finally {
                checkOutButton.setEnabled(true);
            }
        });
        fetchButton.addActionListener(e -> {
            try {
                fetchButton.setEnabled(false);
                List<String> selectedBranches = getSelectedBranches();
                gitUtil.fetch(session.getGitUsername(), session.getGitPassword());
                List<GitRef> branchRefs = gitUtil.getBranchRefs(false).stream().map(GitRef::new)
                        .filter(it -> !it.isHead()).collect(Collectors.toList());
                this.branches = branchRefs.stream().map(GitRef::toString).collect(Collectors.toList());
                List<String> branchOptions = new ArrayList<>(branches);
                branchOptions.add(0, "All branches");
                gitRefsComboBox.setSelections(branchOptions);
                gitRefsComboBox
                        .setSelectedItem(selectedBranches.size() != 1 ? "All branches" : selectedBranches.get(0));
                gitTreeView.setGitBranch(selectedBranches);
                gitTreeView.clearSelection();
                gitTreeView.resetGraph();
            } catch (Exception e1) {
                handleException(e1);
            } finally {
                fetchButton.setEnabled(true);
            }
        });
        useWorkingDirectoryCheckbox.addItemListener(e -> {
            try {
                if (e.getStateChange() == ItemEvent.SELECTED) {
                    session.setUseWorkingDirectory(true);
                    session.setTargetCommit(null);
                    fetchButton.setVisible(false);
                    checkOutButton.setVisible(false);
                    gitTreeView.setDiffToWorkingDirectory(true);
                    gitRefsComboBox.setEditable(true);
                    gitRefsComboBox.setEnabled(false);
                    gitRefsComboBox.setSelectedItem(gitUtil.getHeadRef().getCommitHash());
                    gitRefsComboBox.setLabel(COMMIT_LABEL);
                    entryFilterField.setVisible(true);
                    session.setEntryFilter(null);
                } else if (e.getStateChange() == ItemEvent.DESELECTED) {
                    session.setUseWorkingDirectory(false);
                    gitTreeView.setDiffToWorkingDirectory(false);
                    fetchButton.setVisible(true);
                    gitRefsComboBox.setEditable(false);
                    gitRefsComboBox.setEnabled(true);
                    List<String> selectedBranches = getSelectedBranches();
                    gitRefsComboBox
                            .setSelectedItem(selectedBranches.size() != 1 ? "All branches" : selectedBranches.get(0));
                    gitRefsComboBox.setLabel(BRANCH_LABEL);
                    entryFilterField.setVisible(false);
                    session.setEntryFilter(entryFilterField.getText());
                }
            } catch (Exception e1) {
                handleException(e1);
            }
        });
        refreshButton.addActionListener(e -> {
            try {
                currentCommitLabel.setText(getWorkingDirectoryCommitLabel());
                refreshGitRelatedUI();
            } catch (Exception ex) {
                handleException(ex);
            }
        });
        entryFilterField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                try {
                    session.setEntryFilter(entryFilterField.getText());
                    // Diffview already show up
                    if (session.getBaseCommit() != null) {
                        diffView.clearDiff();
                        session.getDiff().thenAccept(diffs -> SwingGraphicUtil.updateUI(() -> diffView.setDiffEntries(diffs)));
                    }
                } catch (IOException ex) {
                    handleException(ex);
                }
            }
        });
    }

    @Override
    public void handleException(Exception e) {
        gitTreeView.clearSelection();
        try {
            currentCommitLabel.setText(
                    MessageFormatter.format("Commit {}", gitUtil.getHeadRef().getShortCommitHash()).getMessage());
        } catch (IOException e1) {
            JOptionPane.showMessageDialog(this, e1.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
        Throwable cause = e.getCause();
        if (cause != null) {
            logger.error(cause.getMessage(), cause);
            JOptionPane.showMessageDialog(this, cause.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        } else {
            logger.error(e.getMessage(), e);
            JOptionPane.showMessageDialog(this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
