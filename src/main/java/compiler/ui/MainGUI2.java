package compiler.ui;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Dialog;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Box;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.JTree;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableRowSorter;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeSelectionModel;

import compiler.CompilerPipeline;
import compiler.codegen.Instruction;
import compiler.lexer.SymbolTable;
import compiler.util.ErrorHandler;
import compiler.vm.Interpreter;

public class MainGUI2 extends JFrame {

    // ── Dark / Light Theme Toggle ─────────────────────────────────────────────
    private boolean isDarkMode = true;

    // ── Light palette ─────────────────────────────────────────────────────────
    private static final Color L_BG_WHITE        = new Color(0xF9F9F9);
    private static final Color L_BG_LIGHT        = new Color(0xF3F3F3);
    private static final Color L_BG_PANEL        = new Color(0xFAFAFA);
    private static final Color L_HEADER_BG       = new Color(0xF3F3F3);
    private static final Color L_ACCENT_BLACK    = new Color(0x1F2328);
    private static final Color L_ACCENT_DARK     = new Color(0x24292E);
    private static final Color L_BORDER_COLOR    = new Color(0xDDDDDD);
    private static final Color L_CONSOLE_BG      = new Color(0xF9F9F9);
    private static final Color L_CONSOLE_TEXT    = new Color(0x333333);
    private static final Color L_TABLE_HEADER_BG = new Color(0xF0F0F0);
    private static final Color L_TABLE_ROW_ALT   = new Color(0xF3F3F3);
    private static final Color L_LINE_NUM_BG     = new Color(0xF3F3F3);
    private static final Color L_LINE_NUM_FG     = new Color(0x666666);
    private static final Color L_OUTPUT_HEADER   = new Color(0xF0F0F0);
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

    // ── Named inline-use colors (FIX design: single source of truth) ──────────
    // These replace the raw hex literals scattered throughout paintComponent() methods.
    private static final Color HOVER_BG_DARK     = new Color(0x2A2D2E);
    private static final Color HOVER_BG_LIGHT    = new Color(0xE8E8E8);
    private static final Color MUTED_FG          = new Color(0x666666);
    private static final Color LABEL_FG          = new Color(0xCCCCCC); // Fallback, primary used via accessor
    private static final Color HEADER_SEPARATOR  = new Color(0xDDDDDD);
    private static final Color SCROLLTHUMB_DARK  = new Color(0x555555);
    private static final Color SCROLLTHUMB_LIGHT = new Color(0xC1C1C1);
    private static final Color SCROLLTRACK_DARK  = new Color(0x1E1E1E);
    private static final Color SCROLLTRACK_LIGHT = new Color(0xF3F3F3);
    private static final Color PRESSED_BG_DARK   = new Color(0x005F9E);
    private static final Color SELECTION_BG_DARK = new Color(0x094771);
    // Editor / syntax colors
    private static final Color LOGO_BLUE_START   = new Color(0x0098FF);
    private static final Color LOGO_BLUE_END     = new Color(0x006EBD);
    private static final Color EDITOR_CARET      = new Color(0xAEAFAD);
    private static final Color EDITOR_SEL_DARK   = new Color(0x264F78);
    private static final Color EDITOR_SEL_LIGHT  = new Color(0xADD6FF);
    private static final Color RUNTIME_BG        = new Color(0x0C0C0C);
    private static final Color GOLD              = new Color(0xFFD700);
    private static final Color JAVA_BLUE         = new Color(0x519ABA);
    private static final Color LIST_SEL_DARK     = new Color(0x37373D);
    private static final Color LIST_SEL_LIGHT    = new Color(0xE8E8E8);
    private static final Color DIM_BORDER        = new Color(0x444444); // inactive button border
    // AST syntax-highlight colors
    private static final Color AST_CONTROL       = new Color(0xC586C0);
    private static final Color AST_LITERAL       = new Color(0xCE9178);
    private static final Color AST_IDENTIFIER    = new Color(0x9CDCFE);
    // macOS-style traffic-light dots
    private static final Color DOT_RED           = new Color(0xFF5F56);
    private static final Color DOT_YELLOW        = new Color(0xFFBD2E);
    private static final Color DOT_GREEN         = new Color(0x27C93F);

    // VSCode-style accent colors
    private static final Color VSCODE_BLUE      = new Color(0x007ACC);
    private static final Color VSCODE_BLUE_HVR  = new Color(0x1E8AD6);
    private static final Color SUCCESS_GREEN    = new Color(0x4EC994);
    private static final Color WARNING_YELLOW   = new Color(0xED6C02);
    private static final Color ERROR_RED        = new Color(0xD32F2F);
    private static final Color INFO_BLUE        = new Color(0x1976D2);
    private Color labelFg()    { return isDarkMode ? D_ACCENT_BLACK : L_ACCENT_BLACK; }

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
    private Color hoverBg()    { return isDarkMode ? HOVER_BG_DARK     : HOVER_BG_LIGHT; }
    private Color selectionBg(){ return isDarkMode ? SELECTION_BG_DARK : LIST_SEL_LIGHT; }

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

    // ── Visibility State & References ─────────────────────────────────────────
    private boolean showConsoleTab = true, showErrorsTab = true, showWarningsTab = true;
    private boolean showRuntimeTab = true, showSymbolTab = true, showGeneratedTab = true;
    private JPanel runtimeTabPanel, astTreeTabPanel, symbolTableTabPanel, generatedCodeTabPanel;
    private JPanel tabsLeft; // Container for bottom console labels
    private final String[][] tabData = {
        {"\u25B6", "Runtime Output"}, {"\uD83C\uDF32", "AST Tree"},
        {"\u25A3", "Symbol Table"}, {"{ }", "Generated Code"}
    };

    // FIX (appendConsole color): replaced JTextArea with JTextPane + StyledDocument
    // so each log line can carry its own foreground color.
    private JTextPane         consolePane;
    private StyledDocument    consoleDoc;

    private DefaultListModel<String> errorListModel   = new DefaultListModel<>();
    private JList<String>            errorList;
    private DefaultListModel<String> warningListModel = new DefaultListModel<>();
    private JList<String>            warningList;

    private JPanel                   consoleCardPanel;
    private CardLayout               consoleLayout;

    private JLabel            lnColLabel;
    private JTextField        searchField;
    private JComboBox<String> filterCombo;
    private TableRowSorter<DefaultTableModel> symbolSorter;
    private DefaultTableModel symbolModel;
    private JTable            generatedCodeTable;
    private DefaultTableModel generatedCodeModel;
    private JTextArea         runtimeArea;
    private JButton           themeToggleBtn;
    private JPanel            rootPanel;
    private JLabel            errorCountLabel;
    private JLabel            warnCountLabel;
    private int               errorCount = 0;
    private int               warnCount  = 0;
    private String            activeConsoleTab = "Console";

    private JLabel            consoleTabBtn;

    // AST tree model — reset on each run
    private DefaultTreeModel  astTreeModel;
    private JTree             astTree;

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

        errorListModel   = new DefaultListModel<>();
        warningListModel = new DefaultListModel<>();
        errorList        = new JList<>(errorListModel);
        warningList      = new JList<>(warningListModel);

        lnColLabel = new JLabel("Ln 1, Col 1");
        codeEditor = new JTextArea();

        getContentPane().removeAll();
        setBackground(bg());

        rootPanel = new JPanel(new BorderLayout(0, 0));
        rootPanel.setBackground(bg());

        rootPanel.add(buildHeader(),    BorderLayout.NORTH);
        rootPanel.add(buildCenter(),    BorderLayout.CENTER);
        rootPanel.add(buildStatusBar(), BorderLayout.SOUTH);

        setContentPane(rootPanel);

        if (savedCode != null && !savedCode.isEmpty()) {
            codeEditor.setText(savedCode);
        } else {
            codeEditor.setText(
                "//input code here\n"
            );
        }

        revalidate();
        repaint();
    }

    // =========================================================================
    //  HEADER
    // =========================================================================
    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(headerBg());
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(HEADER_SEPARATOR);
                g.drawLine(0, getHeight()-1, getWidth(), getHeight()-1);
            }
        };
        header.setPreferredSize(new Dimension(0, 56));
        header.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
        header.setOpaque(false);

        JPanel left = new JPanel(new GridBagLayout());
        left.setOpaque(false);

        JPanel logoBox = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gp = new GradientPaint(0, 0, LOGO_BLUE_START, getWidth(), getHeight(), LOGO_BLUE_END);
                g2.setPaint(gp);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
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
        t1.setFont(FONT_TITLE); t1.setForeground(labelFg());
        JLabel t2 = new JLabel("Interactive Compiler Stage Explorer");
        t2.setFont(FONT_UI_SM); t2.setForeground(MUTED_FG);
        titlePanel.add(t1); titlePanel.add(t2);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(0, 0, 0, 12);
        left.add(logoBox, gbc);
        gbc.insets = new Insets(0, 0, 0, 0);
        left.add(titlePanel, gbc);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        right.setOpaque(false);

        JButton newBtn = buildHeaderBtn("New File", "", false, "New Project (Ctrl+N)");
        newBtn.addActionListener(e -> handleNewFile());
        right.add(newBtn);

        JButton openBtn = buildHeaderBtn("Open", "", false, "Load File (Ctrl+O)");
        openBtn.addActionListener(e -> handleOpenFile());
        right.add(openBtn);

        JButton runBtn = buildHeaderBtn("Run", "", true, "Run Compiler (F5)");
        runBtn.addActionListener(e -> handleRunAction());
        right.add(runBtn);

        JPanel div = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(MUTED_FG);
                g.fillRect(0, 8, 1, 24);
            }
        };
        div.setOpaque(false);
        div.setPreferredSize(new Dimension(10, 40));
        right.add(div);

        themeToggleBtn = buildThemeToggleBtn();
        JButton settingsBtn = buildIconHeaderBtn("\u2699", "Settings (Ctrl+,)");
        settingsBtn.addActionListener(e -> showSettingsDialog());
        right.add(themeToggleBtn); right.add(settingsBtn);

        header.add(left,  BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    private JButton buildHeaderBtn(String label, String iconChar, boolean primary, String tooltip) {
        String iconText = switch (label) {
            case "New File" -> "\uD83D\uDCC4";
            case "Open"     -> "\uD83D\uDCC2";
            case "Run"      -> "\u25B6";
            default         -> iconChar;
        };

        JButton btn = new JButton() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                Color base, fg, borderC;
                if (primary) {
                    base    = getModel().isPressed()  ? PRESSED_BG_DARK :
                              getModel().isRollover() ? VSCODE_BLUE_HVR : VSCODE_BLUE;
                    fg      = Color.WHITE;
                    borderC = getModel().isRollover() ? VSCODE_BLUE_HVR : LOGO_BLUE_END;
                } else {
                    base    = getModel().isPressed()  ? hoverBg() :
                              getModel().isRollover() ? hoverBg() : new Color(0x00000000, true);
                    fg      = labelFg();
                    borderC = getModel().isRollover() ? MUTED_FG : DIM_BORDER;
                }

                if (primary || getModel().isRollover() || getModel().isPressed()) {
                    g2.setColor(base);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 5, 5);
                }
                if (primary) {
                    g2.setColor(borderC);
                    g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 5, 5);
                }

                g2.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 13));
                g2.setColor(fg);
                FontMetrics fm = g2.getFontMetrics();
                int iconW = fm.stringWidth(iconText);

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
                    g2.setColor(hoverBg());
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 5, 5);
                }

                String icon  = isDarkMode ? "\u2600" : "\uD83C\uDF19";
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
                g2.setColor(isDarkMode ? GOLD : INFO_BLUE);
                g2.drawString(icon, startX, baseY);

                g2.setFont(FONT_UI_SM);
                g2.setColor(labelFg());
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
                    g2.setColor(hoverBg());
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 5, 5);
                }
                g2.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 16));
                g2.setColor(LABEL_FG);
                FontMetrics fm = g2.getFontMetrics();
                String s = getText();
                g2.drawString(s,
                    (getWidth()  - fm.stringWidth(s)) / 2,
                    (getHeight() + fm.getAscent() - fm.getDescent()) / 2 - 1);
            }
        };
        btn.setPreferredSize(new Dimension(34, 32)); btn.setForeground(labelFg());
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
        split.setResizeWeight(0.70);
        split.setDividerSize(4);
        split.setBorder(null);
        split.setBackground(bg());
        SwingUtilities.invokeLater(() -> split.setDividerLocation(0.70));
        styleDivider(split);
        return split;
    }

    private JSplitPane buildTopArea() {
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                buildEditorPanel(), buildOutputPanel());
        split.setResizeWeight(0.60);
        split.setDividerSize(4);
        split.setBorder(null);
        split.setBackground(bg());
        SwingUtilities.invokeLater(() -> split.setDividerLocation(0.60));
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

        JPanel tabBar = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(bgLight());
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(border());
                g.drawLine(0, getHeight()-1, getWidth(), getHeight()-1);
            }
        };
        tabBar.setPreferredSize(new Dimension(0, 35));
        tabBar.setOpaque(false);

        JPanel fileTab = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setColor(bg());
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(VSCODE_BLUE);
                g2.fillRect(0, 0, getWidth(), 2);
            }
        };
        fileTab.setPreferredSize(new Dimension(130, 35));
        fileTab.setOpaque(false);
        fileTab.setBorder(BorderFactory.createEmptyBorder(4, 8, 0, 8));

        JLabel fileIcon = new JLabel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(JAVA_BLUE);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
                g2.drawString("C", 0, 13);
            }
        };
        fileIcon.setPreferredSize(new Dimension(14, 16));

        JLabel fileName = new JLabel("main.java");
        fileName.setFont(FONT_UI_B);
        fileName.setForeground(labelFg());

        JLabel closeBtn = new JLabel("\u00D7");
        closeBtn.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        closeBtn.setForeground(MUTED_FG);
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { closeBtn.setForeground(labelFg()); }
            @Override public void mouseExited(MouseEvent e)  { closeBtn.setForeground(MUTED_FG); }
        });

        fileTab.add(fileIcon); fileTab.add(fileName); fileTab.add(closeBtn);
        tabBar.add(fileTab, BorderLayout.WEST);

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 6));
        toolbar.setOpaque(false);
        toolbar.add(buildEditorBtn("\uD83D\uDCCB", "Copy",   "Copy to clipboard"));
        toolbar.add(buildEditorBtn("\uD83D\uDCCB", "Paste",  "Paste from clipboard"));
        toolbar.add(buildEditorBtn("\uD83D\uDCE4", "Export", "Export file"));
        toolbar.add(buildEditorBtn("\uD83D\uDDD1", "Clear",  "Clear editor"));
        tabBar.add(toolbar, BorderLayout.EAST);

        codeEditor.setFont(FONT_MONO);
        codeEditor.setBackground(bg());
        codeEditor.setForeground(isDarkMode ? D_ACCENT_BLACK : Color.BLACK);
        codeEditor.setCaretColor(isDarkMode ? EDITOR_CARET : Color.BLACK);
        codeEditor.setSelectionColor(isDarkMode ? EDITOR_SEL_DARK : EDITOR_SEL_LIGHT);
        codeEditor.setLineWrap(false);
        codeEditor.setTabSize(4);
        codeEditor.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));

        codeEditor.addCaretListener(e -> {
            try {
                int pos  = codeEditor.getCaretPosition();
                int line = codeEditor.getLineOfOffset(pos);
                int col  = pos - codeEditor.getLineStartOffset(line);
                lnColLabel.setText("Ln " + (line+1) + ", Col " + (col+1));
            } catch (Exception ignored) {}
        });

        LineNumberComponent lnc = new LineNumberComponent(codeEditor,
            lineNumBg(), lineNumFg(), border());
        JScrollPane scroll = new JScrollPane(codeEditor);
        scroll.setRowHeaderView(lnc);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(bg());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
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
                    g2.setColor(hoverBg());
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 4, 4);
                }
                g2.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 13));
                g2.setColor(MUTED_FG);
                FontMetrics fm = g2.getFontMetrics();
                int iconW = fm.stringWidth(icon);
                g2.setFont(FONT_UI_SM);
                FontMetrics fm2 = g2.getFontMetrics();
                int labelW = fm2.stringWidth(label);
                int totalW = iconW + 5 + labelW;
                int startX = (getWidth() - totalW) / 2;
                int baseY  = (getHeight() + fm2.getAscent() - fm2.getDescent()) / 2 - 1;
                g2.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 13));
                g2.setColor(MUTED_FG);
                g2.drawString(icon, startX, baseY);
                g2.setFont(FONT_UI_SM);
                g2.setColor(MUTED_FG);
                g2.drawString(label, startX + iconW + 5, baseY);
            }
        };
        btn.setPreferredSize(new Dimension(100, 26));
        btn.setOpaque(false); btn.setContentAreaFilled(false);
        btn.setBorderPainted(false); btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setToolTipText(tooltip);

        if (label.equals("Copy")) {
            if (tooltip.toLowerCase().contains("output")) {
                btn.addActionListener(e -> handleCopyOutputAction());
            } else {
                btn.addActionListener(e -> handleCopyAction());
            }
        } else if (label.equals("Paste")) {
            btn.addActionListener(e -> handlePasteAction());
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

        JPanel outputHeader = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(outHdr());
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(border());
                g.drawLine(0, getHeight()-1, getWidth(), getHeight()-1);
            }
        };
        outputHeader.setPreferredSize(new Dimension(0, 35));
        outputHeader.setLayout(new BorderLayout());
        outputHeader.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 10));

        JPanel headerLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        headerLeft.setOpaque(false);

        JPanel dots = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(DOT_RED);    g2.fillOval(0,  6, 10, 10);
                g2.setColor(DOT_YELLOW); g2.fillOval(14, 6, 10, 10);
                g2.setColor(DOT_GREEN);  g2.fillOval(28, 6, 10, 10);
            }
        };
        dots.setOpaque(false);
        dots.setPreferredSize(new Dimension(42, 22));

        JLabel outputTitle = new JLabel("OUTPUT AREA");
        outputTitle.setFont(FONT_HEADER);
        outputTitle.setForeground(MUTED_FG);

        headerLeft.add(dots); headerLeft.add(outputTitle);
        outputHeader.add(headerLeft, BorderLayout.WEST);

        JPanel headerRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 4));
        headerRight.setOpaque(false);
        JButton copyOutBtn = buildEditorBtn("\uD83D\uDCCB", "Copy", "Copy output to clipboard");
        copyOutBtn.setPreferredSize(new Dimension(80, 26));
        headerRight.add(copyOutBtn);
        outputHeader.add(headerRight, BorderLayout.EAST);

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
                    g.fillRect(x, y, w, 2);
                }
                g.setColor(border());
                g.drawLine(x+w, y, x+w, y+h);
            }
            @Override protected void paintFocusIndicator(Graphics g, int tp,
                    Rectangle[] rs, int idx, Rectangle ir, Rectangle tr, boolean sel) {}
            @Override protected int getTabLabelShiftX(int tp, int idx, boolean sel) { return 0; }
            @Override protected int getTabLabelShiftY(int tp, int idx, boolean sel) { return 0; }
            @Override protected void paintContentBorder(Graphics g, int tp, int idx) {}
        });

        runtimeTabPanel       = buildRuntimeOutputPanel();
        astTreeTabPanel       = buildAstTreePanel();
        symbolTableTabPanel   = buildSymbolTablePanel();
        generatedCodeTabPanel = buildGeneratedCodePanel();

        updateOutputTabs();

        p.add(outputHeader, BorderLayout.NORTH);
        p.add(outputTabs,   BorderLayout.CENTER);
        return p;
    }

    private void updateOutputTabs() {
        outputTabs.removeAll();
        if (showRuntimeTab)   addTabToOutput(0, runtimeTabPanel);
        addTabToOutput(1, astTreeTabPanel); // AST remains as core anchor
        if (showSymbolTab)    addTabToOutput(2, symbolTableTabPanel);
        if (showGeneratedTab) addTabToOutput(3, generatedCodeTabPanel);
    }

    private void addTabToOutput(int metaIdx, JPanel panel) {
        outputTabs.addTab(tabData[metaIdx][1], panel);
        int i = outputTabs.indexOfComponent(panel);

        JPanel tabComp = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0)) {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(outputTabs.getSelectedComponent() == panel ? bg() : outTabBg());
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        tabComp.setOpaque(false);
        tabComp.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 10));

        JLabel iconLbl = new JLabel(tabData[metaIdx][0]);
        iconLbl.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 11));
        iconLbl.setForeground(MUTED_FG);
        JLabel nameLbl = new JLabel(tabData[metaIdx][1]);
        nameLbl.setFont(FONT_UI_B);
        nameLbl.setForeground(MUTED_FG);

        tabComp.add(iconLbl); tabComp.add(nameLbl);
        outputTabs.setTabComponentAt(i, tabComp);
    }


    // ── Symbol Table ──────────────────────────────────────────────────────────
    private JPanel buildSymbolTablePanel() {
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setBackground(bg());
        p.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        bar.setOpaque(false);

        filterCombo = new JComboBox<>(new String[]{
            "All Categories", "keyword", "identifier", "constant", "literal", "operator", "punctuation"
        });
        filterCombo.setFont(FONT_UI_SM);
        filterCombo.addActionListener(e -> applySymbolFilter());

        JLabel searchIcon = new JLabel("\uD83D\uDD0D");
        searchIcon.setFont(FONT_ICON);
        JLabel searchLbl  = new JLabel("Search:");
        searchLbl.setFont(FONT_UI_SM); searchLbl.setForeground(MUTED_FG);
        searchField = new JTextField(14);
        searchField.setFont(FONT_UI_SM);
        searchField.setBackground(bgPanel());
        searchField.setForeground(accentDark());
        searchField.setCaretColor(accentDark());
        searchField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(border()),
            BorderFactory.createEmptyBorder(3, 6, 3, 6)));
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { applySymbolFilter(); }
            public void removeUpdate(DocumentEvent e)  { applySymbolFilter(); }
            public void changedUpdate(DocumentEvent e) { applySymbolFilter(); }
        });

        bar.add(new JLabel("Category:")); bar.add(filterCombo);
        bar.add(Box.createHorizontalStrut(10));
        bar.add(searchIcon); bar.add(searchLbl); bar.add(searchField);

        String[] cols = {"Category", "Lexeme", "Attribute-Value"};
        symbolModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable sym = new JTable(symbolModel);
        styleTable(sym);

        symbolSorter = new TableRowSorter<>(symbolModel);
        sym.setRowSorter(symbolSorter);

        JScrollPane scroll = new JScrollPane(sym);
        scroll.setBorder(BorderFactory.createLineBorder(border()));
        scroll.getViewport().setBackground(bg());
        styleScrollBar(scroll.getVerticalScrollBar());

        p.add(bar,    BorderLayout.NORTH);
        p.add(scroll, BorderLayout.CENTER);
        return p;
    }

    private void applySymbolFilter() {
        if (symbolSorter == null) return;
        String text     = searchField != null ? searchField.getText().toLowerCase() : "";
        String category = filterCombo != null ? (String) filterCombo.getSelectedItem() : "All Categories";

        RowFilter<DefaultTableModel, Object> rf = new RowFilter<>() {
            @Override
            public boolean include(Entry<? extends DefaultTableModel, ?> entry) {
                // col 0 = Category, col 1 = Lexeme
                String cat    = entry.getStringValue(0).toLowerCase();
                String lexeme = entry.getStringValue(1).toLowerCase();
                boolean matchesLex = text.isEmpty() || lexeme.contains(text);
                boolean matchesCat = "All Categories".equals(category)
                                     || cat.equals(category.toLowerCase());
                return matchesLex && matchesCat;
            }
        };
        symbolSorter.setRowFilter(rf);
    }

    // ── Generated Code ────────────────────────────────────────────────────────
    private JPanel buildGeneratedCodePanel() {
        JPanel p = new JPanel(new BorderLayout(0, 0));
        p.setBackground(bg());
        p.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        String[] cols = {"Instruction", "Operands"};
        generatedCodeModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        generatedCodeTable = new JTable(generatedCodeModel);
        styleTable(generatedCodeTable);
        generatedCodeTable.getColumnModel().getColumn(0).setPreferredWidth(120);
        generatedCodeTable.getColumnModel().getColumn(0).setMaxWidth(250);

        JScrollPane scroll = new JScrollPane(generatedCodeTable);
        scroll.setBorder(BorderFactory.createLineBorder(border()));
        scroll.getViewport().setBackground(bg());
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
        runtimeArea.setBackground(bg());
        runtimeArea.setForeground(labelFg());
        runtimeArea.setEditable(false);
        runtimeArea.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        runtimeArea.setText("// Program output will appear here after execution\n");

        JScrollPane scroll = new JScrollPane(runtimeArea);
        scroll.setBorder(BorderFactory.createLineBorder(border()));
        scroll.getViewport().setBackground(bg());
        styleScrollBar(scroll.getVerticalScrollBar());
        p.add(scroll, BorderLayout.CENTER);
        return p;
    }

    // =========================================================================
    //  AST Tree Panel
    // =========================================================================
    private JPanel buildAstTreePanel() {
        JPanel p = new JPanel(new BorderLayout(0, 0));
        p.setBackground(bg());
        p.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        toolbar.setOpaque(false);

        JLabel toolbarTitle = new JLabel("Abstract Syntax Tree");
        toolbarTitle.setFont(FONT_UI_B);
        toolbarTitle.setForeground(MUTED_FG);
        toolbar.add(toolbarTitle);

        JButton expandBtn   = buildSmallActionBtn("Expand All");
        JButton collapseBtn = buildSmallActionBtn("Collapse All");
        toolbar.add(Box.createHorizontalStrut(8));
        toolbar.add(expandBtn);
        toolbar.add(collapseBtn);

        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Run compiler to generate AST");
        astTreeModel = new DefaultTreeModel(root);
        astTree = new JTree(astTreeModel);
        astTree.setFont(FONT_MONO_SM);
        astTree.setBackground(bg());
        astTree.setForeground(isDarkMode ? D_ACCENT_BLACK : L_ACCENT_BLACK);
        astTree.setSelectionRow(0);
        astTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        astTree.setShowsRootHandles(true);
        astTree.setRootVisible(true);
        astTree.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer() {
            @Override public Component getTreeCellRendererComponent(JTree tree, Object value,
                    boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
                super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
                setFont(FONT_MONO_SM);
                setBackground(sel ? selectionBg() : bg());
                setForeground(sel ? (isDarkMode ? Color.WHITE : labelFg()) : isDarkMode ? D_ACCENT_BLACK : L_ACCENT_BLACK);
                setBackgroundNonSelectionColor(bg());
                setBackgroundSelectionColor(selectionBg());
                setBorderSelectionColor(selectionBg());

                String text = value.toString();
                if (!sel) {
                    if (text.startsWith("Program") || text.startsWith("Method") || text.startsWith("Function")) {
                        setForeground(SUCCESS_GREEN);
                    } else if (text.startsWith("If") || text.startsWith("While") || text.startsWith("For") || text.startsWith("Return")) {
                        setForeground(AST_CONTROL);
                    } else if (text.startsWith("Assign") || text.startsWith("VarDecl")) {
                        setForeground(INFO_BLUE);
                    } else if (text.startsWith("BinaryOp") || text.startsWith("UnaryOp")) {
                        setForeground(WARNING_YELLOW);
                    } else if (text.startsWith("Literal") || text.startsWith("Number") || text.startsWith("String")) {
                        setForeground(AST_LITERAL);
                    } else if (text.startsWith("Identifier") || text.startsWith("Var")) {
                        setForeground(AST_IDENTIFIER);
                    }
                }

                if (!leaf) {
                    setText("\uD83D\uDCC2 " + text);
                } else {
                    setText("\uD83D\uDCCC " + text);
                }
                return this;
            }
        };
        renderer.setLeafIcon(null);
        renderer.setOpenIcon(null);
        renderer.setClosedIcon(null);
        astTree.setCellRenderer(renderer);

        expandBtn.addActionListener(e -> {
            for (int i = 0; i < astTree.getRowCount(); i++) astTree.expandRow(i);
        });
        collapseBtn.addActionListener(e -> {
            for (int i = astTree.getRowCount() - 1; i > 0; i--) astTree.collapseRow(i);
        });

        JScrollPane scroll = new JScrollPane(astTree);
        scroll.setBorder(BorderFactory.createLineBorder(border()));
        scroll.getViewport().setBackground(bg());
        styleScrollBar(scroll.getVerticalScrollBar());
        styleScrollBar(scroll.getHorizontalScrollBar());

        p.add(toolbar, BorderLayout.NORTH);
        p.add(scroll,  BorderLayout.CENTER);
        return p;
    }

    /**
     * Builds an AST tree from the token list.
     *
     * FIX (AST bug 1 — assignment nodes never popped):
     *   Assignment nodes were pushed onto blockStack via the identifier+= branch
     *   but the semicolon handler only popped non-Block/non-Program nodes — so
     *   assignments accumulated and all subsequent tokens nested inside the first
     *   assignment forever. Fixed by tracking whether we pushed an assignment
     *   node and explicitly popping it on the next semicolon.
     *
     * FIX (AST bug 2 — method declaration never popped):
     *   MethodDecl was pushed onto blockStack but never popped; the closing '}'
     *   consumed the Block pushed by '{' and left the MethodDecl dangling. Fixed
     *   by NOT pushing MethodDecl onto blockStack — it is added as a child and
     *   the '{' that follows will push the method's body Block normally.
     *
     * FIX (dead variables):
     *   Removed the unused local variables currentBlock, currentStmt, and depth
     *   that were left over from an incomplete earlier refactor.
     */
    private DefaultMutableTreeNode buildAstFromTokens(List<compiler.lexer.models.Tokens> tokens) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Program");

        java.util.Deque<DefaultMutableTreeNode> blockStack = new java.util.ArrayDeque<>();
        blockStack.push(root);

        // Track whether the top of the stack is an assignment node that needs
        // to be popped when we hit a semicolon.
        boolean assignmentPushed = false;

        for (int i = 0; i < tokens.size(); i++) {
            compiler.lexer.models.Tokens t = tokens.get(i);
            String lexeme = t.getLexeme();
            String type   = t.getClass().getSimpleName();

            switch (lexeme) {
                case "{" -> {
                    DefaultMutableTreeNode block = new DefaultMutableTreeNode("Block");
                    blockStack.peek().add(block);
                    blockStack.push(block);
                    assignmentPushed = false;
                }
                case "}" -> {
                    if (blockStack.size() > 1) blockStack.pop();
                    assignmentPushed = false;
                }
                case "if" -> {
                    DefaultMutableTreeNode node = new DefaultMutableTreeNode("IfStatement");
                    blockStack.peek().add(node);
                    blockStack.push(node);
                    assignmentPushed = false;
                }
                case "while" -> {
                    DefaultMutableTreeNode node = new DefaultMutableTreeNode("WhileStatement");
                    blockStack.peek().add(node);
                    blockStack.push(node);
                    assignmentPushed = false;
                }
                case "for" -> {
                    DefaultMutableTreeNode node = new DefaultMutableTreeNode("ForStatement");
                    blockStack.peek().add(node);
                    blockStack.push(node);
                    assignmentPushed = false;
                }
                case "return" -> {
                    DefaultMutableTreeNode node = new DefaultMutableTreeNode("ReturnStatement");
                    blockStack.peek().add(node);
                    blockStack.push(node);
                    assignmentPushed = false;
                }
                case ";" -> {
                    // FIX (bug 1): pop only if an assignment node was explicitly pushed,
                    // or if the top is a control-flow statement (IfStatement etc.).
                    if (blockStack.size() > 1) {
                        String topName = blockStack.peek().toString();
                        if (assignmentPushed
                                || topName.startsWith("If") || topName.startsWith("While")
                                || topName.startsWith("For") || topName.startsWith("Return")) {
                            blockStack.pop();
                        }
                    }
                    assignmentPushed = false;
                }
                default -> {
                    if (isTypeKeyword(lexeme) && i + 1 < tokens.size()) {
                        String nextLexeme = tokens.get(i + 1).getLexeme();
                        if (!nextLexeme.equals("(")) {
                            // Variable declaration
                            DefaultMutableTreeNode decl = new DefaultMutableTreeNode("VarDecl [" + lexeme + "]");
                            DefaultMutableTreeNode nameNode = new DefaultMutableTreeNode("Identifier: " + nextLexeme);
                            decl.add(nameNode);
                            if (i + 2 < tokens.size() && tokens.get(i + 2).getLexeme().equals("=")) {
                                decl.setUserObject("VarDecl [" + lexeme + "] = "
                                    + (i + 3 < tokens.size() ? tokens.get(i + 3).getLexeme() : "?"));
                            }
                            blockStack.peek().add(decl);
                            i++; // consume the identifier
                        } else {
                            // FIX (bug 2): Do NOT push MethodDecl onto blockStack.
                            // Add it as a child only — the '{' that follows will push the body Block.
                            DefaultMutableTreeNode method = new DefaultMutableTreeNode(
                                "MethodDecl: " + nextLexeme);
                            blockStack.peek().add(method);
                            i++; // consume the method name
                        }
                    } else if (type.equalsIgnoreCase("identifier") && i + 1 < tokens.size()
                               && tokens.get(i + 1).getLexeme().equals("=")) {
                        // Assignment statement — push and mark for semicolon pop
                        String rhs = i + 2 < tokens.size() ? tokens.get(i + 2).getLexeme() : "?";
                        DefaultMutableTreeNode assign = new DefaultMutableTreeNode(
                            "Assign: " + lexeme + " = " + rhs);
                        blockStack.peek().add(assign);
                        blockStack.push(assign);
                        assignmentPushed = true;
                    } else if (type.equalsIgnoreCase("operator")
                               && (lexeme.equals("+") || lexeme.equals("-")
                                   || lexeme.equals("*") || lexeme.equals("/"))) {
                        DefaultMutableTreeNode op = new DefaultMutableTreeNode("BinaryOp: " + lexeme);
                        blockStack.peek().add(op);
                    } else if (type.equalsIgnoreCase("constant") || type.equalsIgnoreCase("literal")) {
                        DefaultMutableTreeNode lit = new DefaultMutableTreeNode(
                            "Literal [" + type + "]: " + lexeme);
                        blockStack.peek().add(lit);
                    }
                }
            }
        }
        return root;
    }

    private boolean isTypeKeyword(String lexeme) {
        return switch (lexeme) {
            case "int", "float", "double", "boolean", "char", "String",
                 "void", "long", "short", "byte" -> true;
            default -> false;
        };
    }

    // =========================================================================
    //  CONSOLE
    //  FIX (appendConsole color): console is now a JTextPane with StyledDocument
    //  so each line can be rendered in its own color.
    // =========================================================================
    private JPanel buildConsole() {
        JPanel p = new JPanel(new BorderLayout(0, 0));
        p.setBackground(consoleBg());

        JPanel strip = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(bgLight());
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(border());
                g.drawLine(0, getHeight()-1, getWidth(), getHeight()-1);
            }
        };
        strip.setPreferredSize(new Dimension(0, 35));
        strip.setOpaque(false);

        tabsLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tabsLeft.setOpaque(false);

        consoleTabBtn   = buildConsoleTabLbl(">_  Console",        "Console");
        errorCountLabel = buildConsoleTabLbl("\u26A0  Errors (0)",    "Errors");
        warnCountLabel  = buildConsoleTabLbl("\u24D8  Warnings (0)", "Warnings");

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

        JPanel cRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
        cRight.setOpaque(false);

        JLabel clearLbl = new JLabel("\uD83D\uDDD1 Clear");
        clearLbl.setFont(FONT_UI_SM);
        clearLbl.setForeground(MUTED_FG);
        clearLbl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        clearLbl.setToolTipText("Clear console output");
        clearLbl.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (consolePane != null) {
                    try { consoleDoc.remove(0, consoleDoc.getLength()); }
                    catch (BadLocationException ignored) {}
                }
                errorCount = 0; warnCount = 0;
                errorListModel.clear();
                warningListModel.clear();
                updateConsoleCounts();
            }
            @Override public void mouseEntered(MouseEvent e) { clearLbl.setForeground(labelFg()); }
            @Override public void mouseExited(MouseEvent e)  { clearLbl.setForeground(MUTED_FG); }
        });
        cRight.add(clearLbl);

        strip.add(tabsLeft, BorderLayout.WEST);
        strip.add(cRight,   BorderLayout.EAST);

        // FIX (appendConsole color): use JTextPane so we can apply per-line color
        consoleDoc  = new DefaultStyledDocument();
        consolePane = new JTextPane(consoleDoc);
        consolePane.setFont(FONT_MONO_SM);
        consolePane.setBackground(consoleBg());
        consolePane.setForeground(consoleTxt());
        consolePane.setEditable(false);
        consolePane.setCaretColor(EDITOR_CARET);
        consolePane.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));

        String now = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        appendConsole("\u2713 [" + now + "] Compiler System ready.", SUCCESS_GREEN);
        appendConsole("\u2713 [" + now + "] Welcome to console! Compiler logs appear here.", SUCCESS_GREEN);



        consoleLayout    = new CardLayout();
        consoleCardPanel = new JPanel(consoleLayout);
        consoleCardPanel.setOpaque(false);

        consoleCardPanel.add(createScrollableConsole(consolePane), "Console");
        consoleCardPanel.add(createScrollableList(errorList,   "Error"),   "Errors");
        consoleCardPanel.add(createScrollableList(warningList, "Warning"), "Warnings");

        errorList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                String selected = errorList.getSelectedValue();
                if (selected != null) highlightLineFromError(selected);
            }
        });
        warningList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                String selected = warningList.getSelectedValue();
                if (selected != null) showWarningInConsole(selected);
            }
        });

        p.add(strip,            BorderLayout.NORTH);
        p.add(consoleCardPanel, BorderLayout.CENTER);
        return p;
    }

    private JLabel buildConsoleTabLbl(String text, String cardName) {
        JLabel lbl = new JLabel(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean isActive = cardName.equals(activeConsoleTab);
                if (isActive) {
                    g2.setColor(consoleBg());
                    g2.fillRect(0, 0, getWidth(), getHeight());
                    g2.setColor(VSCODE_BLUE);
                    g2.fillRect(0, 0, getWidth(), 2);
                }
                setForeground(isActive ? labelFg() : MUTED_FG);
                super.paintComponent(g);
            }
        };
        lbl.setFont(FONT_UI_SM);
        lbl.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        lbl.setOpaque(false);
        lbl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return lbl;
    }

    private JScrollPane createScrollableConsole(JTextPane pane) {
        JScrollPane sp = new JScrollPane(pane);
        sp.setBorder(null);
        sp.getViewport().setBackground(consoleBg());
        styleScrollBar(sp.getVerticalScrollBar());
        return sp;
    }

    private JScrollPane createScrollableList(JList<String> list, String type) {
        list.setFont(FONT_MONO_SM);
        list.setBackground(consoleBg());
        list.setForeground(type.equals("Error") ? ERROR_RED : WARNING_YELLOW);
        list.setSelectionBackground(isDarkMode ? LIST_SEL_DARK : LIST_SEL_LIGHT);
        list.setSelectionForeground(type.equals("Error") ? ERROR_RED : WARNING_YELLOW);
        list.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));

        JScrollPane sp = new JScrollPane(list);
        sp.setBorder(null);
        sp.getViewport().setBackground(consoleBg());
        styleScrollBar(sp.getVerticalScrollBar());
        return sp;
    }

    private void switchConsoleCard(String cardName) {
        activeConsoleTab = cardName;
        consoleLayout.show(consoleCardPanel, cardName);
        updateConsoleCounts();
        consoleTabBtn.repaint();
        errorCountLabel.repaint();
        warnCountLabel.repaint();
    }

    /**
     * FIX (appendConsole color): now actually applies the supplied color to each
     * appended line by inserting styled text into the StyledDocument.
     */
    private void appendConsole(String text, Color color) {
        if (consolePane == null || consoleDoc == null) return;
        try {
            SimpleAttributeSet attrs = new SimpleAttributeSet();
            StyleConstants.setForeground(attrs, color);
            StyleConstants.setFontFamily(attrs, "Consolas");
            StyleConstants.setFontSize(attrs, 11);
            consoleDoc.insertString(consoleDoc.getLength(), text + "\n", attrs);
            // Auto-scroll to bottom
            consolePane.setCaretPosition(consoleDoc.getLength());
        } catch (BadLocationException ignored) {}
    }

    private void highlightLineFromError(String errorMsg) {
        try {
            java.util.regex.Pattern pat = java.util.regex.Pattern.compile("Line (\\d+)");
            java.util.regex.Matcher m   = pat.matcher(errorMsg);
            if (m.find()) {
                int line  = Integer.parseInt(m.group(1));
                int start = codeEditor.getLineStartOffset(line - 1);
                int end   = codeEditor.getLineEndOffset(line - 1);
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
        }
        if (warnCountLabel != null) {
            warnCountLabel.setText("\u24D8  Warnings (" + warnCount + ")");
        }
        if (consoleTabBtn   != null) consoleTabBtn.repaint();
        if (errorCountLabel != null) errorCountLabel.repaint();
        if (warnCountLabel  != null) warnCountLabel.repaint();
    }

    // =========================================================================
    //  ACTIONS
    // =========================================================================
    private void handleNewFile() {
        codeEditor.setText("void main() {\n\n}");
        if (symbolModel        != null) symbolModel.setRowCount(0);
        if (generatedCodeModel != null) generatedCodeModel.setRowCount(0);
        if (runtimeArea        != null) runtimeArea.setText("// Program output will appear here after execution\n");
        errorListModel.clear();
        warningListModel.clear();
        errorCount = 0; warnCount = 0;
        SymbolTable.getInstance().reset();
        ErrorHandler.clear();
        resetAstTree("New file — run compiler to generate AST.");
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
        StringSelection sel = new StringSelection(code);
        Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
        cb.setContents(sel, sel);
        logAction("Editor content copied to clipboard.", INFO_BLUE);
    }

    private void handlePasteAction() {
        codeEditor.paste();
        logAction("Content pasted into editor.", INFO_BLUE);
    }

    private void handleCopyOutputAction() {
        String content = runtimeArea.getText();
        if (content.isEmpty() || content.startsWith("//")) return;
        StringSelection sel = new StringSelection(content);
        Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
        cb.setContents(sel, sel);
        logAction("Runtime output copied to clipboard.", INFO_BLUE);
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
        if (symbolModel        != null) symbolModel.setRowCount(0);
        if (generatedCodeModel != null) generatedCodeModel.setRowCount(0);
        if (runtimeArea        != null) runtimeArea.setText("");
        errorListModel.clear();
        warningListModel.clear();
        errorCount = 0; warnCount = 0;
        SymbolTable.getInstance().reset();
        ErrorHandler.clear();
        resetAstTree("Editor cleared — run compiler to generate AST.");
        updateConsoleCounts();
        logAction("Editor and counters cleared.", INFO_BLUE);
    }

    private void logAction(String msg, Color color) {
        String now = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        appendConsole("[" + now + "] " + msg, color);
    }

    private void showSettingsDialog() {
        JDialog dialog = new JDialog(this, "Settings", true);
        dialog.setLayout(new BorderLayout());
        dialog.setBackground(bg());

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        content.setBackground(bg());

        JLabel title = new JLabel("Hide/Unhide Tabs");
        title.setFont(FONT_UI_B); title.setForeground(labelFg());
        content.add(title); content.add(Box.createVerticalStrut(15));

        content.add(createSettingsCheckbox("Console", showConsoleTab, e -> {
            showConsoleTab = ((JCheckBox)e.getSource()).isSelected();
            consoleTabBtn.setVisible(showConsoleTab); tabsLeft.revalidate();
        }));
        content.add(createSettingsCheckbox("Errors", showErrorsTab, e -> {
            showErrorsTab = ((JCheckBox)e.getSource()).isSelected();
            errorCountLabel.setVisible(showErrorsTab); tabsLeft.revalidate();
        }));
        content.add(createSettingsCheckbox("Warnings", showWarningsTab, e -> {
            showWarningsTab = ((JCheckBox)e.getSource()).isSelected();
            warnCountLabel.setVisible(showWarningsTab); tabsLeft.revalidate();
        }));
        content.add(createSettingsCheckbox("Runtime Output", showRuntimeTab, e -> {
            showRuntimeTab = ((JCheckBox)e.getSource()).isSelected(); updateOutputTabs();
        }));
        content.add(createSettingsCheckbox("Symbol Table", showSymbolTab, e -> {
            showSymbolTab = ((JCheckBox)e.getSource()).isSelected(); updateOutputTabs();
        }));
        content.add(createSettingsCheckbox("Generated Code", showGeneratedTab, e -> {
            showGeneratedTab = ((JCheckBox)e.getSource()).isSelected(); updateOutputTabs();
        }));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.setBackground(bgLight());
        JButton applyBtn = new JButton("Apply");
        applyBtn.addActionListener(e -> dialog.dispose());
        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dialog.dispose());
        actions.add(applyBtn); actions.add(closeBtn);

        dialog.add(content, BorderLayout.CENTER);
        dialog.add(actions, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setSize(300, 400);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private JCheckBox createSettingsCheckbox(String text, boolean selected, java.awt.event.ActionListener al) {
        JCheckBox cb = new JCheckBox(text, selected);
        cb.setFont(FONT_UI);
        cb.setForeground(labelFg());
        cb.setOpaque(false);
        cb.setFocusPainted(false);
        cb.addActionListener(al);
        return cb;
    }

    private void resetAstTree(String message) {
        if (astTreeModel != null && astTree != null) {
            DefaultMutableTreeNode placeholder = new DefaultMutableTreeNode(message);
            astTreeModel.setRoot(placeholder);
            astTree.repaint();
        }
    }

    /**
     * FIX (thread safety — errorCount/warnCount):
     *   errorCount and warnCount are now only written inside SwingUtilities.invokeLater(),
     *   ensuring they are always accessed on the EDT, eliminating the data race.
     *
     * The background thread computes results only; all Swing mutations happen on the EDT.
     */
    private void handleRunAction() {
        String code = codeEditor.getText();
        if (code.trim().isEmpty()) return;

        if (runtimeArea        != null) runtimeArea.setText("");
        if (consolePane        != null) {
            try { consoleDoc.remove(0, consoleDoc.getLength()); }
            catch (BadLocationException ignored) {}
        }
        if (generatedCodeModel != null) generatedCodeModel.setRowCount(0);
        if (symbolModel        != null) symbolModel.setRowCount(0);
        errorListModel.clear();
        warningListModel.clear();
        errorCount = 0; warnCount = 0;
        SymbolTable.getInstance().reset();
        ErrorHandler.clear();
        resetAstTree("Compiling...");
        updateConsoleCounts();

        String now = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        appendConsole("\u2699 [" + now + "] Compilation started...", INFO_BLUE);

        new Thread(() -> {
            try {
                long startTime = System.nanoTime();

                CompilerPipeline pipeline = new CompilerPipeline();
                CompilerPipeline.CompileResult compileResult = pipeline.compile(code);

                long durationMs = (System.nanoTime() - startTime) / 1_000_000;
                String timeStr  = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

                if (compileResult.errors.isEmpty()) {
                    ByteArrayOutputStream buffer      = new ByteArrayOutputStream();
                    PrintStream           originalOut = System.out;
                    PrintStream           originalErr = System.err;

                    String runtimeOutput;
                    try (PrintStream capture = new PrintStream(buffer, true)) {
                        System.setOut(capture);
                        System.setErr(capture);
                        Interpreter interpreter = new Interpreter();
                        interpreter.execute(compileResult.instructions);
                    } finally {
                        System.setOut(originalOut);
                        System.setErr(originalErr);
                    }
                    runtimeOutput = buffer.toString();

                    // Build AST off-EDT (DefaultMutableTreeNode is not a Swing component)
                    final DefaultMutableTreeNode astRoot =
                        buildAstFromTokens(compileResult.tokens);

                    final String finalOutput = runtimeOutput;
                    final long   finalMs     = durationMs;

                    SwingUtilities.invokeLater(() -> {
                        appendConsole("\u2713 [" + timeStr + "] Compilation successful (" + finalMs + "ms)", SUCCESS_GREEN);

                        for (compiler.lexer.models.Tokens t : compileResult.tokens) {
                            String category = t.getClass().getSimpleName().toLowerCase();
                            symbolModel.addRow(new Object[]{
                                category,
                                t.getLexeme(),
                                getAttributeValue(t)
                            });
                        }

                        generatedCodeModel.setRowCount(0);
                        for (Instruction ins : compileResult.instructions) {
                            generatedCodeModel.addRow(new Object[]{
                                ins.getOpcode().name(),
                                ins.toString()
                            });
                        }

                        astTreeModel.setRoot(astRoot);
                        for (int i = 0; i < astTree.getRowCount(); i++) astTree.expandRow(i);

                        runtimeArea.setText(finalOutput.isEmpty() ? "<no runtime output>" : finalOutput);

                        appendConsole("\u2713 [" + timeStr + "] Execution completed.", SUCCESS_GREEN);
                        outputTabs.setSelectedIndex(0);
                    });

                } else {
                    // Capture counts locally on the background thread; apply on EDT
                    int localErrors = 0, localWarnings = 0;
                    for (String err : compileResult.errors) {
                        if (err.toLowerCase().contains("warning")) localWarnings++;
                        else                                        localErrors++;
                    }
                    final int finalErrors   = localErrors;
                    final int finalWarnings = localWarnings;

                    SwingUtilities.invokeLater(() -> {
                        // FIX (thread safety): errorCount/warnCount written only on EDT
                        errorCount = finalErrors;
                        warnCount  = finalWarnings;

                        String logTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                        for (String err : compileResult.errors) {
                            String timestamped = "[" + logTime + "] " + err;
                            if (err.toLowerCase().contains("warning")) {
                                warningListModel.addElement(timestamped);
                            } else {
                                errorListModel.addElement(timestamped);
                            }
                        }

                        appendConsole("\u2716 [" + logTime + "] Compilation failed with "
                            + errorCount + " error(s), " + warnCount + " warning(s).", ERROR_RED);
                        updateConsoleCounts();
                        if (errorCount > 0) switchConsoleCard("Errors");
                        else if (warnCount > 0) switchConsoleCard("Warnings");

                        runtimeArea.setText("<runtime skipped due to compile errors>");
                        resetAstTree("Compilation failed — fix errors and re-run.");
                    });
                }
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() ->
                    appendConsole("\u26A0 [Internal Error] " + ex.getMessage(), ERROR_RED));
            }
        }).start();
    }

    // =========================================================================
    //  STATUS BAR
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

        JLabel branchLbl = new JLabel("\uD83D\uDD00 main");
        branchLbl.setFont(FONT_UI_SM); branchLbl.setForeground(Color.WHITE);

        JLabel enc  = new JLabel("UTF-8");
        enc.setFont(FONT_UI_SM); enc.setForeground(Color.WHITE);

        JLabel lang = new JLabel("Java");
        lang.setFont(FONT_UI_SM); lang.setForeground(Color.WHITE);

        left.add(branchLbl); left.add(enc); left.add(lang);

        JPanel right2 = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 3));
        right2.setOpaque(false);

        lnColLabel.setFont(FONT_UI_SM);
        lnColLabel.setForeground(Color.WHITE);
        right2.add(lnColLabel);

        JLabel spaces = new JLabel("Spaces: 4");
        spaces.setFont(FONT_UI_SM); spaces.setForeground(Color.WHITE);

        JLabel modeLabel = new JLabel(isDarkMode ? "\uD83C\uDF19 Dark" : "\u2600 Light");
        modeLabel.setFont(FONT_UI_SM); modeLabel.setForeground(Color.WHITE);

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
        table.setSelectionBackground(selectionBg());
        table.setSelectionForeground(isDarkMode ? Color.WHITE : labelFg());
        table.setShowVerticalLines(true);
        table.setShowHorizontalLines(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setFillsViewportHeight(true);

        JTableHeader hdr = table.getTableHeader();
        hdr.setFont(FONT_HEADER);
        hdr.setBackground(tblHdrBg());
        hdr.setForeground(MUTED_FG);
        hdr.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, border()));
        hdr.setReorderingAllowed(false);
        hdr.setPreferredSize(new Dimension(0, 28));

        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(
                    JTable t, Object val, boolean sel, boolean foc, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, val, sel, foc, row, col);
                if (!sel) {
                    c.setBackground(row % 2 == 0 ? tblRowAlt() : bg());
                    c.setForeground(accentDark());
                }
                setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
                return c;
            }
        });
    }

    /**
     * FIX (getAttributeValue robustness):
     *   Now uses a fixed set of known token class-name patterns via contains() after
     *   uppercasing, and strips multiple common suffixes ("TOKEN", "TOK", "TYPE").
     *   Unknown token types still return "-" but won't silently mismatch due to a
     *   single-suffix assumption.
     */
    private String getAttributeValue(compiler.lexer.models.Tokens t) {
        // Normalize: uppercase, then strip common class-name suffixes
        String category = t.getClass().getSimpleName().toUpperCase()
            .replace("TOKEN", "")
            .replace("TOK",   "")
            .replace("TYPE",  "")
            .trim();
        String lexeme = t.getLexeme();

        if (category.contains("IDENTIFIER")) {
            return "Symbol Table Ptr: 0x" + Integer.toHexString(System.identityHashCode(lexeme)).toUpperCase();
        } else if (category.contains("CONSTANT")) {
            return "Numeric Value: " + lexeme;
        } else if (category.contains("LITERAL")) {
            return lexeme.startsWith("'") ? "Char Literal" : "String Literal";
        } else if (category.contains("OPERATOR")) {
            return getOperatorType(lexeme);
        } else if (category.contains("KEYWORD")) {
            return "Reserved Word";
        } else if (category.contains("PUNCTUATION") || category.contains("PUNCT")) {
            return "Delimiter";
        }
        return "-";
    }

    /**
     * FIX (regex hyphen): hyphen is now the first character inside each character
     * class so it is unambiguously a literal, not a range operator.
     */
    private String getOperatorType(String op) {
        if (op.matches("[-+*/%]"))           return "Arithmetic Operator";
        if (op.matches("==|!=|<|>|<=|>="))   return "Relational Operator";
        if (op.matches("&&|\\|\\||!"))       return "Logical Operator";
        if (op.matches("=|\\+=|-=|\\*=|/=")) return "Assignment Operator";
        return "Special Operator";
    }

    private void styleScrollBar(JScrollBar sb) {
        sb.setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
            @Override protected void configureScrollBarColors() {
                this.thumbColor = isDarkMode ? SCROLLTHUMB_DARK  : SCROLLTHUMB_LIGHT;
                this.trackColor = isDarkMode ? SCROLLTRACK_DARK  : SCROLLTRACK_LIGHT;
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

    private JButton buildSmallActionBtn(String text) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bgC = getModel().isRollover() ? hoverBg() : bgPanel();
                g2.setColor(bgC);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 4, 4);
                g2.setColor(border());
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 4, 4);
                g2.setFont(FONT_UI_SM);
                g2.setColor(MUTED_FG);
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
            setFont(ta.getFont());
            setBackground(bg);
            setForeground(fg);
            setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, border));
            ta.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                public void insertUpdate(javax.swing.event.DocumentEvent e)  { repaint(); revalidate(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e)  { repaint(); revalidate(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) { repaint(); revalidate(); }
            });
        }

        @Override public Dimension getPreferredSize() {
            int lines  = textArea.getLineCount();
            int lineH  = textArea.getFontMetrics(textArea.getFont()).getHeight();
            int height = Math.max(lines * lineH, 100);
            String maxStr = String.valueOf(Math.max(lines, 999));
            FontMetrics fm = getFontMetrics(getFont());
            return new Dimension(fm.stringWidth(maxStr) + PAD * 2, height);
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(lnBg);
            g2.fillRect(0, 0, getWidth(), getHeight());

            g2.setFont(getFont());
            FontMetrics fm   = g2.getFontMetrics();
            Rectangle   clip = g.getClipBounds();
            int lineH = textArea.getFontMetrics(textArea.getFont()).getHeight();

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