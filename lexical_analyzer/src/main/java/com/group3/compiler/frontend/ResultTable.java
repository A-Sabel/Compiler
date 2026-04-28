    package com.group3.compiler.frontend;

    import javax.swing.*;
    import javax.swing.table.*;
    import java.awt.*;
    import java.util.List;
    import java.util.Map;

    import com.group3.compiler.backend.models.Tokens;

    /**
     * VIEW COMPONENT: Dynamic Token Table
     *
     * Wraps two styled JTables that match the 7-category Lexer model:
     *
     *   LexemeTable  — one row per token in the stream
     *                  Columns: Lexeme | Category | Line | Col | Occurrences
     *
     *   UniqueTable  — one row per distinct token category (summary)
     *                  Columns: Category | Count
     *
     * Both tables are pre-styled to the "Soft Glass" palette used in MainGUI.
     * MainGUI can call:
     *
     *   populate(tokenStream, lexemeCounts, categoryCounts)
     *       → fills both tables in one shot after Lexer runs
     *
     *   getLexemeTable()  / getUniqueTable()
     *       → hand the raw JTable to MainGUI so it can drop them into the
     *         existing JScrollPane viewports (LexemeTableScrollPane / UniqueTableScrollPane)
     *
     *   getLexemeScrollPane() / getUniqueScrollPane()
     *       → alternative: hand ready-made scroll panes to MainGUI
     *
     *   clear()
     *       → wipe both tables on Clear button
     */
    public class ResultTable {

        // ── Palette ───────────────────────────────────────────────────────────────
        private static final Color BG_TABLE      = new Color(240, 242, 247);
        private static final Color BG_HEADER     = new Color(216, 222, 233);
        private static final Color FG_HEADER     = new Color( 44,  58,  75);
        private static final Color SEL_BG        = new Color( 74, 111, 165);
        private static final Color SEL_FG        = Color.WHITE;
        private static final Color STRIPE_EVEN   = new Color(240, 242, 247);
        private static final Color STRIPE_ODD    = new Color(232, 235, 243);
        private static final Color BORDER_HEADER = new Color(200, 208, 222);

        private static final Font TABLE_FONT  = new Font("Franklin Gothic Medium", Font.PLAIN, 12);
        private static final Font HEADER_FONT = new Font("Franklin Gothic Medium", Font.PLAIN, 12);

        // ── Column definitions ────────────────────────────────────────────────────
        private static final String[] LEXEME_COLUMNS = { "Lexeme", "Category", "Line", "Col", "Occurrences" };
        private static final String[] UNIQUE_COLUMNS = { "Category", "Count" };
    private static final String[] REGEX_COLUMNS  = { "Regular Expression", "Token", "Attribute-Value" };

        // ── Components ────────────────────────────────────────────────────────────
        private final JTable      lexemeTable;
        private final JTable      uniqueTable;
    private final JTable      regexTable;
        private final JScrollPane lexemeScrollPane;
        private final JScrollPane uniqueScrollPane;
        private final JScrollPane regexScrollPane;

        private final DefaultTableModel lexemeModel;
        private final DefaultTableModel uniqueModel;
    private final DefaultTableModel regexModel;

        // =========================================================================
        //  Constructor
        // =========================================================================

        public ResultTable() {
            lexemeModel = buildModel(LEXEME_COLUMNS);
            uniqueModel = buildModel(UNIQUE_COLUMNS);
            regexModel  = buildModel(REGEX_COLUMNS);

            lexemeTable = buildTable(lexemeModel);
            uniqueTable = buildTable(uniqueModel);
            regexTable  = buildTable(regexModel);

            // Column widths for LexemeTable
            setColumnWidths(lexemeTable, new int[]{ 200, 180, 60, 60, 100 });
            // Column widths for UniqueTable
            setColumnWidths(uniqueTable, new int[]{ 200, 80 });
            // Column widths for RegexTable
            setColumnWidths(regexTable, new int[]{ 300, 150, 250 });

            regexScrollPane = buildScrollPane(regexTable);

            lexemeScrollPane = buildScrollPane(lexemeTable);
            uniqueScrollPane = buildScrollPane(uniqueTable);
        }

        // =========================================================================
        //  PUBLIC API
        // =========================================================================

        /**
         * Populates both tables from the data that the Lexer + Main already compute.
         *
         * @param tokenStream     ordered list of all tokens from Lexer.tokenize()
         * @param lexemeCounts    map of  lexeme  → total occurrences in the source
         * @param categoryCounts  map of  category → total tokens in that category
         */
        public void populate(List<Tokens> tokenStream,
                            Map<String, Integer> lexemeCounts,
                            Map<String, Integer> categoryCounts) {

            // ── LexemeTable ───────────────────────────────────────────────────────
            lexemeModel.setRowCount(0);
            for (Tokens t : tokenStream) {
                String category   = t.getClass().getSimpleName().toUpperCase();
                int    occurrences = lexemeCounts.getOrDefault(t.getLexeme(), 1);

                lexemeModel.addRow(new Object[]{
                        t.getLexeme(),
                        category,
                        t.getLine(),
                        t.getColumn(),
                        occurrences
                });
            }

            // ── UniqueTable ───────────────────────────────────────────────────────
            uniqueModel.setRowCount(0);
            for (Map.Entry<String, Integer> entry : categoryCounts.entrySet()) {
                uniqueModel.addRow(new Object[]{ entry.getKey(), entry.getValue() });
            }

            // ── RegexTable ────────────────────────────────────────────────────────
            regexModel.setRowCount(0);
            for (Tokens t : tokenStream) {
                String category = t.getClass().getSimpleName().toLowerCase();
                String lexeme   = t.getLexeme();
                String attr     = getAttributeValue(t);

                regexModel.addRow(new Object[]{
                    category,
                    lexeme,
                    attr
                });
            }
        }

        private String getAttributeValue(Tokens t) {
            String category = t.getClass().getSimpleName().toUpperCase();
            String lexeme   = t.getLexeme();

            return switch (category) {
                case "IDENTIFIER"   -> "Symbol Table Pointer: " + Integer.toHexString(System.identityHashCode(lexeme)).toUpperCase();
                case "CONSTANT"     -> "Numeric Value: " + lexeme;
                case "LITERAL"      -> lexeme.startsWith("'") ? "Char Literal" : "String Literal";
                case "OPERATOR"     -> getOperatorType(lexeme);
                case "PUNCTUATION"  -> "-";
                case "KEYWORD"      -> "-";
                default             -> "-";
            };
        }

        private String getOperatorType(String op) {
            if (op.matches("[+\\-*/%]")) return "Arithmetic Operator";
            if (op.matches("==|!=|<|>|<=|>=")) return "Relational Operator";
            if (op.matches("&&|\\|\\||!")) return "Logical Operator";
            if (op.matches("=|\\+=|-=|\\*=|/=")) return "Assignment Operator";
            return "Special Operator";
        }

        /** Clears both tables (called by the Clear button). */
        public void clear() {
            lexemeModel.setRowCount(0);
            uniqueModel.setRowCount(0);
            regexModel.setRowCount(0);
        }

        // ── Getters ───────────────────────────────────────────────────────────────

        /** Raw JTable — pass to LexemeTableScrollPane.setViewportView() in MainGUI. */
        public JTable getLexemeTable()          { return lexemeTable; }

        /** Raw JTable — pass to UniqueTableScrollPane.setViewportView() in MainGUI. */
        public JTable getUniqueTable()          { return uniqueTable; }

        /** Raw JTable for the Regex analysis. */
        public JTable getRegexTable()           { return regexTable; }

        /** Ready-made scroll pane containing the LexemeTable. */
        public JScrollPane getLexemeScrollPane() { return lexemeScrollPane; }

        /** Ready-made scroll pane containing the UniqueTable. */
        public JScrollPane getUniqueScrollPane() { return uniqueScrollPane; }

        /** Ready-made scroll pane containing the RegexTable. */
        public JScrollPane getRegexScrollPane()  { return regexScrollPane; }

        // =========================================================================
        //  PRIVATE HELPERS
        // =========================================================================


        // =========================================================================
        //  PRIVATE BUILDERS
        // =========================================================================

        /** Creates a non-editable DefaultTableModel with the given column headers. */
        private DefaultTableModel buildModel(String[] columns) {
            return new DefaultTableModel(new Object[][]{}, columns) {
                @Override
                public boolean isCellEditable(int row, int col) { return false; }

                // Right-align numeric columns (Line, Col, Occurrences / Count)
                @Override
                public Class<?> getColumnClass(int col) {
                    String name = getColumnName(col);
                    if (name.equals("Line") || name.equals("Col") || 
                        name.equals("Occurrences") || name.equals("Count")) return Integer.class;
                    return String.class;
                }
            };
        }

        /** Builds a fully styled JTable from the given model. */
        private JTable buildTable(DefaultTableModel model) {
            JTable table = new JTable(model) {
                // Alternating row stripe renderer
                @Override
                public Component prepareRenderer(TableCellRenderer renderer, int row, int col) {
                    Component c = super.prepareRenderer(renderer, row, col);
                    if (!isRowSelected(row)) {
                        c.setBackground(row % 2 == 0 ? STRIPE_EVEN : STRIPE_ODD);
                    }
                    return c;
                }
            };

            table.setFont(TABLE_FONT);
            table.setBackground(BG_TABLE);
            table.setForeground(new Color(30, 40, 55));
            table.setRowHeight(24);
            table.setShowGrid(false);
            table.setIntercellSpacing(new Dimension(0, 0));
            table.setSelectionBackground(SEL_BG);
            table.setSelectionForeground(SEL_FG);
            table.setFillsViewportHeight(true);
            table.setAutoCreateRowSorter(true);   // click column header to sort

            styleHeader(table);
            return table;
        }

        /** Applies the Soft Glass header style to a table. */
        private void styleHeader(JTable table) {
            JTableHeader header = table.getTableHeader();
            header.setFont(HEADER_FONT);
            header.setBackground(BG_HEADER);
            header.setForeground(FG_HEADER);
            header.setReorderingAllowed(false);
            header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_HEADER));

            // Centre-align header cells
            DefaultTableCellRenderer headerRenderer = new DefaultTableCellRenderer();
            headerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
            headerRenderer.setBackground(BG_HEADER);
            headerRenderer.setForeground(FG_HEADER);
            headerRenderer.setFont(HEADER_FONT);
            for (int i = 0; i < table.getColumnCount(); i++) {
                table.getColumnModel().getColumn(i).setHeaderRenderer(headerRenderer);
            }

            // Right-align numeric columns, left-align text columns
            DefaultTableCellRenderer rightAlign = new DefaultTableCellRenderer();
            rightAlign.setHorizontalAlignment(SwingConstants.RIGHT);
            DefaultTableCellRenderer leftAlign  = new DefaultTableCellRenderer();
            leftAlign.setHorizontalAlignment(SwingConstants.LEFT);

            for (int i = 0; i < table.getColumnCount(); i++) {
                Class<?> colClass = table.getModel().getColumnClass(i);
                boolean isNumeric = colClass == Integer.class;
                table.getColumnModel().getColumn(i)
                        .setCellRenderer(isNumeric ? rightAlign : leftAlign);
            }
        }

        /** Builds a borderless scroll pane containing the given table. */
        private JScrollPane buildScrollPane(JTable table) {
            JScrollPane sp = new JScrollPane(table);
            sp.setBorder(null);
            sp.getViewport().setBackground(BG_TABLE);
            return sp;
        }

        /** Sets preferred widths for each column in order. */
        private void setColumnWidths(JTable table, int[] widths) {
            for (int i = 0; i < widths.length && i < table.getColumnCount(); i++) {
                table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
            }
        }
    }
