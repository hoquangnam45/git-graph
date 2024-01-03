package com.ttl.internal.vn.tool.builder.component;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;

import javax.swing.BoxLayout;
import javax.swing.GroupLayout;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import com.ttl.internal.vn.tool.builder.component.dialog.ArtifactInfoDialog;
import com.ttl.internal.vn.tool.builder.component.input.*;
import com.ttl.internal.vn.tool.builder.component.input.Button;
import com.ttl.internal.vn.tool.builder.component.input.TextField;
import com.ttl.internal.vn.tool.builder.task.BuildTask;
import com.ttl.internal.vn.tool.builder.task.DefaultSubscriber;
import com.ttl.internal.vn.tool.builder.task.Task;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.slf4j.helpers.MessageFormatter;

import com.ttl.internal.vn.tool.builder.cli.CliBuildTool;
import com.ttl.internal.vn.tool.builder.cli.CliBuildTool.ArtifactInfo;
import com.ttl.internal.vn.tool.builder.cli.CliBuildTool.DefaultCliGetArtifactInfo;
import com.ttl.internal.vn.tool.builder.git.GitCommit;
import com.ttl.internal.vn.tool.builder.util.SwingGraphicUtil;

public class ConfigDashBoard extends JPanel implements ISimpleComponent {
    private final transient Logger logger = LogManager.getLogger(ConfigDashBoard.class);
    private FileField artifactFolderFileField;
    private FileField mavenXmlSettingsFileField;
    private FileField patchFileField;
    private CheckBox buildConfigJarCheckbox;
    private CheckBox buildReleasePackageZipCheckbox;
    private CheckBox buildPatchCheckbox;
    private CheckBox updateSnapshotCheckbox;
    private CheckBox interactive;
    private Button buildBtn;
    private Button cancelBtn;
    private Button openArtifactButton;
    private final transient Session session;
    private DiffView diffView;
    private final transient Object buildLock = new Object();
    private static final int INPUT_WIDTH = 300;
    private static final int LABEL_WIDTH = 150;
    private BuildTask buildTask;
    private BuildToolDashBoard buildToolDashBoard;

    public ConfigDashBoard(Session session, BuildToolDashBoard buildToolDashBoard) {
        super();
        this.session = session;
        this.buildToolDashBoard = buildToolDashBoard;
        initUI();
        registerListeners();
    }

    @Override
    public void initUI() {
        this.artifactFolderFileField = new FileField("Artifact folder*", LABEL_WIDTH, INPUT_WIDTH,
                JFileChooser.DIRECTORIES_ONLY, true, BoxLayout.Y_AXIS, Collections.singletonList(TextField.Validator.notBlank()), false);
        artifactFolderFileField
                .setText(Paths.get(System.getProperty("java.io.tmpdir"), "buildArtifact").toAbsolutePath().toString());

        List<TextValidator> xmlFileFileValidators = Collections.singletonList(val -> {
            File pomFile = new File(val);
            if (!pomFile.isFile() || !pomFile.getName().toLowerCase().endsWith(".xml")) {
                return ValidatorError.builder()
                        .validatorId("NOT_XML_FILE")
                        .validatorMessage("Pom file must be a xml file")
                        .build();
            }
            return null;
        });
        this.mavenXmlSettingsFileField = new FileField("Maven settings xml file*", LABEL_WIDTH, INPUT_WIDTH,
                JFileChooser.FILES_ONLY, true, BoxLayout.Y_AXIS, xmlFileFileValidators, true);
        mavenXmlSettingsFileField
                .setText(Paths.get(System.getProperty("user.home"), ".m2", "settings.xml").toAbsolutePath().toString());
        this.patchFileField = new FileField("Git patch files", LABEL_WIDTH, INPUT_WIDTH, JFileChooser.FILES_ONLY,
                true, BoxLayout.Y_AXIS, null, false);

        JPanel inputPanel = new JPanel();
        GroupLayout inputGroupLayout = new GroupLayout(inputPanel);
        inputPanel.setLayout(inputGroupLayout);
        inputGroupLayout.setHorizontalGroup(inputGroupLayout.createParallelGroup()
                .addComponent(artifactFolderFileField)
                .addComponent(mavenXmlSettingsFileField)
                .addComponent(patchFileField));
        inputGroupLayout.setVerticalGroup(inputGroupLayout.createSequentialGroup()
                .addComponent(artifactFolderFileField)
                .addComponent(mavenXmlSettingsFileField)
                .addComponent(patchFileField));
        inputPanel.setBackground(Color.GREEN);

        this.buildConfigJarCheckbox = new CheckBox(false, "Build config package?");
        this.buildReleasePackageZipCheckbox = new CheckBox(false, "Build release package?");
        this.buildPatchCheckbox = new CheckBox(true, "Build patch");
        this.updateSnapshotCheckbox = new CheckBox(true, "Update maven snapshot");
        this.interactive = new CheckBox(true, "Interactive");

        JPanel checkBoxPanel = new JPanel();
        GroupLayout checkBoxPanelGroupLayout = new GroupLayout(checkBoxPanel);
        checkBoxPanel.setLayout(checkBoxPanelGroupLayout);
        checkBoxPanelGroupLayout.setHorizontalGroup(checkBoxPanelGroupLayout.createParallelGroup()
                .addComponent(updateSnapshotCheckbox)
                .addComponent(buildPatchCheckbox)
                .addComponent(buildConfigJarCheckbox)
                .addComponent(buildReleasePackageZipCheckbox)
                .addComponent(interactive));
        checkBoxPanelGroupLayout.setVerticalGroup(checkBoxPanelGroupLayout.createSequentialGroup()
                .addComponent(updateSnapshotCheckbox)
                .addComponent(buildPatchCheckbox)
                .addComponent(buildConfigJarCheckbox)
                .addComponent(buildReleasePackageZipCheckbox)
                .addComponent(interactive));

        this.buildBtn = new Button("Build");
        this.cancelBtn = new Button("Cancel");
        this.openArtifactButton = new Button("Open artifact folder");
        openArtifactButton.setVisible(false);
        cancelBtn.setVisible(false);
        JPanel buttonPanel = new JPanel();
        GroupLayout buttonPanelGroupLayout = new GroupLayout(buttonPanel);
        buttonPanel.setLayout(buttonPanelGroupLayout);
        buttonPanelGroupLayout.setAutoCreateContainerGaps(true);
        buttonPanelGroupLayout.setAutoCreateGaps(true);
        buttonPanelGroupLayout.setHorizontalGroup(buttonPanelGroupLayout.createSequentialGroup()
                .addComponent(buildBtn)
                .addComponent(cancelBtn)
                .addComponent(openArtifactButton));
        buttonPanelGroupLayout.setVerticalGroup(buttonPanelGroupLayout.createParallelGroup()
                .addComponent(buildBtn)
                .addComponent(cancelBtn)
                .addComponent(openArtifactButton));

        this.diffView = new DiffView();
        diffView.setVisible(false);
        diffView.setMinimumSize(new Dimension(0, 200));

        JPanel mainPanel = new JPanel();

        JScrollPane scrollPane = new JScrollPane();

        GroupLayout mainGroupLayout = new GroupLayout(mainPanel);
        mainPanel.setLayout(mainGroupLayout);
        mainGroupLayout.setAutoCreateContainerGaps(true);
        mainGroupLayout.setAutoCreateGaps(true);
        mainGroupLayout.setHorizontalGroup(mainGroupLayout.createParallelGroup()
                .addComponent(inputPanel)
                .addComponent(checkBoxPanel)
                .addComponent(diffView)
                .addComponent(buttonPanel));
        mainGroupLayout.setVerticalGroup(mainGroupLayout.createSequentialGroup()
                .addComponent(inputPanel)
                .addComponent(checkBoxPanel)
                .addComponent(diffView)
                .addComponent(buttonPanel));

        scrollPane.setViewportView(mainPanel);

        GroupLayout layout = new GroupLayout(this);
        setLayout(layout);
        layout.setHorizontalGroup(layout.createParallelGroup()
                .addComponent(scrollPane));
        layout.setVerticalGroup(layout.createSequentialGroup()
                .addComponent(scrollPane));

        patchFileField.setVisible(!session.getUseWorkingDirectory());
    }

    @Override
    public void refreshUI() {
        /** noop */
    }

    private void disableInput() {
        buildBtn.setEnabled(false);
        artifactFolderFileField.setEnabled(false);
        patchFileField.setEnabled(false);
        mavenXmlSettingsFileField.setEnabled(false);
        buildConfigJarCheckbox.setEnabled(false);
        buildReleasePackageZipCheckbox.setEnabled(false);
        buildPatchCheckbox.setEnabled(false);
        updateSnapshotCheckbox.setEnabled(false);
        interactive.setEnabled(false);
        diffView.setVisible(true);
        openArtifactButton.setVisible(false);
        cancelBtn.setVisible(true);
    }

    private void enableInput() {
        buildBtn.setEnabled(true);
        buildBtn.setVisible(true);
        buildBtn.setEnabled(true);
        SwingUtilities.getRoot(this)
                .setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        artifactFolderFileField.setEnabled(true);
        patchFileField.setEnabled(true);
        mavenXmlSettingsFileField.setEnabled(true);
        updateSnapshotCheckbox.setEnabled(true);
        interactive.setEnabled(true);
        buildConfigJarCheckbox.setEnabled(true);
        buildReleasePackageZipCheckbox.setEnabled(true);
        buildPatchCheckbox.setEnabled(true);
        diffView.setVisible(false);
    }

    @Override
    public void registerListeners() {
        buildBtn.addActionListener(e -> {
            try {
                if (!session.getUseWorkingDirectory()) {
                    if (session.getBaseCommit() == null || session.getTargetCommit() == null) {
                        throw new NullPointerException("Haven't select base commit and target commit yet");
                    }
                } else {
                    if (session.getBaseCommit() == null) {
                        throw new NullPointerException("Haven't select base commit yet");
                    }
                }
                disableInput();
                CliBuildTool command = new CliBuildTool(
                    false,
                    session.getGitRepoURI("origin"),
                    session.getGitWorkingDir(),
                    session.getGitUsername(),
                    session.getGitPassword(),
                    session.getBaseCommit().getHash(),
                    Optional.ofNullable(session.getTargetCommit()).map(GitCommit::getHash).orElse(null),
                    session.getEntryFilter(),
                    artifactFolderFileField.getSelectedFile(),
                    updateSnapshotCheckbox.isSelected(),
                    buildConfigJarCheckbox.isSelected(),
                    buildReleasePackageZipCheckbox.isSelected(),
                    buildPatchCheckbox.isSelected(),
                    session.getUseWorkingDirectory(),
                    null,
                    null,
                    null,
                    patchFileField.getSelectedFile(),
                    mavenXmlSettingsFileField.getSelectedFile(),
                    interactive.isSelected() ? this::getArtifactInfo
                            : DefaultCliGetArtifactInfo.getArtifactInfo(false));

                String targetBuild = session.getUseWorkingDirectory() ? "working directory"
                        : session.getTargetCommit().getShortHash();
                diffView.setLabel(MessageFormatter.format("Build diff {} -> {}",
                        targetBuild, session.getBaseCommit().getShortHash()).getMessage());
                command.startBuildEnvironment();
                List<DiffEntry> diffEntries = command.getDiff();
                diffView.setDiffEntries(diffEntries);
                SwingUtilities.getRoot(this).setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                ConfigDashBoard.this.buildTask = command.build(false);
                buildTask.subscribe(new DefaultSubscriber<Task>() {
                    @Override
                    public void onNext(Task task) {
                        try {
                            SwingGraphicUtil.updateUIBlocking(() -> {
                                cancelBtn.setVisible(true);
                                buildToolDashBoard.getProgressBar().setValue((int) (100. * task.percentage()));
                                buildToolDashBoard.getProgressBar().setMaximum(100);
                                int numberOfDoneTasks = (int) buildTask.getSubtasks().stream().filter(Task::isDone).count();
                                buildToolDashBoard.getProgressBar().setStatus("<html>Current task (" + (numberOfDoneTasks + 1) + "/" + buildTask.getSubtasks().size() + ")<br>Description: " + buildTask.explainTask().replace("\n", "<br>")  + " - " + (int) (100. * task.percentage()) + "%</html>");
                            });
                        } catch (Exception e) {
                            handleException(e);
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        SwingGraphicUtil.updateUI(() -> {
                            enableInput();
                            cancelBtn.setVisible(false);
                            buildToolDashBoard.getProgressBar().setValue(0);
                            buildToolDashBoard.getProgressBar().setStatus("");
                            handleException((Exception) e);
                        });
                    }

                    @Override
                    public void onComplete() {
                        SwingGraphicUtil.updateUI(() -> {
                            enableInput();
                            cancelBtn.setVisible(false);
                            if (buildTask.isDone()) {
                                buildToolDashBoard.getProgressBar().setValue(100);
                                buildToolDashBoard.getProgressBar().setStatus("Build successfully");
                                JOptionPane.showMessageDialog(ConfigDashBoard.this, "Build success", "Success",
                                        JOptionPane.INFORMATION_MESSAGE);
                                openArtifactButton.setVisible(true);
                            } else if (buildTask.isError() || buildTask.isCancelled()) {
                                buildToolDashBoard.getProgressBar().setValue(0);
                                buildToolDashBoard.getProgressBar().setStatus("");
                            }
                        });
                    }
                });
                SwingGraphicUtil.run(buildTask::start);
            } catch (Exception e1) {
                enableInput();
                handleException(e1);
            }
        });
        cancelBtn.addActionListener(evt -> stopBuild());
        buildReleasePackageZipCheckbox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                buildPatchCheckbox.setSelected(true);
                buildConfigJarCheckbox.setSelected(true);
            }
        });
        buildPatchCheckbox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.DESELECTED) {
                buildReleasePackageZipCheckbox.setSelected(false);
            }
        });
        buildConfigJarCheckbox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.DESELECTED) {
                buildReleasePackageZipCheckbox.setSelected(false);
            }
        });

        session.<Boolean>addListener(Session.USE_WORKING_DIRECTORY_CHANGED, useWorkingDirectory -> {
            if (useWorkingDirectory) {
                patchFileField.setVisible(false);
            } else {
                patchFileField.setVisible(true);
            }
        });

        openArtifactButton.addActionListener(e -> {
            try {
                openFile(artifactFolderFileField.getSelectedFile());
            } catch (IOException ex) {
                handleException(ex);
            }
        });
    }

    private void stopBuild() {
        Optional.ofNullable(buildTask).ifPresent(BuildTask::cancel);
    }

    public void openFile(File file) throws IOException {
        // Check if Desktop is supported
        if (Desktop.isDesktopSupported()) {
            Desktop desktop = Desktop.getDesktop();

            // Check if opening a file is supported
            if (desktop.isSupported(Desktop.Action.OPEN)) {
                // Open the file explorer at the specified path
                desktop.open(file);
            } else {
                throw new UnsupportedOperationException("Opening file explorer is not supported on this platform.");
            }
        } else {
            throw new UnsupportedOperationException("Desktop is not supported on this platform.");
        }
    }

    @Override
    public void handleException(Exception e) {
        Throwable cause = e.getCause();
        if (cause != null) {
            logger.error(cause.getMessage(), cause);
            JOptionPane.showMessageDialog(this, cause.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        } else {
            logger.error(e.getMessage(), e);
            JOptionPane.showMessageDialog(this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private Map<String, ArtifactInfo> getArtifactInfo(List<String> modules) {
        Map<String, ArtifactInfo> artifactInfos = new HashMap<>();
        SwingGraphicUtil.updateUI(() -> {
            ArtifactInfoDialog artifactInfoDialog = new ArtifactInfoDialog(modules, buildPatchCheckbox.isSelected(), buildConfigJarCheckbox.isSelected(), buildReleasePackageZipCheckbox.isSelected());
            artifactInfoDialog.setLocationRelativeTo(this);
            artifactInfoDialog.setModalityType(Dialog.ModalityType.APPLICATION_MODAL);
            if (artifactInfoDialog.showDialog() == ArtifactInfoDialog.ArtifactInfoDialogStatus.OK.ordinal()) {
                buildBtn.setVisible(false);
                artifactInfos.clear();
                artifactInfos.putAll(artifactInfoDialog.getCollectedArtifactInfo());
                synchronized (buildLock) {
                    buildLock.notifyAll();
                }
            } else {
                stopBuild();
                synchronized (buildLock) {
                    buildLock.notifyAll();
                }
            }
        });

        synchronized (buildLock) {
            try {
                // Wait until the lock get unlocked by run cli build btn
                buildLock.wait();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        return artifactInfos;
    }
}
