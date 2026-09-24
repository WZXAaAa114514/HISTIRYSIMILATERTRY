package shijianjianting.bianliang.bingpai;



import shijianjianting.gongju.zhujie.Live;

import javax.swing.*;
import java.awt.*;

public class BINGPAI extends JButton {

    @Live(Live.Role.LON) public double x;
    @Live(Live.Role.LAT) public double y;
    public int bianhao;

    /* ==================== 数据字段 ==================== */
    private Image  icon;
    private int    number        = 0;
    private String subText       = "";
    private Color  cardColor     = new Color(0x5B6B3D);
    private Color  borderColor   = new Color(0xD8C58A);
    private Color  numberColor   = Color.WHITE;
    private Color  subTextColor  = new Color(0xF0E6C0);
    private double moralePercent = 1.0;
    private boolean selected     = false;
    private boolean showMoraleBar = true;
    private Font   numberFont    = new Font("Arial", Font.BOLD, 24);
    private Font   subTextFont   = new Font("Arial", Font.PLAIN, 10);

    /* ==================== 构造函数 ==================== */

    public BINGPAI(Image icon, int number, double x, double y) {
        this(icon, number, "", x, y);
    }

    public BINGPAI(Image icon, int number, String subText, double x, double y) {
        this.icon    = icon;
        this.number  = number;
        this.subText = subText == null ? "" : subText;

        setPreferredSize(new Dimension(72, 64));
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        this.x = x;
        this.y = y;
    }

    public BINGPAI(ImageIcon icon, int number, String subText, double x, double y) {
        this(icon == null ? null : icon.getImage(), number, subText, x, y);
    }

    /* ==================== get / set ==================== */

    public Image getIconImage() { return icon; }
    public void setIconImage(Image icon) { this.icon = icon; repaint(); }

    public int getNumber() { return number; }
    public void setNumber(int number) { this.number = number; repaint(); }
    public void addNumber(int delta) { setNumber(this.number + delta); }

    public String getSubText() { return subText; }
    public void setSubText(String subText) {
        this.subText = subText == null ? "" : subText;
        repaint();
    }

    public Color getCardColor() { return cardColor; }
    public void setCardColor(Color cardColor) {
        this.cardColor = cardColor == null ? new Color(0x5B6B3D) : cardColor;
        repaint();
    }

    public Color getBorderColor() { return borderColor; }
    public void setBorderColor(Color borderColor) {
        this.borderColor = borderColor == null ? new Color(0xD8C58A) : borderColor;
        repaint();
    }

    public Color getNumberColor() { return numberColor; }
    public void setNumberColor(Color numberColor) {
        this.numberColor = numberColor == null ? Color.WHITE : numberColor;
        repaint();
    }

    public Color getSubTextColor() { return subTextColor; }
    public void setSubTextColor(Color subTextColor) {
        this.subTextColor = subTextColor == null ? new Color(0xF0E6C0) : subTextColor;
        repaint();
    }

    public double getMoralePercent() { return moralePercent; }
    public void setMoralePercent(double moralePercent) {
        this.moralePercent = Math.max(0.0, Math.min(1.0, moralePercent));
        repaint();
    }

    public boolean isSelected() { return selected; }
    @Override public void setSelected(boolean selected) {
        this.selected = selected;
        repaint();
    }

    public boolean isShowMoraleBar() { return showMoraleBar; }
    public void setShowMoraleBar(boolean showMoraleBar) {
        this.showMoraleBar = showMoraleBar;
        repaint();
    }

    public Font getNumberFont() { return numberFont; }
    public void setNumberFont(Font numberFont) {
        if (numberFont != null) { this.numberFont = numberFont; repaint(); }
    }

    public Font getSubTextFont() { return subTextFont; }
    public void setSubTextFont(Font subTextFont) {
        if (subTextFont != null) { this.subTextFont = subTextFont; repaint(); }
    }

    public void setCardSize(int width, int height) {
        setPreferredSize(new Dimension(width, height));
        revalidate();
        repaint();
    }

    /* ==================== 绘制 ==================== */

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        Color base = cardColor;
        if (getModel().isPressed())       base = base.darker();
        else if (getModel().isRollover()) base = base.brighter();
        g2.setColor(base);
        g2.fillRect(0, 0, w, h);

        g2.setColor(selected ? Color.YELLOW : borderColor);
        g2.setStroke(new BasicStroke(selected ? 3f : 2f));
        g2.drawRect(1, 1, w - 3, h - 3);

        if (icon != null) {
            int iw = 22, ih = 16;
            g2.drawImage(icon, 4, 4, iw, ih, this);
            g2.setColor(new Color(0x00000055));
            g2.drawRect(4, 4, iw - 1, ih - 1);
        }

        String numStr = String.valueOf(number);
        g2.setFont(numberFont);
        FontMetrics fm = g2.getFontMetrics();
        int tw = fm.stringWidth(numStr);
        int tx = (w - tw) / 2;
        int ty = h / 2 + fm.getAscent() / 2 + 2;

        g2.setColor(new Color(0, 0, 0, 160));
        g2.drawString(numStr, tx + 1, ty + 1);
        g2.setColor(numberColor);
        g2.drawString(numStr, tx, ty);

        if (subText != null && !subText.isEmpty()) {
            g2.setFont(subTextFont);
            FontMetrics fm2 = g2.getFontMetrics();
            int sw = fm2.stringWidth(subText);
            g2.setColor(subTextColor);
            g2.drawString(subText, w - sw - 4, h - 4);
        }

        if (showMoraleBar) {
            int barH = 4;
            int fullW = w - 8;
            int barW  = (int) (fullW * moralePercent);
            int y     = h - 8;

            g2.setColor(new Color(0x00000088));
            g2.fillRect(4, y, fullW, barH);

            g2.setColor(barColorFor(moralePercent));
            g2.fillRect(4, y, barW, barH);
        }

        g2.dispose();
    }

    private static Color barColorFor(double p) {
        if (p < 0.33)      return new Color(0xD9534F);
        else if (p < 0.66) return new Color(0xE8B33B);
        else               return new Color(0x6FBF4A);
    }
}