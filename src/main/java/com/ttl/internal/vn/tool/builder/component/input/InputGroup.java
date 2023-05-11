package com.ttl.internal.vn.tool.builder.component.input;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.ttl.internal.vn.tool.builder.util.SwingGraphicUtil;

public class InputGroup {
    private List<Object> inputs = new ArrayList<>();
    
    public void addInput(TextField input) {
        inputs.add(input);
    }

    public void addInputGroup(InputGroup group) {
        inputs.add(group);
    }

    public List<ValidatorError> validate() {
        return inputs.stream().map(it -> {
            if (it instanceof TextField) {
                var textField = (TextField) it;
                if (textField.isEnabled()) {
                    return textField.validateInput();
                }
            } else if (it instanceof InputGroup) {
                return ((InputGroup) it).validate();
            }
            return List.<ValidatorError>of();
        }).flatMap(List::stream).collect(Collectors.toList());
    }

    public void setEditable(boolean editable) {
        SwingGraphicUtil.updateUI(() -> {
            inputs.forEach((Object it) -> {
                if (it instanceof TextField) {
                    ((TextField) it).setEditable(editable);
                } else if (it instanceof InputGroup) { 
                    ((InputGroup) it).setEditable(editable);
                }
            });
        });
    }

    public void setEnabled(boolean enabled) {
        SwingGraphicUtil.updateUI(() -> {
            inputs.forEach((Object it) -> {
                if (it instanceof TextField) {
                    ((TextField) it).setEnabled(enabled);
                } else if (it instanceof InputGroup) {
                    ((InputGroup) it).setEnabled(enabled);
                }
            });
        });
    }
}
