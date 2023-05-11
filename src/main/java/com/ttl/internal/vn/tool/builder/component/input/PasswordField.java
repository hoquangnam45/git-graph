package com.ttl.internal.vn.tool.builder.component.input;

import java.util.ArrayList;
import java.util.List;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPasswordField;

public class PasswordField extends TextField {
    public PasswordField(String label, int labelWidth, int inputWidth, List<TextValidator> validators) {
        this(label, labelWidth, inputWidth, BoxLayout.X_AXIS, true, validators);
    }

    public PasswordField(String label, int labelWidth, int inputWidth, int axis, boolean stretch,
            List<TextValidator> validators) {
        super();
        if (validators != null) {
            this.validators = new ArrayList<>(validators);
        } else {
            this.validators = new ArrayList<>();
        }
        JLabel inputLabel = new JLabel(label);
        this.textField = new JPasswordField();

        setLayout(createLayout(inputLabel, textField, labelWidth, inputWidth, stretch, axis));
    }

    @Override
    public String getText() {
        return new String(((JPasswordField) textField).getPassword());
    }
}