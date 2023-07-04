package com.ttl.internal.vn.tool.builder.component.input;

import java.awt.Color;
import java.awt.Dimension;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import javax.swing.GroupLayout;
import javax.swing.GroupLayout.ParallelGroup;
import javax.swing.GroupLayout.SequentialGroup;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;

import org.eclipse.jgit.diff.DiffEntry;

import com.ttl.internal.vn.tool.builder.component.ISimpleComponent;

public class DiffView extends JPanel implements ISimpleComponent {
    private transient List<DiffEntry> diffEntries;
    private JPanel containerPanel;
    private JLabel diffLabel;

    public DiffView() {
        super();
        initUI();
    }

    public void setDiffEntries(List<DiffEntry> diffEntries) {
        this.diffEntries = diffEntries;
        refreshUI();
    }

    @Override
    public void initUI() {
        this.containerPanel = new JPanel();
        GroupLayout groupLayout = new GroupLayout(containerPanel);
        containerPanel.setLayout(groupLayout);
        setupGroupLayout(groupLayout, getLabelComponents(diffEntries));

        this.diffLabel = new JLabel();

        JScrollPane scrollPane = new JScrollPane();
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setPreferredSize(new Dimension(250, 250));
        scrollPane.setViewportView(containerPanel);

        GroupLayout mainGroupLayout = new GroupLayout(this);
        mainGroupLayout.setAutoCreateContainerGaps(true);
        mainGroupLayout.setAutoCreateGaps(true);
        setLayout(mainGroupLayout);
        mainGroupLayout.setHorizontalGroup(mainGroupLayout.createParallelGroup()
                .addComponent(diffLabel, GroupLayout.Alignment.TRAILING)
                .addComponent(scrollPane));
        mainGroupLayout.setVerticalGroup(mainGroupLayout.createSequentialGroup()
                .addComponent(diffLabel)
                .addComponent(scrollPane));
    }

    public void clearDiff() {
        containerPanel.removeAll();
        containerPanel.revalidate();
        containerPanel.repaint();
    }

    public void setLabel(String label) {
        diffLabel.setText(label);
    }

    @Override
    public void refreshUI() {
        containerPanel.removeAll();
        setupGroupLayout((GroupLayout) containerPanel.getLayout(), getLabelComponents(diffEntries));
        containerPanel.revalidate();
        containerPanel.repaint();
    }

    private void setupGroupLayout(GroupLayout groupLayout, List<JLabel> labelEntries) {
        ParallelGroup horizontalGroup = groupLayout.createParallelGroup(GroupLayout.Alignment.LEADING);
        SequentialGroup verticalGroup = groupLayout.createSequentialGroup();
        groupLayout.setAutoCreateContainerGaps(true);
        groupLayout.setAutoCreateGaps(true);
        for (JLabel label : labelEntries) {
            horizontalGroup.addComponent(label);
            verticalGroup.addComponent(label);
        }
        groupLayout.setVerticalGroup(verticalGroup);
        groupLayout.setHorizontalGroup(horizontalGroup);
    }

    private List<JLabel> getLabelComponents(List<DiffEntry> entries) {
        if (entries == null) {
            return List.of();
        }
        List<JLabel> labels = new ArrayList<>();
        for (DiffEntry entry : diffEntries) {
            String changeType;
            JLabel label = new JLabel();
            Color changeColor;
            String changeDescription;
            switch (entry.getChangeType()) {
                case ADD:
                    changeType = "A";
                    changeColor = Color.GREEN;
                    changeDescription = entry.getNewPath();
                    break;
                case MODIFY:
                    changeType = "M";
                    changeColor = Color.YELLOW;
                    changeDescription = entry.getNewPath();
                    break;
                case DELETE:
                    changeType = "D";
                    changeColor = Color.RED;
                    changeDescription = entry.getOldPath();
                    break;
                case RENAME:
                    changeType = "R";
                    changeColor = Color.CYAN;
                    changeDescription = entry.getOldPath() + " -> " + entry.getNewPath();
                    break;
                case COPY:
                    changeType = "C";
                    changeColor = Color.MAGENTA;
                    changeDescription = entry.getOldPath() + " -> " + entry.getNewPath();
                    break;
                default:
                    throw new UnsupportedOperationException();
            }
            String htmlColor = "#" + Integer.toHexString(changeColor.getRGB()).substring(2).toUpperCase();
            label.setText(MessageFormat.format("<html><b color={0}>{1}</b> {2}</html>", htmlColor, changeType,
                    changeDescription));
            labels.add(label);
        }
        return labels;
    }

    @Override
    public void registerListeners() throws Exception {
        /** noop */
    }

    @Override
    public void handleException(Exception e) throws Exception {
        /** noop */
    }
}
