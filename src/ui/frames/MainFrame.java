package ui.frames;

import controllers.MainController;
import core.network.Device;
import ui.panels.DeviceListPanel;
import ui.panels.TransferListPanel;
import ui.panels.LandingPanel;
import ui.panels.SendPanel;
import ui.panels.ReceivePanel;
import ui.panels.LandingPanel.LandingListener;
import javax.swing.border.EmptyBorder;
import javax.swing.*;
import ui.Theme;
import java.awt.*;

public class MainFrame extends JFrame {

    private JLabel statusLabel;
    private DeviceListPanel deviceListPanel;
    private SendPanel sendPanel;
    private TransferListPanel transferListPanel;
    private LandingPanel landingPanel;
    private JPanel cards;
    private JLabel userLabel;
    private JButton backButton;
    private MainController controller;
    private JProgressBar overallProgressBar;
    private JLabel overallSpeedLabel;
    private JPanel overallProgressContainer;

    public MainFrame(MainController controller) {
        this.controller = controller;
        initUI();
    }

    private void initUI() {
        setTitle("FlowLink - Fast, Simple, Effortless File Transfer");
        setSize(900, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null); // Tengah layar
        setLayout(new BorderLayout(10, 10));

        // --- 1. HEADER (Status Server) ---
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(ui.Theme.HEADER);
        headerPanel.setBorder(new EmptyBorder(8, 12, 8, 12));

        JPanel leftHeader = new JPanel(new FlowLayout(FlowLayout.LEFT));
        leftHeader.setOpaque(false);
        statusLabel = new JLabel("Status: Menginisialisasi...");
        statusLabel.setForeground(ui.Theme.TEXT);
        statusLabel.setFont(Theme.HEADER_FONT);
        leftHeader.add(statusLabel);

        // Right header area (user) uses transparent background
        JPanel rightHeader = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        rightHeader.setOpaque(false);
        rightHeader.setOpaque(false);
        userLabel = new JLabel("");
        userLabel.setForeground(Theme.TEXT);
        userLabel.setFont(Theme.BODY_FONT);

        // Move back button to top-left helper (visually before status)
        backButton = new JButton("← Back");
        backButton.setVisible(false);
        backButton.addActionListener(e -> showLanding());

        // For layout, add back button on the left header before status
        leftHeader.removeAll();
        backButton.setBackground(Theme.ACCENT);
        backButton.setForeground(Theme.TEXT);
        backButton.setFont(Theme.BODY_FONT);
        backButton.setBorderPainted(false);
        backButton.setFocusPainted(false);
        backButton.setPreferredSize(new java.awt.Dimension(84, 30));
        leftHeader.add(backButton);
        leftHeader.add(Box.createRigidArea(new Dimension(8, 0)));
        statusLabel.setBorder(new EmptyBorder(0, 4, 0, 0));
        leftHeader.add(statusLabel);

        rightHeader.add(userLabel);

        headerPanel.add(leftHeader, BorderLayout.WEST);
        headerPanel.add(rightHeader, BorderLayout.EAST);

        add(headerPanel, BorderLayout.NORTH);

        // left device list (will be part of card pages)
        deviceListPanel = new DeviceListPanel();

        // Transfer list
        transferListPanel = new TransferListPanel();

        // Landing panel
        landingPanel = new LandingPanel();
        landingPanel.setLandingListener(new LandingListener() {
            @Override
            public void onSendSelected(String username) {
                setUser(username);
                if (controller != null)
                    controller.setUsername(username);
                showSendPage();
            }

            @Override
            public void onReceiveSelected(String username) {
                setUser(username);
                if (controller != null)
                    controller.setUsername(username);
                showReceivePage();
            }
        });

        // card container
        cards = new JPanel(new CardLayout());

        // Create two pages: main center (send/transfer) and receive
        // Create Send and Receive pages which contain the device list on the left and
        // a main area to manage files and review transfers on the right.
        this.sendPanel = new SendPanel();
        sendPanel.setSendListener(new SendPanel.SendListener() {
            @Override
            public void onAddFiles() {
                if (controller != null) {
                    java.util.List<java.io.File> selected = controller.chooseFilesToAdd();
                    if (selected != null && !selected.isEmpty())
                        sendPanel.addFiles(selected);
                }
            }

            @Override
            public void onAddFolder() {
                if (controller != null) {
                    java.io.File selected = controller.chooseFolder();
                    if (selected != null)
                        sendPanel.addFiles(java.util.Arrays.asList(selected));
                }
            }

            @Override
            public void onRemoveSelected(java.util.List<java.io.File> removed) {
                // nothing extra for now
            }

            @Override
            public void onSendFiles(java.util.List<java.io.File> files) {
                // Pass files and selected device to controller
                if (controller != null)
                    controller.handleSendFiles(files);
            }
        });

        JPanel rightForSend = new JPanel(new BorderLayout(8, 8));
        rightForSend.add(sendPanel, BorderLayout.NORTH);
        rightForSend.add(transferListPanel, BorderLayout.CENTER);

        // Hook device selection changes to update SendPanel selected device
        deviceListPanel.setSelectionListener(new ui.panels.DeviceListPanel.DeviceSelectionListener() {
            @Override
            public void onDeviceSelected(core.network.Device device) {
                sendPanel.setSelectedDevice(device);
            }
        });

        JPanel sendPage = new JPanel(new BorderLayout());
        sendPage.add(rightForSend, BorderLayout.CENTER);

        ReceivePanel receivePanel = new ReceivePanel();
        JPanel rightForReceive = new JPanel(new BorderLayout(8, 8));
        rightForReceive.add(receivePanel, BorderLayout.NORTH);
        rightForReceive.add(transferListPanel, BorderLayout.CENTER);

        JPanel receivePage = new JPanel(new BorderLayout());
        receivePage.add(rightForReceive, BorderLayout.CENTER);

        cards.add(landingPanel, "landing");
        cards.add(sendPage, "send");
        cards.add(receivePage, "receive");

        // Make device list a persistent left column and render the pages on the right
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, deviceListPanel, cards);
        mainSplit.setResizeWeight(0.25);
        add(mainSplit, BorderLayout.CENTER);

        // bottom area: small hint and an overall sending progress area
        JPanel bottomHint = new JPanel(new BorderLayout());
        bottomHint.setBackground(Theme.BG);
        bottomHint.setPreferredSize(new Dimension(100, 80));

        JLabel hint = new JLabel("Tip: set your name on the landing page before sending/receiving");
        hint.setFont(Theme.BODY_FONT);
        JPanel hintWrap = new JPanel(new FlowLayout(FlowLayout.CENTER));
        hintWrap.setOpaque(false);
        hintWrap.add(hint);

        // Overall progress components (hidden by default)
        overallProgressContainer = new JPanel(new BorderLayout(8, 4));
        overallProgressContainer.setOpaque(false);
        overallProgressContainer.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));

        overallProgressBar = new JProgressBar(0, 100);
        overallProgressBar.setStringPainted(true);

        overallSpeedLabel = new JLabel("");
        overallSpeedLabel.setFont(Theme.BODY_FONT);

        overallProgressContainer.add(overallProgressBar, BorderLayout.CENTER);
        overallProgressContainer.add(overallSpeedLabel, BorderLayout.EAST);
        overallProgressContainer.setVisible(false);

        bottomHint.add(hintWrap, BorderLayout.NORTH);
        bottomHint.add(overallProgressContainer, BorderLayout.SOUTH);

        add(bottomHint, BorderLayout.SOUTH);
    }

    // Fungsi untuk mengubah teks status server dari Controller
    public void setServerStatus(String ip, int port, boolean isRunning) {
        if (isRunning) {
            statusLabel.setText("✅ Server Online | IP Anda: " + ip + " | Port: " + port);
            statusLabel.setForeground(new Color(100, 255, 100)); // Hijau muda
        } else {
            statusLabel.setText("❌ Server Offline");
            statusLabel.setForeground(new Color(255, 100, 100)); // Merah muda
        }
    }

    public void setUser(String name) {
        userLabel.setText("User: " + name + "   ");
        backButton.setVisible(true);
    }

    public void setLandingName(String name) {
        if (landingPanel != null)
            landingPanel.setName(name);
    }

    private void showLanding() {
        CardLayout cl = (CardLayout) cards.getLayout();
        cl.show(cards, "landing");
        backButton.setVisible(false);
        // leaving other pages - make sure we stop announcing presence
        if (controller != null) controller.enableReceiveMode(false);
    }

    public void showSendPage() {
        CardLayout cl = (CardLayout) cards.getLayout();
        cl.show(cards, "send");
        // Show only outgoing transfers when on Send page
        if (transferListPanel != null)
            transferListPanel.setFilterDirection(core.transfer.TransferModel.Direction.OUTGOING);
        // Sending page shouldn't keep the device announced as receiver
        if (controller != null) controller.enableReceiveMode(false);
    }

    public void showReceivePage() {
        CardLayout cl = (CardLayout) cards.getLayout();
        cl.show(cards, "receive");
        // Show only incoming transfers when on Receive page
        if (transferListPanel != null)
            transferListPanel.setFilterDirection(core.transfer.TransferModel.Direction.INCOMING);
        // Start announcing presence so peers can see this device as a receiver
        if (controller != null) controller.enableReceiveMode(true);
    }

    // Fungsi untuk menambah device ke list
    public void addDevice(Device device) {
        deviceListPanel.addDevice(device);
    }

    public void updateDevice(Device device) {
        deviceListPanel.updateDevice(device);
    }

    // Fungsi untuk menghapus device dari list
    public void removeDevice(Device device) {
        deviceListPanel.removeDevice(device);
    }

    // Fungsi untuk mendapatkan device yang dipilih
    public Device getSelectedDevice() {
        return deviceListPanel.getSelectedDevice();
    }

    // Fungsi untuk menampilkan error dialog
    public void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    // Fungsi untuk menampilkan info dialog
    public void showInfo(String message) {
        JOptionPane.showMessageDialog(this, message, "Info", JOptionPane.INFORMATION_MESSAGE);
    }

    // Getter untuk TransferListPanel
    public TransferListPanel getTransferListPanel() {
        return transferListPanel;
    }

    // ==================== Overall progress UI helpers ====================

    public void setOverallProgressVisible(boolean visible) {
        if (overallProgressContainer != null) overallProgressContainer.setVisible(visible);
    }

    public void updateOverallProgress(long bytesTransferred, long totalBytes, double speedKBps) {
        if (overallProgressBar == null) return;

        if (totalBytes <= 0) {
            overallProgressBar.setIndeterminate(true);
            overallProgressBar.setString("Sending — 0% (calculating)");
        } else {
            overallProgressBar.setIndeterminate(false);
            int percent = (int) ((bytesTransferred * 100) / totalBytes);
            overallProgressBar.setValue(percent);
            String label = String.format("%s / %s — %d%%", formatBytes(bytesTransferred), formatBytes(totalBytes), percent);
            overallProgressBar.setString(label);
        }

        // speed label
        if (speedKBps <= 0) {
            overallSpeedLabel.setText("");
        } else {
            if (speedKBps < 1024) {
                overallSpeedLabel.setText(String.format("%s/s", String.format("%.2f KB", speedKBps)));
            } else {
                overallSpeedLabel.setText(String.format("%s/s", String.format("%.2f MB", speedKBps / 1024.0)));
            }
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}