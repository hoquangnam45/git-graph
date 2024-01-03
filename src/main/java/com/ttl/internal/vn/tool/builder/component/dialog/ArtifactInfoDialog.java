package com.ttl.internal.vn.tool.builder.component.dialog;

import com.ttl.internal.vn.tool.builder.cli.CliBuildTool.ArtifactInfo;
import com.ttl.internal.vn.tool.builder.component.IDialog;
import com.ttl.internal.vn.tool.builder.component.ValidationException;
import com.ttl.internal.vn.tool.builder.component.input.TextField;
import com.ttl.internal.vn.tool.builder.component.input.ValidatorError;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.ttl.internal.vn.tool.builder.component.input.Button;
import javax.swing.*;
import java.util.*;
import java.util.stream.Collectors;

public class ArtifactInfoDialog extends JDialog implements IDialog {
    public enum ArtifactInfoDialogStatus {
        OK,
        ERROR,
        CANCEL,
    }
    private transient Logger logger = LogManager.getLogger(ArtifactInfoDialog.class);

    private static final int INPUT_WIDTH = 300;
    private static final int LABEL_WIDTH = 150;
    private final Map<String, List<TextField>> artifactInfoFields = new HashMap<>();

    private ArtifactInfoDialogStatus status = ArtifactInfoDialogStatus.CANCEL;
    private final List<String> freeChangedModuleRelativePaths;

    private final JTabbedPane getArtifactInfoTabbedPane = new JTabbedPane();
    private final boolean buildPatch;
    private final boolean buildConfig;
    private final boolean buildReleasePackage;
    private Button confirmBtn;
    private Button cancelBtn;

    public ArtifactInfoDialog(List<String> freeChangedModuleRelativePaths, boolean buildPatch, boolean buildConfig, boolean buildReleasePackage) {
        this.freeChangedModuleRelativePaths = freeChangedModuleRelativePaths;
        this.buildPatch = buildPatch;
        this.buildConfig = buildConfig;
        this.buildReleasePackage = buildReleasePackage;
        initUI();
        registerListeners();
    }

    public Map<String, ArtifactInfo> getCollectedArtifactInfo() {
        return freeChangedModuleRelativePaths.stream().collect(Collectors.toMap(it -> it, it -> {
            List<TextField> fields = artifactInfoFields.get(it);
            String moduleName = it.toLowerCase();
            boolean isServer = moduleName.contains("server");
            return ArtifactInfo.builder()
                    .patchName(fields.get(0).getText())
                    .configName(fields.get(1).getText())
                    .releasePackageName(fields.get(2).getText())
                    .configCompress(!isServer)
                    .build();
        }));
    }

    @Override
    public int showDialog() {
        setVisible(true);
        return status.ordinal();
    }

    @Override
    public void initUI() {
        this.confirmBtn = new Button("Confirm");
        this.cancelBtn = new Button("Cancel");
        for (String module : freeChangedModuleRelativePaths) {
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

                artifactInfoFields.put(module, Arrays.asList(patchNameField, configNameField, releasePackageNameField));

                getArtifactInfoTabbedPane.addTab(module, inputPanel);
            }

            artifactInfoFields.get(module).get(0).setVisible(buildPatch);
            artifactInfoFields.get(module).get(1).setVisible(buildConfig);
            artifactInfoFields.get(module).get(2).setVisible(buildReleasePackage);
        }

        JPanel btnPanel = new JPanel();
        GroupLayout btnGroupLayout = new GroupLayout(btnPanel);
        btnPanel.setLayout(btnGroupLayout);
        btnGroupLayout.setAutoCreateContainerGaps(true);
        btnGroupLayout.setAutoCreateGaps(true);
        btnGroupLayout.setHorizontalGroup(btnGroupLayout.createSequentialGroup()
                .addComponent(cancelBtn)
                .addComponent(confirmBtn));
        btnGroupLayout.setVerticalGroup(btnGroupLayout.createParallelGroup()
                .addComponent(cancelBtn)
                .addComponent(confirmBtn));

        GroupLayout mainGroupLayout = new GroupLayout(getContentPane());
        getContentPane().setLayout(mainGroupLayout);
        mainGroupLayout.setAutoCreateContainerGaps(true);
        mainGroupLayout.setAutoCreateGaps(true);
        mainGroupLayout.setHorizontalGroup(mainGroupLayout.createParallelGroup()
                .addComponent(getArtifactInfoTabbedPane)
                .addComponent(btnPanel));
        mainGroupLayout.setVerticalGroup(mainGroupLayout.createSequentialGroup()
                .addComponent(getArtifactInfoTabbedPane)
                .addComponent(btnPanel));
        pack();
    }

    @Override
    public void refreshUI()  {
        /** noop */
    }

    @Override
    public void registerListeners() {
        cancelBtn.addActionListener(e -> {
            this.status = ArtifactInfoDialogStatus.CANCEL;
            dispose();
        });
        confirmBtn.addActionListener(e -> {
            List<ValidatorError> validatorErrors = artifactInfoFields.values().stream().flatMap(List::stream).map(TextField::validateInput).flatMap(List::stream).collect(Collectors.toList());
            if (!validatorErrors.isEmpty()) {
                validatorErrors.get(0).getSrc().requestFocus();
            } else {
                this.status = ArtifactInfoDialogStatus.OK;
                dispose();
            }
        });
    }

    @Override
    public void handleException(Exception e) {
        logger.error(e.getMessage(), e);
        status = ArtifactInfoDialogStatus.ERROR;
        JOptionPane.showMessageDialog(this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        if (e instanceof ValidationException) {
            ((ValidationException) e).getError().getSrc().requestFocus();
        }
    }
}
