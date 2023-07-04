package com.ttl.internal.vn.tool.builder.component;

public interface ISimpleComponent {
    void initUI() throws Exception;

    void refreshUI() throws Exception;

    void registerListeners() throws Exception;

    void handleException(Exception e) throws Exception;
}
