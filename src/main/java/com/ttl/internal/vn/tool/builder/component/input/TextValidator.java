package com.ttl.internal.vn.tool.builder.component.input;

@FunctionalInterface
public interface TextValidator {
    ValidatorError validate(String text);
}
