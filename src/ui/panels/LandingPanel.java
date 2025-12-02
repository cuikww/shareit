package ui.panels;

import ui.Theme;
import javax.swing.*;
import java.awt.*;

/**
 * Landing page shown when the app starts. Allows user to set username and choose Send/Receive mode.
 */
public class LandingPanel extends JPanel {

    public interface LandingListener {
        void onSendSelected(String username);
        void onReceiveSelected(String username);
    }

    private final JTextField nameField;
    private LandingListener listener;

    public LandingPanel() {
        setLayout(new GridBagLayout());
        setBackground(Theme.BG);

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(8, 8, 8, 8);
        c.gridx = 0;
        c.gridy = 0;
        c.fill = GridBagConstraints.NONE;

        JLabel title = new JLabel("FlowLink");
        title.setForeground(Theme.TEXT);
        title.setFont(Theme.TITLE_FONT);
        add(title, c);

        JLabel subtitle = new JLabel("Fast, Simple, Effortless File Transfer!");
        subtitle.setForeground(Theme.MUTED);
        subtitle.setFont(Theme.BODY_FONT);
        c.gridy = 1;
        add(subtitle, c);

        JPanel form = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 8));
        form.setBackground(Theme.BG);
        JLabel nameLbl = new JLabel("Your name");
        nameLbl.setForeground(Theme.TEXT);
        nameLbl.setFont(Theme.BODY_FONT);
        nameField = new JTextField(18);
        nameField.setFont(Theme.BODY_FONT);

        form.add(nameLbl);
        form.add(nameField);
        c.gridy = 2;
        add(form, c);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 16));
        buttons.setBackground(Theme.BG);

        JButton sendBtn = new JButton("📤 Send File");
        sendBtn.setPreferredSize(new Dimension(160, 40));
        sendBtn.setFont(new Font("Inter", Font.BOLD, 14));
        sendBtn.setBackground(Theme.SUCCESS);
        sendBtn.setForeground(Color.white);
        sendBtn.addActionListener(e -> handleSend());

        JButton receiveBtn = new JButton("📥 Receive File");
        receiveBtn.setPreferredSize(new Dimension(160, 40));
        receiveBtn.setFont(new Font("Inter", Font.BOLD, 14));
        receiveBtn.setBackground(new Color(0x6EB5FF));
        receiveBtn.setForeground(Color.white);
        receiveBtn.addActionListener(e -> handleReceive());

        buttons.add(sendBtn);
        buttons.add(receiveBtn);

        c.gridy = 3;
        add(buttons, c);

        // small hint
        JLabel hint = new JLabel("Tip: choose an easy-to-identify name — it will be visible to peers");
        hint.setForeground(Theme.MUTED);
        hint.setFont(new Font("Inter", Font.ITALIC, 11));
        c.gridy = 4;
        add(hint, c);
    }

    public void setLandingListener(LandingListener l) {
        this.listener = l;
    }

    /**
     * Pre-fill the username field (useful to show system/default name at startup)
     */
    public void setName(String name) {
        if (name != null) nameField.setText(name);
    }

    private void handleSend() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter your name before continuing.", "Missing name", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (listener != null) listener.onSendSelected(name);
    }

    private void handleReceive() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter your name before continuing.", "Missing name", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (listener != null) listener.onReceiveSelected(name);
    }
}
