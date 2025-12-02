package ui.panels;

import core.transfer.TransferModel;
import core.transfer.TransferModel.TransferStatus;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import ui.Theme;
import java.awt.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Panel untuk menampilkan list transfer dengan progress bar
 * 
 * Diagram alur (dari Phase 3 UML):
 * SenderTask.setBytesTransferred(currentBytes)
 * → Model.calculatePercent() (e.g., 45%)
 * → SwingUtilities.invokeLater(() -> refreshTable())
 * → fireTableDataChanged()
 * → ProgressBarCellRenderer.getTableCellRendererComponent()
 * → UI progress bar bergerak ke 45%
 */
public class TransferListPanel extends JPanel {

    private JTable transferTable;
    private TransferTableModel tableModel;
    private List<TransferModel> transfers = new CopyOnWriteArrayList<>();
    private List<TransferModel> filteredTransfers = new CopyOnWriteArrayList<>();
    private TransferModel.Direction filterDirection = null; // null = show all
    private JButton toggleBtn;
    private JButton cancelBtn;
    private ControlListener controlListener;

    public TransferListPanel() {
        setLayout(new BorderLayout(6, 6));
        setBackground(Theme.BG);
        javax.swing.border.TitledBorder t = BorderFactory.createTitledBorder("📊 Active Transfers");
        t.setTitleColor(Theme.TEXT);
        setBorder(t);

        // Create table model
        tableModel = new TransferTableModel();

        // Create table
        transferTable = new JTable(tableModel);
        transferTable.setRowHeight(50);
        transferTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        transferTable.setShowGrid(true);
        transferTable.setGridColor(Theme.MUTED);
        transferTable.setBackground(Theme.CARD);
        transferTable.setForeground(Theme.TEXT);

        // Set column widths
        transferTable.getColumnModel().getColumn(0).setPreferredWidth(150); // Filename
        transferTable.getColumnModel().getColumn(1).setPreferredWidth(200); // Progress
        transferTable.getColumnModel().getColumn(2).setPreferredWidth(100); // Speed
        transferTable.getColumnModel().getColumn(3).setPreferredWidth(100); // Time Left
        transferTable.getColumnModel().getColumn(4).setPreferredWidth(100); // Status

        // Set custom renderer untuk progress bar
        transferTable.getColumnModel().getColumn(1).setCellRenderer(
                new ProgressBarCellRenderer());

        // Add scroll pane
        JScrollPane scrollPane = new JScrollPane(transferTable);
        scrollPane.setPreferredSize(new Dimension(600, 150));
        add(scrollPane, BorderLayout.CENTER);

        // Add button panel
        JPanel buttonPanel = createButtonPanel();
        add(buttonPanel, BorderLayout.SOUTH);

        // Update toggle label when selection changes
        transferTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    updateToggleButtonLabel();
                }
            }
        });
        // initialize filtered view
        rebuildFiltered();
    }

    public void setFilterDirection(TransferModel.Direction dir) {
        this.filterDirection = dir;
        rebuildFiltered();
        SwingUtilities.invokeLater(() -> tableModel.fireTableDataChanged());
    }

    private void rebuildFiltered() {
        filteredTransfers.clear();
        for (TransferModel m : transfers) {
            if (filterDirection == null || m.getDirection() == filterDirection) {
                filteredTransfers.add(m);
            }
        }
    }

    /**
     * Create control buttons panel
     */
    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));

        // Toggle Pause/Resume button
        toggleBtn = new JButton("⏸ Pause");
        toggleBtn.addActionListener(e -> toggleSelectedTransfer());

        // Cancel button
        cancelBtn = new JButton("✕ Cancel");
        cancelBtn.setForeground(Theme.WARN.darker());
        cancelBtn.addActionListener(e -> cancelSelectedTransfer());

        panel.add(toggleBtn);
        panel.add(cancelBtn);

        return panel;
    }

    /**
     * Add atau update transfer di table
     */
    public void updateTransfer(TransferModel model) {
        // Cek apakah transfer sudah ada
        int index = -1;
        for (int i = 0; i < transfers.size(); i++) {
            if (transfers.get(i).getTransferId().equals(model.getTransferId())) {
                index = i;
                break;
            }
        }

        if (index >= 0) {
            transfers.set(index, model);
            rebuildFiltered();
            SwingUtilities.invokeLater(() -> tableModel.fireTableDataChanged());
        } else {
            transfers.add(model);
            rebuildFiltered();
            SwingUtilities.invokeLater(() -> tableModel.fireTableDataChanged());
        }
    }

    /**
     * Remove transfer dari table
     */
    public void removeTransfer(String transferId) {
        int index = -1;
        for (int i = 0; i < transfers.size(); i++) {
            if (transfers.get(i).getTransferId().equals(transferId)) {
                index = i;
                break;
            }
        }

        if (index >= 0) {
            transfers.remove(index);
            rebuildFiltered();
            SwingUtilities.invokeLater(() -> tableModel.fireTableDataChanged());
        }
    }

    /**
     * Refresh entire table
     */
    public void refreshTable() {
        SwingUtilities.invokeLater(() -> tableModel.fireTableDataChanged());
    }

    /**
     * Get transfer by ID
     */
    public TransferModel getTransfer(String transferId) {
        for (TransferModel model : transfers) {
            if (model.getTransferId().equals(transferId)) {
                return model;
            }
        }
        return null;
    }

    /**
     * Get selected transfer
     */
    public TransferModel getSelectedTransfer() {
        int row = transferTable.getSelectedRow();
        if (row >= 0) {
            if (row < filteredTransfers.size()) return filteredTransfers.get(row);
        }
        return null;
    }

    /**
     * Pause selected transfer
     */
    private void pauseSelectedTransfer() {
        TransferModel selected = getSelectedTransfer();
        if (selected != null && selected.isInProgress()) {
            selected.setStatus(TransferStatus.PAUSED);
            refreshTable();
            System.out.println("[TransferListPanel] Paused transfer: " + selected.getFileName());
            if (controlListener != null) {
                controlListener.pauseTransfer(selected.getTransferId());
            }
        }
    }

    /**
     * Resume selected transfer
     */
    private void resumeSelectedTransfer() {
        TransferModel selected = getSelectedTransfer();
        if (selected != null && selected.getStatus() == TransferStatus.PAUSED) {
            selected.setStatus(TransferStatus.TRANSFERRING);
            refreshTable();
            System.out.println("[TransferListPanel] Resumed transfer: " + selected.getFileName());
            if (controlListener != null) {
                controlListener.resumeTransfer(selected.getTransferId());
            }
        }
    }

    /**
     * Cancel selected transfer
     */
    private void cancelSelectedTransfer() {
        TransferModel selected = getSelectedTransfer();
        if (selected != null) {
            selected.setStatus(TransferStatus.CANCELLED);
            refreshTable();
            System.out.println("[TransferListPanel] Cancelled transfer: " + selected.getFileName());
            if (controlListener != null) {
                controlListener.cancelTransfer(selected.getTransferId());
            }
        }
    }

    /**
     * Toggle selected transfer: pause if running, resume if paused.
     */
    private void toggleSelectedTransfer() {
        TransferModel selected = getSelectedTransfer();
        if (selected == null)
            return;

        if (selected.getStatus() == TransferStatus.PAUSED) {
            // resume
            if (controlListener != null) {
                controlListener.resumeTransfer(selected.getTransferId());
            }
            selected.setStatus(TransferStatus.TRANSFERRING);
            System.out.println("[TransferListPanel] Toggled -> Resumed: " + selected.getFileName());
        } else if (selected.isInProgress()) {
            // pause
            if (controlListener != null) {
                controlListener.pauseTransfer(selected.getTransferId());
            }
            selected.setStatus(TransferStatus.PAUSED);
            System.out.println("[TransferListPanel] Toggled -> Paused: " + selected.getFileName());
        }

        refreshTable();
        updateToggleButtonLabel();
    }

    private void updateToggleButtonLabel() {
        TransferModel selected = getSelectedTransfer();
        if (selected == null) {
            toggleBtn.setText("⏸ Pause");
            toggleBtn.setEnabled(false);
            cancelBtn.setEnabled(false);
            return;
        }

        cancelBtn.setEnabled(true);
        toggleBtn.setEnabled(true);

        if (selected.getStatus() == TransferStatus.PAUSED) {
            toggleBtn.setText("▶ Resume");
        } else {
            toggleBtn.setText("⏸ Pause");
        }
    }

    /**
     * Register control listener to delegate pause/resume/cancel actions
     */
    public void setControlListener(ControlListener listener) {
        this.controlListener = listener;
    }

    /**
     * Listener interface for control actions from UI to controller/manager
     */
    public interface ControlListener {
        void pauseTransfer(String transferId);

        void resumeTransfer(String transferId);

        void cancelTransfer(String transferId);
    }

    // ==================== Inner Class: Table Model ====================

    private class TransferTableModel extends AbstractTableModel {

        private static final String[] COLUMN_NAMES = {
                "Filename",
                "Progress",
                "Speed",
                "Time Left",
                "Status"
        };

        @Override
        public int getRowCount() {
            return filteredTransfers.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMN_NAMES.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMN_NAMES[column];
        }

        @Override
        public Object getValueAt(int row, int column) {
            TransferModel model = filteredTransfers.get(row);

            switch (column) {
                case 0: // Filename
                    return model.getFileName();
                case 1: // Progress (akan di-render sebagai progress bar)
                    return model; // Pass entire model untuk renderer
                case 2: // Speed
                    return model.getSpeedFormatted();
                case 3: // Time Left
                    if (model.isComplete()) {
                        return "Done";
                    } else {
                        return model.getRemainingTimeFormatted();
                    }
                case 4: // Status
                    return model.getStatus().getDisplayName();
                default:
                    return null;
            }
        }

        @Override
        public Class<?> getColumnClass(int column) {
            if (column == 1) {
                return TransferModel.class; // Progress column
            }
            return String.class;
        }
    }

    // ==================== Inner Class: Progress Bar Renderer ====================

    private static class ProgressBarCellRenderer extends JPanel implements TableCellRenderer {

        private JProgressBar progressBar;
        private JLabel percentLabel;

        public ProgressBarCellRenderer() {
            setLayout(new BorderLayout(5, 0));
            setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

            progressBar = new JProgressBar(0, 100);
            progressBar.setString("");
            progressBar.setStringPainted(false);

            percentLabel = new JLabel("0%");
            percentLabel.setPreferredSize(new Dimension(40, -1));
            percentLabel.setHorizontalAlignment(JLabel.RIGHT);

            add(progressBar, BorderLayout.CENTER);
            add(percentLabel, BorderLayout.EAST);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {

            if (value instanceof TransferModel) {
                TransferModel model = (TransferModel) value;
                int progress = model.getProgress();

                progressBar.setValue(progress);
                percentLabel.setText(progress + "%");

                // Color based on status
                if (model.getStatus() == TransferStatus.COMPLETED) {
                    progressBar.setForeground(Theme.SUCCESS);
                } else if (model.getStatus() == TransferStatus.FAILED ||
                        model.getStatus() == TransferStatus.CANCELLED) {
                    progressBar.setForeground(Theme.WARN);
                } else if (model.getStatus() == TransferStatus.PAUSED) {
                    progressBar.setForeground(Theme.ACCENT.darker());
                } else {
                    progressBar.setForeground(new Color(0x84C5E8));
                }

                if (isSelected) {
                    setBackground(table.getSelectionBackground());
                    setForeground(table.getSelectionForeground());
                } else {
                    setBackground(table.getBackground());
                    setForeground(table.getForeground());
                }
            }

            return this;
        }
    }
}
