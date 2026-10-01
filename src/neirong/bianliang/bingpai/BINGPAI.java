package neirong.bianliang.bingpai;

import neirong.bianliang.guojia.country;
import neirong.bianliang.xuanding.canchosemany;
import neirong.bianliang.zuobiao.zuobiao;
import neirong.gongju.gongju;
import neirong.gongju.xuanranqi.PaintBoard;
import neirong.gongju.zhujie.Live;
import shunxu.third_shijianpaifaqiqidong.shijian.beixuanze_DUOXUAN;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static neirong.gongju.bianliang.*;

public class BINGPAI extends JButton
        implements canchosemany, PaintBoard.TrailPixelSink {

    /* ==================== 设计基准（5:2 宽高比） ==================== */
    private static final int BASE_W = 100;
    private static final int BASE_H = 40;

    /* ==================== 默认配色 ==================== */
    private static final Color DEFAULT_CARD_COLOR    = new Color(0x5B6B3D);
    private static final Color DEFAULT_BORDER_COLOR  = new Color(0xD8C58A);
    private static final Color DEFAULT_NUMBER_COLOR  = Color.WHITE;
    private static final Color DEFAULT_SUBTEXT_COLOR = new Color(0xF0E6C0);

    /* ==================== 整体缩放控制变量 ==================== */
    private double uiScale = 0.1;

    @Live(Live.Role.LON) public double x;
    @Live(Live.Role.LAT) public double y;
    public int bianhao;

    /* ==================== 数据字段 ==================== */
    private Image  icon;
    private int    number        = 0;
    private String subText       = "";
    private Color  cardColor     = DEFAULT_CARD_COLOR;
    private Color  borderColor   = DEFAULT_BORDER_COLOR;
    private Color  numberColor   = DEFAULT_NUMBER_COLOR;
    private Color  subTextColor  = DEFAULT_SUBTEXT_COLOR;
    private double moralePercent = 1.0;
    private boolean selected      = false;
    private boolean showMoraleBar = true;
    private Font   numberFont     = new Font("Arial", Font.BOLD, 20);
    private Font   subTextFont    = new Font("Arial", Font.PLAIN, 8);
    public country country;
    public bingmoshuju shuju;
    public zuobiao tasktogo = new zuobiao(0, 0);
    public int suoshulujingzhixianbianhao;

    /* ==================== 蓝色加粗边框高亮 ==================== */
    private boolean blueBoldBorder = false;
    private Color   blueBoldColor  = new Color(0x1E90FF);
    private float   blueBoldFactor = 1.8f;

    /* ==================== 轨迹相关 ==================== */
    private float trailWidth = 0f;
    private double trailDotRadius = 0.005d;
    private boolean trailEnabled = true;
    private double trailMinScreenPx = 3.0;
    private double lastTrailLon;
    private double lastTrailLat;
    private boolean trailInitialized = false;
    private transient PaintBoard.dian myDot;
    private boolean showCurrentDot = true;

    /* ---------- 轨迹参数接口 ---------- */
    public float getTrailWidth() { return trailWidth; }
    public void setTrailWidth(float w) { this.trailWidth = w; }

    public double getTrailDotRadius() { return trailDotRadius; }
    public void setTrailDotRadius(double r) { if (r > 0) this.trailDotRadius = r; }

    public boolean isTrailEnabled() { return trailEnabled; }
    public void setTrailEnabled(boolean on) { this.trailEnabled = on; }

    public double getTrailMinScreenPx() { return trailMinScreenPx; }
    public void setTrailMinScreenPx(double px) {
        if (px > 0.5) this.trailMinScreenPx = px;
    }

    public boolean isShowCurrentDot() { return showCurrentDot; }
    public void setShowCurrentDot(boolean on) {
        this.showCurrentDot = on;
        if (!on && myDot != null) {
            board.removeGeoDot(myDot);
            myDot = null;
        }
    }

    private float computeTrailWidthPx() {
        if (trailWidth > 0f) return trailWidth;
        double zoom = board.getZoom();
        if (!(zoom > 0)) return 1.5f;
        return (float) Math.max(1.5d, trailDotRadius * zoom * 2.0d);
    }

    /* ============================================================
     *  ★ 第二步开关
     * ============================================================ */
    private boolean dierbuEnabled = false;

    /* ============================================================
     *  ★ 多线程执行 / 批次处理基础设施
     * ============================================================ */

    /** meichuzhixing 的业务逻辑执行线程池（daemon）。 */
    private static final ExecutorService SHARED_WORKER_POOL =
            Executors.newFixedThreadPool(
                    Math.max(4, Runtime.getRuntime().availableProcessors()),
                    r -> {
                        Thread t = new Thread(r, "meichuzhixing-worker");
                        t.setDaemon(true);
                        return t;
                    });

    /** 批次结束检测线程池（单线程）。 */
    private static final ExecutorService SHARED_FINISH_POOL =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "meichuzhixing-finish");
                t.setDaemon(true);
                return t;
            });

    /**
     * 一个批次的数据。
     */
    private static final class BatchData {
        final List<double[]> pixels = Collections.synchronizedList(new ArrayList<>());
        final AtomicInteger pending = new AtomicInteger(0);
    }

    private volatile BatchData currentBatch = new BatchData();
    private final Object batchLock = new Object();

    /* ============================================================
     *  ★ 构造函数
     * ============================================================ */
    public BINGPAI(Image icon,
                   int number,
                   String subText,
                   double x,
                   double y,
                   country country,
                   bingmoshuju bingmoshuju,
                   Color cardColor) {
        this.icon        = icon;
        this.country     = country;
        this.number      = number;
        this.shuju = bingmoshuju;
        this.subText     = subText == null ? "" : subText;
        this.cardColor   = cardColor == null ? DEFAULT_CARD_COLOR : cardColor;

        super.setBackground(this.cardColor);
        super.setForeground(this.numberColor);

        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        this.x = x;
        this.y = y;
        this.tasktogo.x = x;
        this.tasktogo.y = y;

        this.lastTrailLon = x;
        this.lastTrailLat = y;
        this.trailInitialized = true;
        this.setTrailDotRadius(0.02d);
        applyScale();
    }

    public BINGPAI(Image icon, int number, double x, double y,
                   country country, bingmoshuju bingmoshuju, Color cardColor) {
        this(icon, number, "", x, y, country, bingmoshuju, cardColor);
    }

    public BINGPAI(ImageIcon icon, int number, String subText, double x, double y,
                   country country, bingmoshuju bingmoshuju, Color cardColor) {
        this(icon == null ? null : icon.getImage(), number, subText,
                x, y, country, bingmoshuju, cardColor);
    }

    public BINGPAI(Image icon, int number, double x, double y,
                   country country, bingmoshuju bingmoshuju) {
        this(icon, number, "", x, y, country, bingmoshuju, DEFAULT_CARD_COLOR);
    }

    public BINGPAI(Image icon, int number, String subText, double x, double y,
                   country country, bingmoshuju bingmoshuju) {
        this(icon, number, subText, x, y, country, bingmoshuju, DEFAULT_CARD_COLOR);
    }

    public BINGPAI(ImageIcon icon, int number, String subText, double x, double y,
                   country country, bingmoshuju bingmoshuju) {
        this(icon == null ? null : icon.getImage(), number, subText,
                x, y, country, bingmoshuju, DEFAULT_CARD_COLOR);
    }

    /* ==================== 缩放控制 ==================== */
    public double getUiScale() { return uiScale; }

    public void setUiScale(double uiScale) {
        this.uiScale = Math.max(0.05, uiScale);
        applyScale();
    }

    private void applyScale() {
        int w = Math.max(1, (int) Math.round(BASE_W * uiScale));
        int h = Math.max(1, (int) Math.round(BASE_H * uiScale));
        Dimension d = new Dimension(w, h);
        setPreferredSize(d);
        setSize(d);
        revalidate();
        repaint();
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
        this.cardColor = cardColor == null ? DEFAULT_CARD_COLOR : cardColor;
        super.setBackground(this.cardColor);
        repaint();
    }

    public void setColor(Color color) { setCardColor(color); }
    public void setColor(int argb) { setCardColor(new Color(argb, true)); }
    public Color getColor() { return cardColor; }

    public Color getBorderColor() { return borderColor; }
    public void setBorderColor(Color borderColor) {
        this.borderColor = borderColor == null ? DEFAULT_BORDER_COLOR : borderColor;
        repaint();
    }

    public Color getNumberColor() { return numberColor; }
    public void setNumberColor(Color numberColor) {
        this.numberColor = numberColor == null ? DEFAULT_NUMBER_COLOR : numberColor;
        super.setForeground(this.numberColor);
        repaint();
    }

    public Color getSubTextColor() { return subTextColor; }
    public void setSubTextColor(Color subTextColor) {
        this.subTextColor = subTextColor == null ? DEFAULT_SUBTEXT_COLOR : subTextColor;
        repaint();
    }

    public void setColors(Color card, Color border, Color number, Color subText) {
        if (card    != null) this.cardColor    = card;
        if (border  != null) this.borderColor  = border;
        if (number  != null) this.numberColor  = number;
        if (subText != null) this.subTextColor = subText;
        super.setBackground(this.cardColor);
        super.setForeground(this.numberColor);
        repaint();
    }

    public void autoNumberColor() {
        double lum = (0.299 * cardColor.getRed()
                + 0.587 * cardColor.getGreen()
                + 0.114 * cardColor.getBlue()) / 255.0;
        setNumberColor(lum > 0.6 ? new Color(0x1A1A1A) : Color.WHITE);
    }

    @Override
    public void setBackground(Color bg) {
        super.setBackground(bg);
        this.cardColor = (bg == null) ? DEFAULT_CARD_COLOR : bg;
        repaint();
    }

    @Override
    public Color getBackground() { return cardColor; }

    @Override
    public void setForeground(Color fg) {
        super.setForeground(fg);
        if (fg != null) {
            this.numberColor = fg;
            repaint();
        }
    }

    @Override
    public Color getForeground() { return numberColor; }

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
        double s = Math.min(width  / (double) BASE_W,
                height / (double) BASE_H);
        setUiScale(s);
    }

    /* ==================== 蓝色加粗边框 控制 ==================== */
    public void xianshilansebiankuang() {
        this.blueBoldBorder = true;
        repaint();
    }

    public void quxiaolansebiankuang() {
        this.blueBoldBorder = false;
        repaint();
    }

    public void setBlueBoldBorder(boolean on) {
        this.blueBoldBorder = on;
        repaint();
    }

    public boolean isBlueBoldBorder() { return blueBoldBorder; }

    public Color getBlueBoldColor() { return blueBoldColor; }
    public void setBlueBoldColor(Color c) {
        if (c != null) { this.blueBoldColor = c; repaint(); }
    }

    public float getBlueBoldFactor() { return blueBoldFactor; }
    public void setBlueBoldFactor(float f) {
        if (f > 0f) { this.blueBoldFactor = f; repaint(); }
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
        if (w <= 0 || h <= 0) { g2.dispose(); return; }

        Color base = cardColor;
        if (getModel().isPressed())       base = base.darker();
        else if (getModel().isRollover()) base = base.brighter();
        g2.setColor(base);
        g2.fillRect(0, 0, w, h);

        float s = Math.min(w / (float) BASE_W, h / (float) BASE_H);

        float drawW = BASE_W * s;
        float drawH = BASE_H * s;

        float ox = (w - drawW) / 2f;
        float oy = (h - drawH) / 2f;
        g2.translate(ox, oy);

        int rw = Math.round(drawW);
        int rh = Math.round(drawH);
        int px1 = Math.max(1, Math.round(1 * s));

        float strokeW     = Math.max(1f, (selected ? 3f : 2f) * s);
        Color strokeColor = selected ? Color.YELLOW : borderColor;

        if (blueBoldBorder) {
            strokeW     = Math.max(1f, strokeW * blueBoldFactor);
            strokeColor = blueBoldColor;
        }

        int inset = Math.max(1, Math.round(strokeW / 2f));
        g2.setColor(strokeColor);
        g2.setStroke(new BasicStroke(strokeW));
        g2.drawRect(inset, inset,
                Math.max(1, rw - inset * 2 - 1),
                Math.max(1, rh - inset * 2 - 1));

        if (icon != null) {
            int ix = Math.round(6 * s);
            int iy = Math.round(8 * s);
            int iw = Math.max(4, Math.round(30 * s));
            int ih = Math.max(3, Math.round(20 * s));
            g2.drawImage(icon, ix, iy, iw, ih, this);
            g2.setColor(new Color(0x00000055));
            g2.setStroke(new BasicStroke(Math.max(1f, s)));
            g2.drawRect(ix, iy, iw - px1, ih - px1);
        }

        String numStr = String.valueOf(number);
        g2.setFont(numberFont.deriveFont(numberFont.getSize2D() * s));
        FontMetrics fm = g2.getFontMetrics();
        int tw = fm.stringWidth(numStr);
        int tx = Math.round((drawW - tw) / 2f);
        int ty = Math.round(drawH / 2f + (fm.getAscent() - fm.getDescent()) / 2f);

        g2.setColor(new Color(0, 0, 0, 160));
        g2.drawString(numStr, tx + px1, ty + px1);
        g2.setColor(numberColor);
        g2.drawString(numStr, tx, ty);

        if (subText != null && !subText.isEmpty()) {
            g2.setFont(subTextFont.deriveFont(subTextFont.getSize2D() * s));
            FontMetrics fm2 = g2.getFontMetrics();
            int sw = fm2.stringWidth(subText);
            int margin = Math.round(6 * s);
            g2.setColor(subTextColor);
            g2.drawString(subText, rw - sw - margin, rh - margin);
        }

        if (showMoraleBar) {
            int margin = Math.round(6 * s);
            int barH   = Math.max(1, Math.round(4 * s));
            int fullW  = Math.max(1, rw - margin * 2);
            int barW   = (int) (fullW * moralePercent);
            int by     = rh - barH - margin;

            g2.setColor(new Color(0x00000088));
            g2.fillRect(margin, by, fullW, barH);

            g2.setColor(barColorFor(moralePercent));
            g2.fillRect(margin, by, barW, barH);
        }

        g2.dispose();
    }

    private static Color barColorFor(double p) {
        if (p < 0.33)      return new Color(0xD9534F);
        else if (p < 0.66) return new Color(0xE8B33B);
        else               return new Color(0x6FBF4A);
    }

    @Override
    public JButton beixuanzeanniu() {
        this.setBorder(BorderFactory.createLineBorder(Color.YELLOW));
        return this;
    }

    public void goto_(zuobiao zuobiao){
        this.tasktogo = zuobiao;
    }

    /* ============================================================
     *  ★ 地球仪式寻路
     * ============================================================ */
    public void goto_MEIYIZHENZHIXING() {

        // ---- 1) 交换批次：把旧批次交给后台收尾 ----
        BatchData oldBatch;
        synchronized (batchLock) {
            oldBatch = currentBatch;
            currentBatch = new BatchData();
        }
        scheduleBatchFinish(oldBatch);

        // ---- 2) 清空 suozouzuobiao，使它能只反映本次调用 ----
        clearSuozouzuobiao();

        // ---- 3) 原 goto_MEIYIZHENZHIXING 逻辑 ----
        if(!shuju.gongjizhe.isEmpty()){

        }
        if (tasktogo == null || shuju == null) return;

        if (!trailInitialized) {
            lastTrailLon = this.x;
            lastTrailLat = this.y;
            trailInitialized = true;
        }

        double lon1 = this.x;
        double lat1 = this.y;
        double lon2 = tasktogo.x;
        double lat2 = tasktogo.y;

        double dLon = lon2 - lon1;
        dLon = ((dLon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
        double dLat = lat2 - lat1;

        if (dLon == 0.0 && dLat == 0.0) {
            flushTrailToCurrent();
            syncCurrentDot();
            return;
        }

        double latAvg = (lat1 + lat2) * 0.5;
        double cosLat = Math.cos(Math.toRadians(latAvg));

        final double KM_PER_DEG = 111.32;
        double dLatKm = dLat * KM_PER_DEG;
        double dLonKm = dLon * KM_PER_DEG * cosLat;
        double distKm = Math.sqrt(dLatKm * dLatKm + dLonKm * dLonKm);

        double speed = shuju.speed / 3600;

        if (speed <= 0.0 || distKm <= speed) {
            this.x = lon1 + dLon;
            this.y = lat2;
        } else {
            double t = speed / distKm;
            this.x = lon1 + t * dLon;
            this.y = lat1 + t * dLat;
        }

        maybeAddTrailSegment();
    }

    private void maybeAddTrailSegment() {
        if (!trailEnabled) return;

        double ppd = board.getZoom();
        if (!(ppd > 0)) return;

        double dxPx = Math.abs(this.x - lastTrailLon) * ppd;
        double dyPx = Math.abs(this.y - lastTrailLat) * ppd;
        double distPx = Math.sqrt(dxPx * dxPx + dyPx * dyPx);

        float wPx = computeTrailWidthPx();
        double stepPx = Math.max(trailMinScreenPx, wPx * 0.5d);

        if (distPx >= stepPx) {
            board.addTrailSegment(this,
                    lastTrailLon, lastTrailLat,
                    this.x, this.y,
                    this.cardColor, wPx);
            lastTrailLon = this.x;
            lastTrailLat = this.y;
        }
    }

    private void flushTrailToCurrent() {
        if (!trailEnabled || !trailInitialized) return;
        if (lastTrailLon == this.x && lastTrailLat == this.y) return;

        board.addTrailSegment(this,
                lastTrailLon, lastTrailLat,
                this.x, this.y,
                this.cardColor, computeTrailWidthPx());
        lastTrailLon = this.x;
        lastTrailLat = this.y;
    }

    private void syncCurrentDot() {
        if (!showCurrentDot) return;

        if (myDot == null) {
            myDot = board.addGeoDotLive(this.x, this.y,
                    trailDotRadius, this.cardColor);
        } else {
            myDot.lon = this.x;
            myDot.lat = this.y;
            myDot.color = this.cardColor;
        }
    }

    /** 兵牌被删除时调用，清理资源。 */
    public void dispose() {
        if (myDot != null) {
            board.removeGeoDot(myDot);
            myDot = null;
        }
        if (board != null) {
            board.removeOwnerTrails(this);
        }
        // 静态共享线程池无需关闭，避免影响其他 BINGPAI 实例。
    }

    public  void xianshi(){
        BINGPAI b=this;
        b= (BINGPAI) gongju.quchuquanbujiantingqi(b);
        b.bianhao = board.addAutoComponent(b, b);
        BINGPAI finalB2 = b;
        BINGPAI finalB = b;
        b.addActionListener(e -> {
            try {
                Boolean succeed=true;

                if(gongju.shiftdown())succeed=xuandingbianliang.add(finalB2);
                else {
                    xuandingbianliang.clearall();
                    succeed=xuandingbianliang.add(finalB2);
                }
                if(succeed)EVENTMAIN.post(new beixuanze_DUOXUAN(finalB));
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }

        });
    }

    /* ============================================================
     *  ★ 当前 goto_MEIYIZHENZHIXING 所走路径上的所有坐标
     * ============================================================ */
    public List<zuobiao> suozouzuobiao =
            Collections.synchronizedList(new ArrayList<>());

    /** 去重用的键集合，线程安全。 */
    private final Set<Long> suozouzuobiaoKeys = ConcurrentHashMap.newKeySet();

    /** 清空 suozouzuobiao 与其去重键集合。 */
    public void clearSuozouzuobiao() {
        synchronized (suozouzuobiao) {
            suozouzuobiao.clear();
            suozouzuobiaoKeys.clear();
        }
    }

    /* ============================================================
     *  ★ PaintBoard.TrailPixelSink 实现
     * ============================================================ */
    @Override
    public void meichuzhixing(double jingdu, double weidu) {

        BatchData b = currentBatch;

        b.pixels.add(new double[]{jingdu, weidu});
        b.pending.incrementAndGet();

        SHARED_WORKER_POOL.execute(() -> {
            try {
                doMeichuzhixing(jingdu, weidu);
            } catch (Throwable ignored) {
            } finally {
                b.pending.decrementAndGet();
            }
        });
    }

    /**
     * 真正的 meichuzhixing 业务逻辑，在多线程工作池中执行。
     */
    private void doMeichuzhixing(double jingdu, double weidu) {
        long key = Double.doubleToLongBits(jingdu) * 31L
                + Double.doubleToLongBits(weidu);
        if (suozouzuobiaoKeys.add(key)) {
            zuobiao z = new zuobiao(0, 0);
            z.x = jingdu;
            z.y = weidu;
            suozouzuobiao.add(z);
        }

        // TODO: 第一步：在这里添加你自己的业务逻辑
    }

    /**
     * 把一个批次的「结束处理」提交到 batchFinishPool。
     */
    private void scheduleBatchFinish(final BatchData b) {
        SHARED_FINISH_POOL.execute(() -> {
            while (b.pending.get() > 0) {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }

            if (!dierbuEnabled) return;

            List<double[]> snapshot;
            synchronized (b.pixels) {
                snapshot = new ArrayList<>(b.pixels);
            }

            for (double[] px : snapshot) {
                try {
                    meichuzhixing2(px[0], px[1]);
                } catch (Throwable ignored) {
                }
            }
        });
    }

    /* ============================================================
     *  ★ 第二步回调
     *
     *  对每个像素，检查所有国家的所有兵牌：
     *    - 若对方 suozouzuobiaoKeys 中包含当前像素坐标（O(1)），
     *      则互相把对方加进自己的 gongjizhe（去重）。
     *
     *  性能要点：
     *    1. 坐标 key 只计算一次；
     *    2. 用 Set<Long> 做 O(1) 坐标匹配，取代遍历 suozouzuobiao；
     *    3. 用 bingmoshuju.addGongjizhe 去重，避免重复添加；
     *    4. 无 System.out 打屏。
     * ============================================================ */
    public void meichuzhixing2(double jingdu, double weidu) {

        // 只计算一次坐标 key
        long key = Double.doubleToLongBits(jingdu) * 31L
                + Double.doubleToLongBits(weidu);

        if (country == null || country.gongjizhe == null) return;

        for (country c : country.gongjizhe) {
            if (c == null || c.jundui == null) continue;

            for (int i = 0, n = c.jundui.size(); i < n; i++) {
                BINGPAI bingpai = c.jundui.get(i);
                if (bingpai == null || bingpai == this) continue;
                if (bingpai.shuju == null) continue;

                // O(1) 坐标匹配：对方是否走过当前像素对应的坐标
                if (!bingpai.suozouzuobiaoKeys.contains(key)) continue;

                // 双向添加攻击者，去重
                if (bingpai.shuju.gongjizhe.contains(this)
                        && this.shuju != null
                        && this.shuju.gongjizhe.contains(bingpai)) {
                    // 已互相标记，跳过
                    continue;
                }

                bingpai.shuju.addGongjizhe(this);
                if (this.shuju != null) {
                    this.shuju.addGongjizhe(bingpai);
                }
            }
        }
    }

    /* ============================================================
     *  ★ 第二步开关控制
     * ============================================================ */

    public void kaiqidierbu() {
        this.dierbuEnabled = true;
    }

    public void guanbidierbu() {
        this.dierbuEnabled = false;
    }

    public boolean isDierbuEnabled() {
        return dierbuEnabled;
    }
}