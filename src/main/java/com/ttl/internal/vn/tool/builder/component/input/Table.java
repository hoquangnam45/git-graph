package com.ttl.internal.vn.tool.builder.component.input;

import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JList;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;

public abstract class Table<T> extends JTable {
    private TableModel<T> tableModel;

    protected Table(List<String> columnHeaders) {
        super();
        this.tableModel = new TableModel<>(this, columnHeaders);
        setModel(tableModel);
    }

    public abstract List<Object> convertToRow(T val);

    public void addRow(T val) {
        tableModel.addRow(val);
    }

    public void setDatas(List<T> datas) {
        tableModel.setDatas(datas);
    }

    public void clearDatas() {
        tableModel.clear();
    }

    public static class TableModel<T> extends DefaultTableModel {
        private List<String> columns;
        private Map<Integer, Map<Integer, Boolean>> editables;
        private boolean defaultEditable = false;
        private transient List<T> datas;
        private Table<T> table;

        public TableModel(Table<T> table, List<String> columns) {
            super();
            this.columns = columns;
            this.editables = new HashMap<>();
            this.table = table;
            this.datas = new ArrayList<>();
        }

        public void clear() {
            datas.clear();
            fireTableDataChanged();
        }

        public void addRow(int rowIndex, T data) {
            datas.add(rowIndex, data);
            fireTableRowsInserted(rowIndex, rowIndex);
        }

        public void addRow(T data) {
            addRow(datas.size(), data);
        }

        public void setDatas(List<T> datas) {
            this.datas = datas;
            fireTableDataChanged();
        }

        @Override
        public int getColumnCount() {
            if (columns == null) {
                return super.getColumnCount();
            }
            return columns.size();
        }

        @Override
        public boolean isCellEditable(int row, int col) {
            return editables.getOrDefault(row, new HashMap<>()).getOrDefault(col, defaultEditable);
        }

        @Override
        public int getRowCount() {
            if (datas == null) {
                return super.getRowCount();
            }
            return datas.size();
        }

        @Override
        public String getColumnName(int index) {
            return columns.get(index);
        }

        @Override
        public Object getValueAt(int row, int column) {
            return table.convertToRow(datas.get(row)).get(column);
        }
    }

    public static class MultilineCellRenderer extends JList<String> implements TableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
                int row, int column) {
            // make multi line where the cell value is String[]
            if (value instanceof String[]) {
                setListData((String[]) value);
                if (getPreferredSize().height > table.getRowHeight(row)) {
                    table.setRowHeight(row, getPreferredSize().height);
                }
            }

            // cell backgroud color when selected
            if (isSelected) {
                setBackground(table.getSelectionBackground());
                setForeground(table.getSelectionForeground());
            } else {
                setBackground(table.getBackground());
                setForeground(table.getForeground());
            }

            return this;
        }
    }

    public static class EllipsisMultilineCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
                int row, int column) {
            // make multi line where the cell value is String[]
            if (value instanceof String[]) {
                String[] values = (String[]) value;
                if (values.length > 1) {
                    setText(((String[]) value)[0] + "...");
                    setToolTipText(String.join(",", values));
                } else if (values.length == 1) {
                    setText(((String[]) value)[0]);
                    setToolTipText("");
                } else {
                    setText("");
                    setToolTipText("");
                }

                if (getPreferredSize().height > table.getRowHeight(row)) {
                    table.setRowHeight(row, getPreferredSize().height);
                }
            }

            // cell backgroud color when selected
            if (isSelected) {
                setBackground(table.getSelectionBackground());
                setForeground(table.getSelectionForeground());
            } else {
                setBackground(table.getBackground());
                setForeground(table.getForeground());
            }

            return this;
        }
    }

    private transient MouseListener changeSelectionOnRightClick;

    public void changeSelectionOnRightClick(boolean enable) {
        if (enable) {
            this.changeSelectionOnRightClick = new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (SwingUtilities.isRightMouseButton(e)) {
                        // Get the clicked row and column
                        int clickedRow = rowAtPoint(e.getPoint());
                        int clickedColumn = columnAtPoint(e.getPoint());

                        // Set the selection to the clicked row and column
                        changeSelection(clickedRow, clickedColumn, false, false);
                        // JPopupMenu contextMenu = new JPopupMenu();
                        // JMenuItem baseCommitAction = new JMenuItem("Set base commit");
                        // JMenuItem targetCommitAction = new JMenuItem("Set target commit");
                        // contextMenu.add(baseCommitAction);
                        // contextMenu.add(targetCommitAction);
                        // baseCommitAction.addActionListener(e1 -> {
                        // System.out.println(getSelectedRow());
                        // });
                        // contextMenu.show(Table.this, e.getX(), e.getY());
                    }
                }
            };
            addMouseListener(changeSelectionOnRightClick);
        } else if (changeSelectionOnRightClick != null) {
            removeMouseListener(changeSelectionOnRightClick);
        }
    }

    public static class RowSpanCellRenderer extends JTextArea implements TableCellRenderer {
        public RowSpanCellRenderer() {
            setLineWrap(true);
            setWrapStyleWord(true);
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
                int row, int column) {
            setText(value != null ? value.toString() : "");
            setSize(table.getColumnModel().getColumn(column).getWidth(), table.getPreferredSize().height);

            // cell backgroud color when selected
            if (isSelected) {
                setBackground(table.getSelectionBackground());
                setForeground(table.getSelectionForeground());
            } else {
                setBackground(table.getBackground());
                setForeground(table.getForeground());
            }

            return this;
        }
    }
}
