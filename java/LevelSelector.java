import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.util.*;
import javax.swing.*;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicSliderUI;
import javax.swing.KeyStroke;

public class LevelSelector extends JFrame {

    // ── Palette ────────────────────────────────────────────────
    private static final Color BG        = new Color(0x0C0C0E);
    private static final Color SURFACE   = new Color(0x141416);
    private static final Color SURFACE_H = new Color(0x1C1C20);
    private static final Color BORDER    = new Color(0x252528);
    private static final Color TEXT_1    = new Color(0xF0F0F2);
    private static final Color TEXT_2    = new Color(0x72727A);
    private static final Color ACCENT    = new Color(0x4F8EF7);
    private static final Color ACCENT_H  = new Color(0x6FA3FF);

    // ── Levels ─────────────────────────────────────────────────
    private static final Level[] LEVELS = {
        new Level("Bob-omb Battlefield", "Course 1", new Color(0x5CC96E), "9"),
        new Level("Cool, Cool Mountain",  "Course 4", new Color(0x59A8E8), "5")
    };

    // ── Font ───────────────────────────────────────────────────
    private static final String TECH_FONT;
    static {
        Set<String> available = new HashSet<>(Arrays.asList(
            GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()
        ));
        String found = Font.MONOSPACED;
        for (String f : new String[]{"Ubuntu Mono","JetBrains Mono","Fira Code",
                                      "Consolas","Cascadia Code","Source Code Pro",
                                      "DejaVu Sans Mono","Liberation Mono","Courier New"}) {
            if (available.contains(f)) { found = f; break; }
        }
        TECH_FONT = found;
    }
    private static Font tf(int style, int size) { return new Font(TECH_FONT, style, size); }

    // ── State ──────────────────────────────────────────────────
    private JComboBox<String> levelCombo;
    private JSlider  speedSlider;
    private JLabel   speedLabel;
    private JButton  launchBtn;

    // ══════════════════════════════════════════════════════════

    public LevelSelector() {
        super("SM64 — Launcher");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(false);
        setUndecorated(true);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG);
        root.setBorder(BorderFactory.createLineBorder(BORDER, 1));
        setContentPane(root);

        root.add(buildTitleBar(), BorderLayout.NORTH);
        root.add(buildBody(),     BorderLayout.CENTER);

        // Escape key closes the window cleanly
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
        getRootPane().getActionMap().put("close", new AbstractAction() {
            public void actionPerformed(java.awt.event.ActionEvent e) { System.exit(0); }
        });

        setSize(480, 340);
        setLocationRelativeTo(null);
        // setShape after setSize so the input region matches the actual window size
        setShape(new RoundRectangle2D.Double(0, 0, 480, 340, 16, 16));
    }

    // ── Draggable title bar ────────────────────────────────────
    private JPanel buildTitleBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(BG);
        bar.setBorder(BorderFactory.createEmptyBorder(14, 20, 10, 16));

        JLabel app = new JLabel("SM64 Launcher");
        app.setFont(tf(Font.PLAIN, 12));
        app.setForeground(TEXT_2);

        JButton close = new JButton("×");
        close.setFont(tf(Font.PLAIN, 16));
        close.setForeground(TEXT_2);
        close.setBorderPainted(false);
        close.setContentAreaFilled(false);
        close.setFocusPainted(false);
        close.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        close.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { close.setForeground(TEXT_1); }
            public void mouseExited(MouseEvent e)  { close.setForeground(TEXT_2); }
        });
        close.addActionListener(e -> System.exit(0));

        bar.add(app,   BorderLayout.WEST);
        bar.add(close, BorderLayout.EAST);

        final int[] drag = new int[2];
        bar.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) { drag[0] = e.getX(); drag[1] = e.getY(); }
        });
        bar.addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseDragged(MouseEvent e) {
                Point p = getLocation();
                setLocation(p.x + e.getX() - drag[0], p.y + e.getY() - drag[1]);
            }
        });
        return bar;
    }

    // ── Body ───────────────────────────────────────────────────
    private JPanel buildBody() {
        JPanel p = new JPanel();
        p.setBackground(BG);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createEmptyBorder(4, 28, 28, 28));

        p.add(heading("Select level", 20, TEXT_1, Font.BOLD));
        p.add(Box.createVerticalStrut(4));
        p.add(heading("Choose where to start, then launch.", 12, TEXT_2, Font.PLAIN));
        p.add(Box.createVerticalStrut(22));
        p.add(buildLevelRow());
        p.add(Box.createVerticalStrut(20));
        p.add(divider());
        p.add(Box.createVerticalStrut(18));
        p.add(buildSpeedRow());
        p.add(Box.createVerticalStrut(8));
        p.add(buildSlider());
        p.add(Box.createVerticalStrut(6));
        p.add(buildSpeedHints());
        p.add(Box.createVerticalStrut(20));
        p.add(divider());
        p.add(Box.createVerticalStrut(16));
        p.add(buildLaunchRow());

        return p;
    }

    private JLabel heading(String text, int size, Color color, int style) {
        JLabel l = new JLabel(text);
        l.setFont(tf(style, size));
        l.setForeground(color);
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    // ── Level dropdown ─────────────────────────────────────────
    private JPanel buildLevelRow() {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

        JLabel lbl = new JLabel("Level");
        lbl.setFont(tf(Font.PLAIN, 12));
        lbl.setForeground(TEXT_2);
        lbl.setPreferredSize(new Dimension(54, 38));

        String[] names = Arrays.stream(LEVELS).map(l -> l.name + "  (" + l.course + ")").toArray(String[]::new);
        levelCombo = new JComboBox<>(names);
        levelCombo.setFont(tf(Font.PLAIN, 13));
        levelCombo.setForeground(TEXT_1);
        levelCombo.setBackground(SURFACE);
        levelCombo.setBorder(BorderFactory.createLineBorder(BORDER));
        levelCombo.setFocusable(false);
        styleCombo(levelCombo);

        row.add(lbl,        BorderLayout.WEST);
        row.add(levelCombo, BorderLayout.CENTER);
        return row;
    }

    private void styleCombo(JComboBox<String> cb) {
        cb.setUI(new BasicComboBoxUI() {
            @Override protected JButton createArrowButton() {
                JButton btn = new JButton("▾") {
                    public void paintComponent(Graphics g) {
                        g.setColor(SURFACE_H);
                        g.fillRect(0, 0, getWidth(), getHeight());
                        g.setFont(new Font(TECH_FONT, Font.PLAIN, 12));
                        g.setColor(TEXT_2);
                        FontMetrics fm = g.getFontMetrics();
                        String t = "▾";
                        g.drawString(t, (getWidth() - fm.stringWidth(t)) / 2,
                                        (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                    }
                };
                btn.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, BORDER));
                btn.setFocusPainted(false);
                btn.setContentAreaFilled(false);
                return btn;
            }
            @Override public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
                g.setColor(SURFACE);
                g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
            }
        });

        cb.setRenderer(new DefaultListCellRenderer() {
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                l.setFont(tf(Font.PLAIN, 13));
                l.setForeground(isSelected ? TEXT_1 : TEXT_1);
                l.setBackground(isSelected ? SURFACE_H : SURFACE);
                l.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
                if (index >= 0) {
                    Color dot = LEVELS[index].color;
                    l.setIcon(new Icon() {
                        public void paintIcon(Component c, Graphics g, int x, int y) {
                            g.setColor(dot);
                            g.fillOval(x, y + 3, 8, 8);
                        }
                        public int getIconWidth()  { return 14; }
                        public int getIconHeight() { return 14; }
                    });
                }
                return l;
            }
        });

        // Force popup background
        Object popup = cb.getUI().getAccessibleChild(cb, 0);
        if (popup instanceof JPopupMenu) {
            ((JPopupMenu) popup).setBackground(SURFACE);
        }
    }

    // ── Speed section ──────────────────────────────────────────
    private JPanel buildSpeedRow() {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

        JLabel lbl = new JLabel("Game speed");
        lbl.setFont(tf(Font.PLAIN, 12));
        lbl.setForeground(TEXT_2);

        speedLabel = new JLabel("1.0×");
        speedLabel.setFont(tf(Font.BOLD, 13));
        speedLabel.setForeground(ACCENT);

        row.add(lbl,        BorderLayout.WEST);
        row.add(speedLabel, BorderLayout.EAST);
        return row;
    }

    private JSlider buildSlider() {
        speedSlider = new JSlider(10, 50, 10);
        speedSlider.setOpaque(true);
        speedSlider.setBackground(BG);
        speedSlider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        speedSlider.setAlignmentX(LEFT_ALIGNMENT);
        speedSlider.setUI(new BasicSliderUI(speedSlider) {
            @Override public void paint(Graphics g, JComponent c) {
                // Expand clip to full component — prevents ghost from partial-region repaints
                g.setClip(0, 0, c.getWidth(), c.getHeight());
                g.setColor(BG);
                g.fillRect(0, 0, c.getWidth(), c.getHeight());
                super.paint(g, c);
            }
            @Override public void paintTrack(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int cy = trackRect.y + trackRect.height / 2 - 1;
                int tx = thumbRect.x + thumbRect.width / 2;
                g2.setColor(ACCENT);
                g2.fillRoundRect(trackRect.x, cy, tx - trackRect.x, 3, 3, 3);
                g2.setColor(BORDER);
                g2.fillRoundRect(tx, cy, trackRect.x + trackRect.width - tx, 3, 3, 3);
            }
            @Override public void paintThumb(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int cx = thumbRect.x + thumbRect.width / 2;
                int cy = thumbRect.y + thumbRect.height / 2;
                g2.setColor(BG);     g2.fillOval(cx - 7, cy - 7, 14, 14);
                g2.setColor(ACCENT); g2.setStroke(new BasicStroke(2f));
                g2.drawOval(cx - 7, cy - 7, 14, 14);
            }
            @Override public void paintFocus(Graphics g) {}
        });
        speedSlider.addChangeListener(e -> {
            double v = speedSlider.getValue() / 10.0;
            speedLabel.setText(String.format("%.1f×", v));
        });
        return speedSlider;
    }

    private JPanel buildSpeedHints() {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));
        row.setAlignmentX(LEFT_ALIGNMENT);
        JLabel lo = mk("1.0× — normal", 11, TEXT_2);
        JLabel hi = mk("5.0× — max",    11, TEXT_2);
        row.add(lo, BorderLayout.WEST);
        row.add(hi, BorderLayout.EAST);
        return row;
    }

    // ── Launch row ─────────────────────────────────────────────
    private JPanel buildLaunchRow() {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        JLabel hint = new JLabel("Ready to launch.");
        hint.setFont(tf(Font.PLAIN, 12));
        hint.setForeground(TEXT_2);

        launchBtn = new JButton("Launch") {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean pr = getModel().isPressed(), ro = getModel().isRollover();
                g2.setColor(pr ? ACCENT.darker() : (ro ? ACCENT_H : ACCENT));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setFont(tf(Font.BOLD, 13));
                g2.setColor(Color.WHITE);
                FontMetrics fm = g2.getFontMetrics();
                String txt = getText();
                g2.drawString(txt, (getWidth() - fm.stringWidth(txt)) / 2,
                              (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        };
        launchBtn.setPreferredSize(new Dimension(100, 36));
        launchBtn.setOpaque(false);
        launchBtn.setContentAreaFilled(false);
        launchBtn.setBorderPainted(false);
        launchBtn.setFocusPainted(false);
        launchBtn.setRolloverEnabled(true);
        launchBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        launchBtn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { launchBtn.repaint(); }
            public void mouseExited(MouseEvent e)  { launchBtn.repaint(); }
        });
        launchBtn.addActionListener(e -> {
            int idx   = levelCombo.getSelectedIndex();
            double spd = speedSlider.getValue() / 10.0;
            GameLauncher.launch(LEVELS[idx].courseId, spd);
            dispose();
        });

        row.add(hint,      BorderLayout.WEST);
        row.add(launchBtn, BorderLayout.EAST);
        return row;
    }

    // ── Helpers ────────────────────────────────────────────────
    private JLabel mk(String text, int size, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(tf(Font.PLAIN, size));
        l.setForeground(color);
        return l;
    }

    private JPanel divider() {
        JPanel p = new JPanel() {
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(BORDER);
                g.drawLine(0, 0, getWidth(), 0);
            }
        };
        p.setOpaque(false);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        p.setAlignmentX(LEFT_ALIGNMENT);
        return p;
    }

    // ══════════════════════════════════════════════════════════
    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        SwingUtilities.invokeLater(() -> {
            LevelSelector f = new LevelSelector();
            f.setVisible(true);
        });
    }

    static class Level {
        final String name, course, courseId;
        final Color color;
        Level(String n, String c, Color col, String id) {
            name = n; course = c; color = col; courseId = id;
        }
    }
}
