package ui.panels;

import ui.Theme;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Receive page: shows incoming transfers and sender info
 */
public class ReceivePanel extends JPanel {

    public ReceivePanel() {
        setLayout(new BorderLayout(8,8));
        setBorder(new EmptyBorder(10,10,10,10));
        setBackground(Theme.BG);

        JLabel title = new JLabel("Waiting for incoming files");
        title.setFont(Theme.HEADER_FONT);
        title.setForeground(Theme.TEXT);
        add(title, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout());
        JLabel help = new JLabel("When another user sends files you'll see a confirmation and transfer details here.");
        help.setFont(Theme.BODY_FONT);
        help.setForeground(Theme.MUTED);
        center.add(help, BorderLayout.NORTH);

        // For now, consumers can re-use TransferListPanel to display incoming transfers
        add(center, BorderLayout.CENTER);
    }
}
