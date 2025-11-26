package ui.frames;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

public class MainFrame extends JFrame {

    private JLabel statusLabel;
    private JPanel devicePanelPlaceholder;   // Area kerja Anggota 2
    private JPanel transferPanelPlaceholder; // Area kerja Anggota 3

    public MainFrame() {
        initUI();
    }

    private void initUI() {
        setTitle("ShareIt Clone (Java Socket) - Level 3 Project");
        setSize(900, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null); // Tengah layar
        setLayout(new BorderLayout(10, 10));

        // --- 1. HEADER (Status Server) ---
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        headerPanel.setBackground(new Color(60, 63, 65));
        statusLabel = new JLabel("Status: Menginisialisasi...");
        statusLabel.setForeground(Color.WHITE);
        statusLabel.setFont(new Font("Arial", Font.BOLD, 14));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        headerPanel.add(statusLabel);
        
        add(headerPanel, BorderLayout.NORTH);

        // --- 2. LEFT PANEL (Daftar Teman) ---
        // Ini nanti diganti dengan 'DeviceListPanel' oleh Anggota 2
        devicePanelPlaceholder = new JPanel();
        devicePanelPlaceholder.setPreferredSize(new Dimension(250, 0));
        devicePanelPlaceholder.setBorder(new TitledBorder(" Perangkat Online (Tugas Anggota 2) "));
        devicePanelPlaceholder.setBackground(new Color(240, 240, 240));
        devicePanelPlaceholder.add(new JLabel("<html><i>List teman dari UDP Discovery<br>akan muncul di sini.</i></html>"));
        
        add(devicePanelPlaceholder, BorderLayout.WEST);

        // --- 3. CENTER PANEL (Daftar Transfer) ---
        // Ini nanti diganti dengan 'TransferListPanel' oleh Anggota 3
        transferPanelPlaceholder = new JPanel();
        transferPanelPlaceholder.setBorder(new TitledBorder(" Antrian Transfer (Tugas Anggota 3) "));
        transferPanelPlaceholder.setBackground(Color.WHITE);
        transferPanelPlaceholder.add(new JLabel("<html><i>Progress Bar & Speed<br>akan muncul di sini.</i></html>"));

        add(transferPanelPlaceholder, BorderLayout.CENTER);
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
}