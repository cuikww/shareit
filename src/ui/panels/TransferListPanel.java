package ui.panels;

import core.transfer.TransferModel;
import core.transfer.TransferModel.TransferStatus;
import ui.Theme;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class TransferListPanel extends JPanel {

    private JTable transferTable;
    private TransferTableModel tableModel;
    
    // Data Source
    private List<TransferModel> transfers = new CopyOnWriteArrayList<>();
    private List<TransferModel> filteredTransfers = new CopyOnWriteArrayList<>();
    
    private TransferModel.Direction filterDirection = null; 
    private JButton toggleBtn;
    private JButton cancelBtn;
    private ControlListener controlListener;

    // --- MEMORY: Menyimpan ID yang sedang dipilih ---
    private String selectedTransferId = null;

    public TransferListPanel() {
        setLayout(new BorderLayout(6, 6));
        setBackground(Theme.BG);
        javax.swing.border.TitledBorder t = BorderFactory.createTitledBorder("📊 Active Transfers");
        t.setTitleColor(Theme.TEXT);
        setBorder(t);

        // Init Model & Table
        tableModel = new TransferTableModel();
        transferTable = new JTable(tableModel);
        transferTable.setRowHeight(50);
        transferTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        transferTable.setShowGrid(true);
        transferTable.setGridColor(Theme.MUTED);
        transferTable.setBackground(Theme.CARD);
        transferTable.setForeground(Theme.TEXT);

        // Column Widths
        transferTable.getColumnModel().getColumn(0).setPreferredWidth(150); // Filename
        transferTable.getColumnModel().getColumn(1).setPreferredWidth(200); // Progress
        transferTable.getColumnModel().getColumn(2).setPreferredWidth(100); // Speed
        transferTable.getColumnModel().getColumn(3).setPreferredWidth(80);  // Time
        transferTable.getColumnModel().getColumn(4).setPreferredWidth(100); // Status

        // Renderer
        transferTable.getColumnModel().getColumn(1).setCellRenderer(new ProgressBarCellRenderer());

        JScrollPane scrollPane = new JScrollPane(transferTable);
        scrollPane.setPreferredSize(new Dimension(600, 150));
        add(scrollPane, BorderLayout.CENTER);

        // Buttons
        JPanel buttonPanel = createButtonPanel();
        add(buttonPanel, BorderLayout.SOUTH);

        // --- LISTENER PENTING: MOUSE CLICK ---
        // Menggunakan MouseListener lebih stabil daripada SelectionListener untuk kasus realtime update
        transferTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = transferTable.rowAtPoint(e.getPoint());
                if (row >= 0 && row < filteredTransfers.size()) {
                    // 1. Simpan ID baris yang diklik
                    selectedTransferId = filteredTransfers.get(row).getTransferId();
                    // 2. Update status tombol
                    updateButtonState();
                }
            }
        });
    }

    /**
     * Update data dari Controller/Task
     */
    public void updateTransfer(TransferModel model) {
        // 1. Update Master List
        int masterIndex = -1;
        for (int i = 0; i < transfers.size(); i++) {
            if (transfers.get(i).getTransferId().equals(model.getTransferId())) {
                masterIndex = i;
                break;
            }
        }
        if (masterIndex >= 0) transfers.set(masterIndex, model);
        else transfers.add(model);

        // 2. Cek Filter (Send/Receive)
        boolean isVisible = (filterDirection == null || model.getDirection() == filterDirection);

        SwingUtilities.invokeLater(() -> {
            if (!isVisible) return;

            // 3. Update Filtered List (Tabel)
            int tableIndex = -1;
            for (int i = 0; i < filteredTransfers.size(); i++) {
                if (filteredTransfers.get(i).getTransferId().equals(model.getTransferId())) {
                    tableIndex = i;
                    break;
                }
            }

            if (tableIndex >= 0) {
                // UPDATE
                filteredTransfers.set(tableIndex, model);
                // Hanya render ulang baris tersebut (Smart Update)
                tableModel.fireTableRowsUpdated(tableIndex, tableIndex);
                
                // 4. RESTORE SELECTION (PENTING!)
                // Jika baris yang diupdate adalah yang sedang dipilih user,
                // pastikan tabel tetap menyorotnya & tombol tetap update.
                if (selectedTransferId != null && selectedTransferId.equals(model.getTransferId())) {
                    if (transferTable.getSelectedRow() != tableIndex) {
                        transferTable.setRowSelectionInterval(tableIndex, tableIndex);
                    }
                    updateButtonState(); // Pastikan tombol tetap nyala
                }
            } else {
                // INSERT
                filteredTransfers.add(model);
                int newRow = filteredTransfers.size() - 1;
                tableModel.fireTableRowsInserted(newRow, newRow);
            }
        });
    }

    public void setFilterDirection(TransferModel.Direction dir) {
        this.filterDirection = dir;
        rebuildFiltered();
    }

    private void rebuildFiltered() {
        filteredTransfers.clear();
        for (TransferModel m : transfers) {
            if (filterDirection == null || m.getDirection() == filterDirection) {
                filteredTransfers.add(m);
            }
        }
        SwingUtilities.invokeLater(() -> {
            tableModel.fireTableDataChanged();
            selectedTransferId = null; // Reset selection on page change
            updateButtonState();
        });
    }

    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));
        panel.setOpaque(false);

        toggleBtn = new JButton("⏸ Pause");
        toggleBtn.setEnabled(false);
        toggleBtn.addActionListener(e -> handleToggle());

        cancelBtn = new JButton("✕ Cancel");
        cancelBtn.setForeground(Theme.WARN.darker());
        cancelBtn.setEnabled(false);
        cancelBtn.addActionListener(e -> handleCancel());

        panel.add(toggleBtn);
        panel.add(cancelBtn);

        return panel;
    }

    // --- LOGIC TOMBOL MENGGUNAKAN 'selectedTransferId' ---
    // Kita tidak pakai getSelectedRow() di sini agar lebih aman
    
    private void updateButtonState() {
        // Cari model berdasarkan ID yang tersimpan
        TransferModel selected = findModelById(selectedTransferId);

        if (selected == null) {
            toggleBtn.setText("⏸ Pause");
            toggleBtn.setEnabled(false);
            cancelBtn.setEnabled(false);
            return;
        }

        // Enable tombol
        cancelBtn.setEnabled(true);
        toggleBtn.setEnabled(true);

        // Update Text
        if (selected.getStatus() == TransferStatus.PAUSED) {
            toggleBtn.setText("▶ Resume");
        } else {
            toggleBtn.setText("⏸ Pause");
        }
    }

    private void handleToggle() {
        TransferModel selected = findModelById(selectedTransferId);
        if (selected == null) return;

        if (selected.getStatus() == TransferStatus.PAUSED) {
            selected.setStatus(TransferStatus.TRANSFERRING);
            if (controlListener != null) controlListener.resumeTransfer(selected.getTransferId());
        } else if (selected.isInProgress()) {
            selected.setStatus(TransferStatus.PAUSED);
            if (controlListener != null) controlListener.pauseTransfer(selected.getTransferId());
        }
        // Force update UI baris ini agar status berubah cepat
        updateTransfer(selected);
    }

    private void handleCancel() {
        TransferModel selected = findModelById(selectedTransferId);
        if (selected == null) return;

        selected.setStatus(TransferStatus.CANCELLED);
        if (controlListener != null) controlListener.cancelTransfer(selected.getTransferId());
        
        updateTransfer(selected);
    }

    // Helper: Cari object model berdasarkan ID
    private TransferModel findModelById(String id) {
        if (id == null) return null;
        for (TransferModel m : filteredTransfers) {
            if (m.getTransferId().equals(id)) return m;
        }
        return null;
    }

    public void setControlListener(ControlListener listener) {
        this.controlListener = listener;
    }

    public interface ControlListener {
        void pauseTransfer(String transferId);
        void resumeTransfer(String transferId);
        void cancelTransfer(String transferId);
    }

    // --- TABLE MODEL & RENDERER (Sama seperti sebelumnya) ---
    private class TransferTableModel extends AbstractTableModel {
        private static final String[] COLUMN_NAMES = {"Filename", "Progress", "Speed", "Time Left", "Status"};

        @Override
        public int getRowCount() { return filteredTransfers.size(); }
        @Override
        public int getColumnCount() { return COLUMN_NAMES.length; }
        @Override
        public String getColumnName(int column) { return COLUMN_NAMES[column]; }
        @Override
        public Object getValueAt(int row, int column) {
            if (row >= filteredTransfers.size()) return null;
            TransferModel model = filteredTransfers.get(row);
            switch (column) {
                case 0: return model.getFileName();
                case 1: return model; 
                case 2: return model.getSpeedFormatted();
                case 3: return model.isComplete() ? "Done" : model.getRemainingTimeFormatted();
                case 4: return model.getStatus().getDisplayName();
                default: return null;
            }
        }
        @Override
        public Class<?> getColumnClass(int column) {
            return (column == 1) ? TransferModel.class : String.class;
        }
    }

    private static class ProgressBarCellRenderer extends JPanel implements TableCellRenderer {
        private JProgressBar progressBar;
        private JLabel percentLabel;

        public ProgressBarCellRenderer() {
            setLayout(new BorderLayout(5, 0));
            setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
            progressBar = new JProgressBar(0, 100);
            progressBar.setStringPainted(false);
            percentLabel = new JLabel("0%");
            percentLabel.setPreferredSize(new Dimension(40, -1));
            percentLabel.setHorizontalAlignment(JLabel.RIGHT);
            add(progressBar, BorderLayout.CENTER);
            add(percentLabel, BorderLayout.EAST);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            if (value instanceof TransferModel) {
                TransferModel model = (TransferModel) value;
                int progress = model.getProgress();
                progressBar.setValue(progress);
                percentLabel.setText(progress + "%");

                if (model.getStatus() == TransferStatus.COMPLETED) progressBar.setForeground(Theme.SUCCESS);
                else if (model.getStatus() == TransferStatus.FAILED || model.getStatus() == TransferStatus.CANCELLED) progressBar.setForeground(Theme.WARN);
                else if (model.getStatus() == TransferStatus.PAUSED) progressBar.setForeground(Theme.ACCENT.darker());
                else progressBar.setForeground(new Color(0x84C5E8));

                setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
                setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
            }
            return this;
        }
    }
}