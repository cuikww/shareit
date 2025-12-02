package ui;

import java.awt.Color;
import java.awt.Font;

/**
 * Centralized UI theme (pastel palette + fonts) used across panels/frames.
 */
public class Theme {
    // Pastel background and accents - soft and readable
    public static final Color BG = new Color(0xF8F9FB);          // very light
    public static final Color CARD = new Color(0xFFFFFF);        // cards / panels
    public static final Color HEADER = new Color(0xDFF3F8);      // pastel blue/teal
    public static final Color ACCENT = new Color(0xFFD6E7);      // pastel pink
    public static final Color MUTED = new Color(0xA6B0B9);       // muted text
    public static final Color TEXT = new Color(0x263238);        // dark slate
    public static final Color SUCCESS = new Color(0x78C6A3);     // pastel green
    public static final Color WARN = new Color(0xFFA67A);        // pastel orange

    public static final Font HEADER_FONT = new Font("Inter", Font.BOLD, 18);
    public static final Font TITLE_FONT = new Font("Inter", Font.BOLD, 36);
    public static final Font BODY_FONT = new Font("Inter", Font.PLAIN, 13);
}
