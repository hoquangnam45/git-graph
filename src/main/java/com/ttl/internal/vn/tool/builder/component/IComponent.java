package com.ttl.internal.vn.tool.builder.component;

public interface IComponent extends ISimpleComponent {
    void initData() throws Exception;
    void refreshData() throws Exception;
}
