package com.ttl.internal.vn.tool.builder.component.input;

import java.awt.event.ActionListener;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

public class PopupMenu extends JPopupMenu {
    public void add(JMenuItem item, ActionListener l) {
        add(item);
        item.addActionListener(l);
    }
}
