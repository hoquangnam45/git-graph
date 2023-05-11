package com.ttl.internal.vn.tool.builder.component;

import java.awt.Dimension;
import java.awt.GridBagLayout;
import java.awt.event.ItemEvent;
import java.io.File;
import java.net.URISyntaxException;
import java.util.List;

import javax.swing.GroupLayout;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.WindowConstants;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

import com.formdev.flatlaf.FlatDarkLaf;
import com.ttl.internal.vn.tool.builder.component.Session.CredentialEntry;
import com.ttl.internal.vn.tool.builder.component.Session.GitLoginException;
import com.ttl.internal.vn.tool.builder.component.dialog.GitCloneDialog;
import com.ttl.internal.vn.tool.builder.component.input.Button;
import com.ttl.internal.vn.tool.builder.component.input.CheckBox;
import com.ttl.internal.vn.tool.builder.util.GitUtil;
import com.ttl.internal.vn.tool.builder.util.SwingGraphicUtil;

public class BuildTool extends JFrame implements ISimpleComponent {
    private final transient Logger logger = LogManager.getLogger(BuildTool.class);
    private Button openLocalRepoBtn;
    private Button cloneRepoBtn;
    private CheckBox useGitCredentialCheckBox;

    // UI-mode
    public BuildTool() throws UnsupportedLookAndFeelException {
        super();
        initUI();
        registerListeners();
    }

    @Override
    public void initUI() throws UnsupportedLookAndFeelException {
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        UIManager.setLookAndFeel(new FlatDarkLaf());

        JPanel btnPanel = new JPanel();
        GroupLayout btnGroupLayout = new GroupLayout(btnPanel);
        btnPanel.setLayout(btnGroupLayout);
        btnGroupLayout.setAutoCreateGaps(true);
        btnGroupLayout.setAutoCreateContainerGaps(true);
        this.openLocalRepoBtn = new Button("Open local repo");
        this.cloneRepoBtn = new Button("Clone repo");
        openLocalRepoBtn.setPreferredSize(new Dimension(150, 60));
        cloneRepoBtn.setPreferredSize(new Dimension(150, 60));

        btnGroupLayout.setVerticalGroup(btnGroupLayout.createSequentialGroup()
                .addComponent(openLocalRepoBtn)
                .addGap(10)
                .addComponent(cloneRepoBtn));

        btnGroupLayout.setHorizontalGroup(btnGroupLayout.createParallelGroup()
                .addComponent(openLocalRepoBtn, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE,
                        GroupLayout.PREFERRED_SIZE)
                .addComponent(cloneRepoBtn, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE,
                        GroupLayout.PREFERRED_SIZE));

        Session.getInstance().setUseGitCredential(true);
        useGitCredentialCheckBox = new CheckBox(Session.getInstance().isUseGitCredential(), "Use git credential");

        JPanel mainPanel = new JPanel();
        GroupLayout mainGroupLayout = new GroupLayout(mainPanel);
        mainPanel.setLayout(mainGroupLayout);
        mainGroupLayout.setAutoCreateGaps(true);
        mainGroupLayout.setAutoCreateContainerGaps(true);

        mainGroupLayout.setVerticalGroup(
                mainGroupLayout.createSequentialGroup()
                        .addGap(50)
                        .addComponent(btnPanel)
                        .addComponent(useGitCredentialCheckBox)
                        .addGap(50));

        mainGroupLayout.setHorizontalGroup(
                mainGroupLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addComponent(btnPanel)
                        .addComponent(useGitCredentialCheckBox));

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new GridBagLayout());
        contentPanel.add(mainPanel);

        setMinimumSize(new Dimension(300, 200));
        add(contentPanel);
        setLocationRelativeTo(null);
        setResizable(false);
        setTitle("Build tool");
        pack();
    }

    @Override
    public void registerListeners() {
        // Show file dialog
        openLocalRepoBtn.addActionListener(e -> {
            SwingGraphicUtil.updateUI(() -> {
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                int result = fileChooser.showOpenDialog(this);
                // Check if a file was selected
                if (result == JFileChooser.APPROVE_OPTION) {
                    // Get the selected file
                    File projectFolder = fileChooser.getSelectedFile();
                    try {
                        openBuildToolDashboard(projectFolder);
                    } catch (GitAPIException e1) {
                        handleException(e1);
                    }
                }
            });
        });
        cloneRepoBtn.addActionListener(e -> {
            SwingGraphicUtil.updateUI(() -> {
                GitCloneDialog cloneDialog = new GitCloneDialog(this);
                int result = cloneDialog.showDialog();
                if (result == GitCloneDialog.Status.OK.ordinal()) {
                    try {
                        openBuildToolDashboard(cloneDialog.getClonedFolder());
                    } catch (GitAPIException e1) {
                        handleException(e1);
                    }
                }
            });
        });
        useGitCredentialCheckBox.addItemListener(e -> {
            boolean selected = e.getStateChange() == ItemEvent.SELECTED;
            Session.getInstance().setUseGitCredential(selected);
        });
    }

    private void openBuildToolDashboard(File projectFolder) throws GitAPIException {
        try {
            Git git = GitUtil.openLocalRepo(projectFolder);
            Session session = Session.getInstance();
            session.setGitUtil(new GitUtil(git));
            if (StringUtils.isBlank(session.getGitPassword()) || StringUtils.isBlank(session.getGitUsername())) {
                String repoURI = session.getGitRepoURI("origin");
                List<CredentialEntry> foundEntries = session.scanCredential(repoURI);
                for (CredentialEntry entry : foundEntries) {
                    try {
                        Session.checkLogin(entry.getUsername(), entry.getPassword(), repoURI);
                        session.setGitUsername(entry.getUsername());
                        session.setGitPassword(entry.getPassword());
                    } catch (GitLoginException | URISyntaxException e1) {
                        handleException(e1);
                    }
                }
            }
            session.setClonedFolder(projectFolder);
            dispose();
            BuildToolDashBoard dashBoard = new BuildToolDashBoard(Session.getInstance());
            dashBoard.setVisible(true);
        } catch (Exception e) {
            handleException(e);
        }
    }

    @Override
    public void refreshUI() {
        /** noop */
    }

    @Override
    public void handleException(Exception e) {
        logger.error(e.getMessage(), e);
        JOptionPane.showMessageDialog(this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
    }
}