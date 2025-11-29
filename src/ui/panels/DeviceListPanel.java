package ui.panels;

import core.network.Device;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class DeviceListPanel extends JPanel {

    private final DefaultListModel<Device> deviceListModel;
    private final JList<Device> deviceList;
    private final Map<String, Device> deviceMap = new HashMap<>();

    public DeviceListPanel() {
        setLayout(new BorderLayout(5, 5));
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setPreferredSize(new Dimension(250, 0));

        // Title
        JLabel titleLabel = new JLabel("Perangkat Online");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 14));
        add(titleLabel, BorderLayout.NORTH);

        // List model & list
        deviceListModel = new DefaultListModel<>();
        deviceList = new JList<>(deviceListModel);
        deviceList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        deviceList.setCellRenderer(new DeviceCellRenderer());

        JScrollPane scrollPane = new JScrollPane(deviceList);
        add(scrollPane, BorderLayout.CENTER);

        // Info label
        JLabel infoLabel = new JLabel("<html><i>Menunggu device...</i></html>");
        infoLabel.setFont(new Font("Arial", Font.PLAIN, 11));
        infoLabel.setForeground(Color.GRAY);
        add(infoLabel, BorderLayout.SOUTH);
    }

    public void addDevice(Device device) {
        SwingUtilities.invokeLater(() -> {
            if (!deviceMap.containsKey(device.getIpAddress())) {
                deviceMap.put(device.getIpAddress(), device);
                deviceListModel.addElement(device);
            }
        });
    }

    public void removeDevice(Device device) {
        SwingUtilities.invokeLater(() -> {
            if (deviceMap.containsKey(device.getIpAddress())) {
                deviceMap.remove(device.getIpAddress());
                deviceListModel.removeElement(device);
            }
        });
    }

    public Device getSelectedDevice() {
        return deviceList.getSelectedValue();
    }

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
                label.setIcon(UIManager.getIcon("FileView.computerIcon"));
                label.setBorder(new EmptyBorder(5, 5, 5, 5));
            }

            return label;
        }
    }
}
