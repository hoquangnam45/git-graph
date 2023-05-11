package com.ttl.internal.vn.tool.builder.component.fragment;

import java.awt.Dimension;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.ttl.internal.vn.tool.builder.component.ISimpleComponent;

public class SpinningCircle extends JPanel implements ISimpleComponent {
    private final ImageIcon icon = new ImageIcon(SpinningCircle.class.getClassLoader().getResource("loading.gif"));
    
    public SpinningCircle() {
        super();
        initUI();
    }

    @Override
    public void initUI() {
        Dimension fixedSize = new Dimension(50, 50);

        JLabel label = new JLabel();
        label.setPreferredSize(fixedSize);
        label.setMinimumSize(fixedSize);
        label.setMaximumSize(fixedSize);
        label.setIcon(icon);

        add(label);
    }

    @Override
    public void refreshUI() throws Exception {
        /** noop */
    }

    @Override
    public void registerListeners() {
        /** noop */
    }
    
    public void spin() {
        setVisible(true);
    }

    public void stop() {
        setVisible(false);
    }

    @Override
    public void handleException(Exception e) {
        /** noop */
    }
}
