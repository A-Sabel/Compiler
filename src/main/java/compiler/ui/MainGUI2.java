package compiler.ui;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.table.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Clipboard;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import compiler.util.ErrorHandler;
import compiler.CompilerPipeline;
import compiler.codegen.Instruction;
import compiler.vm.Interpreter;

public class MainGUI2 extends JFrame {

    // ── Dark / Light Theme Toggle ─────────────────────────────────────────────
    private boolean isDarkMode = true; // Default dark mode like VSCode

    // ── Light palette (Updated for better contrast) ───────────────────────────
    private static final Color L_BG_WHITE        = new Color(0xFFFFFF);
    private static final Color L_BG_LIGHT        = new Color(0xF3F3F3);
    private static final Color L_BG_PANEL        = new Color(0xFAFAFA);
    private static final Color L_HEADER_BG       = new Color(0x24292E);
    private static final Color L_ACCENT_BLACK    = new Color(0x1F2328);
    private static final Color L_ACCENT_DARK     = new Color(0x24292E);
    private static final Color L_BORDER_COLOR    = new Color(0xEEEEEE);
    private static final Color L_CONSOLE_BG      = new Color(0x1E1E1E);
    private static final Color L_CONSOLE_TEXT    = new Color(0xCCCCCC);
    private static final Color L_TABLE_HEADER_BG = new Color(0xF0F0F0);
    private static final Color L_TABLE_ROW_ALT   = new Color(0xF8F8F8);
    private static final Color L_LINE_NUM_BG     = new Color(0xF3F3F3);
    private static final Color L_LINE_NUM_FG     = new Color(0x858585);
    private static final Color L_OUTPUT_HEADER   = new Color(0x2C2C2C);
    private static final Color L_OUTPUT_TAB_BG   = new Color(0xE1E1E1);
    private static final Color L_OUTPUT_TAB_FG   = new Color(0xCCCCCC);
    private static final Color L_STATUS_BG       = new Color(0x007ACC);

    // ── Dark palette (VSCode-inspired) ────────────────────────────────────────
    private static final Color D_BG_WHITE        = new Color(0x1E1E1E);
    private static final Color D_BG_LIGHT        = new Color(0x252526);
    private static final Color D_BG_PANEL        = new Color(0x2D2D30);
    private static final Color D_HEADER_BG       = new Color(0x333333);
    private static final Color D_ACCENT_BLACK    = new Color(0xD4D4D4);
    private static final Color D_ACCENT_DARK     = new Color(0xCCCCCC);
    private static final Color D_BORDER_COLOR    = new Color(0x3E3E42);
    private static final Color D_CONSOLE_BG      = new Color(0x1E1E1E);
    private static final Color D_CONSOLE_TEXT    = new Color(0xCCCCCC);
    private static final Color D_TABLE_HEADER_BG = new Color(0x2D2D30);
    private static final Color D_TABLE_ROW_ALT   = new Color(0x252526);
    private static final Color D_LINE_NUM_BG     = new Color(0x1E1E1E);
    private static final Color D_LINE_NUM_FG     = new Color(0x858585);
    private static final Color D_OUTPUT_HEADER   = new Color(0x252526);
    private static final Color D_OUTPUT_TAB_BG   = new Color(0x2D2D30);
    private static final Color D_OUTPUT_TAB_FG   = new Color(0xCCCCCC);
    private static final Color D_STATUS_BG       = new Color(0x007ACC);

    // VSCode-style accent colors
    private static final Color VSCODE_BLUE      = new Color(0x007ACC);
    private static final Color VSCODE_BLUE_HVR  = new Color(0x1E8AD6);
    private static final Color SUCCESS_GREEN    = new Color(0x4EC994);
    private static final Color WARNING_YELLOW   = new Color(0xCCA700);
    private static final Color ERROR_RED        = new Color(0xF14C4C);
    private static final Color INFO_BLUE        = new Color(0x75BEFF);
    private static final Color KEYWORD_PURPLE   = new Color(0xC586C0);
    private static final Color STRING_ORANGE    = new Color(0xCE9178);
    private static final Color COMMENT_GREEN    = new Color(0x6A9955);

    // ── Dynamic color accessors ───────────────────────────────────────────────
    private Color bg()         { return isDarkMode ? D_BG_WHITE        : L_BG_WHITE; }
    private Color bgLight()    { return isDarkMode ? D_BG_LIGHT        : L_BG_LIGHT; }
    private Color bgPanel()    { return isDarkMode ? D_BG_PANEL        : L_BG_PANEL; }
    private Color headerBg()   { return isDarkMode ? D_HEADER_BG       : L_HEADER_BG; }
    private Color accentBlack(){ return isDarkMode ? D_ACCENT_BLACK    : L_ACCENT_BLACK; }
    private Color accentDark() { return isDarkMode ? D_ACCENT_DARK     : L_ACCENT_DARK; }
    private Color border()     { return isDarkMode ? D_BORDER_COLOR    : L_BORDER_COLOR; }
    private Color consoleBg()  { return isDarkMode ? D_CONSOLE_BG      : L_CONSOLE_BG; }
    private Color consoleTxt() { return isDarkMode ? D_CONSOLE_TEXT    : L_CONSOLE_TEXT; }
    private Color tblHdrBg()   { return isDarkMode ? D_TABLE_HEADER_BG : L_TABLE_HEADER_BG; }
    private Color tblRowAlt()  { return isDarkMode ? D_TABLE_ROW_ALT   : L_TABLE_ROW_ALT; }
    private Color lineNumBg()  { return isDarkMode ? D_LINE_NUM_BG     : L_LINE_NUM_BG; }
    private Color lineNumFg()  { return isDarkMode ? D_LINE_NUM_FG     : L_LINE_NUM_FG; }
    private Color outHdr()     { return isDarkMode ? D_OUTPUT_HEADER   : L_OUTPUT_HEADER; }
    private Color outTabBg()   { return isDarkMode ? D_OUTPUT_TAB_BG   : L_OUTPUT_TAB_BG; }
    private Color outTabFg()   { return isDarkMode ? D_OUTPUT_TAB_FG   : L_OUTPUT_TAB_FG; }
    private Color statusBg()   { return isDarkMode ? D_STATUS_BG       : L_STATUS_BG; }

    // ── Fonts ─────────────────────────────────────────────────────────────────
    private static final Font FONT_MONO    = new Font("Consolas", Font.PLAIN, 13);
    private static final Font FONT_MONO_SM = new Font("Consolas", Font.PLAIN, 11);
    private static final Font FONT_UI      = new Font("Segoe UI", Font.PLAIN, 12);
    private static final Font FONT_UI_SM   = new Font("Segoe UI", Font.PLAIN, 11);
    private static final Font FONT_UI_B    = new Font("Segoe UI", Font.BOLD,  12);
    private static final Font FONT_TITLE   = new Font("Segoe UI", Font.BOLD,  13);
    private static final Font FONT_HEADER  = new Font("Segoe UI", Font.BOLD,  11);
    private static final Font FONT_ICON    = new Font("Segoe UI Emoji", Font.PLAIN, 12);

    // ── State ─────────────────────────────────────────────────────────────────
    private JTextArea         codeEditor;
    private JTabbedPane       outputTabs;
    private JTextArea         consoleArea;
    
    private DefaultListModel<String> errorListModel   = new DefaultListModel<>();
    private JList<String>            errorList        = new JList<>(errorListModel);
    private DefaultListModel<String> warningListModel = new DefaultListModel<>();
    private JList<String>            warningList      = new JList<>(warningListModel);
    private JPanel                   consoleCardPanel;
    private CardLayout               consoleLayout;

    private JLabel            lnColLabel;
    private JTextField        searchField;
    private JComboBox<String> filterCombo;
    private DefaultTableModel symbolModel;
    private JTextArea         generatedCodeArea;
    private JTextArea         runtimeArea;
    private JButton           themeToggleBtn;
    private JPanel            rootPanel;
    private JLabel            errorCountLabel;
    private JLabel            warnCountLabel;
    private int               errorCount = 0;
    
    private JLabel            consoleTabBtn;
    private JLabel            errorTabBtn;
    private JLabel            warnTabBtn;
    private int               warnCount  = 0;

    // =========================================================================
    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new MainGUI2().setVisible(true));
    }

    public MainGUI2() {
        super("Compiler Visualization System");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1440, 900);
        setLocationRelativeTo(null);
        buildUI();
    }

    private void buildUI() {
        String savedCode = (codeEditor != null) ? codeEditor.getText() : null;
        getContentPane().removeAll();
        
        // Re-initialize codeEditor early to ensure font availability for line numbers
        if (codeEditor == null) {
            codeEditor = new JTextArea();
        }
        setBackground(bg());

        rootPanel = new JPanel(new BorderLayout(0, 0));
        rootPanel.setBackground(bg());

        rootPanel.add(buildHeader(),    BorderLayout.NORTH);
        rootPanel.add(buildCenter(),    BorderLayout.CENTER);
        rootPanel.add(buildStatusBar(), BorderLayout.SOUTH);

        setContentPane(rootPanel);

        if (savedCode != null && !savedCode.isEmpty()) {
            codeEditor.setText(savedCode);
        } else if (codeEditor != null) {
            codeEditor.setText(
                "void main() {\n" +
                "    int a = 10;\n" +
                "    int b = 20;\n" +
                "    int result = a + b;\n" +
                "    System.out.println(\"The sum is: \" + result);\n" +
                "}"
            );
        }

        revalidate();
        repaint();
    }

    // =========================================================================
    //  HEADER  (VSCode-style activity bar + title bar)
    // =========================================================================
    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(headerBg());
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(isDarkMode ? new Color(0x1A1A1A) : new Color(0x1A1A1A));
                g.drawLine(0, getHeight()-1, getWidth(), getHeight()-1);
            }
        };
        header.setPreferredSize(new Dimension(0, 56));
        header.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
        header.setOpaque(false);

        // ── Left: Logo + Title ────────────────────────────────────────────────
        JPanel left = new JPanel(new GridBagLayout());
        left.setOpaque(false);

        // VSCode-style logo box
        JPanel logoBox = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gp = new GradientPaint(0, 0, new Color(0x0098FF), getWidth(), getHeight(), new Color(0x006EBD));
                g2.setPaint(gp);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                // VSCode-like icon (two angle brackets)
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Consolas", Font.BOLD, 16));
                FontMetrics fm = g2.getFontMetrics();
                String txt = "<>";
                g2.drawString(txt,
                    (getWidth()  - fm.stringWidth(txt)) / 2,
                    (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            }
        };
        logoBox.setOpaque(false);
        logoBox.setPreferredSize(new Dimension(38, 38));

        JPanel titlePanel = new JPanel(new GridLayout(2, 1, 0, 2));
        titlePanel.setOpaque(false);
        JLabel t1 = new JLabel("Compiler Visualization System");
        t1.setFont(FONT_TITLE); t1.setForeground(new Color(0xCCCCCC));
        JLabel t2 = new JLabel("Interactive Compiler Stage Explorer");
        t2.setFont(FONT_UI_SM); t2.setForeground(new Color(0x858585));
        titlePanel.add(t1); titlePanel.add(t2);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(0, 0, 0, 12);
        left.add(logoBox, gbc);
        
        gbc.insets = new Insets(0, 0, 0, 0);
        left.add(titlePanel, gbc);

        // ── Right: VSCode-style action buttons ────────────────────────────────
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        right.setOpaque(false);

        JButton newBtn = buildHeaderBtn("New File", "\uE001", false, "New Project (Ctrl+N)");
        newBtn.addActionListener(e -> handleNewFile());
        right.add(newBtn);

        JButton openBtn = buildHeaderBtn("Open", "\uE178", false, "Load File (Ctrl+O)");
        openBtn.addActionListener(e -> handleOpenFile());
        right.add(openBtn);

        JButton runBtn = buildHeaderBtn("Run", "\uE037", true, "Run Compiler (F5)");
        runBtn.addActionListener(e -> handleRunAction());
        right.add(runBtn);

        // Divider
        JPanel div = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(new Color(0x555555));
                g.fillRect(0, 8, 1, 24);
            }
        };
        div.setOpaque(false);
        div.setPreferredSize(new Dimension(10, 40));
        right.add(div);

        themeToggleBtn = buildThemeToggleBtn();
        right.add(themeToggleBtn);
        right.add(buildIconHeaderBtn("\u2699", "Settings (Ctrl+,)"));

        header.add(left,  BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    // VSCode-style header button with SVG-like Unicode icons
    private JButton buildHeaderBtn(String label, String iconChar, boolean primary, String tooltip) {
        // Use clean unicode chars that look like VSCode icons
        String iconText = switch (label) {
            case "New File" -> "\uD83D\uDCC4"; // 📄
            case "Open"     -> "\uD83D\uDCC2"; // 📂
            case "Run"      -> "\u25B6";        // ▶
            default         -> iconChar;
        };

        JButton btn = new JButton() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                Color base, fg, borderC;
                if (primary) {
                    base    = getModel().isPressed()  ? new Color(0x005F9E) :
                              getModel().isRollover() ? new Color(0x1E8AD6) : VSCODE_BLUE;
                    fg      = Color.WHITE;
                    borderC = getModel().isRollover() ? new Color(0x1E8AD6) : new Color(0x006EBD);
                } else {
                    base    = getModel().isPressed()  ? new Color(0x2A2D2E) :
                              getModel().isRollover() ? new Color(0x2A2D2E) : new Color(0x00000000, true);
                    fg      = new Color(0xCCCCCC);
                    borderC = getModel().isRollover() ? new Color(0x555555) : new Color(0x444444);
                }

                if (primary || getModel().isRollover() || getModel().isPressed()) {
                    g2.setColor(base);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 5, 5);
                }
                if (primary) {
                    g2.setColor(borderC);
                    g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 5, 5);
                }

                // Draw icon
                g2.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 13));
                g2.setColor(fg);
                FontMetrics fm = g2.getFontMetrics();
                int iconW = fm.stringWidth(iconText);

                // Draw text label
                g2.setFont(FONT_UI_SM);
                FontMetrics fm2 = g2.getFontMetrics();
                int labelW = fm2.stringWidth(label);
                int totalW = iconW + 5 + labelW;
                int startX = (getWidth() - totalW) / 2;
                int baseY  = (getHeight() + fm2.getAscent() - fm2.getDescent()) / 2 - 1;

                g2.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 13));
                g2.setColor(fg);
                g2.drawString(iconText, startX, baseY);

                g2.setFont(FONT_UI_SM);
                g2.setColor(fg);
                g2.drawString(label, startX + iconW + 5, baseY);
            }
        };

        int w = primary ? 90 : 80;
        btn.setPreferredSize(new Dimension(w, 32));
        btn.setOpaque(false); btn.setContentAreaFilled(false);
        btn.setBorderPainted(false); btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setToolTipText(tooltip);
        return btn;
    }

    private JButton buildThemeToggleBtn() {
        JButton btn = new JButton() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                if (getModel().isRollover() || getModel().isPressed()) {
                    g2.setColor(new Color(0x2A2D2E));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 5, 5);
                }

                String icon  = isDarkMode ? "\u2600" : "\uD83C\uDF19"; // ☀ or 🌙
                String label = isDarkMode ? " Light" : " Dark";

                g2.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 13));
                FontMetrics fm = g2.getFontMetrics();
                int iconW = fm.stringWidth(icon);
                g2.setFont(FONT_UI_SM);
                FontMetrics fm2 = g2.getFontMetrics();
                int labelW = fm2.stringWidth(label);
                int totalW = iconW + labelW;
                int startX = (getWidth() - totalW) / 2;
                int baseY  = (getHeight() + fm2.getAscent() - fm2.getDescent()) / 2 - 1;

                g2.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 13));
                g2.setColor(isDarkMode ? new Color(0xFFD700) : new Color(0x75BEFF));
                g2.drawString(icon, startX, baseY);

                g2.setFont(FONT_UI_SM);
                g2.setColor(new Color(0xCCCCCC));
                g2.drawString(label, startX + iconW, baseY);
            }
        };
        btn.setPreferredSize(new Dimension(80, 32));
        btn.setOpaque(false); btn.setContentAreaFilled(false);
        btn.setBorderPainted(false); btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setToolTipText("Toggle Dark / Light mode");
        btn.addActionListener(e -> {
            isDarkMode = !isDarkMode;
            String savedCode = codeEditor != null ? codeEditor.getText() : "";
            buildUI();
            if (codeEditor != null && !savedCode.isEmpty()) codeEditor.setText(savedCode);
            String now = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            appendConsole((isDarkMode ? "\u2600 " : "\uD83C\uDF19 ") +
                "[" + now + "] " + (isDarkMode ? "Dark" : "Light") + " mode enabled", INFO_BLUE);
        });
        return btn;
    }

    private JButton buildIconHeaderBtn(String icon, String tooltip) {
        JButton btn = new JButton(icon) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (getModel().isRollover() || getModel().isPressed()) {
                    g2.setColor(new Color(0x2A2D2E));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 5, 5);
                }
                g2.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 16));
                g2.setColor(new Color(0xCCCCCC));
                FontMetrics fm = g2.getFontMetrics();
                String s = getText();
                g2.drawString(s,
                    (getWidth()  - fm.stringWidth(s)) / 2,
                    (getHeight() + fm.getAscent() - fm.getDescent()) / 2 - 1);
            }
        };
        btn.setPreferredSize(new Dimension(34, 32));
        btn.setOpaque(false); btn.setContentAreaFilled(false);
        btn.setBorderPainted(false); btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setToolTipText(tooltip);
        return btn;
    }

    // =========================================================================
    //  CENTER
    // =========================================================================
    private JSplitPane buildCenter() {
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                buildTopArea(), buildConsole());
        split.setResizeWeight(0.70); // 70% top area, 30% console
        split.setDividerSize(4);
        split.setBorder(null);
        split.setBackground(bg());
        // Ensure initial location is set correctly
        SwingUtilities.invokeLater(() -> {
            split.setDividerLocation(0.70);
        });

        styleDivider(split);
        return split;
    }

    private JSplitPane buildTopArea() {
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                buildEditorPanel(), buildOutputPanel());
        split.setResizeWeight(0.60); // 60% editor, 40% output area
        split.setDividerSize(4);
        split.setBorder(null);
        split.setBackground(bg());
        // Ensure initial location is set correctly
        SwingUtilities.invokeLater(() -> {
            split.setDividerLocation(0.60);
        });

        styleDivider(split);
        return split;
    }

    private void styleDivider(JSplitPane sp) {
        sp.setUI(new javax.swing.plaf.basic.BasicSplitPaneUI() {
            @Override public BasicSplitPaneDivider createDefaultDivider() {
                return new BasicSplitPaneDivider(this) {
                    @Override public void paint(Graphics g) {
                        g.setColor(border());
                        g.fillRect(0, 0, getWidth(), getHeight());
                    }
                };
            }
        });
    }

    // ── Editor panel ──────────────────────────────────────────────────────────
    private JPanel buildEditorPanel() {
        JPanel p = new JPanel(new BorderLayout(0, 0));
        p.setBackground(bg());

        // ── Tab bar (VSCode explorer-like) ────────────────────────────────────
        JPanel tabBar = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(bgLight());
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(border());
                g.drawLine(0, getHeight()-1, getWidth(), getHeight()-1);
            }
        };
        tabBar.setPreferredSize(new Dimension(0, 35)); // Aligned with Output header
        tabBar.setOpaque(false);

        // Active tab with VSCode look
        JPanel fileTab = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                // active tab has bg() background
                g2.setColor(bg());
                g2.fillRect(0, 0, getWidth(), getHeight());
                // top orange accent line (like VSCode active tab)
                g2.setColor(VSCODE_BLUE);
                g2.fillRect(0, 0, getWidth(), 2);
            }
        };
        fileTab.setPreferredSize(new Dimension(130, 35));
        fileTab.setOpaque(false);
        fileTab.setBorder(BorderFactory.createEmptyBorder(4, 8, 0, 8));

        // C file icon (mimics VSCode C file icon)
        JLabel fileIcon = new JLabel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Draw a small file-like icon with "C" in blue
                g2.setColor(new Color(0x519ABA));
                g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
                g2.drawString("C", 0, 13);
            }
        };
        fileIcon.setPreferredSize(new Dimension(14, 16));

        JLabel fileName = new JLabel("main.c");
        fileName.setFont(FONT_UI_B); // Make "main.c" text bold
        fileName.setForeground(new Color(0xCCCCCC));

        JLabel closeBtn = new JLabel("\u00D7"); // ×
        closeBtn.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        closeBtn.setForeground(new Color(0x858585));
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { closeBtn.setForeground(new Color(0xCCCCCC)); }
            @Override public void mouseExited(MouseEvent e)  { closeBtn.setForeground(new Color(0x858585)); }
        });

        fileTab.add(fileIcon); fileTab.add(fileName); fileTab.add(closeBtn);
        tabBar.add(fileTab, BorderLayout.WEST);

        // Editor toolbar (right side of tab bar)
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 6));
        toolbar.setOpaque(false);
        toolbar.add(buildEditorBtn("\uD83D\uDCCB", "Copy",  "Copy to clipboard"));  // 📋
        toolbar.add(buildEditorBtn("\uD83D\uDCE4", "Export", "Export file"));         // 📤
        toolbar.add(buildEditorBtn("\uD83D\uDDD1", "Clear",  "Clear editor"));        // 🗑
        tabBar.add(toolbar, BorderLayout.EAST);

        // ── Code editor ───────────────────────────────────────────────────────
        codeEditor.setFont(FONT_MONO);
        codeEditor.setBackground(bg());
        codeEditor.setForeground(isDarkMode ? new Color(0xD4D4D4) : Color.BLACK);
        codeEditor.setCaretColor(isDarkMode ? new Color(0xAEAFAD) : Color.BLACK);
        codeEditor.setSelectionColor(isDarkMode ? new Color(0x264F78) : new Color(0xADD6FF));
        codeEditor.setLineWrap(false);
        codeEditor.setTabSize(4);
        codeEditor.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));

        // Ln/Col Logic
        if (lnColLabel == null) lnColLabel = new JLabel("Ln 1, Col 1");
        codeEditor.addCaretListener(e -> {
            try {
                int pos  = codeEditor.getCaretPosition();
                int line = codeEditor.getLineOfOffset(pos);
                int col  = pos - codeEditor.getLineStartOffset(line);
                if (lnColLabel != null) lnColLabel.setText("Ln " + (line+1) + ", Col " + (col+1));
            } catch (Exception ignored) {}
        });

        LineNumberComponent lnc = new LineNumberComponent(codeEditor,
            lineNumBg(), lineNumFg(), border());
        JScrollPane scroll = new JScrollPane(codeEditor);
        scroll.setRowHeaderView(lnc);
        scroll.setBorder(null); // Fix: Remove right border to align with SplitPane divider
        scroll.getViewport().setBackground(bg());
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        // Custom scrollbar styling
        styleScrollBar(scroll.getVerticalScrollBar());
        styleScrollBar(scroll.getHorizontalScrollBar());

        p.add(tabBar, BorderLayout.NORTH);
        p.add(scroll, BorderLayout.CENTER);
        return p;
    }

    private JButton buildEditorBtn(String icon, String label, String tooltip) {
        JButton btn = new JButton(icon + " " + label) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (getModel().isRollover() || getModel().isPressed()) {
                    g2.setColor(isDarkMode ? new Color(0x2A2D2E) : new Color(0xE8E8E8));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 4, 4);
                }
                String iconText = icon;
                String labelText = label;
                g2.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 13));
                g2.setColor(new Color(0x858585));
                FontMetrics fm = g2.getFontMetrics();
                int iconW = fm.stringWidth(iconText);
                g2.setFont(FONT_UI_SM);
                FontMetrics fm2 = g2.getFontMetrics();
                int labelW = fm2.stringWidth(labelText);
                int totalW = iconW + 5 + labelW;
                int startX = (getWidth() - totalW) / 2;
                int baseY  = (getHeight() + fm2.getAscent() - fm2.getDescent()) / 2 - 1;
                g2.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 13));
                g2.setColor(new Color(0x858585));
                g2.drawString(iconText, startX, baseY);
                g2.setFont(FONT_UI_SM);
                g2.setColor(new Color(0x858585));
                g2.drawString(labelText, startX + iconW + 5, baseY);
            }
        };
        btn.setPreferredSize(new Dimension(100, 26));
        btn.setOpaque(false); btn.setContentAreaFilled(false);
        btn.setBorderPainted(false); btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setToolTipText(tooltip);

        // Wire up buttons
        if (label.equals("Copy")) {
            btn.addActionListener(e -> handleCopyAction());
        } else if (label.equals("Export")) {
            btn.addActionListener(e -> handleExportAction());
        } else if (label.equals("Clear")) {
            btn.addActionListener(e -> handleClearAction());
        }
        return btn;
    }

    // ── Output panel ──────────────────────────────────────────────────────────
    private JPanel buildOutputPanel() {
        JPanel p = new JPanel(new BorderLayout(0, 0));
        p.setBackground(bg());

        // Header bar
        JPanel outputHeader = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(outHdr());
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(border());
                g.drawLine(0, getHeight()-1, getWidth(), getHeight()-1);
            }
        };
        outputHeader.setPreferredSize(new Dimension(0, 35)); // Aligned with Editor tab bar
        outputHeader.setLayout(new BorderLayout());
        outputHeader.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 10));

        JPanel headerLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        headerLeft.setOpaque(false); // Changed from true to false

        // Colored dot indicators (like VSCode)
        JPanel dots = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0xFF5F56)); g2.fillOval(0, 6, 10, 10);
                g2.setColor(new Color(0xFFBD2E)); g2.fillOval(14, 6, 10, 10);
                g2.setColor(new Color(0x27C93F)); g2.fillOval(28, 6, 10, 10);
            }
        };
        dots.setOpaque(false);
        dots.setPreferredSize(new Dimension(42, 22));

        JLabel outputTitle = new JLabel("OUTPUT AREA");
        outputTitle.setFont(FONT_HEADER);
        outputTitle.setForeground(new Color(0x858585));

        headerLeft.add(dots); headerLeft.add(outputTitle);
        outputHeader.add(headerLeft, BorderLayout.WEST);

        // Tab definitions — Sequence: Runtime Output -> Symbol Table -> Generated Code
        String[][] tabs = { // Reordered per request
            {"\u25B6", "Runtime Output"},
            {"\u25A3", "Symbol Table"},
            {"{ }", "Generated Code"}
        };

        outputTabs = new JTabbedPane();
        outputTabs.setTabPlacement(JTabbedPane.TOP);
        outputTabs.setFont(FONT_UI_SM);
        outputTabs.setBackground(outTabBg());

        outputTabs.setUI(new javax.swing.plaf.basic.BasicTabbedPaneUI() {
            @Override protected void installDefaults() {
                super.installDefaults();
                highlight      = outTabBg();
                lightHighlight = outTabBg();
                shadow         = outTabBg();
                darkShadow     = outTabBg();
                focus          = outTabBg();
            }
            @Override protected void paintTabBackground(Graphics g, int tp,
                    int idx, int x, int y, int w, int h, boolean sel) {
                g.setColor(sel ? bg() : outTabBg());
                g.fillRect(x, y, w, h);
            }
            @Override protected void paintTabBorder(Graphics g, int tp,
                    int idx, int x, int y, int w, int h, boolean sel) {
                if (sel) {
                    g.setColor(VSCODE_BLUE);
                    g.fillRect(x, y, w, 2); // top blue indicator line
                }
                g.setColor(border());
                g.drawLine(x+w, y, x+w, y+h); // right border between tabs
            }
            @Override protected void paintFocusIndicator(Graphics g, int tp,
                    Rectangle[] rs, int idx, Rectangle ir, Rectangle tr, boolean sel) {}
            @Override protected int getTabLabelShiftX(int tp, int idx, boolean sel) { return 0; }
            @Override protected int getTabLabelShiftY(int tp, int idx, boolean sel) { return 0; }
            @Override protected void paintContentBorder(Graphics g, int tp, int idx) {}
        });

        outputTabs.addTab(tabs[0][1], buildRuntimeOutputPanel());
        outputTabs.addTab(tabs[1][1], buildSymbolTablePanel());
        outputTabs.addTab(tabs[2][1], buildGeneratedCodePanel());

        for (int i = 0; i < outputTabs.getTabCount(); i++) {
            final int idx = i;
            JPanel tabComp = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0)) {
                @Override protected void paintComponent(Graphics g) {
                    g.setColor(idx == outputTabs.getSelectedIndex() ? bg() : outTabBg());
                    g.fillRect(0, 0, getWidth(), getHeight());
                }
            };
            tabComp.setOpaque(false);
            tabComp.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 10));

            boolean sel = i == outputTabs.getSelectedIndex();
            JLabel iconLbl = new JLabel(tabs[i][0]);
            iconLbl.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 11));
            iconLbl.setForeground(sel ? new Color(0xCCCCCC) : outTabFg());
            JLabel nameLbl = new JLabel(tabs[i][1]);
            nameLbl.setFont(FONT_UI_B); // Always apply bold to tab text
            nameLbl.setForeground(sel ? new Color(0xCCCCCC) : outTabFg());

            tabComp.add(iconLbl); tabComp.add(nameLbl);
            outputTabs.setTabComponentAt(i, tabComp);
        }

        outputTabs.addChangeListener(e -> {
            for (int i = 0; i < outputTabs.getTabCount(); i++) {
                Component tc = outputTabs.getTabComponentAt(i);
                if (tc instanceof JPanel panel) {
                    boolean sel = i == outputTabs.getSelectedIndex();
                    Color fg = sel ? new Color(0xCCCCCC) : new Color(0x858585);
                    for (Component c : panel.getComponents()) {
                        if (c instanceof JLabel lbl) {
                            lbl.setForeground(fg);
                            lbl.setFont(FONT_UI_B);
                        }
                    }
                    panel.repaint();
                }
            }
            outputTabs.repaint();
        });

        p.add(outputHeader, BorderLayout.NORTH);
        p.add(outputTabs,   BorderLayout.CENTER);
        return p;
    }

    // ── Symbol Table ──────────────────────────────────────────────────────────
    private JPanel buildSymbolTablePanel() {
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setBackground(bg());
        p.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        // Search + Filter toolbar
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        bar.setOpaque(false);

        JLabel searchIcon = new JLabel("\uD83D\uDD0D"); // 🔍
        searchIcon.setFont(FONT_ICON);
        JLabel searchLbl  = new JLabel("Search:");
        searchLbl.setFont(FONT_UI_SM); searchLbl.setForeground(new Color(0x858585));
        searchField = new JTextField(14);
        searchField.setFont(FONT_UI_SM);
        searchField.setBackground(bgPanel());
        searchField.setForeground(accentDark());
        searchField.setCaretColor(accentDark());
        searchField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(border()),
            BorderFactory.createEmptyBorder(3, 6, 3, 6)));

        bar.add(searchIcon); bar.add(searchLbl); bar.add(searchField);

        String[] cols = {"Name","Type","Scope","Line Declared","Value"};
        symbolModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable sym = new JTable(symbolModel);
        styleTable(sym);

        JScrollPane scroll = new JScrollPane(sym);
        scroll.setBorder(BorderFactory.createLineBorder(border()));
        scroll.getViewport().setBackground(bg());
        styleScrollBar(scroll.getVerticalScrollBar());

        p.add(bar,    BorderLayout.NORTH);
        p.add(scroll, BorderLayout.CENTER);
        return p;
    }

    private JButton buildSmallActionBtn(String text) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bgC = getModel().isRollover()
                    ? (isDarkMode ? new Color(0x37373D) : new Color(0xE8E8E8))
                    : bgPanel();
                g2.setColor(bgC);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 4, 4);
                g2.setColor(border());
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 4, 4);
                g2.setFont(FONT_UI_SM);
                g2.setColor(new Color(0x858585));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(),
                    (getWidth()  - fm.stringWidth(getText())) / 2,
                    (getHeight() + fm.getAscent() - fm.getDescent()) / 2 - 1);
            }
        };
        btn.setPreferredSize(new Dimension(86, 22));
        btn.setOpaque(false); btn.setContentAreaFilled(false);
        btn.setBorderPainted(false); btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    // ── Generated Code ────────────────────────────────────────────────────────
    private JPanel buildGeneratedCodePanel() {
        JPanel p = new JPanel(new BorderLayout(0, 0));
        p.setBackground(bg());
        p.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        generatedCodeArea = new JTextArea();
        generatedCodeArea.setFont(FONT_MONO_SM);
        generatedCodeArea.setBackground(bgPanel());
        generatedCodeArea.setForeground(new Color(0xD4D4D4));
        generatedCodeArea.setEditable(false);
        generatedCodeArea.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        generatedCodeArea.setText(
            "; Generated Intermediate Representation\n" +
            "; ──────────────────────────────────────\n" +
            "; Run compiler to generate IR code\n"
        );

        JScrollPane scroll = new JScrollPane(generatedCodeArea);
        scroll.setBorder(BorderFactory.createLineBorder(border()));
        scroll.getViewport().setBackground(bgPanel());
        styleScrollBar(scroll.getVerticalScrollBar());
        p.add(scroll, BorderLayout.CENTER);
        return p;
    }

    // ── Runtime Output ────────────────────────────────────────────────────────
    private JPanel buildRuntimeOutputPanel() {
        JPanel p = new JPanel(new BorderLayout(0, 0));
        p.setBackground(bg());
        p.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        runtimeArea = new JTextArea();
        runtimeArea.setFont(FONT_MONO_SM);
        runtimeArea.setBackground(new Color(0x0C0C0C));
        runtimeArea.setForeground(new Color(0xCCCCCC));
        runtimeArea.setEditable(false);
        runtimeArea.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        runtimeArea.setText("// Program output will appear here after execution\n");

        JScrollPane scroll = new JScrollPane(runtimeArea);
        scroll.setBorder(BorderFactory.createLineBorder(border()));
        scroll.getViewport().setBackground(new Color(0x0C0C0C));
        styleScrollBar(scroll.getVerticalScrollBar());
        p.add(scroll, BorderLayout.CENTER);
        return p;
    }

    // =========================================================================
    //  CONSOLE  (VSCode-style terminal panel)
    // =========================================================================
    private JPanel buildConsole() {
        JPanel p = new JPanel(new BorderLayout(0, 0));
        p.setBackground(consoleBg());

        // Console tab strip
        JPanel strip = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(isDarkMode ? new Color(0x252526) : new Color(0x1E1E1E));
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(new Color(0x3E3E42));
                g.drawLine(0, getHeight()-1, getWidth(), getHeight()-1);
            }
        };
        strip.setPreferredSize(new Dimension(0, 35)); // Consistent header heights
        strip.setOpaque(false);

        JPanel tabsLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tabsLeft.setOpaque(false);
        
        consoleTabBtn = buildConsoleTabLbl(">_  Console", true);
        errorCountLabel = buildConsoleTabLbl("\u26A0  Errors (0)", false);
        warnCountLabel  = buildConsoleTabLbl("\u24D8  Warnings (0)", false);
        
        // Tab Switching Logic
        consoleTabBtn.addMouseListener(new MouseAdapter() { 
            @Override public void mouseClicked(MouseEvent e) { switchConsoleCard("Console"); } 
        });
        errorCountLabel.addMouseListener(new MouseAdapter() { 
            @Override public void mouseClicked(MouseEvent e) { switchConsoleCard("Errors"); } 
        });
        warnCountLabel.addMouseListener(new MouseAdapter() { 
            @Override public void mouseClicked(MouseEvent e) { switchConsoleCard("Warnings"); } 
        });

        tabsLeft.add(consoleTabBtn); tabsLeft.add(errorCountLabel); tabsLeft.add(warnCountLabel);

        // Right side actions
        JPanel cRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
        cRight.setOpaque(false);

        JLabel clearLbl = new JLabel("\uD83D\uDDD1 Clear");  // 🗑 Clear
        clearLbl.setFont(FONT_UI_SM);
        clearLbl.setForeground(new Color(0x858585));
        clearLbl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        clearLbl.setToolTipText("Clear console output");
        clearLbl.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (consoleArea != null) {
                    consoleArea.setText("");
                    errorCount = 0; warnCount = 0;
                    errorListModel.clear();
                    warningListModel.clear();
                    updateConsoleCounts();
                }
            }
            @Override public void mouseEntered(MouseEvent e) { clearLbl.setForeground(new Color(0xCCCCCC)); }
            @Override public void mouseExited(MouseEvent e)  { clearLbl.setForeground(new Color(0x858585)); }
        });
        cRight.add(clearLbl);

        strip.add(tabsLeft, BorderLayout.WEST);
        strip.add(cRight,   BorderLayout.EAST);

        // Console text area
        consoleArea = new JTextArea();
        consoleArea.setFont(FONT_MONO_SM);
        consoleArea.setBackground(consoleBg());
        consoleArea.setForeground(consoleTxt());
        consoleArea.setEditable(false);
        consoleArea.setCaretColor(new Color(0xAEAFAD));
        consoleArea.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));

        String now = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        appendConsole("\u2713 [" + now + "] Lexical analysis completed successfully", SUCCESS_GREEN);
        appendConsole("\u2713 [" + now + "] Generated 42 tokens from source code",    SUCCESS_GREEN);

        // Setup CardLayout for bottom panel
        consoleLayout = new CardLayout();
        consoleCardPanel = new JPanel(consoleLayout);
        consoleCardPanel.setOpaque(false);

        consoleCardPanel.add(createScrollableConsole(consoleArea), "Console");
        consoleCardPanel.add(createScrollableList(errorList, "Error"), "Errors");
        consoleCardPanel.add(createScrollableList(warningList, "Warning"), "Warnings");
        
        // Interaction for Error/Warning list items
        errorList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 1) {
                    String selected = errorList.getSelectedValue();
                    if (selected != null) highlightLineFromError(selected);
                }
            }
        });
        
        warningList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 1) {
                    String selected = warningList.getSelectedValue();
                    if (selected != null) showWarningInConsole(selected);
                }
            }
        });

        p.add(strip,  BorderLayout.NORTH);
        p.add(consoleCardPanel, BorderLayout.CENTER);
        return p;
    }

    private JLabel buildConsoleTabLbl(String text, boolean active) {
        JLabel lbl = new JLabel(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                if (active) {
                    g2.setColor(consoleBg());
                    g2.fillRect(0, 0, getWidth(), getHeight());
                    // VSCode-style top blue indicator
                    g2.setColor(VSCODE_BLUE);
                    g2.fillRect(0, 0, getWidth(), 2);
                }
                super.paintComponent(g);
            }
        };
        lbl.setFont(FONT_UI_SM);
        lbl.setForeground(active ? new Color(0xCCCCCC) : new Color(0x858585));
        lbl.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        lbl.setOpaque(false);
        return lbl;
    }

    private JScrollPane createScrollableConsole(JTextArea area) {
        JScrollPane sp = new JScrollPane(area);
        sp.setBorder(null);
        sp.getViewport().setBackground(consoleBg());
        styleScrollBar(sp.getVerticalScrollBar());
        return sp;
    }

    private JScrollPane createScrollableList(JList<String> list, String type) {
        list.setFont(FONT_MONO_SM);
        list.setBackground(consoleBg());
        list.setForeground(type.equals("Error") ? ERROR_RED : WARNING_YELLOW);
        list.setSelectionBackground(isDarkMode ? new Color(0x37373D) : new Color(0xE8E8E8));
        list.setSelectionForeground(type.equals("Error") ? ERROR_RED : WARNING_YELLOW);
        list.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        
        JScrollPane sp = new JScrollPane(list);
        sp.setBorder(null);
        sp.getViewport().setBackground(consoleBg());
        styleScrollBar(sp.getVerticalScrollBar());
        return sp;
    }

    private void switchConsoleCard(String cardName) {
        consoleLayout.show(consoleCardPanel, cardName);
        // Update labels visibility/active style
        consoleTabBtn.setForeground(cardName.equals("Console")  ? new Color(0xCCCCCC) : new Color(0x858585));
        errorCountLabel.setForeground(cardName.equals("Errors") ? new Color(0xCCCCCC) : new Color(0x858585));
        warnCountLabel.setForeground(cardName.equals("Warnings") ? new Color(0xCCCCCC) : new Color(0x858585));
        
        // Force redraw for active indicator (blue line)
        consoleTabBtn.repaint();
        errorCountLabel.repaint();
        warnCountLabel.repaint();
    }

    private void appendConsole(String text, Color color) {
        if (consoleArea != null) consoleArea.append(text + "\n");
    }

    private void highlightLineFromError(String errorMsg) {
        try {
            // Pattern to find "[Line X, Col Y]"
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("Line (\\d+)");
            java.util.regex.Matcher m = p.matcher(errorMsg);
            if (m.find()) {
                int line = Integer.parseInt(m.group(1));
                int start = codeEditor.getLineStartOffset(line - 1);
                int end = codeEditor.getLineEndOffset(line - 1);
                codeEditor.setCaretPosition(start);
                codeEditor.setSelectionStart(start);
                codeEditor.setSelectionEnd(end);
                codeEditor.requestFocusInWindow();
            }
        } catch (Exception ex) {
            appendConsole("Could not navigate to error line: " + ex.getMessage(), ERROR_RED);
        }
    }

    private void showWarningInConsole(String warningMsg) {
        appendConsole("\n--- Warning Detail ---", WARNING_YELLOW);
        appendConsole(warningMsg, accentDark());
        switchConsoleCard("Console");
    }

    private void updateConsoleCounts() {
        if (errorCountLabel != null) {
            errorCountLabel.setText("\u26A0  Errors (" + errorCount + ")");
            // If errors exist, highlight label in red
            errorCountLabel.setForeground(errorCount > 0 ? ERROR_RED : new Color(0x858585));
        }
        if (warnCountLabel != null) {
            warnCountLabel.setText("\u24D8  Warnings (" + warnCount + ")");
            warnCountLabel.setForeground(warnCount > 0 ? WARNING_YELLOW : new Color(0x858585));
        }
    }

    private void handleNewFile() {
        codeEditor.setText("void main() {\n\n}");
        if (symbolModel != null) symbolModel.setRowCount(0);
        if (generatedCodeArea != null) {
            generatedCodeArea.setText(
                "; Generated Intermediate Representation\n" +
                "; ──────────────────────────────────────\n" +
                "; Run compiler to generate IR code\n"
            );
        }
        errorListModel.clear();
        warningListModel.clear();
        if (runtimeArea != null) runtimeArea.setText("// Program output will appear here after execution\n");
        errorCount = 0;
        warnCount = 0;
        updateConsoleCounts();
        logAction("New project template created.", INFO_BLUE);
    }

    private void handleOpenFile() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Open Source File");
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                codeEditor.read(br, null);
                logAction("Opened file: " + file.getAbsolutePath(), INFO_BLUE);
            } catch (IOException ex) {
                logAction("Error opening file: " + ex.getMessage(), ERROR_RED);
            }
        }
    }

    private void handleCopyAction() {
        String code = codeEditor.getText();
        if (code.isEmpty()) return;
        StringSelection selection = new StringSelection(code);
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(selection, selection);
        logAction("Editor content copied to clipboard.", INFO_BLUE);
    }

    private void handleExportAction() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Export Source Code");
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            try (FileWriter fw = new FileWriter(file)) {
                codeEditor.write(fw);
                logAction("Code exported to: " + file.getAbsolutePath(), SUCCESS_GREEN);
            } catch (IOException ex) {
                logAction("Error exporting file: " + ex.getMessage(), ERROR_RED);
            }
        }
    }

    private void handleClearAction() {
        codeEditor.setText("");
        errorCount = 0;
        warnCount = 0;
        errorListModel.clear();
        warningListModel.clear();
        updateConsoleCounts();
        logAction("Editor and counters cleared.", INFO_BLUE);
    }

    private void logAction(String msg, Color color) {
        String now = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        appendConsole("[" + now + "] " + msg, color);
    }

    /**
     * Integrates the compilation and execution pipeline.
     * Captures System.out/System.err during interpretation to populate the Runtime Output tab.
     */
    private void handleRunAction() {
        String code = codeEditor.getText();
        if (code.trim().isEmpty()) return;

        // Reset UI state for new run
        ErrorHandler.clear();
        if (runtimeArea != null) runtimeArea.setText("");
        if (consoleArea != null) consoleArea.setText("");
        if (generatedCodeArea != null) generatedCodeArea.setText("");
        errorListModel.clear();
        warningListModel.clear();
        outputTabs.setSelectedIndex(0); // Focus Runtime Output tab

        errorCount = 0;
        warnCount = 0;
        updateConsoleCounts();
        
        String now = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        appendConsole("\u2699 [" + now + "] Compilation started...", INFO_BLUE);

        new Thread(() -> {
            try {
                CompilerPipeline pipeline = new CompilerPipeline();
                CompilerPipeline.CompileResult compileResult = pipeline.compile(code);
                String timeStr = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

                if (compileResult.errors.isEmpty()) {
                    SwingUtilities.invokeLater(() -> 
                        appendConsole("\u2713 [" + timeStr + "] Compilation successful!", SUCCESS_GREEN));

                    // Capture runtime output
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    PrintStream originalOut = System.out;
                    PrintStream originalErr = System.err;
                    String runtimeOutput;
                    try (PrintStream capture = new PrintStream(buffer, true)) {
                        System.setOut(capture);
                        System.setErr(capture);
                        
                        Interpreter interpreter = new Interpreter();
                        Object result = interpreter.execute(compileResult.instructions);
                        if (result != null) capture.println(result);
                        
                        runtimeOutput = buffer.toString();
                    } finally {
                        System.setOut(originalOut);
                        System.setErr(originalErr);
                    }

                    SwingUtilities.invokeLater(() -> {
                        runtimeArea.setText(runtimeOutput.isEmpty() ? "<no runtime output>" : runtimeOutput);
                        appendConsole("\u2713 [" + timeStr + "] Execution completed.", SUCCESS_GREEN);
                        
                        // Populate the Generated Code tab with the TAC instructions
                        StringBuilder irText = new StringBuilder("; Generated Intermediate Representation\n");
                        irText.append("; ──────────────────────────────────────\n");
                        for (Instruction ins : compileResult.instructions) {
                            irText.append(ins.toString()).append("\n");
                        }
                        generatedCodeArea.setText(irText.toString());
                    });
                } else {
                    SwingUtilities.invokeLater(() -> {
                        errorCount = compileResult.errors.size();
                        updateConsoleCounts();
                        
                        String logTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                        appendConsole("\u2716 [" + logTime + "] Compilation failed with " + errorCount + " errors.", ERROR_RED);
                        
                        for (String err : compileResult.errors) {
                            String timestamped = "[" + logTime + "] " + err;
                            if (err.toLowerCase().contains("warning")) {
                                warningListModel.addElement(timestamped);
                                warnCount++;
                            } else {
                                errorListModel.addElement(timestamped);
                            }
                        }
                        updateConsoleCounts();
                        if (errorCount > 0) switchConsoleCard("Errors");
                        runtimeArea.setText("<runtime skipped due to compile errors>");
                    });
                }
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> appendConsole("\u26A0 [Internal Error] " + ex.getMessage(), ERROR_RED));
            }
        }).start();
    }

    // =========================================================================
    //  STATUS BAR  (VSCode-style blue bottom bar)
    // =========================================================================
    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(statusBg());
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        bar.setPreferredSize(new Dimension(0, 24));
        bar.setOpaque(false);
        bar.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 3));
        left.setOpaque(false);

        // Branch icon + name
        JLabel branchLbl = new JLabel("\uD83D\uDD00 main");  // 🔀 main
        branchLbl.setFont(FONT_UI_SM); branchLbl.setForeground(Color.WHITE);
        if (lnColLabel != null) lnColLabel.setForeground(new Color(0xCCCCCC));

        JLabel enc  = new JLabel("UTF-8");
        enc.setFont(FONT_UI_SM); enc.setForeground(new Color(0xCCCCCC));

        JLabel lang = new JLabel("Java");
        lang.setFont(FONT_UI_SM); lang.setForeground(new Color(0xCCCCCC));

        left.add(branchLbl); left.add(lnColLabel); left.add(enc); left.add(lang);

        JPanel right2 = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 3));
        right2.setOpaque(false);

        // Move Ln/Col to right side to align with Output area divider
        if (lnColLabel != null) {
            left.remove(lnColLabel);
            right2.add(lnColLabel);
        }

        JLabel spaces = new JLabel("Spaces: 4");
        spaces.setFont(FONT_UI_SM); spaces.setForeground(new Color(0xCCCCCC));

        JLabel modeLabel = new JLabel(isDarkMode ? "\uD83C\uDF19 Dark" : "\u2600 Light");
        modeLabel.setFont(FONT_UI_SM); modeLabel.setForeground(new Color(0xCCCCCC));
        right2.add(spaces); right2.add(modeLabel);

        bar.add(left,   BorderLayout.WEST);
        bar.add(right2, BorderLayout.EAST);
        return bar;
    }

    // =========================================================================
    //  HELPERS
    // =========================================================================
    private void styleTable(JTable table) {
        table.setFont(FONT_UI_SM);
        table.setRowHeight(26);
        table.setBackground(bg());
        table.setForeground(accentDark());
        table.setGridColor(border());
        table.setSelectionBackground(new Color(0x094771));
        table.setSelectionForeground(Color.WHITE);
        table.setShowVerticalLines(true);
        table.setShowHorizontalLines(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setFillsViewportHeight(true);

        JTableHeader hdr = table.getTableHeader();
        hdr.setFont(FONT_HEADER);
        hdr.setBackground(tblHdrBg());
        hdr.setForeground(new Color(0x858585));
        hdr.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, border()));
        hdr.setReorderingAllowed(false);
        hdr.setPreferredSize(new Dimension(0, 28));

        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(
                    JTable t, Object val, boolean sel, boolean foc, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, val, sel, foc, row, col);
                if (!sel) {
                    c.setBackground(row % 2 == 0 ? bg() : tblRowAlt());
                    c.setForeground(accentDark());
                }
                setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
                return c;
            }
        });
    }

    private void styleScrollBar(JScrollBar sb) {
        sb.setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
            @Override protected void configureScrollBarColors() {
                this.thumbColor = isDarkMode ? new Color(0x555555) : new Color(0xC1C1C1);
                this.trackColor = isDarkMode ? new Color(0x1E1E1E) : new Color(0xF3F3F3);
            }
            @Override protected JButton createDecreaseButton(int o) { return createZeroButton(); }
            @Override protected JButton createIncreaseButton(int o) { return createZeroButton(); }
            private JButton createZeroButton() {
                JButton b = new JButton();
                b.setPreferredSize(new Dimension(0, 0));
                b.setMinimumSize(new Dimension(0, 0));
                b.setMaximumSize(new Dimension(0, 0));
                return b;
            }
        });
    }

    // =========================================================================
    //  LINE NUMBER COMPONENT
    // =========================================================================
    static class LineNumberComponent extends JComponent {
        private final JTextArea textArea;
        private final Color lnBg, lnFg, lnBorder;
        private static final int PAD = 10;

        LineNumberComponent(JTextArea ta, Color bg, Color fg, Color border) {
            this.textArea = ta;
            this.lnBg = bg; this.lnFg = fg; this.lnBorder = border;
            setFont(ta.getFont()); // Fix: Sync font with editor for vertical alignment
            setBackground(bg);
            setForeground(fg);
            setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, border));
            ta.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                public void insertUpdate(javax.swing.event.DocumentEvent e)  { repaint(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e)  { repaint(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) { repaint(); }
            });
        }

        @Override public Dimension getPreferredSize() {
            int lines = textArea.getLineCount();
            String max = String.valueOf(Math.max(lines, 999));
            FontMetrics fm = getFontMetrics(getFont());
            return new Dimension(fm.stringWidth(max) + PAD * 2, textArea.getHeight());
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(lnBg);
            g2.fillRect(0, 0, getWidth(), getHeight());

            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            Rectangle clip = g.getClipBounds();
            int lineH    = textArea.getFontMetrics(textArea.getFont()).getHeight();
            int startLine = (clip == null) ? 0 : Math.max(0, clip.y / lineH);
            int endLine   = (clip == null)
                ? textArea.getLineCount() - 1
                : Math.min(textArea.getLineCount() - 1, (clip.y + clip.height) / lineH + 1);

            for (int i = startLine; i <= endLine; i++) {
                String num = String.valueOf(i + 1);
                int x = getWidth() - fm.stringWidth(num) - PAD;
                int y = i * lineH + fm.getAscent() + 4;
                g2.setColor(lnFg);
                g2.drawString(num, x, y);
            }
        }
    }
}