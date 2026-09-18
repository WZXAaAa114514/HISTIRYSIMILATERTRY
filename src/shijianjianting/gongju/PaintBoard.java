package shijianjianting.gongju;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PaintBoard extends JPanel {

    // ==================== 字体 ====================
    private static final String UI_FONT = pickUIFont();

    private static String pickUIFont() {
        String[] prefer = {
                "Microsoft YaHei", "微软雅黑",
                "PingFang SC", "Hiragino Sans GB",
                "Noto Sans CJK SC", "Source Han Sans SC",
                "WenQuanYi Micro Hei", "SimHei", "SimSun"
        };
        Set<String> available = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment()
                        .getAvailableFontFamilyNames()));
        for (String name : prefer) {
            if (available.contains(name)) return name;
        }
        return Font.SANS_SERIF;
    }

    // ==================== 世界画布尺寸 ====================
    private static final int WORLD_PPD = 32;
    private static final int WORLD_W = 360 * WORLD_PPD;
    private static final int WORLD_H = 180 * WORLD_PPD;

    private final BufferedImage worldCanvas;
    private volatile boolean worldDirty = true;
    private boolean bakingMode = false;

    // ==================== 批量模式 ====================
    private int batchDepth = 0;

    // ==================== 鼠标快照 ====================
    private static final class MouseSnapshot {
        final double lon, lat;
        MouseSnapshot(double lon, double lat) { this.lon = lon; this.lat = lat; }
    }
    private volatile MouseSnapshot mouseSnap = null;

    // ==================== 世界边界 ====================
    private static final double WORLD_LON_MIN = -180;
    private static final double WORLD_LON_MAX =  180;
    private static final double WORLD_LAT_MIN =  -90;
    private static final double WORLD_LAT_MAX =   90;

    // ==================== 视图状态 ====================
    private double lonCenter = 0;
    private double latCenter = 0;
    private double ppd = 6;

    private double minPpd = 6;
    private double maxPpd = 50;

    private Double lonBoundMin = null, lonBoundMax = null;
    private Double latBoundMin = null, latBoundMax = null;

    private static final double GRID_STEP = 15;

    /**
     * ★ 非关联模式下，把 lon/lat 视为"虚拟按钮"中心时使用的半高。
     *   默认 15，跟按钮默认高度 30 匹配。
     *   文字底部 = 锚点y - virtualButtonHalfHeight - gap - descent
     */
    private int virtualButtonHalfHeight = 15;
    public void setVirtualButtonHalfHeight(int h) { this.virtualButtonHalfHeight = Math.max(0, h); }
    public int  getVirtualButtonHalfHeight()      { return virtualButtonHalfHeight; }

    // ==================== 画笔 ====================
    private Color brushColor = Color.WHITE;
    private int brushSize = 3;

    public void setBrushColor(Color c) { this.brushColor = c; }
    public Color getBrushColor()       { return brushColor; }
    public void setBrushSize(int s)    { this.brushSize = s; }
    public int getBrushSize()          { return brushSize; }

    // ==================== 图形 / 标记 / 点 ====================
    private interface GeoShape { void paint(Graphics2D g2, PaintBoard b); }
    private final List<GeoShape> shapes = new ArrayList<>();

    private static class Marker {
        double lon, lat; Color color; int size; String text;
        Marker(double lon, double lat, Color c, int s, String t) {
            this.lon = lon; this.lat = lat; color = c; size = s; text = t;
        }
    }
    private final List<Marker> markers = new ArrayList<>();

    public static class dian {
        public double lon, lat;
        public double radiusDeg;
        public Color  color;
        public Color  borderColor;
        public Color  textColor;
        public String text;
        public boolean textScale;
        public float  borderWidth;

        public dian(double lon, double lat, double radiusDeg,
                    Color color, Color borderColor, Color textColor,
                    String text, boolean textScale, float borderWidth) {
            this.lon = lon; this.lat = lat;
            this.radiusDeg = radiusDeg;
            this.color = color;
            this.borderColor = borderColor;
            this.textColor = textColor;
            this.text = text;
            this.textScale = textScale;
            this.borderWidth = borderWidth;
        }
    }
    private final List<dian> geoDots = new ArrayList<>();

    // ============================================================
    //  跟随地图的 Swing 组件
    // ============================================================
    private static final class GeoComponent {
        final JComponent comp;
        final double lon, lat;
        final int anchorX, anchorY;
        final int baseW, baseH;
        final double basePpd;
        final Font baseFont;
        boolean scaleWithZoom;
        float currentFontSize = -1f;

        GeoComponent(JComponent comp, double lon, double lat,
                     int ax, int ay, int baseW, int baseH,
                     double basePpd, Font baseFont, boolean scaleWithZoom) {
            this.comp = comp;
            this.lon = lon; this.lat = lat;
            this.anchorX = ax; this.anchorY = ay;
            this.baseW = baseW; this.baseH = baseH;
            this.basePpd = basePpd;
            this.baseFont = baseFont;
            this.scaleWithZoom = scaleWithZoom;
        }
    }
    private final List<GeoComponent> geoComponents = new ArrayList<>();

    // ============================================================
    //  跟随地图的文本
    // ============================================================
    public static class GeoText {
        public double lon, lat;
        public String text;
        public Color color;
        public float  baseFontSize;
        public boolean scaleWithZoom;
        public double offsetX = 0, offsetY = 0;
        final double basePpd;

        /** 关联的 Swing 组件；null 时走虚拟按钮逻辑 */
        public JComponent attachedTo = null;

        /** 文字底部与参照物（按钮顶 / 虚拟按钮顶）之间的间距（屏幕像素） */
        public int gap = 6;

        GeoText(double lon, double lat, String text, float fontSize, Color color,
                boolean scaleWithZoom, double basePpd) {
            this.lon = lon; this.lat = lat;
            this.text = text;
            this.baseFontSize = fontSize;
            this.color = color;
            this.scaleWithZoom = scaleWithZoom;
            this.basePpd = basePpd;
        }
    }
    private final List<GeoText> geoTexts = new ArrayList<>();

    // ==================== 拖拽 ====================
    private Point dragStart;
    private double startLon, startLat;
    private boolean dragging;

    public double[] shibiaojingweidu() {
        MouseSnapshot s = mouseSnap;
        if (s == null) return null;
        return new double[]{ s.lon, s.lat };
    }

    public PaintBoard() {
        worldCanvas = new BufferedImage(WORLD_W, WORLD_H, BufferedImage.TYPE_INT_ARGB);

        setBackground(Color.BLACK);
        setPreferredSize(new Dimension(900, 600));
        setLayout(null);

        MouseAdapter ma = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                dragStart = e.getPoint();
                startLon = lonCenter; startLat = latCenter;
                dragging = true;
                setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                updateMouse(e);
            }
            @Override public void mouseDragged(MouseEvent e) {
                if (dragging && dragStart != null) {
                    double dx = e.getX() - dragStart.x;
                    double dy = e.getY() - dragStart.y;
                    lonCenter = startLon - dx / ppd;
                    latCenter = startLat + dy / ppd;
                    clampView();
                }
                updateMouse(e);
                repaint();
            }
            @Override public void mouseReleased(MouseEvent e) {
                dragging = false; dragStart = null;
                setCursor(Cursor.getDefaultCursor());
                updateMouse(e);
                repaint();
            }
            @Override public void mouseMoved(MouseEvent e) {
                updateMouse(e);
                repaint();
            }
            @Override public void mouseExited(MouseEvent e) {
                mouseSnap = null;
                repaint();
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);

        addMouseWheelListener(e -> {
            if (getWidth() <= 0 || getHeight() <= 0) return;
            double factor = Math.pow(1.12, -e.getWheelRotation());
            double target = clamp(ppd * factor, minPpd, maxPpd);
            if (target == ppd) return;

            int mx = e.getX(), my = e.getY();
            double lonUnder = lonCenter + (mx - getWidth() / 2.0) / ppd;
            double latUnder = latCenter - (my - getHeight() / 2.0) / ppd;

            ppd = target;
            lonCenter = lonUnder - (mx - getWidth() / 2.0) / ppd;
            latCenter = latUnder + (my - getHeight() / 2.0) / ppd;
            clampView();
            repaint();
        });

        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                clampView();
                repaint();
            }
        });
    }

    private void updateMouse(MouseEvent e) {
        if (getWidth() <= 0 || getHeight() <= 0) return;
        double[] ll = screenToLonLat(e.getX(), e.getY());
        mouseSnap = new MouseSnapshot(ll[0], ll[1]);
    }

    private void markWorldDirty() { worldDirty = true; }

    public void repaintStatic() {
        markWorldDirty();
        repaint();
    }

    // ============================================================
    //  批量模式 API
    // ============================================================
    public void beginBatch() {
        if (SwingUtilities.isEventDispatchThread()) {
            batchDepth++;
        } else {
            SwingUtilities.invokeLater(this::beginBatch);
        }
    }

    public void endBatch() {
        Runnable r = () -> {
            if (batchDepth > 0) batchDepth--;
            if (batchDepth == 0) {
                markWorldDirty();
                repaint();
            }
        };
        if (SwingUtilities.isEventDispatchThread()) r.run();
        else SwingUtilities.invokeLater(r);
    }

    // ============================================================
    //  圆点 API
    // ============================================================
    public dian addGeoDot(double lon, double lat, double radiusDeg, Color color) {
        return addGeoDot(lon, lat, radiusDeg, color, null, null, null, false, 0f);
    }

    public dian addGeoDot(double lon, double lat, double radiusDeg,
                          Color color, String text) {
        return addGeoDot(lon, lat, radiusDeg, color, null, null, text, false, 0f);
    }

    public dian addGeoDot(double lon, double lat, double radiusDeg,
                          Color color, Color borderColor, Color textColor,
                          String text, boolean textScale, float borderWidth) {
        dian d = new dian(lon, lat, radiusDeg,
                color, borderColor, textColor, text, textScale, borderWidth);
        geoDots.add(d);
        if (batchDepth == 0) {
            markWorldDirty();
            repaint();
        }
        return d;
    }

    public void removeGeoDot(dian d) {
        geoDots.remove(d);
        markWorldDirty();
        repaint();
    }

    public void clearGeoDots() {
        geoDots.clear();
        markWorldDirty();
        repaint();
    }

    public void setGeoDotColor(dian d, Color color) {
        runOnEDT(() -> { d.color = color; markWorldDirty(); repaint(); });
    }
    public void setGeoDotBorderColor(dian d, Color color) {
        runOnEDT(() -> { d.borderColor = color; markWorldDirty(); repaint(); });
    }
    public void setGeoDotTextColor(dian d, Color color) {
        runOnEDT(() -> { d.textColor = color; markWorldDirty(); repaint(); });
    }
    public void setGeoDotText(dian d, String text) {
        runOnEDT(() -> { d.text = text; markWorldDirty(); repaint(); });
    }
    public void setGeoDotBorderWidth(dian d, float width) {
        runOnEDT(() -> { d.borderWidth = width; markWorldDirty(); repaint(); });
    }
    public void setGeoDotStyle(dian d, Color fill, Color border, Color text) {
        runOnEDT(() -> {
            d.color = fill; d.borderColor = border; d.textColor = text;
            markWorldDirty(); repaint();
        });
    }
    public void setGeoDotLonLat(dian d, double lon, double lat) {
        runOnEDT(() -> { d.lon = lon; d.lat = lat; markWorldDirty(); repaint(); });
    }
    public void setGeoDotRadius(dian d, double radiusDeg) {
        runOnEDT(() -> { d.radiusDeg = radiusDeg; markWorldDirty(); repaint(); });
    }

    // ============================================================
    //  地理文本 API
    // ============================================================
    public GeoText addGeoText(String text, double lon, double lat, int fontSize) {
        return addGeoText(text, lon, lat, fontSize, Color.WHITE, true);
    }

    public GeoText addGeoText(String text, double lon, double lat,
                              int fontSize, Color color) {
        return addGeoText(text, lon, lat, fontSize, color, true);
    }

    public GeoText addGeoText(String text, double lon, double lat,
                              int fontSize, Color color, boolean scaleWithZoom) {
        GeoText t = new GeoText(lon, lat, text, fontSize, color,
                scaleWithZoom, this.ppd);
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> { geoTexts.add(t); repaint(); });
        } else {
            geoTexts.add(t);
            repaint();
        }
        return t;
    }

    /** 便捷重载：直接指定关联的 Swing 组件 */
    public GeoText addGeoText(String text, double lon, double lat,
                              int fontSize, Color color, JComponent attachedTo) {
        GeoText t = addGeoText(text, lon, lat, fontSize, color, true);
        t.attachedTo = attachedTo;
        repaint();
        return t;
    }

    public void removeGeoText(GeoText t) { geoTexts.remove(t); repaint(); }
    public void clearGeoTexts()          { geoTexts.clear();  repaint(); }

    // ============================================================
    //  跟随地图的 Swing 组件 API
    // ============================================================
    public void addGeoComponent(JComponent comp, double lon, double lat) {
        Dimension d = comp.getPreferredSize();
        addGeoComponent(comp, lon, lat, -d.width / 2, -d.height / 2, true);
    }

    public void addGeoComponent(JComponent comp, double lon, double lat,
                                int anchorX, int anchorY) {
        addGeoComponent(comp, lon, lat, anchorX, anchorY, true);
    }

    public void addGeoComponent(JComponent comp, double lon, double lat,
                                int anchorX, int anchorY, boolean scaleWithZoom) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() ->
                    addGeoComponent(comp, lon, lat, anchorX, anchorY, scaleWithZoom));
            return;
        }
        comp.setFocusable(false);
        Dimension pref = comp.getPreferredSize();
        int w = pref.width  > 0 ? pref.width  : 60;
        int h = pref.height > 0 ? pref.height : 24;

        Font f = comp.getFont();
        if (f == null) f = UIManager.getFont("Button.font");
        if (f == null) f = new Font(UI_FONT, Font.PLAIN, 12);

        add(comp);
        geoComponents.add(new GeoComponent(
                comp, lon, lat, anchorX, anchorY, w, h,
                this.ppd, f, scaleWithZoom));
        layoutGeoComponents();
        repaint();
    }

    public void removeGeoComponent(JComponent comp) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> removeGeoComponent(comp));
            return;
        }
        geoComponents.removeIf(g -> g.comp == comp);
        remove(comp);
        repaint();
    }

    public void clearGeoComponents() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::clearGeoComponents);
            return;
        }
        for (GeoComponent g : geoComponents) remove(g.comp);
        geoComponents.clear();
        repaint();
    }

    public void setGeoComponentLonLat(JComponent comp, double lon, double lat) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> setGeoComponentLonLat(comp, lon, lat));
            return;
        }
        for (int i = 0; i < geoComponents.size(); i++) {
            GeoComponent g = geoComponents.get(i);
            if (g.comp == comp) {
                GeoComponent ng = new GeoComponent(
                        g.comp, lon, lat, g.anchorX, g.anchorY,
                        g.baseW, g.baseH, g.basePpd, g.baseFont, g.scaleWithZoom);
                ng.currentFontSize = g.currentFontSize;
                geoComponents.set(i, ng);
                layoutGeoComponents();
                repaint();
                return;
            }
        }
    }

    public void setGeoComponentScaleWithZoom(JComponent comp, boolean on) {
        runOnEDT(() -> {
            for (GeoComponent g : geoComponents) {
                if (g.comp == comp) {
                    g.scaleWithZoom = on;
                    layoutGeoComponents();
                    repaint();
                    return;
                }
            }
        });
    }

    private void layoutGeoComponents() {
        if (geoComponents.isEmpty()) return;
        int W = getWidth(), H = getHeight();
        if (W <= 0 || H <= 0) return;

        for (GeoComponent g : geoComponents) {
            int x = (int) Math.round((g.lon - lonCenter) * ppd + W / 2.0);
            int y = (int) Math.round((latCenter - g.lat) * ppd + H / 2.0);

            double scale = 1.0;
            if (g.scaleWithZoom && g.basePpd > 1e-9) {
                scale = ppd / g.basePpd;
            }

            int w  = Math.max(1, (int) Math.round(g.baseW * scale));
            int h  = Math.max(1, (int) Math.round(g.baseH * scale));
            int ax = (int) Math.round(g.anchorX * scale);
            int ay = (int) Math.round(g.anchorY * scale);

            int px = x + ax;
            int py = y + ay;

            g.comp.setBounds(px, py, w, h);

            if (g.scaleWithZoom && g.baseFont != null) {
                float newSize = (float) (g.baseFont.getSize2D() * scale);
                if (newSize < 1f) newSize = 1f;
                if (Math.abs(newSize - g.currentFontSize) > 0.5f) {
                    g.comp.setFont(g.baseFont.deriveFont(newSize));
                    g.currentFontSize = newSize;
                }
            }

            g.comp.setVisible(px + w > 0 && px < W && py + h > 0 && py < H);
        }
    }

    // ============================================================
    //  限制控制 API
    // ============================================================
    public void setZoomRange(double minPixelsPerDegree, double maxPixelsPerDegree) {
        if (minPixelsPerDegree <= 0) minPixelsPerDegree = 0.001;
        this.minPpd = minPixelsPerDegree;
        this.maxPpd = Math.max(minPixelsPerDegree, maxPixelsPerDegree);
        clampView();
        repaint();
    }

    public void setLonBounds(double lonMin, double lonMax) {
        if (lonMin > lonMax) { double t = lonMin; lonMin = lonMax; lonMax = t; }
        this.lonBoundMin = lonMin; this.lonBoundMax = lonMax;
        clampView(); repaint();
    }
    public void setLatBounds(double latMin, double latMax) {
        if (latMin > latMax) { double t = latMin; latMin = latMax; latMax = t; }
        this.latBoundMin = latMin; this.latBoundMax = latMax;
        clampView(); repaint();
    }
    public void clearPanBounds() {
        this.lonBoundMin = null; this.lonBoundMax = null;
        this.latBoundMin = null; this.latBoundMax = null;
        clampView(); repaint();
    }

    private void clampView() {
        int W = Math.max(getWidth(), 1);
        int H = Math.max(getHeight(), 1);

        boolean hasLonBounds = (lonBoundMin != null && lonBoundMax != null);
        boolean hasLatBounds = (latBoundMin != null && latBoundMax != null);

        double spanLon = hasLonBounds ? Math.max(lonBoundMax - lonBoundMin, 1e-6) : 360.0;
        double spanLat = hasLatBounds ? Math.max(latBoundMax - latBoundMin, 1e-6) : 180.0;

        double fitMin = Math.max(W / spanLon, H / spanLat);
        double effMinPpd = Math.max(minPpd, fitMin);
        ppd = clamp(ppd, effMinPpd, Math.max(effMinPpd, maxPpd));

        double halfLon = W / (2.0 * ppd);
        double halfLat = H / (2.0 * ppd);

        double latMin = hasLatBounds ? Math.max(latBoundMin, WORLD_LAT_MIN) : WORLD_LAT_MIN;
        double latMax = hasLatBounds ? Math.min(latBoundMax, WORLD_LAT_MAX) : WORLD_LAT_MAX;
        if (halfLat >= (latMax - latMin) / 2) {
            latCenter = (latMin + latMax) / 2;
        } else {
            latCenter = clamp(latCenter, latMin + halfLat, latMax - halfLat);
        }

        if (hasLonBounds) {
            if (halfLon >= (lonBoundMax - lonBoundMin) / 2) {
                lonCenter = (lonBoundMin + lonBoundMax) / 2;
            } else {
                lonCenter = clamp(lonCenter,
                        lonBoundMin + halfLon,
                        lonBoundMax - halfLon);
            }
        } else {
            lonCenter = normalizeLon(lonCenter);
        }

        layoutGeoComponents();
    }

    // ============================================================
    //  绘图方法
    // ============================================================
    public void drawLine(double lon1, double lat1, double lon2, double lat2) {
        final Color c = brushColor; final int s = brushSize;
        shapes.add((g2, b) -> {
            g2.setColor(c);
            g2.setStroke(new BasicStroke(s, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int k = -1; k <= 1; k++) {
                int[] p1 = b.lonLatToScreen(lon1 + k * 360.0, lat1);
                int[] p2 = b.lonLatToScreen(lon2 + k * 360.0, lat2);
                g2.drawLine(p1[0], p1[1], p2[0], p2[1]);
            }
        });
        markWorldDirty();
        repaint();
    }

    public void drawPoint(double lon, double lat) {
        final Color c = brushColor; final int s = brushSize;
        shapes.add((g2, b) -> {
            int r = Math.max(s / 2, 1);
            g2.setColor(c);
            for (int k = -1; k <= 1; k++) {
                int[] p = b.lonLatToScreen(lon + k * 360.0, lat);
                g2.fill(new Ellipse2D.Double(p[0] - r, p[1] - r, r * 2.0, r * 2.0));
            }
        });
        markWorldDirty();
        repaint();
    }

    public void drawOval(double lon, double lat, double lonSpan, double latSpan) {
        final Color c = brushColor; final int s = brushSize;
        shapes.add((g2, b) -> {
            g2.setColor(c);
            g2.setStroke(new BasicStroke(s, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int k = -1; k <= 1; k++) {
                int[] p1 = b.lonLatToScreen(lon + k * 360.0, lat);
                int[] p2 = b.lonLatToScreen(lon + lonSpan + k * 360.0, lat + latSpan);
                int x = Math.min(p1[0], p2[0]), y = Math.min(p1[1], p2[1]);
                int w = Math.abs(p2[0] - p1[0]), h = Math.abs(p2[1] - p1[1]);
                g2.drawOval(x, y, w, h);
            }
        });
        markWorldDirty();
        repaint();
    }

    public void drawRect(double lon, double lat, double lonSpan, double latSpan) {
        final Color c = brushColor; final int s = brushSize;
        shapes.add((g2, b) -> {
            g2.setColor(c);
            g2.setStroke(new BasicStroke(s, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int k = -1; k <= 1; k++) {
                int[] p1 = b.lonLatToScreen(lon + k * 360.0, lat);
                int[] p2 = b.lonLatToScreen(lon + lonSpan + k * 360.0, lat + latSpan);
                int x = Math.min(p1[0], p2[0]), y = Math.min(p1[1], p2[1]);
                int w = Math.abs(p2[0] - p1[0]), h = Math.abs(p2[1] - p1[1]);
                g2.drawRect(x, y, w, h);
            }
        });
        markWorldDirty();
        repaint();
    }

    public void drawText(String text, double lon, double lat, int fontSize) {
        final Color c = brushColor;
        shapes.add((g2, b) -> {
            g2.setColor(c);
            int fs = b.bakingMode
                    ? Math.max(1, (int) Math.round(fontSize * WORLD_PPD / 6.0))
                    : fontSize;
            g2.setFont(new Font(UI_FONT, Font.PLAIN, fs));
            for (int k = -1; k <= 1; k++) {
                int[] p = b.lonLatToScreen(lon + k * 360.0, lat);
                g2.drawString(text, p[0], p[1]);
            }
        });
        markWorldDirty();
        repaint();
    }

    public void clear() {
        shapes.clear();
        markWorldDirty();
        repaint();
    }

    public void undo() {
        if (!shapes.isEmpty()) {
            shapes.remove(shapes.size() - 1);
            markWorldDirty();
            repaint();
        }
    }

    // ==================== 视图控制 ====================
    public void centerOn(double lon, double lat) {
        lonCenter = lon; latCenter = lat;
        clampView(); repaint();
    }
    public void setZoom(double pixelsPerDegree) {
        ppd = pixelsPerDegree; clampView(); repaint();
    }
    public void resetView() {
        lonCenter = 0; latCenter = 0; ppd = Math.max(minPpd, 6);
        clampView(); repaint();
    }
    public double getLonCenter() { return lonCenter; }
    public double getLatCenter() { return latCenter; }
    public double getZoom()      { return ppd; }

    // ==================== 标记 ====================
    public void addMarker(double lon, double lat, Color color, int size, String text) {
        markers.add(new Marker(lon, lat, color, size, text));
        markWorldDirty();
        repaint();
    }
    public void clearMarkers() {
        markers.clear();
        markWorldDirty();
        repaint();
    }

    // ==================== 坐标换算 ====================
    public int[] lonLatToScreen(double lon, double lat) {
        if (bakingMode) {
            int x = (int) Math.round((lon + 180) * (double) WORLD_PPD);
            int y = (int) Math.round((90 - lat) * (double) WORLD_PPD);
            return new int[]{ x, y };
        }
        int x = (int) Math.round((lon - lonCenter) * ppd + getWidth() / 2.0);
        int y = (int) Math.round((latCenter - lat) * ppd + getHeight() / 2.0);
        return new int[]{ x, y };
    }

    public double[] screenToLonLat(int x, int y) {
        double lon = lonCenter + (x - getWidth() / 2.0) / ppd;
        double lat = latCenter - (y - getHeight() / 2.0) / ppd;
        lon = normalizeLon(lon);
        lat = clamp(lat, WORLD_LAT_MIN, WORLD_LAT_MAX);
        return new double[]{ lon, lat };
    }

    // ============================================================
    //  绘制入口
    // ============================================================
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int W = getWidth(), H = getHeight();
        if (W <= 0 || H <= 0) return;

        if (worldDirty) rebuildWorldCanvas();

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            drawGridOnScreen(g2, W, H);
            drawWorldCanvas(g2, W, H);
            drawGeoTexts(g2, W, H);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            drawHUD(g2, W, H);
        } finally {
            g2.dispose();
        }
    }

    // ============================================================
    //  ★ 绘制地理文本 —— 两种模式，视觉逻辑统一：文字总在"参照物"上方
    // ============================================================
    // ============================================================
//  绘制地理文本 —— 文字中心 = 按钮中心（或锚点中心）
// ============================================================
    private void drawGeoTexts(Graphics2D g2, int W, int H) {
        if (geoTexts.isEmpty()) return;
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        double panelWidthPx = 360.0 * ppd;
        int copies = (int) Math.ceil(W / Math.max(panelWidthPx, 1.0)) + 1;
        if (copies > 5) copies = 5;

        for (GeoText t : geoTexts) {
            if (t.text == null || t.text.isEmpty()) continue;

            // ---- 字号 ----
            float fs = t.baseFontSize;
            if (t.scaleWithZoom && t.basePpd > 1e-9) {
                fs = (float) (t.baseFontSize * ppd / t.basePpd);
            }
            if (fs < 1f) fs = 1f;

            Font font = new Font(UI_FONT, Font.PLAIN, Math.round(fs));
            g2.setFont(font);
            g2.setColor(t.color != null ? t.color : Color.WHITE);

            FontMetrics fm = g2.getFontMetrics(font);
            int textW   = fm.stringWidth(t.text);
            int ascent  = fm.getAscent();
            int descent = fm.getDescent();

            // 让"文字视觉中心"落在目标点上：
            //   文字顶 = y - ascent, 文字底 = y + descent
            //   中心 = (顶 + 底) / 2 = y + (descent - ascent) / 2
            //   想让它等于 cy ⇒ y = cy + (ascent - descent) / 2
            int centerOffset = (ascent - descent) / 2;

            if (t.attachedTo != null) {
                // ============================================================
                //  模式 A：文字中心 = 关联组件（按钮）中心
                // ============================================================
                if (!t.attachedTo.isVisible()) continue;

                Rectangle b = t.attachedTo.getBounds();

                int cx = b.x + b.width  / 2;
                int cy = b.y + b.height / 2;

                int x = cx - textW / 2 + (int) Math.round(t.offsetX);
                int y = cy + centerOffset + (int) Math.round(t.offsetY);

                if (x + textW < 0 || x > W) continue;
                if (y - ascent > H || y + descent < 0) continue;

                g2.drawString(t.text, x, y);
            } else {
                // ============================================================
                //  模式 B：文字中心 = lon/lat 锚点
                // ============================================================
                for (int k = -copies; k <= copies; k++) {
                    int[] p = lonLatToScreen(t.lon + k * 360.0, t.lat);

                    int x = p[0] - textW / 2 + (int) Math.round(t.offsetX);
                    int y = p[1] + centerOffset + (int) Math.round(t.offsetY);

                    if (x + textW < 0 || x > W) continue;
                    if (y - ascent > H || y + descent < 0) continue;

                    g2.drawString(t.text, x, y);
                }
            }
        }
    }

    // ============================================================
    //  第 1 层：经纬线
    // ============================================================
    private void drawGridOnScreen(Graphics2D g2, int W, int H) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_OFF);
        g2.setStroke(new BasicStroke(1f));

        double lonMin = lonCenter - W / (2.0 * ppd);
        double lonMax = lonCenter + W / (2.0 * ppd);
        double startLon = Math.ceil(lonMin / GRID_STEP) * GRID_STEP;
        for (double lon = startLon; lon <= lonMax; lon += GRID_STEP) {
            int x = (int) Math.round((lon - lonCenter) * ppd + W / 2.0);
            if (x < -1 || x > W + 1) continue;
            boolean prime = Math.abs(normalizeLon(lon)) < 1e-6;
            g2.setColor(prime ? new Color(255, 200, 100, 220) : new Color(70, 70, 70, 160));
            g2.drawLine(x, 0, x, H);
        }

        for (double lat = -90; lat <= 90 + 1e-6; lat += GRID_STEP) {
            int y = (int) Math.round((latCenter - lat) * ppd + H / 2.0);
            if (y < -1 || y > H + 1) continue;
            boolean equator = Math.abs(lat) < 1e-6;
            g2.setColor(equator ? new Color(255, 200, 100, 220) : new Color(70, 70, 70, 160));
            g2.drawLine(0, y, W, y);
        }
    }

    // ============================================================
    //  第 2 层：世界画布
    // ============================================================
    private void drawWorldCanvas(Graphics2D g2, int W, int H) {
        double scale = ppd / (double) WORLD_PPD;
        double tx = (-180 - lonCenter) * ppd + W / 2.0;
        double ty = (latCenter - 90) * ppd + H / 2.0;

        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,
                RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);

        double panelWidthPx = 360.0 * ppd;
        int copies = (int) Math.ceil(W / Math.max(panelWidthPx, 1.0)) + 1;
        if (copies > 5) copies = 5;

        for (int k = -copies; k <= copies; k++) {
            double x0 = tx + k * panelWidthPx;
            if (x0 + panelWidthPx < 0) continue;
            if (x0 > W) continue;

            AffineTransform at = new AffineTransform();
            at.translate(x0, ty);
            at.scale(scale, scale);
            g2.drawImage(worldCanvas, at, null);
        }
    }

    // ============================================================
    //  世界画布重建
    // ============================================================
    private void rebuildWorldCanvas() {
        Graphics2D g = worldCanvas.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                    RenderingHints.VALUE_STROKE_PURE);

            g.setComposite(AlphaComposite.Clear);
            g.fillRect(0, 0, WORLD_W, WORLD_H);
            g.setComposite(AlphaComposite.SrcOver);

            bakingMode = true;
            try {
                for (GeoShape s : shapes) s.paint(g, this);
                bakeMarkers(g);
                bakeGeoDots(g);
            } finally {
                bakingMode = false;
            }
        } finally {
            g.dispose();
        }
        worldDirty = false;
    }

    private void bakeMarkers(Graphics2D g) {
        if (markers.isEmpty()) return;
        int baseSize = 12;
        g.setFont(new Font(UI_FONT, Font.PLAIN,
                Math.max(1, (int) Math.round(baseSize * WORLD_PPD / 6.0))));
        for (Marker m : markers) {
            int[] p = lonLatToScreen(m.lon, m.lat);
            int s = Math.max(1, (int) Math.round(m.size * (double) WORLD_PPD / 6.0));
            g.setColor(m.color);
            g.fillOval(p[0] - s / 2, p[1] - s / 2, s, s);
            if (m.text != null && !m.text.isEmpty()) {
                g.setColor(Color.WHITE);
                g.drawString(m.text, p[0] + s / 2 + 4, p[1] + 5);
            }
        }
    }

    private void bakeGeoDots(Graphics2D g) {
        if (geoDots.isEmpty()) return;

        Map<Color, Path2D.Double> batches = new HashMap<>();
        List<dian> individual = new ArrayList<>();

        for (dian d : geoDots) {
            boolean simple = (d.borderWidth <= 0)
                    && (d.text == null || d.text.isEmpty());
            if (simple && d.color != null) {
                Path2D.Double path = batches.get(d.color);
                if (path == null) {
                    path = new Path2D.Double();
                    batches.put(d.color, path);
                }
                int[] p = lonLatToScreen(d.lon, d.lat);
                int r = (int) Math.round(d.radiusDeg * WORLD_PPD);
                if (r < 1) r = 1;
                if (r <= 2) {
                    path.append(new Rectangle2D.Double(p[0] - r, p[1] - r, r * 2.0, r * 2.0), false);
                } else {
                    path.append(new Ellipse2D.Double(p[0] - r, p[1] - r, r * 2.0, r * 2.0), false);
                }
            } else {
                individual.add(d);
            }
        }

        for (Map.Entry<Color, Path2D.Double> e : batches.entrySet()) {
            g.setColor(e.getKey());
            g.fill(e.getValue());
        }

        for (dian d : individual) {
            int[] p = lonLatToScreen(d.lon, d.lat);
            int r = (int) Math.round(d.radiusDeg * WORLD_PPD);
            if (r < 1) r = 1;

            if (d.color != null) {
                g.setColor(d.color);
                g.fillOval(p[0] - r, p[1] - r, r * 2, r * 2);
            }
            if (d.borderWidth > 0) {
                Color border = d.borderColor != null ? d.borderColor
                        : (d.color != null ? d.color.darker() : Color.WHITE);
                g.setColor(border);
                float bw = Math.max(1f, d.borderWidth * WORLD_PPD / 6f);
                g.setStroke(new BasicStroke(bw));
                g.drawOval(p[0] - r, p[1] - r, r * 2, r * 2);
            }
            if (d.text != null && !d.text.isEmpty()) {
                int fs;
                if (d.textScale) {
                    fs = (int) Math.max(9, Math.min(200, d.radiusDeg * WORLD_PPD * 0.8));
                } else {
                    fs = Math.max(1, (int) Math.round(12.0 * WORLD_PPD / 6.0));
                }
                g.setFont(new Font(UI_FONT, Font.PLAIN, fs));
                g.setColor(d.textColor != null ? d.textColor : Color.WHITE);
                g.drawString(d.text, p[0] + r + 4, p[1] + fs / 3);
            }
        }
    }

    // ============================================================
    //  第 3 层：HUD
    // ============================================================
    private void drawHUD(Graphics2D g2, int W, int H) {
        g2.setFont(new Font(UI_FONT, Font.PLAIN, 13));

        g2.setColor(new Color(255, 255, 255, 200));
        g2.drawString(String.format("中心  经度 %.1f 度  纬度 %.1f 度  缩放 %.2f px/度",
                lonCenter, latCenter, ppd), 12, 22);

        g2.setColor(new Color(180, 180, 180, 200));
        g2.drawString("拖拽旋转 · 滚轮缩放", 12, 42);

        MouseSnapshot s = mouseSnap;
        if (s != null) {
            g2.setColor(new Color(120, 220, 255, 230));
            g2.drawString(String.format("鼠标  经度 %.2f 度  纬度 %.2f 度",
                    s.lon, s.lat), 12, 62);
        } else {
            g2.setColor(new Color(140, 140, 140, 200));
            g2.drawString("鼠标  移出画板", 12, 62);
        }
    }

    // ==================== 工具 ====================
    private static double normalizeLon(double lon) {
        lon = lon % 360;
        if (lon >= 180) lon -= 360;
        if (lon < -180) lon += 360;
        return lon;
    }
    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
    private static void runOnEDT(Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) r.run();
        else SwingUtilities.invokeLater(r);
    }
}