package com.grip.graphql.ui;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.ui.Theme;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.AbstractBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class GripTheme {

    public static final String FONT_FAMILY = "SF Pro Display";
    public static final String CODE_FONT_FAMILY = "SF Mono";
    public static final int FONT_SIZE_SMALL = 11;
    public static final int FONT_SIZE_NORMAL = 13;
    public static final int FONT_SIZE_LARGE = 15;
    public static final int FONT_SIZE_TITLE = 18;
    public static final int FONT_SIZE_HEADER = 22;

    public static final int SPACING_XS = 4;
    public static final int SPACING_SM = 8;
    public static final int SPACING_MD = 12;
    public static final int SPACING_LG = 16;
    public static final int SPACING_XL = 24;

    public static final int CORNER_RADIUS_SM = 6;
    public static final int CORNER_RADIUS_MD = 10;
    public static final int CORNER_RADIUS_LG = 14;

    private static GripTheme instance;

    private final MontoyaApi api;
    private Font normalFont;
    private Font boldFont;
    private Font titleFont;
    private Font codeFont;
    private boolean isDarkTheme;

    public static class Colors {

        public static final Color ACCENT = new Color(255, 120, 50);
        public static final Color ACCENT_LIGHT = new Color(255, 150, 90);
        public static final Color ACCENT_DARK = new Color(220, 90, 30);
        public static final Color ACCENT_SUBTLE = new Color(255, 120, 50, 20);

        public static final Color CRITICAL = new Color(255, 69, 58);
        public static final Color HIGH = new Color(255, 149, 0);
        public static final Color MEDIUM = new Color(255, 204, 0);
        public static final Color LOW = new Color(48, 209, 88);
        public static final Color INFO = new Color(94, 92, 230);

        public static final Color SUCCESS = new Color(48, 209, 88);
        public static final Color WARNING = new Color(255, 204, 0);
        public static final Color ERROR = new Color(255, 69, 58);

        public static final Color DARK_BACKGROUND = new Color(28, 28, 30);
        public static final Color DARK_SURFACE = new Color(44, 44, 46);
        public static final Color DARK_ELEVATED = new Color(58, 58, 60);
        public static final Color DARK_FOREGROUND = new Color(255, 255, 255);
        public static final Color DARK_SECONDARY = new Color(142, 142, 147);
        public static final Color DARK_TERTIARY = new Color(99, 99, 102);
        public static final Color DARK_BORDER = new Color(56, 56, 58);
        public static final Color DARK_SEPARATOR = new Color(56, 56, 58);

        public static final Color LIGHT_BACKGROUND = new Color(255, 255, 255);
        public static final Color LIGHT_SURFACE = new Color(242, 242, 247);
        public static final Color LIGHT_ELEVATED = new Color(255, 255, 255);
        public static final Color LIGHT_FOREGROUND = new Color(0, 0, 0);
        public static final Color LIGHT_SECONDARY = new Color(60, 60, 67);
        public static final Color LIGHT_TERTIARY = new Color(99, 99, 102);
        public static final Color LIGHT_BORDER = new Color(209, 209, 214);
        public static final Color LIGHT_SEPARATOR = new Color(198, 198, 200);

        public static final Color HOVER_OVERLAY = new Color(128, 128, 128, 20);
        public static final Color PRESSED_OVERLAY = new Color(128, 128, 128, 40);
    }

    public static class Syntax {
        private static final Map<String, Color> DARK_COLORS = new HashMap<>();
        private static final Map<String, Color> LIGHT_COLORS = new HashMap<>();

        static {

            DARK_COLORS.put("STRING", new Color(152, 195, 121));
            DARK_COLORS.put("NUMBER", new Color(209, 154, 102));
            DARK_COLORS.put("KEYWORD", new Color(198, 120, 221));
            DARK_COLORS.put("TYPE", new Color(97, 175, 239));
            DARK_COLORS.put("FIELD", new Color(229, 192, 123));
            DARK_COLORS.put("ARGUMENT", new Color(152, 195, 121));
            DARK_COLORS.put("DIRECTIVE", new Color(86, 182, 194));
            DARK_COLORS.put("COMMENT", new Color(92, 99, 112));
            DARK_COLORS.put("VARIABLE", new Color(224, 108, 117));
            DARK_COLORS.put("PUNCTUATION", new Color(171, 178, 191));

            LIGHT_COLORS.put("STRING", new Color(80, 161, 79));
            LIGHT_COLORS.put("NUMBER", new Color(152, 104, 1));
            LIGHT_COLORS.put("KEYWORD", new Color(166, 38, 164));
            LIGHT_COLORS.put("TYPE", new Color(1, 132, 188));
            LIGHT_COLORS.put("FIELD", new Color(152, 104, 1));
            LIGHT_COLORS.put("ARGUMENT", new Color(80, 161, 79));
            LIGHT_COLORS.put("DIRECTIVE", new Color(1, 132, 188));
            LIGHT_COLORS.put("COMMENT", new Color(160, 161, 167));
            LIGHT_COLORS.put("VARIABLE", new Color(228, 86, 73));
            LIGHT_COLORS.put("PUNCTUATION", new Color(56, 58, 66));
        }

        public static Map<String, Color> getDarkColors() {
            return new HashMap<>(DARK_COLORS);
        }

        public static Map<String, Color> getLightColors() {
            return new HashMap<>(LIGHT_COLORS);
        }
    }

    private GripTheme(MontoyaApi api) {
        this.api = api;
        this.isDarkTheme = api.userInterface().currentTheme() == Theme.DARK;
        initializeFonts();
    }

    public static synchronized GripTheme getInstance(MontoyaApi api) {
        if (instance == null) {
            instance = new GripTheme(api);
        }
        return instance;
    }

    public static GripTheme getInstance() {
        return instance;
    }

    private void initializeFonts() {
        try {

            InputStream fontStream = getClass().getResourceAsStream("/fonts/Montserrat-Regular.ttf");
            if (fontStream != null) {
                Font montserrat = Font.createFont(Font.TRUETYPE_FONT, fontStream);
                GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(montserrat);
                normalFont = montserrat.deriveFont(Font.PLAIN, FONT_SIZE_NORMAL);
                boldFont = montserrat.deriveFont(Font.BOLD, FONT_SIZE_NORMAL);
                titleFont = montserrat.deriveFont(Font.BOLD, FONT_SIZE_TITLE);
                fontStream.close();
            } else {

                normalFont = new Font(Font.SANS_SERIF, Font.PLAIN, FONT_SIZE_NORMAL);
                boldFont = new Font(Font.SANS_SERIF, Font.BOLD, FONT_SIZE_NORMAL);
                titleFont = new Font(Font.SANS_SERIF, Font.BOLD, FONT_SIZE_TITLE);
            }

            codeFont = new Font(Font.MONOSPACED, Font.PLAIN, FONT_SIZE_NORMAL);

        } catch (Exception e) {

            normalFont = new Font(Font.SANS_SERIF, Font.PLAIN, FONT_SIZE_NORMAL);
            boldFont = new Font(Font.SANS_SERIF, Font.BOLD, FONT_SIZE_NORMAL);
            titleFont = new Font(Font.SANS_SERIF, Font.BOLD, FONT_SIZE_TITLE);
            codeFont = new Font(Font.MONOSPACED, Font.PLAIN, FONT_SIZE_NORMAL);
        }
    }

    public boolean isDarkTheme() {
        return isDarkTheme;
    }

    public void refreshTheme() {
        this.isDarkTheme = api.userInterface().currentTheme() == Theme.DARK;
    }

    public Font getNormalFont() { return normalFont; }
    public Font getBoldFont() { return boldFont; }
    public Font getTitleFont() { return titleFont; }
    public Font getCodeFont() { return codeFont; }

    public Font getFont(int style, int size) {
        return normalFont.deriveFont(style, size);
    }

    public Color getBackground() {
        return isDarkTheme ? Colors.DARK_BACKGROUND : Colors.LIGHT_BACKGROUND;
    }

    public Color getSurface() {
        return isDarkTheme ? Colors.DARK_SURFACE : Colors.LIGHT_SURFACE;
    }

    public Color getForeground() {
        return isDarkTheme ? Colors.DARK_FOREGROUND : Colors.LIGHT_FOREGROUND;
    }

    public Color getSecondaryText() {
        return isDarkTheme ? Colors.DARK_SECONDARY : Colors.LIGHT_SECONDARY;
    }

    public Color getBorder() {
        return isDarkTheme ? Colors.DARK_BORDER : Colors.LIGHT_BORDER;
    }

    public Map<String, Color> getSyntaxColors() {
        return isDarkTheme ? Syntax.getDarkColors() : Syntax.getLightColors();
    }

    public Color getElevated() {
        return isDarkTheme ? Colors.DARK_ELEVATED : Colors.LIGHT_ELEVATED;
    }

    public Color getSeparator() {
        return isDarkTheme ? Colors.DARK_SEPARATOR : Colors.LIGHT_SEPARATOR;
    }

    public Color getTertiaryText() {
        return isDarkTheme ? Colors.DARK_TERTIARY : Colors.LIGHT_TERTIARY;
    }

    public Border createBorder() {
        return new RoundedBorder(getBorder(), CORNER_RADIUS_SM, 1);
    }

    public Border createTitledBorder(String title) {
        return BorderFactory.createTitledBorder(
                BorderFactory.createCompoundBorder(
                    new RoundedBorder(getBorder(), CORNER_RADIUS_MD, 1),
                    BorderFactory.createEmptyBorder(SPACING_SM, SPACING_MD, SPACING_SM, SPACING_MD)
                ),
                title,
                javax.swing.border.TitledBorder.LEADING,
                javax.swing.border.TitledBorder.TOP,
                boldFont,
                getSecondaryText()
        );
    }

    public Border createCardBorder() {
        return BorderFactory.createCompoundBorder(
            new RoundedBorder(getBorder(), CORNER_RADIUS_MD, 1),
            BorderFactory.createEmptyBorder(SPACING_MD, SPACING_MD, SPACING_MD, SPACING_MD)
        );
    }

    public Border createPadding(int size) {
        return BorderFactory.createEmptyBorder(size, size, size, size);
    }

    public void styleComponent(JComponent component) {
        component.setFont(normalFont);
        component.setBackground(getBackground());
        component.setForeground(getForeground());
    }

    public void styleButton(JButton button) {
        button.setFont(boldFont);
        button.setFocusPainted(false);
        button.setBorderPainted(true);
        button.setContentAreaFilled(true);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setBorder(BorderFactory.createCompoundBorder(
            new RoundedBorder(getBorder(), CORNER_RADIUS_SM, 1),
            BorderFactory.createEmptyBorder(SPACING_SM, SPACING_MD, SPACING_SM, SPACING_MD)
        ));
    }

    public void stylePrimaryButton(JButton button) {
        button.setFont(boldFont);
        button.setFocusPainted(false);
        button.setBackground(Colors.ACCENT);
        button.setForeground(Color.WHITE);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setBorder(BorderFactory.createCompoundBorder(
            new RoundedBorder(Colors.ACCENT_DARK, CORNER_RADIUS_SM, 0),
            BorderFactory.createEmptyBorder(SPACING_SM + 2, SPACING_LG, SPACING_SM + 2, SPACING_LG)
        ));
    }

    public void styleSecondaryButton(JButton button) {
        button.setFont(normalFont);
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        button.setForeground(Colors.ACCENT);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setBorder(BorderFactory.createCompoundBorder(
            new RoundedBorder(Colors.ACCENT, CORNER_RADIUS_SM, 1),
            BorderFactory.createEmptyBorder(SPACING_SM, SPACING_MD, SPACING_SM, SPACING_MD)
        ));
    }

    public void styleTextField(JTextField field) {
        field.setFont(normalFont);
        field.setBorder(BorderFactory.createCompoundBorder(
            new RoundedBorder(getBorder(), CORNER_RADIUS_SM, 1),
            BorderFactory.createEmptyBorder(SPACING_SM, SPACING_MD, SPACING_SM, SPACING_MD)
        ));
    }

    public static class RoundedBorder extends AbstractBorder {
        private final Color color;
        private final int radius;
        private final int thickness;

        public RoundedBorder(Color color, int radius, int thickness) {
            this.color = color;
            this.radius = radius;
            this.thickness = thickness;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.setStroke(new BasicStroke(thickness));
            if (thickness > 0) {
                g2.draw(new RoundRectangle2D.Double(x + 0.5, y + 0.5, width - 1, height - 1, radius, radius));
            }
            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(thickness, thickness, thickness, thickness);
        }

        @Override
        public Insets getBorderInsets(Component c, Insets insets) {
            insets.left = insets.top = insets.right = insets.bottom = thickness;
            return insets;
        }
    }
}
