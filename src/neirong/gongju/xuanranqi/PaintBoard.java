package neirong.gongju.xuanranqi;

import neirong.gongju.zhujie.LiveBinder;
import neirong.gongju.zhujie.LiveRegistry;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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

    // ============================================================
    //  字体 / Stroke 缓存
    // ============================================================
    private static final Font[] FONT_CACHE = new Font[256];

    private static Font uiFont(int size) {
        if (size < 1) size = 1;
        if (size > 255) return new Font(UI_FONT, Font.PLAIN, size);
        Font f = FONT_CACHE[size];
        if (f == null) {
            f = new Font(UI_FONT, Font.PLAIN, size);
            FONT_CACHE[size] = f;
        }
        return f;
    }

    private static final Map<Float, BasicStroke> STROKE_CACHE = new ConcurrentHashMap<>(8);

    private static BasicStroke strokeOf(float w) {
        Float key = w;
        BasicStroke s = STROKE_CACHE.get(key);
        if (s == null) {
            s = new BasicStroke(w);
            STROKE_CACHE.put(key, s);
        }
        return s;
    }

    // ============================================================
    //  4 个缩放控制变量
    // ============================================================
    private float buttonMinScale = 2f;
    private float buttonMaxScale = 5f;
    private float textMinScale   = 2f;
    private float textMaxScale   = 5f;

    public float getButtonMinScale() { return buttonMinScale; }
    public void setButtonMinScale(float v) {
        this.buttonMinScale = Math.max(0.01f, v);
        if (this.buttonMaxScale < this.buttonMinScale) {
            this.buttonMaxScale = this.buttonMinScale;
        }
    }
    public float getButtonMaxScale() { return buttonMaxScale; }
    public void setButtonMaxScale(float v) {
        this.buttonMaxScale = Math.max(this.buttonMinScale, v);
    }
    public float getTextMinScale() { return textMinScale; }
    public void setTextMinScale(float v) {
        this.textMinScale = Math.max(0.01f, v);
        if (this.textMaxScale < this.textMinScale) {
            this.textMaxScale = this.textMinScale;
        }
    }
    public float getTextMaxScale() { return textMaxScale; }
    public void setTextMaxScale(float v) {
        this.textMaxScale = Math.max(this.textMinScale, v);
    }

    public void setScaleLimits(float buttonMin, float buttonMax,
                               float textMin,   float textMax) {
        setButtonMinScale(buttonMin);
        setButtonMaxScale(buttonMax);
        setTextMinScale(textMin);
        setTextMaxScale(textMax);
    }

    public void applyScaleLimitsToAll() {
        runOnEDT(() -> {
            for (int i = 0, n = geoComponents.size(); i < n; i++) {
                GeoComponent g = geoComponents.get(i);
                g.minScale = buttonMinScale;
                g.maxScale = buttonMaxScale;
            }
            for (int i = 0, n = geoTexts.size(); i < n; i++) {
                GeoText t = geoTexts.get(i);
                t.minScale = textMinScale;
                t.maxScale = textMaxScale;
            }
            layoutGeoComponents();
            repaint();
        });
    }

    // ============================================================
    //  实时数据源接口
    // ============================================================
    public interface Source {
        double getLon();
        double getLat();
        default Color   getColor()    { return null; }
        default String  getText()     { return null; }
        default Double  getRadius()   { return null; }
        default Float   getFontSize() { return null; }
        default Boolean getVisible()  { return null; }
    }

    // ==================== 清晰度常量 ====================
    private static final float MIN_SCREEN_FONT_SIZE = 8f;
    private static final float MAX_SCREEN_FONT_SIZE = 200f;
    private double minLiveDotRadiusPx = 0.6;
    public void setMinLiveDotRadiusPx(double r) { this.minLiveDotRadiusPx = Math.max(0, r); }
    public double getMinLiveDotRadiusPx() { return minLiveDotRadiusPx; }

    // ============================================================
    //  ★ 磁盘金字塔（全图存硬盘，每帧只读可见区域）
    // ============================================================
    private static final int WORLD_BASE_PPD = 32;   // 最底层 PPD
    private static final int WORLD_LEVELS   = 6;    // 32,16,8,4,2,1

    private static final int TRAIL_BASE_PPD = 16;
    private static final int TRAIL_LEVELS   = 5;    // 16,8,4,2,1

    // 一次性使用的高分辨率临时画布（rebuild 时用，用完即 flush）
    private static final int WORLD_W_HIGH = 360 * WORLD_BASE_PPD;
    private static final int WORLD_H_HIGH = 180 * WORLD_BASE_PPD;

    private MappedPyramid worldPyramid;
    private File pyramidDir;

    // ★ 轨迹层改为“按 owner 分层”，每个 owner 一份金字塔
    //   GLOBAL_TRAIL_OWNER 用于兼容原来的“全局轨迹”接口。
    private static final Object GLOBAL_TRAIL_OWNER = new Object();

    /** ★ 每个 owner 的轨迹层。 */
    private static final class OwnerTrail {
        final Object owner;
        final MappedPyramid pyramid;
        volatile boolean hasAny = false;
        OwnerTrail(Object owner, MappedPyramid pyramid) {
            this.owner = owner;
            this.pyramid = pyramid;
        }
    }

    private final Map<Object, OwnerTrail> ownerTrails = new ConcurrentHashMap<>();

    /** ★ 轨迹像素回调接口。只有实现了此接口的 owner，才会收到 meichuzhixing 回调。 */
    public interface TrailPixelSink {
        void meichuzhixing(double jingdu, double weidu);
    }

    private volatile boolean worldDirty = true;
    private volatile boolean hasAnyTrails = false;
    private boolean bakingMode = false;

    private int batchDepth = 0;

    // ============================================================
    //  非 Live 点烘焙开关
    // ============================================================
    private volatile boolean bakeStaticDots = true;

    public boolean isBakeStaticDots() { return bakeStaticDots; }

    public void setBakeStaticDots(boolean on) {
        if (this.bakeStaticDots == on) return;
        this.bakeStaticDots = on;
        if (on) markWorldDirty();
        repaint();
    }

    // ============================================================
    //  轨迹层开关
    // ============================================================
    private volatile boolean trailEnabled = true;
    public boolean isTrailEnabled() { return trailEnabled; }
    public void setTrailEnabled(boolean on) { this.trailEnabled = on; }

    private boolean trailAntialias = true;
    public boolean isTrailAntialias() { return trailAntialias; }
    public void setTrailAntialias(boolean on) { this.trailAntialias = on; }

    private boolean trailForceOpaque = true;
    public boolean isTrailForceOpaque() { return trailForceOpaque; }
    public void setTrailForceOpaque(boolean on) { this.trailForceOpaque = on; }

    // ★ 惰性创建某个 owner 的轨迹层
    private OwnerTrail ensureOwnerTrail(Object owner) {
        if (owner == null) owner = GLOBAL_TRAIL_OWNER;
        OwnerTrail ot = ownerTrails.get(owner);
        if (ot != null) return ot;
        synchronized (ownerTrails) {
            ot = ownerTrails.get(owner);
            if (ot != null) return ot;
            try {
                File dir = new File(pyramidDir,
                        "trail_" + Integer.toHexString(System.identityHashCode(owner))
                                + "_" + ownerTrails.size());
                MappedPyramid p = new MappedPyramid(dir, TRAIL_BASE_PPD, TRAIL_LEVELS);
                ot = new OwnerTrail(owner, p);
                ownerTrails.put(owner, ot);
            } catch (IOException e) {
                throw new RuntimeException("无法创建轨迹金字塔", e);
            }
        }
        return ot;
    }

    /** 清空全部轨迹（所有 owner、所有层级）。 */
    public void clearTrails() {
        runOnEDT(() -> {
            for (OwnerTrail ot : ownerTrails.values()) {
                ot.pyramid.clear();
                ot.hasAny = false;
            }
            hasAnyTrails = false;
            repaint();
        });
    }

    /** ★ 清空某个 owner 的轨迹。 */
    public void clearTrails(Object owner) {
        final Object fOwner = (owner != null) ? owner : GLOBAL_TRAIL_OWNER;
        runOnEDT(() -> {
            OwnerTrail ot = ownerTrails.get(fOwner);
            if (ot == null) return;
            ot.pyramid.clear();
            ot.hasAny = false;
            boolean any = false;
            for (OwnerTrail x : ownerTrails.values()) {
                if (x.hasAny) { any = true; break; }
            }
            hasAnyTrails = any;
            repaint();
        });
    }

    /** ★ 移除某个 owner 的轨迹层（释放磁盘资源）。 */
    public void removeOwnerTrails(Object owner) {
        if (owner == null || owner == GLOBAL_TRAIL_OWNER) return;
        runOnEDT(() -> {
            OwnerTrail ot = ownerTrails.remove(owner);
            if (ot == null) return;
            try { ot.pyramid.close(); } catch (Exception ignored) {}
            boolean any = false;
            for (OwnerTrail x : ownerTrails.values()) {
                if (x.hasAny) { any = true; break; }
            }
            hasAnyTrails = any;
            repaint();
        });
    }

    /**
     * 追加一条轨迹线段到全局层（旧接口，保持兼容）。
     */
    public void addTrailSegment(double lon1, double lat1,
                                double lon2, double lat2,
                                Color color, float screenWidthPx) {
        addTrailSegment(GLOBAL_TRAIL_OWNER, lon1, lat1, lon2, lat2, color, screenWidthPx);
    }

    /**
     * ★ 追加一条轨迹线段到指定 owner 的轨迹层。
     * <p>只有该 owner 实现了 {@link TrailPixelSink} 时，绘制它自己的轨迹像素才会回调。</p>
     */
    public void addTrailSegment(Object owner,
                                double lon1, double lat1,
                                double lon2, double lat2,
                                Color color, float screenWidthPx) {
        if (!trailEnabled) return;
        if (color == null) color = Color.WHITE;
        if (screenWidthPx <= 0f) screenWidthPx = 1f;

        final Object fOwner = (owner != null) ? owner : GLOBAL_TRAIL_OWNER;
        final OwnerTrail ot = ensureOwnerTrail(fOwner);

        Color baseColor = color;
        if (trailForceOpaque && color.getAlpha() != 255) {
            baseColor = new Color(color.getRGB() | 0xFF000000, true);
        }

        final Color   fColor   = baseColor;
        final float   fScreenW = screenWidthPx;
        final double  fLon1 = lon1, fLat1 = lat1;
        final double  fLon2 = lon2, fLat2 = lat2;
        final boolean fAA = trailAntialias;

        runOnEDT(() -> {
            double p = ppd;
            if (!(p > 0)) p = 1;

            for (int lv = 0; lv < ot.pyramid.levels; lv++) {
                MappedImage img = ot.pyramid.get(lv);
                int srcPpd = ot.pyramid.getPpd(lv);

                double nLon1 = normalizeLon(fLon1);
                double dLon = fLon2 - fLon1;
                dLon = ((dLon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
                double nLon2 = nLon1 + dLon;

                double x1 = (nLon1 + 180.0) * srcPpd;
                double y1 = (90 - fLat1) * srcPpd;
                double x2 = (nLon2 + 180.0) * srcPpd;
                double y2 = (90 - fLat2) * srcPpd;

                float bw = (float) Math.max(1.0, fScreenW * srcPpd / p);
                double pad = bw + 1;

                int bx1 = (int) Math.floor(Math.min(x1, x2) - pad);
                int by1 = (int) Math.floor(Math.min(y1, y2) - pad);
                int bx2 = (int) Math.ceil (Math.max(x1, x2) + pad);
                int by2 = (int) Math.ceil (Math.max(y1, y2) + pad);

                bx1 = Math.max(0, bx1);
                by1 = Math.max(0, by1);
                bx2 = Math.min(img.getWidth(),  bx2);
                by2 = Math.min(img.getHeight(), by2);
                if (bx1 >= bx2 || by1 >= by2) continue;

                int rw = bx2 - bx1, rh = by2 - by1;

                BufferedImage tile = img.readRegion(bx1, by1, rw, rh);
                if (tile == null) continue;

                Graphics2D g = tile.createGraphics();
                try {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            fAA ? RenderingHints.VALUE_ANTIALIAS_ON
                                    : RenderingHints.VALUE_ANTIALIAS_OFF);
                    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                            RenderingHints.VALUE_STROKE_PURE);
                    g.setColor(fColor);
                    g.setStroke(new BasicStroke(bw,
                            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.draw(new Line2D.Double(x1 - bx1, y1 - by1,
                            x2 - bx1, y2 - by1));
                } finally {
                    g.dispose();
                }

                img.writeRect(tile, bx1, by1);
            }

            ot.hasAny = true;
            hasAnyTrails = true;
            repaint();
        });
    }

    /** 追加一个轨迹点（全局层）。 */
    public void addTrailDot(double lon, double lat,
                            double radiusDeg, Color color) {
        addTrailDot(GLOBAL_TRAIL_OWNER, lon, lat, radiusDeg, color);
    }

    /** ★ 追加一个轨迹点到指定 owner 的轨迹层。 */
    public void addTrailDot(Object owner, double lon, double lat,
                            double radiusDeg, Color color) {
        if (!trailEnabled) return;
        if (color == null) color = Color.WHITE;
        double p = ppd;
        if (!(p > 0)) p = 1;
        float w = (float) Math.max(1.0, 2.0 * radiusDeg * p);
        addTrailSegment(owner, lon, lat, lon, lat, color, w);
    }

    // ==================== 静态颜色/字体 ====================
    private static final Color GRID_COLOR_PRIME  = new Color(255, 200, 100, 220);
    private static final Color GRID_COLOR_NORMAL = new Color(70, 70, 70, 160);
    private static final Color HUD_COLOR_MAIN    = new Color(255, 255, 255, 200);
    private static final Color HUD_COLOR_TIP     = new Color(180, 180, 180, 200);
    private static final Color HUD_COLOR_MOUSE   = new Color(120, 220, 255, 230);
    private static final Color HUD_COLOR_NOMSE   = new Color(140, 140, 140, 200);
    private static final Font  HUD_FONT          = new Font(UI_FONT, Font.PLAIN, 13);

    // ============================================================
    //  选择框
    // ============================================================
    private Point selectionStart = null;
    private Point selectionEnd   = null;
    private volatile boolean selectionActive = false;

    private Color selectionFillColor   = new Color(0, 120, 215, 60);
    private Color selectionBorderColor = new Color(0, 120, 215, 220);

    public boolean isSelectionActive() { return selectionActive; }

    public void setSelectionColors(Color fill, Color border) {
        if (fill   != null) selectionFillColor   = fill;
        if (border != null) selectionBorderColor = border;
        repaint();
    }

    public void beginSelection(int x, int y) {
        selectionStart  = new Point(x, y);
        selectionEnd    = new Point(x, y);
        selectionActive = true;
        repaint();
    }

    public void updateSelection(int x, int y) {
        if (!selectionActive) return;
        selectionEnd = new Point(x, y);
        repaint();
    }

    public Rectangle endSelection() {
        if (!selectionActive) return null;
        Rectangle r = getSelectionRect();
        selectionActive = false;
        selectionStart  = null;
        selectionEnd    = null;
        repaint();
        return r;
    }

    public void cancelSelection() {
        if (!selectionActive) return;
        selectionActive = false;
        selectionStart  = null;
        selectionEnd    = null;
        repaint();
    }

    public Rectangle getSelectionRect() {
        if (!selectionActive || selectionStart == null || selectionEnd == null) return null;
        int x1 = Math.min(selectionStart.x, selectionEnd.x);
        int y1 = Math.min(selectionStart.y, selectionEnd.y);
        int x2 = Math.max(selectionStart.x, selectionEnd.x);
        int y2 = Math.max(selectionStart.y, selectionEnd.y);
        if (x2 - x1 <= 0 && y2 - y1 <= 0) return null;
        return new Rectangle(x1, y1, x2 - x1, y2 - y1);
    }

    // ==================== 鼠标快照 ====================
    public static final class MouseSnapshot {
        public final double lon;
        public final double lat;
        MouseSnapshot(double lon, double lat) { this.lon = lon; this.lat = lat; }
    }
    public  MouseSnapshot mouseSnap = null;

    // ==================== 世界边界 ====================
    private static final double WORLD_LAT_MIN = -90;
    private static final double WORLD_LAT_MAX =  90;

    // ==================== 视图状态 ====================
    private double lonCenter = 0;
    private double latCenter = 0;
    private double ppd = 6;

    private double minPpd = 6;
    private double maxPpd = 400;

    private Double lonBoundMin = null, lonBoundMax = null;
    private Double latBoundMin = null, latBoundMax = null;

    private static final double GRID_STEP = 15;

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

        public boolean visible = true;
        public boolean screenSpace = false;
        public Source source = null;

        public transient double cachedSx;
        public transient double cachedSy;

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
    private final List<dian> liveDots = new ArrayList<>();
    private final List<dian> visibleLiveDots = new ArrayList<>();

    private double cachedVpLonCenter = Double.NaN;
    private double cachedVpLatCenter = Double.NaN;
    private double cachedVpPpd = Double.NaN;
    private int cachedVpW = -1;
    private int cachedVpH = -1;
    private int cachedLiveSize = -1;

    // ============================================================
    //  Live 直线
    // ============================================================
    public static class LiveLine {
        public int bianhao;
        public double lon1, lat1, lon2, lat2;
        public Color  color;
        public float  strokeWidth;
        public boolean visible = true;
        public Object tag = null;

        LiveLine(int bianhao,
                 double lon1, double lat1, double lon2, double lat2,
                 Color color, float strokeWidth) {
            this.bianhao = bianhao;
            this.lon1 = lon1; this.lat1 = lat1;
            this.lon2 = lon2; this.lat2 = lat2;
            this.color = color;
            this.strokeWidth = strokeWidth;
        }
    }

    private final List<LiveLine> liveLines = new ArrayList<>();
    private final AtomicInteger nextLiveLineBianhao = new AtomicInteger(1);

    // ============================================================
    //  跟随地图的 Swing 组件
    // ============================================================
    private static final class GeoComponent {
        final int bianhao;
        final JComponent comp;
        double lon, lat;
        final int anchorX, anchorY;
        int baseW, baseH;
        final double basePpd;
        final Font baseFont;
        boolean scaleWithZoom;
        float currentFontSize = -1f;
        Source source = null;
        boolean visibleBySource = true;

        float minScale = 0.4f;
        float maxScale = 2.5f;

        GeoComponent(int bianhao, JComponent comp, double lon, double lat,
                     int ax, int ay, int baseW, int baseH,
                     double basePpd, Font baseFont, boolean scaleWithZoom) {
            this.bianhao = bianhao;
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

    private volatile boolean geoLayoutScheduled = false;
    private final PropertyChangeListener geoCompPropListener = new PropertyChangeListener() {
        @Override public void propertyChange(PropertyChangeEvent evt) {
            if ("preferredSize".equals(evt.getPropertyName())) {
                scheduleGeoLayout();
            }
        }
    };

    private void scheduleGeoLayout() {
        if (geoLayoutScheduled) return;
        geoLayoutScheduled = true;
        Runnable r = () -> {
            geoLayoutScheduled = false;
            for (int i = 0, n = geoComponents.size(); i < n; i++) {
                GeoComponent g = geoComponents.get(i);
                Dimension d = g.comp.getPreferredSize();
                if (d != null) {
                    if (d.width  > 0) g.baseW = d.width;
                    if (d.height > 0) g.baseH = d.height;
                }
            }
            layoutGeoComponents();
            repaint();
        };
        SwingUtilities.invokeLater(r);
    }

    // ============================================================
    //  跟随地图的文本
    // ============================================================
    public static class GeoText {
        public int bianhao = 0;

        public double lon, lat;
        public String text;
        public Color color;
        public float  baseFontSize;
        public boolean scaleWithZoom;
        public double offsetX = 0, offsetY = 0;
        final double basePpd;

        public JComponent attachedTo = null;
        public Color outlineColor = null;
        public float outlineWidth = 2f;
        public int fontStyle = Font.BOLD;

        public Source source = null;
        public boolean visible = true;

        public float minScale = 0.4f;
        public float maxScale = 2.5f;

        transient Font cachedFont = null;
        transient float cachedFontSize = -1f;
        transient int   cachedFontStyle = -1;
        transient FontMetrics cachedFm = null;
        transient Font cachedFmFont = null;

        GeoText(double lon, double lat, String text, float fontSize, Color color,
                boolean scaleWithZoom, double basePpd, int bianhao) {
            this.lon = lon; this.lat = lat;
            this.text = text;
            this.baseFontSize = fontSize;
            this.color = color;
            this.scaleWithZoom = scaleWithZoom;
            this.basePpd = basePpd;
            this.bianhao = bianhao;
        }

        Font fontFor(float size) {
            if (cachedFont == null
                    || Math.abs(cachedFontSize - size) > 0.01f
                    || cachedFontStyle != fontStyle) {
                cachedFont = new Font(UI_FONT, fontStyle, 12).deriveFont(size);
                cachedFontSize = size;
                cachedFontStyle = fontStyle;
            }
            return cachedFont;
        }
    }
    private final List<GeoText> geoTexts = new ArrayList<>();

    // ============================================================
    //  编号分配器
    // ============================================================
    private final AtomicInteger nextAnniuBianhao  = new AtomicInteger(1);
    private final AtomicInteger nextWenbenBianhao = new AtomicInteger(1);

    private GeoComponent findAnniu(int bianhao) {
        if (bianhao <= 0) return null;
        for (int i = 0, n = geoComponents.size(); i < n; i++) {
            GeoComponent g = geoComponents.get(i);
            if (g.bianhao == bianhao) return g;
        }
        return null;
    }

    private GeoText findWenben(int bianhao) {
        if (bianhao <= 0) return null;
        for (int i = 0, n = geoTexts.size(); i < n; i++) {
            GeoText t = geoTexts.get(i);
            if (t.bianhao == bianhao) return t;
        }
        return null;
    }

    // ==================== 拖拽 ====================
    private Point dragStart;
    private double startLon, startLat;
    private boolean dragging;

    // ============================================================
    //  帧率统计
    // ============================================================
    private final AtomicInteger frameCounter = new AtomicInteger(0);
    private final AtomicInteger currentFps   = new AtomicInteger(0);

    private final ScheduledExecutorService fpsMonitor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "fps-monitor");
                t.setDaemon(true);
                t.setPriority(Thread.MIN_PRIORITY);
                return t;
            });

    private void tickFrame() { frameCounter.incrementAndGet(); }

    public int getFps() { return currentFps.get(); }

    public int sampleFpsNow() {
        int v = frameCounter.getAndSet(0);
        currentFps.set(v);
        return v;
    }

    public double[] shibiaojingweidu() {
        MouseSnapshot s = mouseSnap;
        if (s == null) return null;
        return new double[]{ s.lon, s.lat };
    }

    // ============================================================
    //  批绘制缓存
    // ============================================================
    private final Map<Color, Path2D.Double> batchPathCache = new HashMap<>();
    private final List<dian> individualDotCache = new ArrayList<>();

    private void resetBatchCache() {
        for (Path2D.Double p : batchPathCache.values()) p.reset();
        individualDotCache.clear();
    }

    private Path2D.Double pathFor(Color c) {
        Path2D.Double p = batchPathCache.get(c);
        if (p == null) {
            p = new Path2D.Double();
            batchPathCache.put(c, p);
        }
        return p;
    }

    private static boolean hasContent(Path2D.Double p) {
        return !p.getPathIterator(null).isDone();
    }

    // ============================================================
    //  取色离屏位图复用
    // ============================================================
    private BufferedImage pickBuffer;
    private int pickBufferW = -1, pickBufferH = -1;

    private BufferedImage ensurePickBuffer(int W, int H) {
        if (pickBuffer == null || pickBufferW != W || pickBufferH != H) {
            pickBuffer = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
            pickBufferW = W;
            pickBufferH = H;
        }
        return pickBuffer;
    }

    // ============================================================
    //  构造
    // ============================================================
    public PaintBoard() {
        // 创建临时目录 + 金字塔
        this.setLayout(null);
        pyramidDir = new File(System.getProperty("java.io.tmpdir"),
                "paintboard_" + Integer.toHexString(System.identityHashCode(this))
                        + "_" + System.currentTimeMillis());
        try {
            worldPyramid = new MappedPyramid(
                    new File(pyramidDir, "world"), WORLD_BASE_PPD, WORLD_LEVELS);

            // ★ 创建全局轨迹层
            OwnerTrail globalTrail = new OwnerTrail(
                    GLOBAL_TRAIL_OWNER,
                    new MappedPyramid(new File(pyramidDir, "trail"),
                            TRAIL_BASE_PPD, TRAIL_LEVELS));
            ownerTrails.put(GLOBAL_TRAIL_OWNER, globalTrail);
        } catch (IOException e) {
            throw new RuntimeException("无法创建磁盘金字塔", e);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { worldPyramid.close(); } catch (Exception ignored) {}
            for (OwnerTrail ot : ownerTrails.values()) {
                try { ot.pyramid.close(); } catch (Exception ignored) {}
            }
            deleteRecursively(pyramidDir);
        }));

        setBackground(Color.BLACK);
        setPreferredSize(new Dimension(900, 600));
        setLayout(null);

        MouseAdapter ma = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) return;

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
                if (!dragging) {
                    updateMouse(e);
                    return;
                }
                if (!SwingUtilities.isLeftMouseButton(e)) return;

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

        fpsMonitor.scheduleAtFixedRate(() -> {
            int frames = frameCounter.getAndSet(0);
            currentFps.set(frames);
        }, 1, 1, TimeUnit.SECONDS);
    }

    private static void deleteRecursively(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] ks = f.listFiles();
            if (ks != null) for (File k : ks) deleteRecursively(k);
        }
        f.delete();
    }

    private void updateMouse(MouseEvent e) {
        if (getWidth() <= 0 || getHeight() <= 0) return;
        double[] ll = screenToLonLat(e.getX(), e.getY());
        mouseSnap = new MouseSnapshot(ll[0], ll[1]);
    }

    private void markWorldDirty() { worldDirty = true; }

    public void repaintStatic() { markWorldDirty(); repaint(); }

    // ============================================================
    //  批量模式
    // ============================================================
    public void beginBatch() {
        if (SwingUtilities.isEventDispatchThread()) batchDepth++;
        else SwingUtilities.invokeLater(this::beginBatch);
    }

    public void endBatch() {
        Runnable r = () -> {
            if (batchDepth > 0) batchDepth--;
            if (batchDepth == 0) { markWorldDirty(); repaint(); }
        };
        if (SwingUtilities.isEventDispatchThread()) r.run();
        else SwingUtilities.invokeLater(r);
    }

    // ============================================================
    //  单个对象覆盖缩放限制
    // ============================================================
    public void setComponentScaleLimit(JComponent comp, float min, float max) {
        if (comp == null) return;
        runOnEDT(() -> {
            for (int i = 0, n = geoComponents.size(); i < n; i++) {
                GeoComponent g = geoComponents.get(i);
                if (g.comp == comp) {
                    g.minScale = Math.max(0.01f, min);
                    g.maxScale = Math.max(g.minScale, max);
                    layoutGeoComponents();
                    repaint();
                    return;
                }
            }
        });
    }

    public void setComponentScaleLimitByBianhao(int bianhao, float min, float max) {
        runOnEDT(() -> {
            GeoComponent g = findAnniu(bianhao);
            if (g == null) return;
            g.minScale = Math.max(0.01f, min);
            g.maxScale = Math.max(g.minScale, max);
            layoutGeoComponents();
            repaint();
        });
    }

    public void setTextScaleLimit(int bianhao, float min, float max) {
        runOnEDT(() -> {
            GeoText t = findWenben(bianhao);
            if (t == null) return;
            t.minScale = Math.max(0.01f, min);
            t.maxScale = Math.max(t.minScale, max);
            repaint();
        });
    }

    // ============================================================
    //  圆点 API —— 烘焙版
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
        d.screenSpace = false;
        geoDots.add(d);
        if (batchDepth == 0) {
            if (bakeStaticDots) markWorldDirty();
            repaint();
        }
        return d;
    }

    public dian addGeoDot(double lon, double lat, double radiusDeg,
                          Color color, Source source) {
        dian d = new dian(lon, lat, radiusDeg,
                color, null, null, null, false, 0f);
        d.screenSpace = false;
        d.source = source;
        geoDots.add(d);
        if (batchDepth == 0) {
            if (bakeStaticDots) markWorldDirty();
            repaint();
        }
        return d;
    }

    // ============================================================
    //  圆点 API —— Live 版
    // ============================================================
    public dian addGeoDotLive(double lon, double lat, double radiusDeg, Color color) {
        dian d = new dian(lon, lat, radiusDeg, color, null, null, null, false, 0f);
        d.screenSpace = true;
        geoDots.add(d);
        liveDots.add(d);
        if (batchDepth == 0) repaint();
        return d;
    }

    public dian addGeoDotLive(double lon, double lat, double radiusDeg,
                              Color color, Color borderColor, float borderWidth) {
        dian d = new dian(lon, lat, radiusDeg,
                color, borderColor, null, null, false, borderWidth);
        d.screenSpace = true;
        geoDots.add(d);
        liveDots.add(d);
        if (batchDepth == 0) repaint();
        return d;
    }

    public dian addGeoDotLive(double lon, double lat, double radiusDeg,
                              Color color, Source source) {
        dian d = new dian(lon, lat, radiusDeg, color, null, null, null, false, 0f);
        d.screenSpace = true;
        d.source = source;
        geoDots.add(d);
        liveDots.add(d);
        if (batchDepth == 0) repaint();
        return d;
    }

    // ============================================================
    //  Live 直线 API
    // ============================================================
    public int addLiveLine(double lon1, double lat1,
                           double lon2, double lat2) {
        return addLiveLine(lon1, lat1, lon2, lat2, Color.WHITE, 2f);
    }

    public int addLiveLine(double lon1, double lat1,
                           double lon2, double lat2,
                           Color color) {
        return addLiveLine(lon1, lat1, lon2, lat2, color, 2f);
    }

    public int addLiveLine(double lon1, double lat1,
                           double lon2, double lat2,
                           Color color, float strokeWidth) {
        final int bianhao = nextLiveLineBianhao.getAndIncrement();
        final LiveLine L = new LiveLine(bianhao, lon1, lat1, lon2, lat2,
                color, strokeWidth);
        runOnEDT(() -> {
            liveLines.add(L);
            repaint();
        });
        return bianhao;
    }

    public void removeLiveLine(int bianhao) {
        if (bianhao <= 0) return;
        runOnEDT(() -> {
            for (int i = 0, n = liveLines.size(); i < n; i++) {
                if (liveLines.get(i).bianhao == bianhao) {
                    liveLines.remove(i);
                    repaint();
                    return;
                }
            }
        });
    }

    public void clearLiveLines() {
        runOnEDT(() -> {
            if (liveLines.isEmpty()) return;
            liveLines.clear();
            repaint();
        });
    }

    public LiveLine getLiveLine(int bianhao) {
        if (bianhao <= 0) return null;
        for (int i = 0, n = liveLines.size(); i < n; i++) {
            LiveLine L = liveLines.get(i);
            if (L.bianhao == bianhao) return L;
        }
        return null;
    }

    public void setLiveLineVisible(int bianhao, boolean visible) {
        runOnEDT(() -> {
            LiveLine L = getLiveLine(bianhao);
            if (L == null || L.visible == visible) return;
            L.visible = visible;
            repaint();
        });
    }

    public int getLiveLineCount() { return liveLines.size(); }

    // ============================================================
    //  全自动绑定 API
    // ============================================================
    public dian addAutoDot(Object target, double radiusDeg) {
        Source s = LiveBinder.toSource(target);
        dian d = new dian(s.getLon(), s.getLat(), radiusDeg,
                s.getColor(), null, null, s.getText(), false, 0f);
        d.source = s;
        d.screenSpace = false;
        geoDots.add(d);
        LiveRegistry.bind(target, this);
        if (batchDepth == 0) {
            if (bakeStaticDots) markWorldDirty();
            repaint();
        }
        return d;
    }

    public dian addAutoDot(Object pos, Object colorObj, double radiusDeg) {
        Source ps = LiveBinder.toSource(pos);
        Source cs = (colorObj == null) ? null : LiveBinder.toSource(colorObj);

        Color init = (cs != null ? cs.getColor() : ps.getColor());
        dian d = new dian(ps.getLon(), ps.getLat(), radiusDeg,
                init, null, null, ps.getText(), false, 0f);

        if (cs == null) {
            d.source = ps;
        } else {
            d.source = new Source() {
                @Override public double getLon() { return ps.getLon(); }
                @Override public double getLat() { return ps.getLat(); }
                @Override public Color getColor() {
                    Color c = cs.getColor();
                    return (c != null) ? c : ps.getColor();
                }
                @Override public String  getText()    { return ps.getText(); }
                @Override public Double  getRadius()  { return ps.getRadius(); }
                @Override public Float   getFontSize(){ return ps.getFontSize(); }
                @Override public Boolean getVisible() { return ps.getVisible(); }
            };
        }
        d.screenSpace = false;
        geoDots.add(d);

        LiveRegistry.bind(pos, this);
        if (colorObj != null) LiveRegistry.bind(colorObj, this);

        if (batchDepth == 0) {
            if (bakeStaticDots) markWorldDirty();
            repaint();
        }
        return d;
    }

    public int addAutoText(Object target, int fontSize) {
        Source s = LiveBinder.toSource(target);
        GeoText t = addGeoTextInternal(
                s.getText(), s.getLon(), s.getLat(),
                fontSize, s.getColor(), true);
        t.minScale = textMinScale;
        t.maxScale = textMaxScale;
        t.source = s;
        LiveRegistry.bind(target, this);
        return t.bianhao;
    }

    public int addAutoText(Object target, int fontSize, JComponent attachedTo) {
        Source s = LiveBinder.toSource(target);
        GeoText t = addGeoTextInternal(
                s.getText(), s.getLon(), s.getLat(),
                fontSize, s.getColor(), true);
        t.attachedTo = attachedTo;
        t.minScale = textMinScale;
        t.maxScale = textMaxScale;
        t.source = s;
        LiveRegistry.bind(target, this);
        return t.bianhao;
    }

    public int addAutoComponent(JComponent comp, Object target) {
        if (comp == null) return -1;
        Source s = LiveBinder.toSource(target);
        Dimension d = comp.getPreferredSize();
        int bianhao = addGeoComponentInternal2(
                comp, s.getLon(), s.getLat(),
                -d.width / 2, -d.height / 2, true, s);
        LiveRegistry.bind(target, this);
        return bianhao;
    }

    public int addAutoComponent(JComponent comp, Object target,
                                int anchorX, int anchorY) {
        if (comp == null) return -1;
        Source s = LiveBinder.toSource(target);
        int bianhao = addGeoComponentInternal2(
                comp, s.getLon(), s.getLat(), anchorX, anchorY, true, s);
        LiveRegistry.bind(target, this);
        return bianhao;
    }

    // ============================================================
    //  圆点修改
    // ============================================================
    public void setGeoDotScreenSpace(dian d, boolean on) {
        if (d == null) return;
        runOnEDT(() -> {
            if (d.screenSpace == on) return;
            d.screenSpace = on;
            if (on) { if (!liveDots.contains(d)) liveDots.add(d); }
            else    { liveDots.remove(d); }
            markWorldDirty();
            repaint();
        });
    }

    public void removeGeoDot(dian d) {
        if (d == null) return;
        geoDots.remove(d);
        liveDots.remove(d);
        markWorldDirty();
        repaint();
    }

    public void clearGeoDots() {
        geoDots.clear();
        liveDots.clear();
        visibleLiveDots.clear();
        markWorldDirty();
        repaint();
    }

    public void setGeoDotColor(dian d, Color color) {
        if (d == null) return;
        runOnEDT(() -> {
            d.color = color;
            if (!d.screenSpace && bakeStaticDots) markWorldDirty();
            repaint();
        });
    }
    public void setGeoDotBorderColor(dian d, Color color) {
        if (d == null) return;
        runOnEDT(() -> {
            d.borderColor = color;
            if (!d.screenSpace && bakeStaticDots) markWorldDirty();
            repaint();
        });
    }
    public void setGeoDotTextColor(dian d, Color color) {
        if (d == null) return;
        runOnEDT(() -> {
            d.textColor = color;
            if (!d.screenSpace && bakeStaticDots) markWorldDirty();
            repaint();
        });
    }
    public void setGeoDotText(dian d, String text) {
        if (d == null) return;
        runOnEDT(() -> {
            d.text = text;
            if (!d.screenSpace && bakeStaticDots) markWorldDirty();
            repaint();
        });
    }
    public void setGeoDotBorderWidth(dian d, float width) {
        if (d == null) return;
        runOnEDT(() -> {
            d.borderWidth = width;
            if (!d.screenSpace && bakeStaticDots) markWorldDirty();
            repaint();
        });
    }
    public void setGeoDotStyle(dian d, Color fill, Color border, Color text) {
        if (d == null) return;
        runOnEDT(() -> {
            d.color = fill; d.borderColor = border; d.textColor = text;
            if (!d.screenSpace && bakeStaticDots) markWorldDirty();
            repaint();
        });
    }
    public void setGeoDotLonLat(dian d, double lon, double lat) {
        if (d == null) return;
        runOnEDT(() -> {
            d.lon = lon; d.lat = lat;
            if (!d.screenSpace && bakeStaticDots) markWorldDirty();
            repaint();
        });
    }
    public void setGeoDotRadius(dian d, double radiusDeg) {
        if (d == null) return;
        runOnEDT(() -> {
            d.radiusDeg = radiusDeg;
            if (!d.screenSpace && bakeStaticDots) markWorldDirty();
            repaint();
        });
    }
    public void setGeoDotVisible(dian d, boolean visible) {
        if (d == null) return;
        runOnEDT(() -> {
            d.visible = visible;
            if (!d.screenSpace && bakeStaticDots) markWorldDirty();
            repaint();
        });
    }

    // ============================================================
    //  地理文本 API
    // ============================================================
    private GeoText addGeoTextInternal(String text, double lon, double lat,
                                       int fontSize, Color color, boolean scaleWithZoom) {
        final int bianhao = nextWenbenBianhao.getAndIncrement();
        GeoText t = new GeoText(lon, lat, text, fontSize, color, scaleWithZoom,
                this.ppd, bianhao);
        t.outlineColor = new Color(0, 0, 0, 220);
        t.outlineWidth = 2.5f;
        t.minScale = textMinScale;
        t.maxScale = textMaxScale;
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> { geoTexts.add(t); repaint(); });
        } else {
            geoTexts.add(t);
            repaint();
        }
        return t;
    }

    public int addGeoText(String text, double lon, double lat, int fontSize) {
        return addGeoText(text, lon, lat, fontSize, Color.WHITE, true);
    }

    public int addGeoText(String text, double lon, double lat,
                          int fontSize, Color color) {
        return addGeoText(text, lon, lat, fontSize, color, true);
    }

    public int addGeoText(String text, double lon, double lat,
                          int fontSize, Color color, boolean scaleWithZoom) {
        GeoText t = addGeoTextInternal(text, lon, lat, fontSize, color, scaleWithZoom);
        return t.bianhao;
    }

    public int addGeoText(String text, double lon, double lat,
                          int fontSize, Color color, JComponent attachedTo) {
        GeoText t = addGeoTextInternal(text, lon, lat, fontSize, color, true);
        t.attachedTo = attachedTo;
        repaint();
        return t.bianhao;
    }

    public int addGeoText(String text, double lon, double lat,
                          int fontSize, Color color, JComponent attachedTo,
                          Source source) {
        GeoText t = addGeoTextInternal(text, lon, lat, fontSize, color, true);
        t.attachedTo = attachedTo;
        t.source = source;
        repaint();
        return t.bianhao;
    }

    public void removeGeoText(GeoText t) { if (t != null) { geoTexts.remove(t); repaint(); } }
    public void clearGeoTexts()          { geoTexts.clear();  repaint(); }

    public GeoText getWenbenObject(int bianhao) { return findWenben(bianhao); }

    // ============================================================
    //  setwenbenBYbianhao
    // ============================================================
    public void setwenbenBYbianhao(int bianhao, String text,
                                   double lon, double lat, int fontSize) {
        setWenben0(bianhao, text, lon, lat, fontSize, null, null, null, false);
    }

    public void setwenbenBYbianhao(int bianhao, String text,
                                   double lon, double lat, int fontSize, Color color) {
        setWenben0(bianhao, text, lon, lat, fontSize, color, null, null, false);
    }

    public void setwenbenBYbianhao(int bianhao, String text,
                                   double lon, double lat, int fontSize, Color color,
                                   boolean scaleWithZoom) {
        setWenben0(bianhao, text, lon, lat, fontSize, color, scaleWithZoom, null, false);
    }

    public void setwenbenBYbianhao(int bianhao, String text,
                                   double lon, double lat, int fontSize, Color color,
                                   JComponent attachedTo) {
        setWenben0(bianhao, text, lon, lat, fontSize, color, null, attachedTo, true);
    }

    private void setWenben0(final int bianhao, final String text,
                            final double lon, final double lat, final int fontSize,
                            final Color color, final Boolean scaleWithZoom,
                            final JComponent attachedTo, final boolean changeAttached) {
        if (bianhao <= 0) return;
        runOnEDT(() -> {
            GeoText t = findWenben(bianhao);
            if (t == null) return;

            if (text != null) t.text = text;
            t.lon = lon;
            t.lat = lat;
            if (fontSize > 0) t.baseFontSize = fontSize;
            if (color != null) t.color = color;
            if (scaleWithZoom != null) t.scaleWithZoom = scaleWithZoom;
            if (changeAttached) t.attachedTo = attachedTo;

            repaint();
        });
    }

    // ============================================================
    //  跟随地图的 Swing 组件 API
    // ============================================================
    public int addGeoComponent(JComponent comp, double lon, double lat) {
        if (comp == null) return -1;
        Dimension d = comp.getPreferredSize();
        return addGeoComponent(comp, lon, lat, -d.width / 2, -d.height / 2, true);
    }

    public int addGeoComponent(JComponent comp, double lon, double lat,
                               int anchorX, int anchorY) {
        return addGeoComponent(comp, lon, lat, anchorX, anchorY, true);
    }

    public int addGeoComponent(JComponent comp, double lon, double lat,
                               int anchorX, int anchorY, boolean scaleWithZoom) {
        return addGeoComponentInternal2(comp, lon, lat, anchorX, anchorY, scaleWithZoom, null);
    }

    public int addGeoComponent(JComponent comp, double lon, double lat, Source source) {
        if (comp == null) return -1;
        Dimension d = comp.getPreferredSize();
        return addGeoComponentInternal2(comp, lon, lat,
                -d.width / 2, -d.height / 2, true, source);
    }

    public int addGeoComponent(JComponent comp, double lon, double lat,
                               int anchorX, int anchorY, Source source) {
        return addGeoComponentInternal2(comp, lon, lat, anchorX, anchorY, true, source);
    }

    public int addGeoComponent(JComponent comp, double lon, double lat,
                               int anchorX, int anchorY, boolean scaleWithZoom,
                               Source source) {
        return addGeoComponentInternal2(comp, lon, lat, anchorX, anchorY, scaleWithZoom, source);
    }

    private int addGeoComponentInternal2(JComponent comp, double lon, double lat,
                                         int anchorX, int anchorY, boolean scaleWithZoom,
                                         Source source) {
        if (comp == null) return -1;
        final int bianhao = nextAnniuBianhao.getAndIncrement();
        final double flon = lon, flat = lat;
        final int fax = anchorX, fay = anchorY;
        final boolean fscale = scaleWithZoom;
        final Source fsource = source;

        Runnable r = () -> addGeoComponentInternal(bianhao, comp, flon, flat, fax, fay, fscale, fsource);
        if (SwingUtilities.isEventDispatchThread()) r.run();
        else                                        SwingUtilities.invokeLater(r);

        return bianhao;
    }

    private void addGeoComponentInternal(int bianhao, JComponent comp,
                                         double lon, double lat,
                                         int anchorX, int anchorY,
                                         boolean scaleWithZoom, Source source) {
        comp.setFocusable(false);
        Dimension pref = comp.getPreferredSize();
        int w = pref.width  > 0 ? pref.width  : 60;
        int h = pref.height > 0 ? pref.height : 24;

        Font f = comp.getFont();
        if (f == null) f = UIManager.getFont("Button.font");
        if (f == null) f = new Font(UI_FONT, Font.PLAIN, 12);

        add(comp);
        GeoComponent gc = new GeoComponent(
                bianhao, comp, lon, lat, anchorX, anchorY, w, h,
                this.ppd, f, scaleWithZoom);
        gc.source = source;
        gc.minScale = buttonMinScale;
        gc.maxScale = buttonMaxScale;
        geoComponents.add(gc);

        comp.addPropertyChangeListener("preferredSize", geoCompPropListener);

        layoutGeoComponents();
        repaint();
    }

    public void removeGeoComponent(JComponent comp) {
        if (comp == null) return;
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> removeGeoComponent(comp));
            return;
        }
        geoComponents.removeIf(g -> {
            if (g.comp == comp) {
                g.comp.removePropertyChangeListener("preferredSize", geoCompPropListener);
                return true;
            }
            return false;
        });
        remove(comp);
        repaint();
    }

    public void removeAnniuBYbianhao(int bianhao) {
        if (bianhao <= 0) return;
        runOnEDT(() -> {
            for (int i = 0, n = geoComponents.size(); i < n; i++) {
                GeoComponent g = geoComponents.get(i);
                if (g.bianhao == bianhao) {
                    g.comp.removePropertyChangeListener("preferredSize", geoCompPropListener);
                    remove(g.comp);
                    geoComponents.remove(i);
                    repaint();
                    return;
                }
            }
        });
    }

    public void removeWenbenBYbianhao(int bianhao) {
        if (bianhao <= 0) return;
        runOnEDT(() -> {
            for (int i = 0, n = geoTexts.size(); i < n; i++) {
                if (geoTexts.get(i).bianhao == bianhao) {
                    geoTexts.remove(i);
                    repaint();
                    return;
                }
            }
        });
    }

    public void clearGeoComponents() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::clearGeoComponents);
            return;
        }
        for (int i = 0, n = geoComponents.size(); i < n; i++) {
            GeoComponent g = geoComponents.get(i);
            g.comp.removePropertyChangeListener("preferredSize", geoCompPropListener);
            remove(g.comp);
        }
        geoComponents.clear();
        repaint();
    }

    public void setGeoComponentLonLat(JComponent comp, double lon, double lat) {
        if (comp == null) return;
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> setGeoComponentLonLat(comp, lon, lat));
            return;
        }
        for (int i = 0, n = geoComponents.size(); i < n; i++) {
            GeoComponent g = geoComponents.get(i);
            if (g.comp == comp) {
                GeoComponent ng = new GeoComponent(
                        g.bianhao, g.comp, lon, lat, g.anchorX, g.anchorY,
                        g.baseW, g.baseH, g.basePpd, g.baseFont, g.scaleWithZoom);
                ng.currentFontSize  = g.currentFontSize;
                ng.source           = g.source;
                ng.visibleBySource  = g.visibleBySource;
                ng.minScale         = g.minScale;
                ng.maxScale         = g.maxScale;
                geoComponents.set(i, ng);
                layoutGeoComponents();
                repaint();
                return;
            }
        }
    }

    public void setGeoComponentScaleWithZoom(JComponent comp, boolean on) {
        if (comp == null) return;
        runOnEDT(() -> {
            for (int i = 0, n = geoComponents.size(); i < n; i++) {
                GeoComponent g = geoComponents.get(i);
                if (g.comp == comp) {
                    g.scaleWithZoom = on;
                    layoutGeoComponents();
                    repaint();
                    return;
                }
            }
        });
    }

    // ============================================================
    //  setanniuBYbianhao
    // ============================================================
    public void setanniuBYbianhao(int bianhao, JComponent comp, double lon, double lat) {
        setAnniu0(bianhao, comp, lon, lat, null, null, null);
    }

    public void setanniuBYbianhao(int bianhao, JComponent comp, double lon, double lat,
                                  int anchorX, int anchorY) {
        setAnniu0(bianhao, comp, lon, lat, anchorX, anchorY, null);
    }

    public void setanniuBYbianhao(int bianhao, JComponent comp, double lon, double lat,
                                  int anchorX, int anchorY, boolean scaleWithZoom) {
        setAnniu0(bianhao, comp, lon, lat, anchorX, anchorY, scaleWithZoom);
    }

    private void setAnniu0(final int bianhao, final JComponent newComp,
                           final double lon, final double lat,
                           final Integer anchorX, final Integer anchorY,
                           final Boolean scaleWithZoom) {
        if (bianhao <= 0) return;
        runOnEDT(() -> {
            int idx = -1;
            for (int i = 0, n = geoComponents.size(); i < n; i++) {
                if (geoComponents.get(i).bianhao == bianhao) { idx = i; break; }
            }
            if (idx < 0) return;

            GeoComponent old = geoComponents.get(idx);
            JComponent comp = (newComp != null) ? newComp : old.comp;
            boolean swapped = (comp != old.comp);

            if (swapped) {
                old.comp.removePropertyChangeListener("preferredSize", geoCompPropListener);
                remove(old.comp);

                comp.setFocusable(false);
                comp.addPropertyChangeListener("preferredSize", geoCompPropListener);
                add(comp);
            }

            Dimension pref = comp.getPreferredSize();
            int w = (pref != null && pref.width  > 0) ? pref.width  : old.baseW;
            int h = (pref != null && pref.height > 0) ? pref.height : old.baseH;

            Font f = comp.getFont();
            if (f == null) f = old.baseFont;
            if (f == null) f = new Font(UI_FONT, Font.PLAIN, 12);

            GeoComponent ng = new GeoComponent(
                    bianhao, comp, lon, lat,
                    anchorX != null ? anchorX : old.anchorX,
                    anchorY != null ? anchorY : old.anchorY,
                    w, h,
                    old.basePpd, f,
                    scaleWithZoom != null ? scaleWithZoom : old.scaleWithZoom);
            ng.currentFontSize = swapped ? -1f : old.currentFontSize;
            ng.source          = old.source;
            ng.visibleBySource = old.visibleBySource;
            ng.minScale        = old.minScale;
            ng.maxScale        = old.maxScale;

            geoComponents.set(idx, ng);
            layoutGeoComponents();
            repaint();
        });
    }

    public JComponent getAnniuBYbianhao(int bianhao) {
        GeoComponent g = findAnniu(bianhao);
        return g == null ? null : g.comp;
    }

    public GeoText getWenbenBYbianhao(int bianhao) {
        return findWenben(bianhao);
    }

    // ============================================================
    //  布局
    // ============================================================
    private void layoutGeoComponents() {
        if (geoComponents.isEmpty()) return;
        int W = getWidth(), H = getHeight();
        if (W <= 0 || H <= 0) return;

        double halfW = W / 2.0;
        double halfH = H / 2.0;

        for (int i = 0, n = geoComponents.size(); i < n; i++) {
            GeoComponent g = geoComponents.get(i);

            double dLon = g.lon - lonCenter;
            dLon = ((dLon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
            int x = (int) Math.round(dLon * ppd + halfW);
            int y = (int) Math.round((latCenter - g.lat) * ppd + halfH);

            double scale = 1.0;
            if (g.scaleWithZoom && g.basePpd > 1e-9) {
                scale = ppd / g.basePpd;
                if (scale < g.minScale) scale = g.minScale;
                if (scale > g.maxScale) scale = g.maxScale;
            }

            int w  = Math.max(1, (int) Math.round(g.baseW * scale));
            int h  = Math.max(1, (int) Math.round(g.baseH * scale));
            int ax = (int) Math.round(g.anchorX * scale);
            int ay = (int) Math.round(g.anchorY * scale);

            int px = x + ax;
            int py = y + ay;

            Rectangle cur = g.comp.getBounds();
            if (cur.x != px || cur.y != py || cur.width != w || cur.height != h) {
                g.comp.setBounds(px, py, w, h);
            }

            if (g.scaleWithZoom && g.baseFont != null) {
                float newSize = (float) (g.baseFont.getSize2D() * scale);
                if (newSize < 1f) newSize = 1f;
                if (Math.abs(newSize - g.currentFontSize) > 0.5f) {
                    g.currentFontSize = newSize;
                    g.comp.setFont(g.baseFont.deriveFont(newSize));
                }
            }

            boolean onScreen = (px + w > 0 && px < W && py + h > 0 && py < H);
            g.comp.setVisible(g.visibleBySource && onScreen);
        }
    }

    // ============================================================
    //  同步 live 数据
    // ============================================================
    private void syncLive() {
        if (geoDots.isEmpty() && geoTexts.isEmpty() && geoComponents.isEmpty()) return;

        final int dotCount = geoDots.size();
        if (dotCount > 0) {
            boolean dotDirty = false;
            for (int i = 0; i < dotCount; i++) {
                dian d = geoDots.get(i);
                Source s = d.source;
                if (s == null) continue;

                double nl = s.getLon();
                double na = s.getLat();
                if (nl != d.lon || na != d.lat) {
                    d.lon = nl; d.lat = na;
                    if (!d.screenSpace) dotDirty = true;
                }
                Color nc = s.getColor();
                if (nc != null && !nc.equals(d.color)) {
                    d.color = nc;
                    if (!d.screenSpace) dotDirty = true;
                }
                String nt = s.getText();
                if (nt != null && !nt.equals(d.text)) {
                    d.text = nt;
                    if (!d.screenSpace) dotDirty = true;
                }
                Double nr = s.getRadius();
                if (nr != null && nr != d.radiusDeg) {
                    d.radiusDeg = nr;
                    if (!d.screenSpace) dotDirty = true;
                }
                Boolean nv = s.getVisible();
                if (nv != null && nv != d.visible) {
                    d.visible = nv;
                    if (!d.screenSpace) dotDirty = true;
                }
            }
            if (dotDirty && bakeStaticDots) markWorldDirty();
        }

        final int textCount = geoTexts.size();
        if (textCount > 0) {
            for (int i = 0; i < textCount; i++) {
                GeoText t = geoTexts.get(i);
                Source s = t.source;
                if (s == null) continue;

                double nl = s.getLon();
                double na = s.getLat();
                if (nl != t.lon) t.lon = nl;
                if (na != t.lat) t.lat = na;

                String nt = s.getText();
                if (nt != null && !nt.equals(t.text)) t.text = nt;

                Color nc = s.getColor();
                if (nc != null && !nc.equals(t.color)) t.color = nc;

                Float nf = s.getFontSize();
                if (nf != null && nf > 0f && nf != t.baseFontSize) t.baseFontSize = nf;

                Boolean nv = s.getVisible();
                if (nv != null) t.visible = nv;
            }
        }

        final int compCount = geoComponents.size();
        if (compCount > 0) {
            boolean needLayout = false;
            for (int i = 0; i < compCount; i++) {
                GeoComponent g = geoComponents.get(i);
                Source s = g.source;
                if (s == null) continue;

                double nl = s.getLon();
                double na = s.getLat();
                if (nl != g.lon || na != g.lat) {
                    g.lon = nl; g.lat = na;
                    needLayout = true;
                }
                Boolean nv = s.getVisible();
                if (nv != null && nv != g.visibleBySource) {
                    g.visibleBySource = nv;
                    needLayout = true;
                }
            }
            if (needLayout) layoutGeoComponents();
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
                        lonBoundMin + halfLon, lonBoundMax - halfLon);
            }
        } else {
            lonCenter = normalizeLon(lonCenter);
        }

        layoutGeoComponents();
    }

    // ============================================================
    //  绘图方法（画笔）
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
        markWorldDirty(); repaint();
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
        markWorldDirty(); repaint();
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
        markWorldDirty(); repaint();
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
        markWorldDirty(); repaint();
    }

    public void drawText(String text, double lon, double lat, int fontSize) {
        final Color c = brushColor;
        shapes.add((g2, b) -> {
            g2.setColor(c);
            int fs = b.bakingMode
                    ? Math.max(1, (int) Math.round(fontSize * WORLD_BASE_PPD / 6.0))
                    : fontSize;
            g2.setFont(uiFont(fs));
            for (int k = -1; k <= 1; k++) {
                int[] p = b.lonLatToScreen(lon + k * 360.0, lat);
                g2.drawString(text, p[0], p[1]);
            }
        });
        markWorldDirty(); repaint();
    }

    public void clear() { shapes.clear(); markWorldDirty(); repaint(); }
    public void undo() {
        if (!shapes.isEmpty()) {
            shapes.remove(shapes.size() - 1);
            markWorldDirty(); repaint();
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
        markWorldDirty(); repaint();
    }
    public void clearMarkers() { markers.clear(); markWorldDirty(); repaint(); }

    // ==================== 坐标换算 ====================
    public int[] lonLatToScreen(double lon, double lat) {
        if (bakingMode) {
            int x = (int) Math.round((lon + 180) * (double) WORLD_BASE_PPD);
            int y = (int) Math.round((90 - lat) * (double) WORLD_BASE_PPD);
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
    //  取色 API
    // ============================================================
    public Color getColorAtLonLat(double lon, double lat) {
        return getColorAtLonLat(lon, lat, null);
    }

    public Color getColorAtLonLat(double lon, double lat, Color emptyColor) {
        if (lat < WORLD_LAT_MIN || lat > WORLD_LAT_MAX) return emptyColor;

        if (!SwingUtilities.isEventDispatchThread()) {
            final double flon = lon, flat = lat;
            final Color  femp = emptyColor;
            final Color[] out = new Color[1];
            try {
                SwingUtilities.invokeAndWait(() -> out[0] = getColorAtLonLat(flon, flat, femp));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return femp;
            } catch (InvocationTargetException ite) {
                return femp;
            }
            return out[0] != null ? out[0] : femp;
        }

        if (worldDirty) rebuildWorldCanvas();

        MappedImage base = worldPyramid.get(0);
        int ppdBase = worldPyramid.getPpd(0);

        double nlon = normalizeLon(lon);
        int px = (int) Math.floor((nlon + 180.0) * ppdBase);
        int py = (int) Math.floor((WORLD_LAT_MAX - lat) * ppdBase);

        if (px < 0 || px >= base.getWidth() || py < 0 || py >= base.getHeight())
            return emptyColor;

        int argb = base.getARGB(px, py);
        int a = (argb >>> 24) & 0xFF;
        if (a == 0) return emptyColor;
        return new Color(argb, true);
    }

    public Color getColorAtScreen(int x, int y) {
        return getColorAtScreen(x, y, null);
    }

    public Color getColorAtScreen(int x, int y, Color emptyColor) {
        final int W = getWidth(), H = getHeight();
        if (W <= 0 || H <= 0) return emptyColor;
        if (x < 0 || y < 0 || x >= W || y >= H) return emptyColor;

        if (!SwingUtilities.isEventDispatchThread()) {
            final int fx = x, fy = y;
            final Color femp = emptyColor;
            final Color[] out = new Color[1];
            try {
                SwingUtilities.invokeAndWait(() -> out[0] = getColorAtScreen(fx, fy, femp));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return femp;
            } catch (InvocationTargetException ite) {
                return femp;
            }
            return out[0] != null ? out[0] : femp;
        }

        BufferedImage img = ensurePickBuffer(W, H);
        Graphics2D ig = img.createGraphics();
        try {
            ig.setComposite(AlphaComposite.Clear);
            ig.fillRect(0, 0, W, H);
            ig.setComposite(AlphaComposite.SrcOver);
            paintComponent(ig);
        } finally {
            ig.dispose();
        }

        int argb = img.getRGB(x, y);
        int a = (argb >>> 24) & 0xFF;
        if (a == 0) return emptyColor;
        return new Color(argb, true);
    }

    public Color getColorAtLonLatOnScreen(double lon, double lat) {
        return getColorAtLonLatOnScreen(lon, lat, null);
    }

    public Color getColorAtLonLatOnScreen(double lon, double lat, Color emptyColor) {
        if (getWidth() <= 0 || getHeight() <= 0) return emptyColor;

        double nlon = normalizeLon(lon);
        double d = nlon - lonCenter;
        while (d >  180.0) d -= 360.0;
        while (d < -180.0) d += 360.0;
        double useLon = lonCenter + d;

        int sx = (int) Math.round((useLon - lonCenter) * ppd + getWidth()  / 2.0);
        int sy = (int) Math.round((latCenter - lat)    * ppd + getHeight() / 2.0);

        return getColorAtScreen(sx, sy, emptyColor);
    }

    // ============================================================
    //  视图变化检测
    // ============================================================
    private boolean viewChanged() {
        int W = getWidth(), H = getHeight();
        return lonCenter != cachedVpLonCenter
                || latCenter != cachedVpLatCenter
                || ppd != cachedVpPpd
                || W != cachedVpW
                || H != cachedVpH
                || liveDots.size() != cachedLiveSize;
    }

    private void updateViewSnapshot() {
        cachedVpLonCenter = lonCenter;
        cachedVpLatCenter = latCenter;
        cachedVpPpd = ppd;
        cachedVpW = getWidth();
        cachedVpH = getHeight();
        cachedLiveSize = liveDots.size();
    }

    // ============================================================
    //  重建可见集
    // ============================================================
    private void rebuildVisibleLiveDots() {
        visibleLiveDots.clear();
        int W = getWidth(), H = getHeight();
        if (W <= 0 || H <= 0 || liveDots.isEmpty()) {
            updateViewSnapshot();
            return;
        }

        double halfW = W * 0.5;
        double halfH = H * 0.5;

        double halfLon = halfW / ppd + 1.0;
        double halfLat = halfH / ppd + 1.0;
        boolean wholeWorld = halfLon >= 180.0;

        double panelWidthPx = 360.0 * ppd;
        int copies = computeCopies(W, panelWidthPx);

        double sxMin = -panelWidthPx * (copies + 1);
        double sxMax =  W + panelWidthPx * (copies + 1);

        for (int i = 0, n = liveDots.size(); i < n; i++) {
            dian d = liveDots.get(i);
            if (!d.visible) continue;

            if (d.lat < latCenter - halfLat || d.lat > latCenter + halfLat) continue;

            if (!wholeWorld) {
                double dlon = d.lon - lonCenter;
                dlon = ((dlon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
                if (dlon < -halfLon || dlon > halfLon) continue;
            }

            double sx = (d.lon - lonCenter) * ppd + halfW;
            double sy = (latCenter - d.lat) * ppd + halfH;

            if (sx < sxMin || sx > sxMax) continue;
            if (sy < -halfH - 4 || sy > H + halfH + 4) continue;

            d.cachedSx = sx;
            d.cachedSy = sy;
            visibleLiveDots.add(d);
        }

        updateViewSnapshot();
    }

    private static int computeCopies(int W, double panelWidthPx) {
        int copies = (int) Math.ceil(W / Math.max(panelWidthPx, 1.0)) + 1;
        if (copies > 5) copies = 5;
        return copies;
    }

    // ============================================================
    //  绘制入口
    // ============================================================
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int W = getWidth(), H = getHeight();
        if (W <= 0 || H <= 0) return;

        tickFrame();

        syncLive();

        if (bakeStaticDots && worldDirty) rebuildWorldCanvas();
        if (viewChanged()) rebuildVisibleLiveDots();

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            drawGridOnScreen(g2, W, H);
            drawWorldCanvas(g2, W, H);
            drawTrailCanvas(g2, W, H);
            if (!bakeStaticDots) {
                drawGeoDotsStaticOnScreen(g2, W, H);
            }
            drawLiveLines(g2, W, H);
            drawGeoDotsLive(g2, W, H);
            drawGeoTexts(g2, W, H);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            drawHUD(g2, W, H);
            drawSelectionBox(g2);
        } finally {
            g2.dispose();
        }
    }

    // ============================================================
    //  选择框绘制
    // ============================================================
    private void drawSelectionBox(Graphics2D g2) {
        if (!selectionActive || selectionStart == null || selectionEnd == null) return;

        int x1 = Math.min(selectionStart.x, selectionEnd.x);
        int y1 = Math.min(selectionStart.y, selectionEnd.y);
        int x2 = Math.max(selectionStart.x, selectionEnd.x);
        int y2 = Math.max(selectionStart.y, selectionEnd.y);
        int w  = x2 - x1;
        int h  = y2 - y1;
        if (w <= 0 && h <= 0) return;

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_OFF);
        g2.setColor(selectionFillColor);
        g2.fillRect(x1, y1, w, h);
        g2.setColor(selectionBorderColor);
        g2.setStroke(new BasicStroke(1f));
        g2.drawRect(x1, y1, w, h);
    }

    // ============================================================
    //  ★ 轨迹层绘制：逐个 owner 绘制，只对该 owner 回调
    // ============================================================
    private void drawTrailCanvas(Graphics2D g2, int W, int H) {
        if (!hasAnyTrails) return;
        for (OwnerTrail ot : ownerTrails.values()) {
            if (!ot.hasAny) continue;
            drawOwnerTrailCanvas(g2, W, H, ot);
        }
    }

    private void drawOwnerTrailCanvas(Graphics2D g2, int W, int H, OwnerTrail ot) {
        MappedPyramid trailPyramid = ot.pyramid;

        int level = trailPyramid.pickLevel(ppd);
        MappedImage img = trailPyramid.get(level);
        int srcPpd = trailPyramid.getPpd(level);

        double scale = ppd / (double) srcPpd;
        double tx = (-180 - lonCenter) * ppd + W / 2.0;
        double ty = (latCenter - 90) * ppd + H / 2.0;

        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,
                RenderingHints.VALUE_ALPHA_INTERPOLATION_SPEED);

        double panelWidthPx = 360.0 * ppd;
        int copies = computeCopies(W, panelWidthPx);

        for (int k = -copies; k <= copies; k++) {
            double x0 = tx + k * panelWidthPx;
            if (x0 + panelWidthPx < 0 || x0 > W) continue;

            int cx1 = Math.max(0, (int) Math.floor((0 - x0) / scale));
            int cy1 = Math.max(0, (int) Math.floor((0 - ty) / scale));
            int cx2 = Math.min(img.getWidth(),  (int) Math.ceil((W - x0) / scale));
            int cy2 = Math.min(img.getHeight(), (int) Math.ceil((H - ty) / scale));
            if (cx1 >= cx2 || cy1 >= cy2) continue;

            int cw = cx2 - cx1, ch = cy2 - cy1;
            BufferedImage region = img.readRegion(cx1, cy1, cw, ch);
            if (region == null) continue;

            double sx = x0 + scale * cx1;
            double sy = ty + scale * cy1;

            int dstX1 = (int) Math.round(sx);
            int dstY1 = (int) Math.round(sy);
            int dstX2 = (int) Math.round(sx + scale * cw);
            int dstY2 = (int) Math.round(sy + scale * ch);

            // ★ 只对该 owner 的轨迹像素回调
            notifyTrailPixels(ot.owner, region, dstX1, dstY1, dstX2, dstY2, W, H);

            g2.drawImage(region,
                    dstX1, dstY1, dstX2, dstY2,
                    0, 0, cw, ch, null);
        }
    }

    // ============================================================
    //  ★ 轨迹像素回调辅助方法
    //
    //  只有当 owner 实现了 TrailPixelSink 时才会回调 meichuzhixing。
    //  遍历本次 drawImage 会画到屏幕上的每个像素，如果该屏幕像素对应
    //  region 源像素 alpha != 0，则认为是该 owner 的拖尾像素，
    //  将其屏幕像素中心反算成经纬度并调用 owner.meichuzhixing。
    // ============================================================
    private void notifyTrailPixels(Object owner,
                                   BufferedImage region,
                                   int dstX1, int dstY1,
                                   int dstX2, int dstY2,
                                   int W, int H) {
        if (!(owner instanceof TrailPixelSink)) return;
        TrailPixelSink sink = (TrailPixelSink) owner;

        int dw = dstX2 - dstX1;
        int dh = dstY2 - dstY1;
        if (dw <= 0 || dh <= 0) return;

        int cw = region.getWidth();
        int ch = region.getHeight();
        if (cw <= 0 || ch <= 0) return;

        int x1 = Math.max(0, dstX1);
        int y1 = Math.max(0, dstY1);
        int x2 = Math.min(W, dstX2);
        int y2 = Math.min(H, dstY2);
        if (x1 >= x2 || y1 >= y2) return;

        for (int py = y1; py < y2; py++) {
            for (int px = x1; px < x2; px++) {

                // 屏幕像素中心映射到 region 中的源像素
                double rx = (px + 0.5 - dstX1) * cw / (double) dw;
                double ry = (py + 0.5 - dstY1) * ch / (double) dh;

                int ix = (int) Math.floor(rx);
                int iy = (int) Math.floor(ry);
                if (ix < 0 || ix >= cw || iy < 0 || iy >= ch) continue;

                int argb = region.getRGB(ix, iy);
                int alpha = (argb >>> 24) & 0xFF;
                if (alpha == 0) continue;

                // 当前屏幕像素中心反算经纬度
                double lon = lonCenter + (px + 0.5 - W / 2.0) / ppd;
                double lat = latCenter - (py + 0.5 - H / 2.0) / ppd;

                lon = normalizeLon(lon);
                if (lat < WORLD_LAT_MIN || lat > WORLD_LAT_MAX) continue;

                sink.meichuzhixing(lon, lat);
            }
        }
    }

    // ============================================================
    //  世界画布绘制：从金字塔读取
    // ============================================================
    private void drawWorldCanvas(Graphics2D g2, int W, int H) {
        int level = worldPyramid.pickLevel(ppd);
        MappedImage img = worldPyramid.get(level);
        int srcPpd = worldPyramid.getPpd(level);

        double scale = ppd / (double) srcPpd;
        double panelWidthPx = 360.0 * ppd;
        int copies = computeCopies(W, panelWidthPx);

        double tx = (-180 - lonCenter) * ppd + W / 2.0;
        double ty = (latCenter - 90) * ppd + H / 2.0;

        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                scale >= 2.0
                        ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
                        : RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,
                RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);

        for (int k = -copies; k <= copies; k++) {
            double x0 = tx + k * panelWidthPx;
            if (x0 + panelWidthPx < 0 || x0 > W) continue;

            int cx1 = Math.max(0, (int) Math.floor((0 - x0) / scale));
            int cy1 = Math.max(0, (int) Math.floor((0 - ty) / scale));
            int cx2 = Math.min(img.getWidth(),  (int) Math.ceil((W - x0) / scale));
            int cy2 = Math.min(img.getHeight(), (int) Math.ceil((H - ty) / scale));
            if (cx1 >= cx2 || cy1 >= cy2) continue;

            int cw = cx2 - cx1, ch = cy2 - cy1;
            BufferedImage region = img.readRegion(cx1, cy1, cw, ch);
            if (region == null) continue;

            double sx = x0 + scale * cx1;
            double sy = ty + scale * cy1;
            g2.drawImage(region,
                    (int) Math.round(sx),               (int) Math.round(sy),
                    (int) Math.round(sx + scale * cw),  (int) Math.round(sy + scale * ch),
                    0, 0, cw, ch, null);
        }
    }

    // ============================================================
    //  非 Live 圆点：屏幕直绘
    // ============================================================
    private void drawGeoDotsStaticOnScreen(Graphics2D g2, int W, int H) {
        if (geoDots.isEmpty()) return;

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                RenderingHints.VALUE_STROKE_PURE);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);

        double panelWidthPx = 360.0 * ppd;
        int copies = computeCopies(W, panelWidthPx);
        double halfW = W * 0.5;
        double halfH = H * 0.5;

        resetBatchCache();

        for (int i = 0, n = geoDots.size(); i < n; i++) {
            dian d = geoDots.get(i);
            if (d.screenSpace) continue;
            if (!d.visible) continue;
            if (d.color == null) continue;

            double rPx = d.radiusDeg * ppd;
            if (rPx < minLiveDotRadiusPx) rPx = minLiveDotRadiusPx;

            double dLon = d.lon - lonCenter;
            dLon = ((dLon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
            double baseX = dLon * ppd + halfW;
            double baseY = (latCenter - d.lat) * ppd + halfH;

            boolean simple = (d.borderWidth <= 0)
                    && (d.text == null || d.text.isEmpty());

            if (simple) {
                Path2D.Double path = pathFor(d.color);
                boolean useRect = rPx < 2.0;
                for (int k = -copies; k <= copies; k++) {
                    double sx = baseX + k * panelWidthPx;
                    double sy = baseY;
                    if (sx + rPx < 0 || sx - rPx > W) continue;
                    if (sy + rPx < 0 || sy - rPx > H) continue;
                    if (useRect) {
                        path.append(new Rectangle2D.Double(
                                sx - rPx, sy - rPx, rPx * 2, rPx * 2), false);
                    } else {
                        path.append(new Ellipse2D.Double(
                                sx - rPx, sy - rPx, rPx * 2, rPx * 2), false);
                    }
                }
            } else {
                individualDotCache.add(d);
            }
        }

        for (Map.Entry<Color, Path2D.Double> e : batchPathCache.entrySet()) {
            Path2D.Double p = e.getValue();
            if (!hasContent(p)) continue;
            g2.setColor(e.getKey());
            g2.fill(p);
        }

        for (int i = 0, m = individualDotCache.size(); i < m; i++) {
            dian d = individualDotCache.get(i);
            double rPx = d.radiusDeg * ppd;
            if (rPx < minLiveDotRadiusPx) rPx = minLiveDotRadiusPx;

            double dLon = d.lon - lonCenter;
            dLon = ((dLon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
            double baseX = dLon * ppd + halfW;
            double baseY = (latCenter - d.lat) * ppd + halfH;

            for (int k = -copies; k <= copies; k++) {
                double sx = baseX + k * panelWidthPx;
                double sy = baseY;
                if (sx + rPx < 0 || sx - rPx > W) continue;
                if (sy + rPx < 0 || sy - rPx > H) continue;

                if (d.color != null) {
                    g2.setColor(d.color);
                    g2.fill(new Ellipse2D.Double(
                            sx - rPx, sy - rPx, rPx * 2, rPx * 2));
                }
                if (d.borderWidth > 0) {
                    Color border = d.borderColor != null ? d.borderColor
                            : (d.color != null ? d.color.darker() : Color.WHITE);
                    g2.setColor(border);
                    g2.setStroke(strokeOf(d.borderWidth));
                    g2.draw(new Ellipse2D.Double(
                            sx - rPx, sy - rPx, rPx * 2, rPx * 2));
                }
                if (d.text != null && !d.text.isEmpty()) {
                    int fs;
                    if (d.textScale) {
                        fs = (int) Math.max(9, Math.min(200, rPx * 0.8));
                    } else {
                        fs = 12;
                    }
                    g2.setFont(uiFont(fs));
                    g2.setColor(d.textColor != null ? d.textColor : Color.WHITE);
                    g2.drawString(d.text,
                            (float) (sx + rPx + 4),
                            (float) (sy + fs / 3));
                }
            }
        }
    }

    // ============================================================
    //  只烘焙非 Live 圆点（独立入口）
    // ============================================================
    public void bakeGeoDotsOnly() {
        rebuildWorldCanvas();
        repaint();
    }

    // ============================================================
    //  Live 直线绘制
    // ============================================================
    private void drawLiveLines(Graphics2D g2, int W, int H) {
        if (liveLines.isEmpty()) return;

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                RenderingHints.VALUE_STROKE_PURE);

        double halfW = W * 0.5;
        double halfH = H * 0.5;
        double panelWidthPx = 360.0 * ppd;
        int copies = computeCopies(W, panelWidthPx);

        for (int i = 0, n = liveLines.size(); i < n; i++) {
            LiveLine L = liveLines.get(i);
            if (!L.visible) continue;

            Color c = (L.color != null) ? L.color : Color.WHITE;
            float sw = (L.strokeWidth > 0f) ? L.strokeWidth : 1f;

            double lon2Adj = L.lon2;
            double dd = lon2Adj - L.lon1;
            dd = ((dd + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
            lon2Adj = L.lon1 + dd;

            double x1 = (L.lon1   - lonCenter) * ppd + halfW;
            double y1 = (latCenter - L.lat1)   * ppd + halfH;
            double x2 = (lon2Adj  - lonCenter) * ppd + halfW;
            double y2 = (latCenter - L.lat2)   * ppd + halfH;

            double minY = Math.min(y1, y2) - sw;
            double maxY = Math.max(y1, y2) + sw;
            if (maxY < 0 || minY > H) continue;

            g2.setColor(c);
            g2.setStroke(new BasicStroke(sw,
                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            for (int k = -copies; k <= copies; k++) {
                double dx = k * panelWidthPx;
                double ax = x1 + dx;
                double bx = x2 + dx;

                if ((ax < 0 && bx < 0) || (ax > W && bx > W)) continue;

                g2.draw(new Line2D.Double(ax, y1, bx, y2));
            }
        }
    }

    // ============================================================
    //  Live 点绘制
    // ============================================================
    private void drawGeoDotsLive(Graphics2D g2, int W, int H) {
        if (visibleLiveDots.isEmpty()) return;

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                RenderingHints.VALUE_STROKE_PURE);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);

        double panelWidthPx = 360.0 * ppd;
        int copies = computeCopies(W, panelWidthPx);

        resetBatchCache();

        int n = visibleLiveDots.size();
        for (int i = 0; i < n; i++) {
            dian d = visibleLiveDots.get(i);
            if (!d.visible || d.color == null) continue;

            double rPx = d.radiusDeg * ppd;
            if (rPx < minLiveDotRadiusPx) rPx = minLiveDotRadiusPx;

            boolean simple = (d.borderWidth <= 0)
                    && (d.text == null || d.text.isEmpty());

            if (simple) {
                Path2D.Double path = pathFor(d.color);
                boolean useRect = rPx < 2.0;

                for (int k = -copies; k <= copies; k++) {
                    double sx = d.cachedSx + k * panelWidthPx;
                    double sy = d.cachedSy;
                    if (sx + rPx < 0 || sx - rPx > W) continue;
                    if (sy + rPx < 0 || sy - rPx > H) continue;

                    if (useRect) {
                        path.append(new Rectangle2D.Double(
                                sx - rPx, sy - rPx, rPx * 2, rPx * 2), false);
                    } else {
                        path.append(new Ellipse2D.Double(
                                sx - rPx, sy - rPx, rPx * 2, rPx * 2), false);
                    }
                }
            } else {
                individualDotCache.add(d);
            }
        }

        for (Map.Entry<Color, Path2D.Double> e : batchPathCache.entrySet()) {
            Path2D.Double p = e.getValue();
            if (!hasContent(p)) continue;
            g2.setColor(e.getKey());
            g2.fill(p);
        }

        for (int i = 0, m = individualDotCache.size(); i < m; i++) {
            dian d = individualDotCache.get(i);
            double rPx = d.radiusDeg * ppd;
            if (rPx < minLiveDotRadiusPx) rPx = minLiveDotRadiusPx;

            for (int k = -copies; k <= copies; k++) {
                double sx = d.cachedSx + k * panelWidthPx;
                double sy = d.cachedSy;
                if (sx + rPx < 0 || sx - rPx > W) continue;
                if (sy + rPx < 0 || sy - rPx > H) continue;

                if (d.color != null) {
                    g2.setColor(d.color);
                    g2.fill(new Ellipse2D.Double(
                            sx - rPx, sy - rPx, rPx * 2, rPx * 2));
                }
                if (d.borderWidth > 0) {
                    Color border = d.borderColor != null ? d.borderColor
                            : (d.color != null ? d.color.darker() : Color.WHITE);
                    g2.setColor(border);
                    g2.setStroke(strokeOf(d.borderWidth));
                    g2.draw(new Ellipse2D.Double(
                            sx - rPx, sy - rPx, rPx * 2, rPx * 2));
                }
                if (d.text != null && !d.text.isEmpty()) {
                    int fs = (int) Math.max(9, Math.min(200, rPx * 1.5));
                    g2.setFont(uiFont(fs));
                    g2.setColor(d.textColor != null ? d.textColor : Color.WHITE);
                    g2.drawString(d.text, (float)(sx + rPx + 4), (float)(sy + fs / 3));
                }
            }
        }
    }

    // ============================================================
    //  绘制地理文本
    // ============================================================
    private void drawGeoTexts(Graphics2D g2, int W, int H) {
        if (geoTexts.isEmpty()) return;

        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_LCD_CONTRAST, 180);

        double panelWidthPx = 360.0 * ppd;
        int copies = computeCopies(W, panelWidthPx);

        for (int ti = 0, tn = geoTexts.size(); ti < tn; ti++) {
            GeoText t = geoTexts.get(ti);
            if (!t.visible) continue;
            if (t.text == null || t.text.isEmpty()) continue;

            float fs = t.baseFontSize;
            if (t.scaleWithZoom && t.basePpd > 1e-9) {
                double s = ppd / t.basePpd;
                if (s < t.minScale) s = t.minScale;
                if (s > t.maxScale) s = t.maxScale;
                fs = (float) (t.baseFontSize * s);
            }
            fs = clampF(fs, MIN_SCREEN_FONT_SIZE, MAX_SCREEN_FONT_SIZE);

            Font font = t.fontFor(fs);
            g2.setFont(font);

            if (t.cachedFm == null || t.cachedFmFont != font) {
                t.cachedFm = g2.getFontMetrics(font);
                t.cachedFmFont = font;
            }
            FontMetrics fm = t.cachedFm;

            int textW   = fm.stringWidth(t.text);
            int ascent  = fm.getAscent();
            int descent = fm.getDescent();
            int centerOffset = (ascent - descent) / 2;

            if (t.attachedTo != null) {
                if (!t.attachedTo.isVisible()) continue;

                Rectangle b = t.attachedTo.getBounds();
                int cx = b.x + b.width  / 2;
                int cy = b.y + b.height / 2;

                int x = cx - textW / 2 + (int) Math.round(t.offsetX);
                int y = cy + centerOffset + (int) Math.round(t.offsetY);

                if (x + textW < 0 || x > W) continue;
                if (y - ascent > H || y + descent < 0) continue;

                drawTextWithOutline(g2, t, x, y);
            } else {
                for (int k = -copies; k <= copies; k++) {
                    int[] p = lonLatToScreen(t.lon + k * 360.0, t.lat);
                    int x = p[0] - textW / 2 + (int) Math.round(t.offsetX);
                    int y = p[1] + centerOffset + (int) Math.round(t.offsetY);

                    if (x + textW < 0 || x > W) continue;
                    if (y - ascent > H || y + descent < 0) continue;

                    drawTextWithOutline(g2, t, x, y);
                }
            }
        }
    }

    private void drawTextWithOutline(Graphics2D g2, GeoText t, int x, int y) {
        if (t.outlineColor != null && t.outlineWidth > 0f) {
            g2.setColor(t.outlineColor);
            int ow = Math.max(1, (int) Math.ceil(t.outlineWidth));
            for (int dx = -ow; dx <= ow; dx++) {
                for (int dy = -ow; dy <= ow; dy++) {
                    if (dx == 0 && dy == 0) continue;
                    if (dx * dx + dy * dy > ow * ow + 1) continue;
                    g2.drawString(t.text, x + dx, y + dy);
                }
            }
        }
        g2.setColor(t.color != null ? t.color : Color.WHITE);
        g2.drawString(t.text, x, y);
    }

    // ============================================================
    //  经纬线
    // ============================================================
    private void drawGridOnScreen(Graphics2D g2, int W, int H) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_OFF);
        g2.setStroke(new BasicStroke(1f));

        double lonMin = lonCenter - W / (2.0 * ppd);
        double lonMax = lonCenter + W / (2.0 * ppd);
        int startIdx = (int) Math.ceil(lonMin / GRID_STEP);
        int endIdx   = (int) Math.floor(lonMax / GRID_STEP);
        for (int i = startIdx; i <= endIdx; i++) {
            double lon = i * GRID_STEP;
            int x = (int) Math.round((lon - lonCenter) * ppd + W / 2.0);
            if (x < -1 || x > W + 1) continue;
            boolean prime = Math.abs(normalizeLon(lon)) < 1e-6;
            g2.setColor(prime ? GRID_COLOR_PRIME : GRID_COLOR_NORMAL);
            g2.drawLine(x, 0, x, H);
        }

        for (double lat = -90; lat <= 90 + 1e-6; lat += GRID_STEP) {
            int y = (int) Math.round((latCenter - lat) * ppd + H / 2.0);
            if (y < -1 || y > H + 1) continue;
            boolean equator = Math.abs(lat) < 1e-6;
            g2.setColor(equator ? GRID_COLOR_PRIME : GRID_COLOR_NORMAL);
            g2.drawLine(0, y, W, y);
        }
    }

    // ============================================================
    //  世界画布重建：绘制临时高分辨率图 → 交给金字塔降采样
    // ============================================================
    private void rebuildWorldCanvas() {
        BufferedImage high = new BufferedImage(WORLD_W_HIGH, WORLD_H_HIGH,
                BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = high.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                    RenderingHints.VALUE_STROKE_PURE);

            g.setComposite(AlphaComposite.Clear);
            g.fillRect(0, 0, WORLD_W_HIGH, WORLD_H_HIGH);
            g.setComposite(AlphaComposite.SrcOver);

            bakingMode = true;
            try {
                for (int i = 0, n = shapes.size(); i < n; i++) shapes.get(i).paint(g, this);
                bakeMarkers(g);
                bakeGeoDots(g);
            } finally {
                bakingMode = false;
            }
        } finally {
            g.dispose();
        }

        worldPyramid.rebuildFromBase(high);
        high.flush();

        worldDirty = false;
    }

    private void bakeMarkers(Graphics2D g) {
        if (markers.isEmpty()) return;
        int baseSize = 12;
        g.setFont(uiFont(Math.max(1, (int) Math.round(baseSize * WORLD_BASE_PPD / 6.0))));
        for (int i = 0, n = markers.size(); i < n; i++) {
            Marker m = markers.get(i);
            int[] p = lonLatToScreen(m.lon, m.lat);
            int s = Math.max(1, (int) Math.round(m.size * (double) WORLD_BASE_PPD / 6.0));
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

        resetBatchCache();

        for (int i = 0, n = geoDots.size(); i < n; i++) {
            dian d = geoDots.get(i);
            if (d.screenSpace) continue;
            if (!d.visible) continue;

            boolean simple = (d.borderWidth <= 0)
                    && (d.text == null || d.text.isEmpty());
            if (simple && d.color != null) {
                Path2D.Double path = pathFor(d.color);
                int[] p = lonLatToScreen(d.lon, d.lat);
                int r = (int) Math.round(d.radiusDeg * WORLD_BASE_PPD);
                if (r < 1) r = 1;
                if (r <= 2) {
                    path.append(new Rectangle2D.Double(p[0] - r, p[1] - r, r * 2.0, r * 2.0), false);
                } else {
                    path.append(new Ellipse2D.Double(p[0] - r, p[1] - r, r * 2.0, r * 2.0), false);
                }
            } else {
                individualDotCache.add(d);
            }
        }

        for (Map.Entry<Color, Path2D.Double> e : batchPathCache.entrySet()) {
            Path2D.Double p = e.getValue();
            if (!hasContent(p)) continue;
            g.setColor(e.getKey());
            g.fill(p);
        }

        for (int i = 0, m = individualDotCache.size(); i < m; i++) {
            dian d = individualDotCache.get(i);
            int[] p = lonLatToScreen(d.lon, d.lat);
            int r = (int) Math.round(d.radiusDeg * WORLD_BASE_PPD);
            if (r < 1) r = 1;

            if (d.color != null) {
                g.setColor(d.color);
                g.fillOval(p[0] - r, p[1] - r, r * 2, r * 2);
            }
            if (d.borderWidth > 0) {
                Color border = d.borderColor != null ? d.borderColor
                        : (d.color != null ? d.color.darker() : Color.WHITE);
                g.setColor(border);
                float bw = Math.max(1f, d.borderWidth * WORLD_BASE_PPD / 6f);
                g.setStroke(strokeOf(bw));
                g.drawOval(p[0] - r, p[1] - r, r * 2, r * 2);
            }
            if (d.text != null && !d.text.isEmpty()) {
                int fs;
                if (d.textScale) {
                    fs = (int) Math.max(9, Math.min(200, d.radiusDeg * WORLD_BASE_PPD * 0.8));
                } else {
                    fs = Math.max(1, (int) Math.round(12.0 * WORLD_BASE_PPD / 6.0));
                }
                g.setFont(uiFont(fs));
                g.setColor(d.textColor != null ? d.textColor : Color.WHITE);
                g.drawString(d.text, p[0] + r + 4, p[1] + fs / 3);
            }
        }
    }

    // ============================================================
    //  HUD
    // ============================================================
    private void drawHUD(Graphics2D g2, int W, int H) {
        g2.setFont(HUD_FONT);

        g2.setColor(HUD_COLOR_MAIN);
        g2.drawString(String.format("中心  经度 %.1f 度  纬度 %.1f 度  缩放 %.2f px/度",
                lonCenter, latCenter, ppd), 12, 22);

        g2.setColor(HUD_COLOR_TIP);
        g2.drawString("左键拖拽旋转 · 滚轮缩放 · 右键框选", 12, 42);

        MouseSnapshot s = mouseSnap;

        if (s != null) {
            g2.setColor(HUD_COLOR_MOUSE);
            g2.drawString(String.format("鼠标  经度 %.2f 度  纬度 %.2f 度",
                    s.lon, s.lat), 12, 62);
        } else {
            g2.setColor(HUD_COLOR_NOMSE);
            g2.drawString("鼠标  移出画板", 12, 62);
        }
        g2.setColor(HUD_COLOR_MAIN);
        g2.drawString(String.valueOf(getFps()), 40, 92);
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
    private static float clampF(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
    private static void runOnEDT(Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) r.run();
        else SwingUtilities.invokeLater(r);
    }
    // ============================================================
//  取色：获取某个坐标点处「非 Live 内容」的颜色
//
//  非 Live 内容 = 画笔图形(shapes) + 标记(markers) + 静态圆点(geoDots 中 screenSpace=false)
//  这些在 rebuildWorldCanvas() 时被统一烘焙进 worldPyramid。
//  不包含：Live 圆点、Live 直线、经纬网格、HUD、选择框、轨迹层。
// ============================================================

    /**
     * 获取指定经纬度处的非 Live 颜色。
     *
     * @param lon        经度（自动归一化到 [-180, 180)）
     * @param lat        纬度；超出 ±90 直接返回 emptyColor
     * @param emptyColor 该处没有任何非 Live 内容时返回的颜色，可为 null
     * @return 该处颜色；若完全透明则返回 emptyColor
     */
    public Color getNonLiveColorAtLonLat(double lon, double lat, Color emptyColor) {
        if (lat < WORLD_LAT_MIN || lat > WORLD_LAT_MAX) return emptyColor;

        // 非 EDT 线程 → 切到 EDT 上执行
        if (!SwingUtilities.isEventDispatchThread()) {
            final double flon = lon, flat = lat;
            final Color  femp = emptyColor;
            final Color[] out = new Color[1];
            try {
                SwingUtilities.invokeAndWait(
                        () -> out[0] = getNonLiveColorAtLonLat(flon, flat, femp));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return femp;
            } catch (InvocationTargetException ite) {
                return femp;
            }
            return out[0] != null ? out[0] : femp;
        }

        // 静态内容有改动 → 先重建世界画布，保证读到的是最新的
        if (worldDirty) rebuildWorldCanvas();

        // 从最高分辨率层（level 0）采样，精度最高
        MappedImage base = worldPyramid.get(0);
        int ppdBase = worldPyramid.getPpd(0);

        double nlon = normalizeLon(lon);
        int px = (int) Math.floor((nlon + 180.0) * ppdBase);
        int py = (int) Math.floor((WORLD_LAT_MAX - lat) * ppdBase);

        if (px < 0 || px >= base.getWidth() || py < 0 || py >= base.getHeight())
            return emptyColor;

        int argb = base.getARGB(px, py);
        int a = (argb >>> 24) & 0xFF;
        if (a == 0) return emptyColor;
        return new Color(argb, true);
    }

    /** 便捷重载：没有内容时返回 null。 */
    public Color getNonLiveColorAtLonLat(double lon, double lat) {
        return getNonLiveColorAtLonLat(lon, lat, null);
    }

    /**
     * 获取屏幕上某个像素处的非 Live 颜色。
     *
     * @param x          屏幕像素 x
     * @param y          屏幕像素 y
     * @param emptyColor 该处没有任何非 Live 内容时返回的颜色，可为 null
     * @return 该处颜色；若完全透明则返回 emptyColor
     */
    public Color huoqux_ychuyanse(int x, int y, Color emptyColor) {
        int W = getWidth(), H = getHeight();
        if (W <= 0 || H <= 0) return emptyColor;
        if (x < 0 || y < 0 || x >= W || y >= H) return emptyColor;

        // 屏幕坐标 → 经纬度
        double lon = lonCenter + (x - W / 2.0) / ppd;
        double lat = latCenter - (y - H / 2.0) / ppd;

        return getNonLiveColorAtLonLat(lon, lat, emptyColor);
    }

    /** 便捷重载：没有内容时返回 null。 */
    public Color huoqux_ychuyanse(int x, int y) {
        return huoqux_ychuyanse(x, y, null);
    }
}