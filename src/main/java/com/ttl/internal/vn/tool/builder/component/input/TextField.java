package com.ttl.internal.vn.tool.builder.component.input;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusEvent.Cause;
import java.awt.event.FocusListener;
import java.awt.event.InputMethodListener;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.swing.BoxLayout;
import javax.swing.GroupLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentListener;

import org.apache.commons.lang3.StringUtils;

public class TextField extends JPanel {
    protected static final int TEXTFIELD_HEIGHT = 28;

    protected transient List<TextValidator> validators;
    protected JTextField textField;

    protected TextField() {
        super();
    }

    public TextField(String label, int labelWidth, int inputWidth, List<TextValidator> validators) {
        this(label, labelWidth, inputWidth, true, BoxLayout.X_AXIS, validators);
    }

    // NOTE: axis refer to the axis of label and input not the axis of stretch
    public TextField(String label, int labelWidth, int inputWidth, boolean stretch, int axis,
            List<TextValidator> validators) {
        super();
        if (validators != null) {
            this.validators = new ArrayList<>(validators);
        } else {
            this.validators = new ArrayList<>();
        }
        JLabel inputLabel = new JLabel(label);
        this.textField = new JTextField();

        setLayout(createLayout(inputLabel, textField, labelWidth, labelWidth, stretch, axis));

        textField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                textField.setText(getText().trim());
            }
        });
    }

    protected GroupLayout createLayout(JLabel label, Component textField, int labelWidth, int textFieldWidth,
            boolean stretch, int axis) {
        label.setPreferredSize(new Dimension(labelWidth, label.getPreferredSize().height));
        textField.setPreferredSize(new Dimension(textFieldWidth, 50));

        GroupLayout layout = new GroupLayout(this);
        layout.setAutoCreateContainerGaps(true);
        layout.setAutoCreateGaps(true);
        int maxTextFieldSize = stretch ? Short.MAX_VALUE : textFieldWidth;
        if (axis == BoxLayout.X_AXIS) {
            layout.setHorizontalGroup(
                    layout.createSequentialGroup()
                            .addComponent(label, labelWidth, labelWidth, labelWidth)
                            .addComponent(textField, textFieldWidth, textFieldWidth, maxTextFieldSize));
            layout.setVerticalGroup(
                    layout.createParallelGroup(GroupLayout.Alignment.CENTER)
                            .addComponent(label, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE,
                                    GroupLayout.PREFERRED_SIZE)
                            .addComponent(textField, TEXTFIELD_HEIGHT, TEXTFIELD_HEIGHT, TEXTFIELD_HEIGHT));
        } else {
            layout.setHorizontalGroup(
                    layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                            .addComponent(label, labelWidth, labelWidth, Short.MAX_VALUE)
                            .addComponent(textField, textFieldWidth, textFieldWidth, maxTextFieldSize));
            layout.setVerticalGroup(
                    layout.createSequentialGroup()
                            .addComponent(label, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE,
                                    GroupLayout.PREFERRED_SIZE)
                            .addComponent(textField, TEXTFIELD_HEIGHT, TEXTFIELD_HEIGHT, TEXTFIELD_HEIGHT));
        }
        return layout;
    }

    public String getText() {
        return textField.getText();
    }

    public void setEditable(boolean editable) {
        textField.setEditable(editable);
    }

    public boolean isEditable() {
        return textField.isEditable();
    }

    public void setText(String text) {
        textField.setText(text);
    }

    @Override
    public void requestFocus() {
        textField.requestFocus();
    }

    @Override
    public boolean requestFocus(boolean temporary) {
        return textField.requestFocus(temporary);
    }

    @Override
    public synchronized void addFocusListener(FocusListener l) {
        textField.addFocusListener(l);
    }

    @Override
    public void requestFocus(Cause cause) {
        textField.requestFocus(cause);
    }

    public List<ValidatorError> validateInput() {
        return validators.stream().map(it -> it.validate(getText())).filter(Objects::nonNull)
                .map(it -> {
                    it.setSrc(this);
                    return it;
                })
                .collect(Collectors.toList());
    }

    public void addValidators(List<TextValidator> validators) {
        this.validators.addAll(validators);
    }

    public void addValidator(TextValidator validator) {
        validators.add(validator);
    }

    public void removeValidator(TextValidator validator) {
        validators.remove(validator);
    }

    public void addActionListener(ActionListener l) {
        textField.addActionListener(l);
    }

    @Override
    public synchronized void addKeyListener(KeyListener l) {
        textField.addKeyListener(l);
    }

    @Override
    public synchronized void addInputMethodListener(InputMethodListener l) {
        textField.addInputMethodListener(l);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        textField.setEnabled(enabled);
    }

    public void addDocumentListener(DocumentListener l) {
        textField.getDocument().addDocumentListener(l);
    }

    public static class Validator {
        public static TextValidator notBlank() {
            return (text) -> {
                if (StringUtils.isBlank(text)) {
                    return ValidatorError.builder()
                            .validatorId("NOT_BLANK")
                            .validatorMessage("Not allow blank value")
                            .build();
                }
                return null;
            };
        }
    }
}
