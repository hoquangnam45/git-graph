package com.ttl.internal.vn.tool.builder.component.input;

import java.awt.Component;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class ValidatorError {
    public Component src;
    public String validatorId;
    public String validatorGroup;
    public String validatorMessage;

    public void setSrc(Component src) {
        this.src = src;
    }
}
