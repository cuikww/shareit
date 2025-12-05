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

        JPanel rightHeader = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        rightHeader.setOpaque(false);
        userLabel = new JLabel("");
        userLabel.setForeground(Theme.TEXT);
        userLabel.setFont(Theme.BODY_FONT);

        // Tombol Back
        backButton = new JButton("← Back");
        backButton.setVisible(false); // Default hidden di awal
        backButton.addActionListener(e -> showLanding());

        // Setup Header Layout
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

        // --- 2. MAIN CONTENT ---
        deviceListPanel = new DeviceListPanel();
        transferListPanel = new TransferListPanel();

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

        cards = new JPanel(new CardLayout());
        
        // Setup Send Page
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
            public void onRemoveSelected(java.util.List<java.io.File> removed) { }

            @Override
            public void onSendFiles(java.util.List<java.io.File> files) {
                if (controller != null)
                    controller.handleSendFiles(files);
            }
        });

        JPanel rightForSend = new JPanel(new BorderLayout(8, 8));
        rightForSend.add(sendPanel, BorderLayout.NORTH);
        rightForSend.add(transferListPanel, BorderLayout.CENTER);

        deviceListPanel.setSelectionListener(new ui.panels.DeviceListPanel.DeviceSelectionListener() {
            @Override
            public void onDeviceSelected(core.network.Device device) {
                sendPanel.setSelectedDevice(device);
            }
        });

        JPanel sendPage = new JPanel(new BorderLayout());
        sendPage.add(rightForSend, BorderLayout.CENTER);

        // Setup Receive Page
        ReceivePanel receivePanel = new ReceivePanel();
        JPanel rightForReceive = new JPanel(new BorderLayout(8, 8));
        rightForReceive.add(receivePanel, BorderLayout.NORTH);
        rightForReceive.add(transferListPanel, BorderLayout.CENTER);

        JPanel receivePage = new JPanel(new BorderLayout());
        receivePage.add(rightForReceive, BorderLayout.CENTER);

        cards.add(landingPanel, "landing");
        cards.add(sendPage, "send");
        cards.add(receivePage, "receive");

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, deviceListPanel, cards);
        mainSplit.setResizeWeight(0.25);
        add(mainSplit, BorderLayout.CENTER);

        // --- 3. BOTTOM / FOOTER (REVISED) ---
        // Hapus "Tip" label, sisakan progress bar container saja
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

        // Langsung add container progress bar ke SOUTH tanpa panel tambahan text
        add(overallProgressContainer, BorderLayout.SOUTH);
    }

    // --- LOGIC PERUBAHAN WARNA STATUS ---
    public void setServerStatus(String ip, int port, boolean isRunning) {
        if (isRunning) {
            statusLabel.setText("✅ Server Online | IP: " + ip + ":" + port);
            // Ganti warna menjadi Hijau Tua (Dark Green) agar lebih enak di mata
            statusLabel.setForeground(new Color(0, 100, 0)); 
        } else {
            statusLabel.setText("❌ Server Offline");
            statusLabel.setForeground(new Color(178, 34, 34)); // Firebrick Red
        }
    }

    public void setUser(String name) {
        userLabel.setText("User: " + name + "    ");
        // HAPUS baris ini: backButton.setVisible(true); 
        // Agar tombol back tidak muncul saat aplikasi baru start (di set dari Controller)
    }

    public void setLandingName(String name) {
        if (landingPanel != null)
            landingPanel.setName(name);
    }

    private void showLanding() {
        CardLayout cl = (CardLayout) cards.getLayout();
        cl.show(cards, "landing");
        // Sembunyikan tombol back di halaman awal
        backButton.setVisible(false); 
        
        if (controller != null) controller.enableReceiveMode(false);
    }

    public void showSendPage() {
        CardLayout cl = (CardLayout) cards.getLayout();
        cl.show(cards, "send");
        // Tampilkan tombol back
        backButton.setVisible(true);
        
        if (transferListPanel != null)
            transferListPanel.setFilterDirection(core.transfer.TransferModel.Direction.OUTGOING);
        if (controller != null) controller.enableReceiveMode(false);
    }

    public void showReceivePage() {
        CardLayout cl = (CardLayout) cards.getLayout();
        cl.show(cards, "receive");
        // Tampilkan tombol back
        backButton.setVisible(true);
        
        if (transferListPanel != null)
            transferListPanel.setFilterDirection(core.transfer.TransferModel.Direction.INCOMING);
        if (controller != null) controller.enableReceiveMode(true);
    }

    // Delegasi method lainnya tidak berubah...
    public void addDevice(Device device) { deviceListPanel.addDevice(device); }
    public void updateDevice(Device device) { deviceListPanel.updateDevice(device); }
    public void removeDevice(Device device) { deviceListPanel.removeDevice(device); }
    public Device getSelectedDevice() { return deviceListPanel.getSelectedDevice(); }
    public void showError(String message) { JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE); }
    public void showInfo(String message) { JOptionPane.showMessageDialog(this, message, "Info", JOptionPane.INFORMATION_MESSAGE); }
    public TransferListPanel getTransferListPanel() { return transferListPanel; }

    public void setOverallProgressVisible(boolean visible) {
        if (overallProgressContainer != null) overallProgressContainer.setVisible(visible);
    }

    public void updateOverallProgress(long bytesTransferred, long totalBytes, double speedKBps) {
        if (overallProgressBar == null) return;
        if (totalBytes <= 0) {
            overallProgressBar.setIndeterminate(true);
            overallProgressBar.setString("Sending — Calculating...");
        } else {
            overallProgressBar.setIndeterminate(false);
            int percent = (int) ((bytesTransferred * 100) / totalBytes);
            overallProgressBar.setValue(percent);
            String label = String.format("%s / %s — %d%%", formatBytes(bytesTransferred), formatBytes(totalBytes), percent);
            overallProgressBar.setString(label);
        }
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