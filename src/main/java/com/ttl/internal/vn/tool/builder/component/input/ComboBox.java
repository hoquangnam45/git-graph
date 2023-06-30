package com.ttl.internal.vn.tool.builder.component.input;

import java.awt.event.ActionListener;
import java.awt.event.ItemListener;
import java.util.List;
import java.util.Vector;

import javax.swing.GroupLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.GroupLayout.Group;

public class ComboBox<E> extends JPanel {
    private JComboBox<E> comboBox;
    private JLabel label;

    public ComboBox(String label, ListCellRenderer<? super E> renderer) {
        this(label, new Vector<>(), renderer);
    }

    public ComboBox(String label, Vector<E> options, ListCellRenderer<? super E> renderer) {
        this(label, options, -1, renderer);
    }
    
    public ComboBox(String label, Vector<E> options, Integer selectedIndex, ListCellRenderer<? super E> renderer) {
        super();

        GroupLayout mainLayout = new GroupLayout(this);
        mainLayout.setAutoCreateContainerGaps(true);
        mainLayout.setAutoCreateGaps(true);
        setLayout(mainLayout);
        comboBox = new JComboBox<>(options);
        this.label = new JLabel(label);
        if (renderer != null) {
            comboBox.setRenderer(renderer);
        }

        mainLayout.setHorizontalGroup(mainLayout.createParallelGroup()
            .addComponent(this.label)
            .addComponent(comboBox, 0, GroupLayout.PREFERRED_SIZE, Short.MAX_VALUE));
            
        mainLayout.setVerticalGroup(mainLayout.createSequentialGroup()
            .addComponent(this.label)
            .addComponent(comboBox, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE));
    }

    public void setRenderer(ListCellRenderer<? super E> renderer) {
        comboBox.setRenderer(renderer);
    }

    @SuppressWarnings("unchecked")
    public E getSelectedItem() {
        return (E) comboBox.getSelectedItem();
    }

    public int getSelectedIndex() {
        return comboBox.getSelectedIndex();
    }

    public void addActionListener(ActionListener l) {
        comboBox.addActionListener(l);
    }

    public void addItemListener(ItemListener l) {
        comboBox.addItemListener(l);
    }

    public void setSelections(List<E> selections) {
        comboBox.removeAllItems();
        for (E item: selections) {
            comboBox.addItem(item);
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        comboBox.setEnabled(enabled);
    }
    
    public void setSelectedItem(E item) {
        comboBox.setSelectedItem(item);
    }
    
    public void setEditable(boolean editable) {
        comboBox.setEditable(editable);
    }

    public void setLabel(String branch) {
        label.setText(branch);
    }
}
