package com.ttl.internal.vn.tool.builder.component;

public interface ISimpleComponent {
    void initUI() throws Throwable;

    void refreshUI() throws Throwable;

    void registerListeners() throws Throwable;

    void handleException(Throwable e) throws Throwable;
}
