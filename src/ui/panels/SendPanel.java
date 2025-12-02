package ui.panels;

import ui.Theme;
import core.network.Device;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SendPanel extends JPanel {

    public interface SendListener {
        void onAddFiles(); // pops file chooser via controller
        void onAddFolder(); // pops folder chooser via controller
        void onRemoveSelected(List<File> removed);
        void onSendFiles(List<File> files);
    }

    private final DefaultListModel<File> fileListModel = new DefaultListModel<>();
    private final JList<File> fileJList = new JList<>(fileListModel);
    private JButton sendBtn;
    private SendListener listener;
    private javax.swing.JLabel selectedDeviceLabel;
    private Device selectedDevice;

    public SendPanel() {
        setLayout(new BorderLayout(8, 8));
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setBackground(Theme.BG);

        JLabel title = new JLabel("Prepare files to send");
        title.setFont(Theme.HEADER_FONT);
        title.setForeground(Theme.TEXT);

        selectedDeviceLabel = new JLabel("No receiver selected");
        selectedDeviceLabel.setFont(Theme.BODY_FONT);
        selectedDeviceLabel.setForeground(Theme.MUTED);
        // title will be placed inside the topBox (below) so don't add directly here

        fileJList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof File) {
                    File f = (File) value;
                    l.setText(f.getName() + " (" + f.length() + " bytes)");
                }
                return l;
            }
        });

        JScrollPane scroll = new JScrollPane(fileJList);
        add(scroll, BorderLayout.CENTER);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        controls.setOpaque(false);
        JButton addBtn = new JButton("＋ Add");
        addBtn.setBackground(Theme.SUCCESS);
        addBtn.setForeground(Color.white);
        addBtn.addActionListener(e -> { if (listener != null) listener.onAddFiles(); });
        JButton addFolderBtn = new JButton("＋ Add Folder");
        addFolderBtn.setBackground(new Color(0xFFB4C6));
        addFolderBtn.setForeground(Color.white);
        addFolderBtn.addActionListener(e -> { if (listener != null) listener.onAddFolder(); });
        JButton removeBtn = new JButton("— Remove");
        removeBtn.setBackground(Theme.WARN);
        removeBtn.setForeground(Color.white);
        removeBtn.addActionListener(e -> {
            List<File> selected = fileJList.getSelectedValuesList();
            for (File f : selected) fileListModel.removeElement(f);
            if (listener != null && !selected.isEmpty()) listener.onRemoveSelected(selected);
            updateSendEnabled();
        });

        sendBtn = new JButton("Send");
        sendBtn.setBackground(new Color(0x6EB5FF));
        sendBtn.setForeground(Color.white);
        sendBtn.setEnabled(false);
        sendBtn.addActionListener(e -> handleSend());

        controls.add(addBtn);
        controls.add(addFolderBtn);
        controls.add(removeBtn);
        controls.add(sendBtn);

        add(controls, BorderLayout.SOUTH);

        JPanel topBox = new JPanel(new BorderLayout());
        topBox.setOpaque(false);
        topBox.setBackground(Theme.CARD);
        topBox.add(title, BorderLayout.WEST);
        topBox.add(selectedDeviceLabel, BorderLayout.SOUTH);
        add(topBox, BorderLayout.NORTH);
    }

    public void setSendListener(SendListener l) { this.listener = l; }

    public void addFiles(List<File> selectedFiles) {
        for (File f : selectedFiles) {
            if (!fileListModel.contains(f)) fileListModel.addElement(f);
        }
        updateSendEnabled();
    }

    public void setSelectedDevice(Device device) {
        this.selectedDevice = device;
        if (device == null) {
            selectedDeviceLabel.setText("No receiver selected");
            selectedDeviceLabel.setForeground(Theme.MUTED);
        } else {
            selectedDeviceLabel.setText("Receiver: " + device.getUsername());
            selectedDeviceLabel.setForeground(Theme.TEXT);
        }
        updateSendEnabled();
    }

    public List<File> getFiles() {
        List<File> out = new ArrayList<>();
        for (int i = 0; i < fileListModel.size(); i++) out.add(fileListModel.get(i));
        return out;
    }

    private void updateSendEnabled() {
        boolean ok = !fileListModel.isEmpty() && selectedDevice != null;
        sendBtn.setEnabled(ok);
    }

    private void handleSend() {
        // SendPanel knows the currently selected device and will pass files to listener
        if (selectedDevice == null) {
            JOptionPane.showMessageDialog(this, "No receiver selected. Please choose a device first.", "No recipient", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (listener != null) listener.onSendFiles(getFiles());
    }
}
