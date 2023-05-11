package com.ttl.internal.vn.tool.builder.component;

import com.ttl.internal.vn.tool.builder.component.input.ValidatorError;

import lombok.Getter;

@Getter
public class ValidationException extends Exception {
    private final ValidatorError error;

    public ValidationException(ValidatorError e) {
        super(e.getValidatorMessage());
        this.error = e;
    }
}
