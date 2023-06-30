package com.ttl.internal.vn.tool.builder.component;

import java.awt.Dimension;
import java.awt.event.ItemEvent;
import java.io.IOException;
import java.util.List;
import java.util.Vector;
import java.util.stream.Collectors;

import javax.swing.BoxLayout;
import javax.swing.GroupLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

import com.ttl.internal.vn.tool.builder.component.input.CheckBox;
import com.ttl.internal.vn.tool.builder.git.GitWalk;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.helpers.MessageFormatter;

import com.ttl.internal.vn.tool.builder.component.input.Button;
import com.ttl.internal.vn.tool.builder.component.input.ComboBox;
import com.ttl.internal.vn.tool.builder.component.input.DiffView;
import com.ttl.internal.vn.tool.builder.component.input.GitTreeView;
import com.ttl.internal.vn.tool.builder.component.input.TextField;
import com.ttl.internal.vn.tool.builder.git.GitCommit;
import com.ttl.internal.vn.tool.builder.git.GitRef;
import com.ttl.internal.vn.tool.builder.util.GitUtil;
import com.ttl.internal.vn.tool.builder.util.SwingGraphicUtil;

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
    private transient Session session;
    private CheckBox useWorkingDirectoryCheckbox;

    public BuildToolDashBoard(Session session) throws GitAPIException, IOException {
        super();
        this.gitUtil = session.getGitUtil();
        this.session = session;
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
            gitTreeView.setGitCommits(List.of(gitUtil.getHeadRef().getCommitHash()));
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
                
        List<GitRef> branchRefs = gitUtil.getBranchRefs(false).stream().map(GitRef::new).filter(it -> !it.isHead()).collect(Collectors.toList());
        this.branches = branchRefs.stream().map(GitRef::toString).collect(Collectors.toList());
        Vector<String> branchOptions = new Vector<>(branches);
        branchOptions.add(0, "All branches");
        gitRefsComboBox = new ComboBox<>("", branchOptions, null);
        gitRefsComboBox.setPreferredSize(new Dimension(300, 50));

        this.searchCommitTextField = new TextField("Search commit", 60, 120, true, BoxLayout.Y_AXIS, List.of());

        this.gitTreeView = new GitTreeView(new GitWalk(gitUtil));
        refreshGitRelatedUI();
        
        this.diffView = new DiffView();
        diffView.setVisible(false);

        JPanel btnPanel = new JPanel();
        GroupLayout btnGroupLayout = new GroupLayout(btnPanel);
        btnGroupLayout.setAutoCreateContainerGaps(true);
        btnGroupLayout.setAutoCreateGaps(true);
        btnPanel.setLayout(btnGroupLayout);
        this.checkOutButton = new Button("Checkout commit");
        checkOutButton.setVisible(false);
        this.fetchButton = new Button("Fetch");
        this.refreshButton = new Button("Refresh");
        btnGroupLayout.setHorizontalGroup(btnGroupLayout.createSequentialGroup()
            .addComponent(refreshButton)
            .addComponent(fetchButton)
            .addComponent(checkOutButton));
        btnGroupLayout.setVerticalGroup(btnGroupLayout.createParallelGroup()
            .addComponent(refreshButton)
            .addComponent(fetchButton)
            .addComponent(checkOutButton));

        this.dashBoard = new ConfigDashBoard(session);

        JPanel gitViewPanel = new JPanel();
        GroupLayout gitGroupLayout = new GroupLayout(gitViewPanel);
        gitGroupLayout.setAutoCreateContainerGaps(true);
        gitGroupLayout.setAutoCreateGaps(true);
        gitViewPanel.setLayout(gitGroupLayout);
        gitGroupLayout.setHorizontalGroup(gitGroupLayout.createParallelGroup()
                .addComponent(currentCommitLabel, GroupLayout.Alignment.TRAILING)
                .addComponent(gitRefsComboBox)
                .addComponent(searchCommitTextField)
                .addComponent(useWorkingDirectoryCheckbox)
                .addComponent(gitTreeView)
                .addComponent(diffView)
                .addComponent(btnPanel)
                .addGap(10));
        gitGroupLayout.setVerticalGroup(gitGroupLayout.createSequentialGroup()
                .addComponent(currentCommitLabel)
                .addComponent(gitRefsComboBox)
                .addComponent(searchCommitTextField)
                .addComponent(useWorkingDirectoryCheckbox)
                .addComponent(gitTreeView)
                .addComponent(diffView)
                .addComponent(btnPanel)
                .addGap(10));

        JPanel container = new JPanel();
        GroupLayout mainGroupLayout = new GroupLayout(container);
        container.setLayout(mainGroupLayout);

        mainGroupLayout.setHorizontalGroup(mainGroupLayout.createSequentialGroup()
                .addComponent(gitViewPanel)
                .addComponent(dashBoard, 0, GroupLayout.PREFERRED_SIZE, Short.MAX_VALUE));
        mainGroupLayout.setVerticalGroup(mainGroupLayout.createParallelGroup()
                .addComponent(gitViewPanel)
                .addComponent(dashBoard));

        if (session.getUseWorkingDirectory()) {
            fetchButton.setVisible(false);
        }
        
        add(container);
        pack();
    }

    private List<String> getSelectedBranches() {
        if (gitRefsComboBox.getSelectedIndex() > 0) {
            return List.of(gitRefsComboBox.getSelectedItem());
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
                        gitTreeView.setGitCommits(List.of(gitUtil.getHeadRef().getCommitHash()));
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
                int[] selectedRows = gitTreeView.getSelectedRows();
                if (selectedRows.length == 0) {
                    session.setBaseCommit(null);
                    session.setTargetCommit(null);
                    checkOutButton.setVisible(false);
                    diffView.setVisible(false);
                } else if (selectedRows.length == 1) {
                    if (session.getUseWorkingDirectory()) {
                        SwingGraphicUtil.supply(() -> {
                            try {
                                diffView.setVisible(true);
                                GitCommit baseCommit = gitTreeView.getCommit(selectedRows[0]);
                                session.setBaseCommit(baseCommit);
                                session.setTargetCommit(null);
                                checkOutButton.setVisible(false);
                                diffView.setLabel(MessageFormatter.format("Diff {} -> {}", "working directory", baseCommit.getShortHash()).getMessage());
                                return gitUtil.getDiffWd(baseCommit.getHash());
                            } catch (Exception e1) {
                                handleException(e1);
                                return null;
                            }
                        }).thenAccept(diffs -> {
                            SwingGraphicUtil.updateUI(() -> diffView.setDiffEntries(diffs));
                        });
                    } else {
                        SwingGraphicUtil.supply(() -> {
                            try {
                                diffView.setVisible(true);
                                GitCommit targetCommit = gitTreeView.getCommit(selectedRows[0]);
                                // NOTE: Perform diff against first parent
                                // Justification here https://github.com/libgit2/pygit2/issues/907
                                GitCommit firstParentBaseCommit = gitUtil.fromHash(targetCommit.getParentHashs().get(0));
                                session.setBaseCommit(firstParentBaseCommit);
                                session.setTargetCommit(targetCommit);
                                checkOutButton.setVisible(true);
                                checkOutButton.setText("Checkout " + targetCommit.getShortHash());
                                diffView.setLabel(MessageFormatter.format("Diff {} -> {}", targetCommit.getShortHash(), firstParentBaseCommit.getShortHash()).getMessage());
                                return gitUtil.getDiff(firstParentBaseCommit.getHash(), targetCommit.getHash());
                            } catch (Exception e1) {
                                handleException(e1);
                                return null;
                            }
                        }).thenAccept(diffs -> {
                            SwingGraphicUtil.updateUI(() -> diffView.setDiffEntries(diffs));
                        });
                    }
                } else {
                    try {
                        SwingGraphicUtil.supply(() -> {
                            try {
                                diffView.setVisible(true);
                                GitCommit baseCommit = gitTreeView.getCommit(selectedRows[selectedRows.length - 1]);
                                GitCommit targetCommit = gitTreeView.getCommit(selectedRows[0]);
                                session.setBaseCommit(baseCommit);
                                session.setTargetCommit(targetCommit);
                                checkOutButton.setVisible(true);
                                checkOutButton.setText("Checkout " + targetCommit.getShortHash());
                                diffView.setLabel(MessageFormatter.format("Diff {} -> {}", targetCommit.getShortHash(), baseCommit.getShortHash()).getMessage());
                                return gitUtil.getDiff(baseCommit.getHash(), targetCommit.getHash());
                            } catch (IOException e1) {
                                handleException(e1);
                                return null;
                            }
                        }).thenAccept(diffs -> {
                            SwingGraphicUtil.updateUI(() -> diffView.setDiffEntries(diffs));
                        });
                    } catch (Exception e1) {
                        handleException(e1);
                    }
                }
            }
        });
        checkOutButton.addActionListener(e -> {
            try {
                checkOutButton.setEnabled(false);
                gitUtil.checkoutAndStash(session.getTargetCommit().getHash());
                currentCommitLabel.setText(MessageFormatter.format("Commit {}", gitUtil.getHeadRef().getShortCommitHash()).getMessage());
                JOptionPane.showMessageDialog(this, "Checkout success",  "Checkout", JOptionPane.INFORMATION_MESSAGE);
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
                List<GitRef> branchRefs = gitUtil.getBranchRefs(false).stream().map(GitRef::new).filter(it -> !it.isHead()).collect(Collectors.toList());
                this.branches = branchRefs.stream().map(GitRef::toString).collect(Collectors.toList());
                Vector<String> branchOptions = new Vector<>(branches);
                branchOptions.add(0, "All branches");
                gitRefsComboBox.setSelections(branchOptions);
                gitRefsComboBox.setSelectedItem(selectedBranches.size() != 1 ? "All branches" : selectedBranches.get(0));
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
                } else if (e.getStateChange() == ItemEvent.DESELECTED) {
                    session.setUseWorkingDirectory(false);
                    gitTreeView.setDiffToWorkingDirectory(false);
                    fetchButton.setVisible(true);
                    gitRefsComboBox.setEditable(false);
                    gitRefsComboBox.setEnabled(true);
                    List<String> selectedBranches = getSelectedBranches();
                    gitRefsComboBox.setSelectedItem(selectedBranches.size() != 1 ? "All branches" : selectedBranches.get(0));
                    gitRefsComboBox.setLabel(BRANCH_LABEL);
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
    }

    @Override
    public void handleException(Exception e) {
        gitTreeView.clearSelection();
        try {
            currentCommitLabel.setText(MessageFormatter.format("Commit {}", gitUtil.getHeadRef().getShortCommitHash()).getMessage());
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
