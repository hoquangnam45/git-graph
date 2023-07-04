package com.ttl.internal.vn.tool.builder.component.input;

import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BoxLayout;
import javax.swing.GroupLayout;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.apache.commons.lang3.StringUtils;

import com.ttl.internal.vn.tool.builder.util.SwingGraphicUtil;

public class FileField extends TextField {
    protected Button selectFolderButton;

    public FileField(String label, int labelWidth, int inputWidth, int fileSelectionMode,
            List<TextValidator> validators) {
        this(label, labelWidth, inputWidth, fileSelectionMode, true, BoxLayout.X_AXIS, validators);
    }

    public FileField(String label, int labelWidth, int inputWidth, int fileSelectionMode, boolean stretch, int axis,
            List<TextValidator> validators) {
        super();
        this.validators = new ArrayList<>();
        if (validators != null) {
            this.validators.addAll(validators);
        }
        this.selectFolderButton = new Button("...");
        JLabel inputLabel = new JLabel(label);
        this.innerTextField = new JTextField();
        JPanel textFieldPanel = new JPanel();
        GroupLayout textFieldPanelGroupLayout = new GroupLayout(textFieldPanel);
        textFieldPanel.setLayout(textFieldPanelGroupLayout);
        textFieldPanelGroupLayout.setHorizontalGroup(
                textFieldPanelGroupLayout.createSequentialGroup()
                        .addComponent(innerTextField)
                        .addComponent(selectFolderButton));
        textFieldPanelGroupLayout.setVerticalGroup(textFieldPanelGroupLayout.createParallelGroup()
                .addComponent(innerTextField, TEXTFIELD_HEIGHT, TEXTFIELD_HEIGHT, TEXTFIELD_HEIGHT)
                .addComponent(selectFolderButton, TEXTFIELD_HEIGHT, TEXTFIELD_HEIGHT, TEXTFIELD_HEIGHT));

        setLayout(createLayout(inputLabel, textFieldPanel, labelWidth, inputWidth, stretch, axis));

        selectFolderButton.addActionListener(e -> {
            SwingGraphicUtil.updateUI(() -> {
                JFileChooser fileChooser = new JFileChooser();
                File location = new File(getText());
                if (location.exists()) {
                    fileChooser.setSelectedFile(location);
                }
                fileChooser.setFileSelectionMode(fileSelectionMode);
                int result = fileChooser.showOpenDialog(this);
                // Check if a file was selected
                if (result == JFileChooser.APPROVE_OPTION) {
                    // Get the selected file
                    File selectedFile = fileChooser.getSelectedFile();
                    String text = selectedFile.getAbsolutePath();
                    this.innerTextField.setText(text);
                }
            });
        });

        innerTextField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                innerTextField.setText(getText().trim());
            }
        });
    }

    public File getSelectedFile() {
        String inputText = getText();
        if (StringUtils.isBlank(inputText)) {
            return null;
        }
        return new File(inputText);
    }

    @Override
    public void setEditable(boolean editable) {
        super.setEditable(editable);
        selectFolderButton.setEnabled(editable);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        selectFolderButton.setEnabled(enabled);
    }
}
