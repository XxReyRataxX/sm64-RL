import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;

public class LevelSelector extends JFrame {

    private static final Color BG_COLOR      = new Color(20, 20, 40);
    private static final Color CARD_COLOR    = new Color(30, 30, 60);
    private static final Color HOVER_COLOR   = new Color(50, 50, 90);
    private static final Color ACCENT_RED    = new Color(200, 30, 30);
    private static final Color ACCENT_YELLOW = new Color(255, 200, 0);
    private static final Color TEXT_WHITE    = new Color(240, 240, 240);
    private static final Color TEXT_GRAY     = new Color(160, 160, 180);

    private static final int CARD_W = 260;
    private static final int CARD_H = 180;

    private JButton selectedButton = null;
    private JButton launchBtn;

    private static final Level[] LEVELS = {
        new Level("Bob-omb Battlefield", "Course 1",
                  "The very first course. Defeat King Bob-omb\nand collect the Power Stars!",
                  new Color(100, 200, 100), "1"),
        new Level("Cool, Cool Mountain", "Course 4",
                  "A snowy mountain full of secrets.\nSlide down and rescue the baby penguin!",
                  new Color(120, 180, 255), "4")
    };

    public LevelSelector() {
        super("SM64 — Level Selector");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(false);
        getContentPane().setBackground(BG_COLOR);
        setLayout(new BorderLayout(0, 0));

        add(buildHeader(), BorderLayout.NORTH);
        add(buildCardsPanel(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
    }

    private JPanel buildHeader() {
        JPanel p = new JPanel();
        p.setBackground(BG_COLOR);
        p.setBorder(BorderFactory.createEmptyBorder(30, 0, 10, 0));
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("SUPER MARIO 64");
        title.setFont(new Font("Arial Black", Font.BOLD, 28));
        title.setForeground(ACCENT_YELLOW);
        title.setAlignmentX(CENTER_ALIGNMENT);

        JLabel sub = new JLabel("Select a level to begin");
        sub.setFont(new Font("Arial", Font.PLAIN, 14));
        sub.setForeground(TEXT_GRAY);
        sub.setAlignmentX(CENTER_ALIGNMENT);

        p.add(title);
        p.add(Box.createVerticalStrut(6));
        p.add(sub);
        p.add(Box.createVerticalStrut(20));

        JSeparator sep = new JSeparator();
        sep.setForeground(new Color(60, 60, 80));
        sep.setBackground(new Color(60, 60, 80));
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        p.add(sep);

        return p;
    }

    private JPanel buildCardsPanel() {
        JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 24, 30));
        wrapper.setBackground(BG_COLOR);
        wrapper.setBorder(BorderFactory.createEmptyBorder(10, 30, 10, 30));

        for (Level lvl : LEVELS) {
            wrapper.add(buildCard(lvl));
        }
        return wrapper;
    }

    private JButton buildCard(Level lvl) {
        JButton btn = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                Color bg = (this == selectedButton) ? HOVER_COLOR : CARD_COLOR;
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);

                if (this == selectedButton) {
                    g2.setColor(ACCENT_YELLOW);
                    g2.setStroke(new BasicStroke(2.5f));
                    g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 16, 16);
                }

                // Color stripe at top
                g2.setColor(lvl.color);
                g2.fillRoundRect(0, 0, getWidth(), 6, 4, 4);

                // Course tag
                g2.setFont(new Font("Arial", Font.BOLD, 11));
                g2.setColor(lvl.color);
                g2.drawString(lvl.course.toUpperCase(), 16, 26);

                // Level name
                g2.setFont(new Font("Arial Black", Font.BOLD, 15));
                g2.setColor(TEXT_WHITE);
                g2.drawString(lvl.name, 16, 52);

                // Description (multi-line)
                g2.setFont(new Font("Arial", Font.PLAIN, 12));
                g2.setColor(TEXT_GRAY);
                String[] lines = lvl.description.split("\n");
                int y = 80;
                for (String line : lines) {
                    g2.drawString(line, 16, y);
                    y += 18;
                }

                // Star icon at bottom-right
                g2.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 22));
                g2.setColor(ACCENT_YELLOW);
                g2.drawString("★", getWidth() - 38, getHeight() - 14);

                g2.dispose();
            }
        };

        btn.setPreferredSize(new Dimension(CARD_W, CARD_H));
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { btn.repaint(); }
            @Override public void mouseExited(MouseEvent e)  { btn.repaint(); }
        });

        btn.addActionListener(e -> {
            selectedButton = btn;
            launchBtn.setEnabled(true);
            for (Component c : btn.getParent().getComponents()) c.repaint();
        });

        return btn;
    }

    private JPanel buildFooter() {
        JPanel p = new JPanel();
        p.setBackground(BG_COLOR);
        p.setBorder(BorderFactory.createEmptyBorder(0, 0, 28, 0));
        p.setLayout(new FlowLayout(FlowLayout.CENTER));

        launchBtn = new JButton("LAUNCH LEVEL") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color c = isEnabled()
                    ? (getModel().isPressed() ? ACCENT_RED.darker() : ACCENT_RED)
                    : new Color(60, 60, 70);
                g2.setColor(c);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setFont(new Font("Arial Black", Font.BOLD, 14));
                g2.setColor(isEnabled() ? TEXT_WHITE : TEXT_GRAY);
                FontMetrics fm = g2.getFontMetrics();
                String txt = getText();
                int tx = (getWidth() - fm.stringWidth(txt)) / 2;
                int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(txt, tx, ty);
                g2.dispose();
            }
        };

        launchBtn.setPreferredSize(new Dimension(200, 46));
        launchBtn.setOpaque(false);
        launchBtn.setContentAreaFilled(false);
        launchBtn.setBorderPainted(false);
        launchBtn.setFocusPainted(false);
        launchBtn.setEnabled(false);
        launchBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        launchBtn.addActionListener(e -> {
            if (selectedButton == null) return;
            int idx = getSelectedIndex();
            if (idx < 0) return;
            GameLauncher.launch(LEVELS[idx].courseId);
            dispose();
        });

        p.add(launchBtn);
        return p;
    }

    private int getSelectedIndex() {
        JPanel cards = (JPanel) ((JPanel) getContentPane().getComponent(1));
        Component[] comps = cards.getComponents();
        for (int i = 0; i < comps.length; i++) {
            if (comps[i] == selectedButton) return i;
        }
        return -1;
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new LevelSelector().setVisible(true));
    }

    // -------------------------------------------------------------------------

    static class Level {
        final String name, course, description, courseId;
        final Color color;
        Level(String name, String course, String description, Color color, String courseId) {
            this.name = name; this.course = course;
            this.description = description; this.color = color;
            this.courseId = courseId;
        }
    }
}
