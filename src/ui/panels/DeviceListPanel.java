package ui.panels;

import core.network.Device;
import ui.Theme;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class DeviceListPanel extends JPanel {

    private final DefaultListModel<Device> deviceListModel;
    private final JList<Device> deviceList;
    private JLabel countLabel;
    private JLabel emptyState;
    private final Map<String, Device> deviceMap = new HashMap<>();

    public DeviceListPanel() {
        setLayout(new BorderLayout(6, 6));
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setPreferredSize(new Dimension(260, 0));
        setBackground(Theme.CARD);

        // Title
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JLabel titleLabel = new JLabel("Devices Online");
        titleLabel.setFont(Theme.HEADER_FONT);
        titleLabel.setForeground(Theme.TEXT);
        top.add(titleLabel, BorderLayout.WEST);

        JLabel countLabel = new JLabel("(0)");
        countLabel.setForeground(Theme.MUTED);
        countLabel.setFont(Theme.BODY_FONT);
        top.add(countLabel, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        // List model & list
        deviceListModel = new DefaultListModel<>();
        deviceList = new JList<>(deviceListModel);
        deviceList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        deviceList.setCellRenderer(new DeviceCellRenderer());

        JScrollPane scrollPane = new JScrollPane(deviceList);
        scrollPane.setBorder(null);
        add(scrollPane, BorderLayout.CENTER);

        // Info label
        // make countLabel and emptyState accessible to instance methods
        this.countLabel = countLabel;
        JLabel emptyState = new JLabel("No devices discovered yet.", SwingConstants.CENTER);
        this.emptyState = emptyState;
        emptyState.setFont(Theme.BODY_FONT);
        emptyState.setForeground(Theme.MUTED);
        add(emptyState, BorderLayout.SOUTH);

        // selection listener propagation
        deviceList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                Device selected = deviceList.getSelectedValue();
                if (selectionListener != null) selectionListener.onDeviceSelected(selected);
                countLabel.setText("(" + deviceListModel.getSize() + ")");
                emptyState.setVisible(deviceListModel.isEmpty());
            }
        });
    }

    public void addDevice(Device device) {
        SwingUtilities.invokeLater(() -> {
            if (!deviceMap.containsKey(device.getIpAddress())) {
                deviceMap.put(device.getIpAddress(), device);
                deviceListModel.addElement(device);
                // update any external listener
                if (selectionListener != null) selectionListener.onDeviceAdded(device);
                // auto-select first device when list was empty, so send UI becomes usable
                if (deviceList.getSelectedIndex() == -1 && deviceListModel.getSize() > 0) {
                    deviceList.setSelectedIndex(0);
                }
                // update UI badges
                countLabel.setText("(" + deviceListModel.getSize() + ")");
                emptyState.setVisible(deviceListModel.isEmpty());
            }
        });
    }

    public void removeDevice(Device device) {
        SwingUtilities.invokeLater(() -> {
            if (deviceMap.containsKey(device.getIpAddress())) {
                deviceMap.remove(device.getIpAddress());
                deviceListModel.removeElement(device);
                if (selectionListener != null) selectionListener.onDeviceRemoved(device);
                // update count/empty state
                countLabel.setText("(" + deviceListModel.getSize() + ")");
                emptyState.setVisible(deviceListModel.isEmpty());
            }
        });
    }

    /**
     * Update existing device record (e.g., username changed) and refresh UI element.
     */
    public void updateDevice(Device device) {
        SwingUtilities.invokeLater(() -> {
            int idx = -1;
            for (int i = 0; i < deviceListModel.size(); i++) {
                Device d = deviceListModel.get(i);
                if (d.getIpAddress().equals(device.getIpAddress())) {
                    idx = i;
                    break;
                }
            }
            if (idx >= 0) {
                // replace element to trigger renderer/refresh
                deviceListModel.set(idx, device);
            }
        });
    }

    public Device getSelectedDevice() {
        return deviceList.getSelectedValue();
    }

    // Selection listener callbacks allow other UI components to react
    public interface DeviceSelectionListener {
        void onDeviceSelected(Device device);
        default void onDeviceAdded(Device d) {}
        default void onDeviceRemoved(Device d) {}
    }

    private DeviceSelectionListener selectionListener;

    public void setSelectionListener(DeviceSelectionListener l) { this.selectionListener = l; }

    // Custom cell renderer untuk tampilan lebih bagus
    private static class DeviceCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

                if (value instanceof Device) {
                Device device = (Device) value;
                label.setText("<html><b>" + device.getUsername() + "</b><br>" +
                    "<small>" + device.getIpAddress() + "</small></html>");
                label.setIcon(UIManager.getIcon("FileView.directoryIcon"));
                label.setBorder(new EmptyBorder(6, 6, 6, 6));
                label.setOpaque(true);
                label.setBackground(isSelected ? Theme.HEADER : Theme.CARD);
                label.setForeground(Theme.TEXT);
            }

            return label;
        }
    }
}
