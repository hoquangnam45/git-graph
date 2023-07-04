package com.ttl.internal.vn.tool.builder.component;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.event.ItemEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.swing.BoxLayout;
import javax.swing.GroupLayout;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jgit.diff.DiffEntry;
import org.slf4j.helpers.MessageFormatter;

import com.ttl.internal.vn.tool.builder.cli.CliBuildTool;
import com.ttl.internal.vn.tool.builder.cli.CliBuildTool.ArtifactInfo;
import com.ttl.internal.vn.tool.builder.cli.CliBuildTool.DefaultCliGetArtifactInfo;
import com.ttl.internal.vn.tool.builder.component.input.Button;
import com.ttl.internal.vn.tool.builder.component.input.CheckBox;
import com.ttl.internal.vn.tool.builder.component.input.DiffView;
import com.ttl.internal.vn.tool.builder.component.input.FileField;
import com.ttl.internal.vn.tool.builder.component.input.TextField;
import com.ttl.internal.vn.tool.builder.component.input.TextValidator;
import com.ttl.internal.vn.tool.builder.component.input.ValidatorError;
import com.ttl.internal.vn.tool.builder.git.GitCommit;
import com.ttl.internal.vn.tool.builder.util.SwingGraphicUtil;

public class ConfigDashBoard extends JPanel implements ISimpleComponent {
    private final transient Logger logger = LogManager.getLogger(ConfigDashBoard.class);
    private FileField artifactFolderFileField;
    private FileField artifactPomFileField;
    private FileField mavenXmlSettingsFileField;
    private FileField patchFileField;
    private TextField entryFilterField;
    private CheckBox buildConfigJarBtn;
    private CheckBox buildReleasePackageZipBtn;
    private CheckBox buildPatchBtn;
    private CheckBox updateSnapshotCheckbox;
    private CheckBox interactive;
    private Button buildBtn;
    private Button runCliBuildBtn;
    private Button openArtifactButton;
    private final transient Session session;
    private JTabbedPane getArtifactInfoTabbedPane;
    private DiffView diffView;
    private transient Object buildLock = new Object();
    private Map<String, List<TextField>> artifactInfoFields = new HashMap<>();

    private static final int INPUT_WIDTH = 300;
    private static final int LABEL_WIDTH = 150;

    public ConfigDashBoard(Session session) {
        super();
        this.session = session;
        initUI();
        registerListeners();
    }

    @Override
    public void initUI() {
        this.artifactFolderFileField = new FileField("Artifact folder*", LABEL_WIDTH, INPUT_WIDTH,
                JFileChooser.DIRECTORIES_ONLY, true, BoxLayout.Y_AXIS, List.of(TextField.Validator.notBlank()));
        artifactFolderFileField
                .setText(Paths.get(System.getProperty("java.io.tmpdir"), "buildArtifact").toAbsolutePath().toString());

        List<TextValidator> xmlFileFileValidators = List.of(val -> {
            File pomFile = new File(val);
            if (!pomFile.isFile() || !pomFile.getName().toLowerCase().endsWith(".xml")) {
                return ValidatorError.builder()
                        .validatorId("NOT_XML_FILE")
                        .validatorMessage("Pom file must be a xml file")
                        .build();
            }
            return null;
        });
        this.artifactPomFileField = new FileField("Artifact pom*", LABEL_WIDTH, INPUT_WIDTH, JFileChooser.FILES_ONLY,
                true, BoxLayout.Y_AXIS, xmlFileFileValidators);
        artifactPomFileField.setText(new File(session.getClonedFolder(), "pom.xml").getAbsolutePath());
        this.mavenXmlSettingsFileField = new FileField("Maven settings xml file*", LABEL_WIDTH, INPUT_WIDTH,
                JFileChooser.FILES_ONLY, true, BoxLayout.Y_AXIS, xmlFileFileValidators);
        mavenXmlSettingsFileField
                .setText(Paths.get(System.getProperty("user.home"), ".m2", "settings.xml").toAbsolutePath().toString());
        this.patchFileField = new FileField("Git patch files", LABEL_WIDTH, INPUT_WIDTH, JFileChooser.FILES_ONLY,
                true, BoxLayout.Y_AXIS, null);
        this.entryFilterField = new TextField("Filter entry regex", LABEL_WIDTH, INPUT_WIDTH, true, BoxLayout.Y_AXIS,
                null);

        JPanel inputPanel = new JPanel();
        GroupLayout inputGroupLayout = new GroupLayout(inputPanel);
        inputPanel.setLayout(inputGroupLayout);
        inputGroupLayout.setHorizontalGroup(inputGroupLayout.createParallelGroup()
                .addComponent(artifactFolderFileField)
                .addComponent(artifactPomFileField)
                .addComponent(mavenXmlSettingsFileField)
                .addComponent(patchFileField)
                .addComponent(entryFilterField));
        inputGroupLayout.setVerticalGroup(inputGroupLayout.createSequentialGroup()
                .addComponent(artifactFolderFileField)
                .addComponent(artifactPomFileField)
                .addComponent(mavenXmlSettingsFileField)
                .addComponent(patchFileField)
                .addComponent(entryFilterField));
        inputPanel.setBackground(Color.GREEN);

        this.buildConfigJarBtn = new CheckBox(false, "Build config package?");
        this.buildReleasePackageZipBtn = new CheckBox(false, "Build release package?");
        this.buildPatchBtn = new CheckBox(true, "Build patch");
        this.updateSnapshotCheckbox = new CheckBox(true, "Update maven snapshot");
        this.interactive = new CheckBox(true, "Interactive");

        JPanel checkBoxPanel = new JPanel();
        GroupLayout checkBoxPanelGroupLayout = new GroupLayout(checkBoxPanel);
        checkBoxPanel.setLayout(checkBoxPanelGroupLayout);
        checkBoxPanelGroupLayout.setHorizontalGroup(checkBoxPanelGroupLayout.createParallelGroup()
                .addComponent(updateSnapshotCheckbox)
                .addComponent(buildPatchBtn)
                .addComponent(buildConfigJarBtn)
                .addComponent(buildReleasePackageZipBtn)
                .addComponent(interactive));
        checkBoxPanelGroupLayout.setVerticalGroup(checkBoxPanelGroupLayout.createSequentialGroup()
                .addComponent(updateSnapshotCheckbox)
                .addComponent(buildPatchBtn)
                .addComponent(buildConfigJarBtn)
                .addComponent(buildReleasePackageZipBtn)
                .addComponent(interactive));

        this.buildBtn = new Button("Build");
        this.runCliBuildBtn = new Button("Continue");
        this.openArtifactButton = new Button("Open artifact folder");
        openArtifactButton.setVisible(false);
        runCliBuildBtn.setVisible(false);
        runCliBuildBtn.setEnabled(false);
        JPanel buttonPanel = new JPanel();
        GroupLayout buttonPanelGroupLayout = new GroupLayout(buttonPanel);
        buttonPanel.setLayout(buttonPanelGroupLayout);
        buttonPanelGroupLayout.setAutoCreateContainerGaps(true);
        buttonPanelGroupLayout.setAutoCreateGaps(true);
        buttonPanelGroupLayout.setHorizontalGroup(buttonPanelGroupLayout.createSequentialGroup()
                .addComponent(buildBtn)
                .addComponent(runCliBuildBtn)
                .addComponent(openArtifactButton));
        buttonPanelGroupLayout.setVerticalGroup(buttonPanelGroupLayout.createParallelGroup()
                .addComponent(buildBtn)
                .addComponent(runCliBuildBtn)
                .addComponent(openArtifactButton));

        this.diffView = new DiffView();
        diffView.setVisible(false);
        diffView.setMinimumSize(new Dimension(0, 200));

        this.getArtifactInfoTabbedPane = new JTabbedPane();
        getArtifactInfoTabbedPane.setVisible(false);

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
                .addComponent(getArtifactInfoTabbedPane)
                .addComponent(buttonPanel));
        mainGroupLayout.setVerticalGroup(mainGroupLayout.createSequentialGroup()
                .addComponent(inputPanel)
                .addComponent(checkBoxPanel)
                .addComponent(diffView)
                .addComponent(getArtifactInfoTabbedPane)
                .addComponent(buttonPanel));

        scrollPane.setViewportView(mainPanel);

        GroupLayout layout = new GroupLayout(this);
        setLayout(layout);
        layout.setHorizontalGroup(layout.createSequentialGroup().addComponent(scrollPane));
        layout.setVerticalGroup(layout.createParallelGroup().addComponent(scrollPane));

        if (session.getUseWorkingDirectory()) {
            patchFileField.setVisible(false);
            entryFilterField.setVisible(true);
        } else {
            entryFilterField.setVisible(false);
            patchFileField.setVisible(true);
        }
    }

    @Override
    public void refreshUI() {
        /** noop */
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
                buildBtn.setEnabled(false);
                artifactFolderFileField.setEnabled(false);
                artifactPomFileField.setEnabled(false);
                patchFileField.setEnabled(false);
                mavenXmlSettingsFileField.setEnabled(false);
                buildConfigJarBtn.setEnabled(false);
                buildReleasePackageZipBtn.setEnabled(false);
                buildPatchBtn.setEnabled(false);
                updateSnapshotCheckbox.setEnabled(false);
                interactive.setEnabled(false);
                entryFilterField.setEnabled(false);
                diffView.setVisible(true);
                openArtifactButton.setVisible(false);
                SwingGraphicUtil.run(() -> {
                    CliBuildTool command = null;
                    try {
                        command = new CliBuildTool(
                                false,
                                session.getGitRepoURI("origin"),
                                session.getGitWorkingDir(),
                                session.getGitUsername(),
                                session.getGitPassword(),
                                session.getBaseCommit().getHash(),
                                Optional.ofNullable(session.getTargetCommit()).map(GitCommit::getHash).orElse(null),
                                session.getUseWorkingDirectory() ? entryFilterField.getText() : null,
                                artifactFolderFileField.getSelectedFile(),
                                updateSnapshotCheckbox.isSelected(),
                                buildConfigJarBtn.isSelected(),
                                buildReleasePackageZipBtn.isSelected(),
                                buildPatchBtn.isSelected(),
                                session.getUseWorkingDirectory(),
                                null,
                                null,
                                session.getUseWorkingDirectory() ? null : artifactPomFileField.getSelectedFile(),
                                null,
                                patchFileField.getSelectedFile(),
                                mavenXmlSettingsFileField.getSelectedFile(),
                                interactive.isSelected() ? this::getArtifactInfo
                                        : DefaultCliGetArtifactInfo.getArtifactInfo(false));
                        CliBuildTool finalCommand = command;
                        SwingGraphicUtil.updateUI(() -> {
                            try {
                                String targetBuild = session.getUseWorkingDirectory() ? "working directory"
                                        : session.getTargetCommit().getShortHash();
                                diffView.setLabel(MessageFormatter.format("Build diff {} -> {}",
                                        targetBuild, session.getBaseCommit().getShortHash()).getMessage());
                                List<DiffEntry> diffEntries = finalCommand.getDiff();
                                diffView.setDiffEntries(diffEntries);
                                SwingUtilities.getRoot(this).setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                            } catch (IOException ex) {
                                handleException(ex);
                            }
                        });
                        command.startBuildEnvironment();
                        command.build(false);
                        JOptionPane.showMessageDialog(ConfigDashBoard.this, "Build success", "Success",
                                JOptionPane.INFORMATION_MESSAGE);
                    } catch (Exception e1) {
                        handleException(e1);
                    } finally {
                        buildBtn.setEnabled(true);
                        buildBtn.setVisible(true);
                        runCliBuildBtn.setEnabled(false);
                        runCliBuildBtn.setVisible(false);

                        buildBtn.setEnabled(true);
                        SwingGraphicUtil.updateUI(() -> SwingUtilities.getRoot(this)
                                .setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR)));
                        artifactFolderFileField.setEnabled(true);
                        artifactPomFileField.setEnabled(true);
                        patchFileField.setEnabled(true);
                        mavenXmlSettingsFileField.setEnabled(true);
                        updateSnapshotCheckbox.setEnabled(true);
                        interactive.setEnabled(true);
                        buildConfigJarBtn.setEnabled(true);
                        buildReleasePackageZipBtn.setEnabled(true);
                        buildPatchBtn.setEnabled(true);
                        diffView.setVisible(false);
                        openArtifactButton.setVisible(true);
                        entryFilterField.setEnabled(true);

                        getArtifactInfoTabbedPane.setVisible(false);
                        if (command != null) {
                            try {
                                command.close();
                            } catch (Exception e2) {
                                logger.error(e2.getMessage(), e2);
                            }
                        }
                    }
                });
            } catch (Exception e1) {
                handleException(e1);
            }
        });

        runCliBuildBtn.addActionListener(e -> {
            synchronized (buildLock) {
                runCliBuildBtn.setEnabled(false);
                buildLock.notifyAll();
            }
        });

        buildReleasePackageZipBtn.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                buildPatchBtn.setSelected(true);
                buildConfigJarBtn.setSelected(true);
            }
        });
        buildPatchBtn.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.DESELECTED) {
                buildReleasePackageZipBtn.setSelected(false);
            }
        });
        buildConfigJarBtn.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.DESELECTED) {
                buildReleasePackageZipBtn.setSelected(false);
            }
        });

        session.<Boolean>addListener(Session.USE_WORKING_DIRECTORY_CHANGED, useWorkingDirectory -> {
            if (useWorkingDirectory) {
                patchFileField.setVisible(false);
                artifactPomFileField.setVisible(false);
                entryFilterField.setVisible(true);
            } else {
                patchFileField.setVisible(true);
                artifactPomFileField.setVisible(true);
                entryFilterField.setVisible(false);
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
        SwingGraphicUtil.updateUI(() -> {
            getArtifactInfoTabbedPane.setVisible(true);
            Set<String> modulesSet = new HashSet<>(modules);
            int i = -1;
            // Remove outdated module tab
            for (String module : artifactInfoFields.keySet()) {
                i++;
                if (!modulesSet.contains(module)) {
                    artifactInfoFields.remove(module);
                    getArtifactInfoTabbedPane.removeTabAt(i);
                }
            }

            boolean buildPatch = buildPatchBtn.isSelected();
            boolean buildConfigJar = buildConfigJarBtn.isSelected();
            boolean buildReleasePackage = buildReleasePackageZipBtn.isSelected();

            for (String module : modules) {
                if (!artifactInfoFields.containsKey(module)) {
                    JPanel inputPanel = new JPanel();

                    GroupLayout inputPanelGroupLayout = new GroupLayout(inputPanel);
                    inputPanel.setLayout(inputPanelGroupLayout);
                    inputPanelGroupLayout.setAutoCreateContainerGaps(true);
                    inputPanelGroupLayout.setAutoCreateGaps(true);

                    TextField patchNameField = new TextField("Patch name", LABEL_WIDTH, INPUT_WIDTH, null);
                    TextField configNameField = new TextField("Config name", LABEL_WIDTH, INPUT_WIDTH, null);
                    TextField releasePackageNameField = new TextField("Release package name", LABEL_WIDTH, INPUT_WIDTH,
                            null);

                    if (module.toLowerCase().contains("client")) {
                        patchNameField.setText("Patch.jar");
                    } else if (module.toLowerCase().contains("server")) {
                        patchNameField.setText("SPatch.jar");
                    }
                    configNameField.setText("Config.jar");
                    releasePackageNameField.setText("ReleasePackage.zip");

                    inputPanelGroupLayout.setHorizontalGroup(inputPanelGroupLayout.createParallelGroup()
                            .addComponent(patchNameField)
                            .addComponent(configNameField)
                            .addComponent(releasePackageNameField));

                    inputPanelGroupLayout.setVerticalGroup(inputPanelGroupLayout.createSequentialGroup()
                            .addComponent(patchNameField)
                            .addComponent(configNameField)
                            .addComponent(releasePackageNameField));

                    artifactInfoFields.put(module, List.of(patchNameField, configNameField, releasePackageNameField));

                    getArtifactInfoTabbedPane.addTab(module, inputPanel);
                }

                artifactInfoFields.get(module).get(0).setVisible(buildPatch);
                artifactInfoFields.get(module).get(1).setVisible(buildConfigJar);
                artifactInfoFields.get(module).get(2).setVisible(buildReleasePackage);
            }

            runCliBuildBtn.setEnabled(true);
            runCliBuildBtn.setVisible(true);
            buildBtn.setVisible(false);
        });

        synchronized (buildLock) {
            try {
                // Wait until the lock get unlocked by run cli build btn
                buildLock.wait();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        Map<String, ArtifactInfo> info = new HashMap<>();
        for (Map.Entry<String, List<TextField>> entry : artifactInfoFields.entrySet()) {
            boolean isServer = entry.getKey().toLowerCase().contains("server");
            info.put(entry.getKey(), ArtifactInfo.builder()
                    .patchName(entry.getValue().get(0).getText())
                    .configName(entry.getValue().get(1).getText())
                    .releasePackageName(entry.getValue().get(2).getText())
                    .compress(!isServer)
                    .build());
        }
        return info;
    }
}
