package shijianjianting.gongju;



import shijianjianting.gongju.zhujie.LiveBinder;
import shijianjianting.gongju.zhujie.LiveRegistry;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.*;
import java.util.List;
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
    //  ★★★ 4 个缩放控制变量（你直接改这 4 个字段即可）★★★
    // ============================================================

    /** 按钮最小缩放倍数。1.0 = 不缩小，0.5 = 最多缩小到一半，0.1 = 极小。 */
    private float buttonMinScale = 2f;

    /** 按钮最大缩放倍数。1.0 = 不放大，2.0 = 最多放大到 2 倍，5.0 = 更大。 */
    private float buttonMaxScale = 5f;

    /** 文字最小缩放倍数。1.0 = 不缩小，0.5 = 最多缩小到一半。 */
    private float textMinScale   = 2f;

    /** 文字最大缩放倍数。1.0 = 不放大，2.0 = 最多放大到 2 倍。 */
    private float textMaxScale   = 5f;

    // ---------- 4 个变量的 getter / setter ----------

    public float getButtonMinScale() { return buttonMinScale; }
    /** 设置按钮最小缩放倍数，< 0.01 会被夹到 0.01。 */
    public void setButtonMinScale(float v) {
        this.buttonMinScale = Math.max(0.01f, v);
        if (this.buttonMaxScale < this.buttonMinScale) {
            this.buttonMaxScale = this.buttonMinScale;
        }
    }

    public float getButtonMaxScale() { return buttonMaxScale; }
    /** 设置按钮最大缩放倍数，< buttonMinScale 会被夹到 buttonMinScale。 */
    public void setButtonMaxScale(float v) {
        this.buttonMaxScale = Math.max(this.buttonMinScale, v);
    }

    public float getTextMinScale() { return textMinScale; }
    /** 设置文字最小缩放倍数。 */
    public void setTextMinScale(float v) {
        this.textMinScale = Math.max(0.01f, v);
        if (this.textMaxScale < this.textMinScale) {
            this.textMaxScale = this.textMinScale;
        }
    }

    public float getTextMaxScale() { return textMaxScale; }
    /** 设置文字最大缩放倍数。 */
    public void setTextMaxScale(float v) {
        this.textMaxScale = Math.max(this.textMinScale, v);
    }

    // ---------- 一次性设置 & 立即生效 ----------

    /** 一次性设置 4 个变量。 */
    public void setScaleLimits(float buttonMin, float buttonMax,
                               float textMin,   float textMax) {
        setButtonMinScale(buttonMin);
        setButtonMaxScale(buttonMax);
        setTextMinScale(textMin);
        setTextMaxScale(textMax);
    }

    /** 把当前 4 个变量应用到已经存在的所有按钮和文字上，立即生效。 */
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

    // ==================== 世界画布 ====================
    private static final int WORLD_PPD = 32;
    private static final int WORLD_W = 360 * WORLD_PPD;
    private static final int WORLD_H = 180 * WORLD_PPD;

    private final BufferedImage worldCanvas;
    private volatile boolean worldDirty = true;
    private boolean bakingMode = false;

    private int batchDepth = 0;

    // ==================== 静态颜色/字体 ====================
    private static final Color GRID_COLOR_PRIME  = new Color(255, 200, 100, 220);
    private static final Color GRID_COLOR_NORMAL = new Color(70, 70, 70, 160);
    private static final Color HUD_COLOR_MAIN    = new Color(255, 255, 255, 200);
    private static final Color HUD_COLOR_TIP     = new Color(180, 180, 180, 200);
    private static final Color HUD_COLOR_MOUSE   = new Color(120, 220, 255, 230);
    private static final Color HUD_COLOR_NOMSE   = new Color(140, 140, 140, 200);
    private static final Font  HUD_FONT          = new Font(UI_FONT, Font.PLAIN, 13);

    // ==================== 鼠标快照 ====================
    private static final class MouseSnapshot {
        final double lon, lat;
        MouseSnapshot(double lon, double lat) { this.lon = lon; this.lat = lat; }
    }
    private volatile MouseSnapshot mouseSnap = null;

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

        // ★ 每个组件自身的缩放上下限（创建时从 PaintBoard 4 个变量读取）
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

        // ★ 每条文字自身的缩放上下限（创建时从 PaintBoard 4 个变量读取）
        public float minScale = 0.4f;
        public float maxScale = 2.5f;

        transient Font cachedFont = null;
        transient float cachedFontSize = -1f;
        transient int   cachedFontStyle = -1;

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
    //  构造
    // ============================================================
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

        fpsMonitor.scheduleAtFixedRate(() -> {
            int frames = frameCounter.getAndSet(0);
            currentFps.set(frames);
        }, 1, 1, TimeUnit.SECONDS);
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
    //  单个对象覆盖缩放限制（可选 API）
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
        if (batchDepth == 0) { markWorldDirty(); repaint(); }
        return d;
    }

    public dian addGeoDot(double lon, double lat, double radiusDeg,
                          Color color, Source source) {
        dian d = new dian(lon, lat, radiusDeg,
                color, null, null, null, false, 0f);
        d.screenSpace = false;
        d.source = source;
        geoDots.add(d);
        if (batchDepth == 0) { markWorldDirty(); repaint(); }
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
    //  全自动绑定 API（注解驱动）
    // ============================================================
    public dian addAutoDot(Object target, double radiusDeg) {
        Source s = LiveBinder.toSource(target);
        dian d = new dian(s.getLon(), s.getLat(), radiusDeg,
                s.getColor(), null, null, s.getText(), false, 0f);
        d.source = s;
        d.screenSpace = false;
        geoDots.add(d);
        LiveRegistry.bind(target, this);
        if (batchDepth == 0) { markWorldDirty(); repaint(); }
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

        if (batchDepth == 0) { markWorldDirty(); repaint(); }
        return d;
    }

    public int addAutoText(Object target, int fontSize) {
        Source s = LiveBinder.toSource(target);
        GeoText t = addGeoTextInternal(
                s.getText(), s.getLon(), s.getLat(),
                fontSize, s.getColor(), true);
        // ★ 从 4 个变量中读取文字缩放限制
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
        // ★ 从 4 个变量中读取文字缩放限制
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
            if (!d.screenSpace) markWorldDirty();
            repaint();
        });
    }
    public void setGeoDotBorderColor(dian d, Color color) {
        if (d == null) return;
        runOnEDT(() -> {
            d.borderColor = color;
            if (!d.screenSpace) markWorldDirty();
            repaint();
        });
    }
    public void setGeoDotTextColor(dian d, Color color) {
        if (d == null) return;
        runOnEDT(() -> { d.textColor = color; if (!d.screenSpace) markWorldDirty(); repaint(); });
    }
    public void setGeoDotText(dian d, String text) {
        if (d == null) return;
        runOnEDT(() -> { d.text = text; if (!d.screenSpace) markWorldDirty(); repaint(); });
    }
    public void setGeoDotBorderWidth(dian d, float width) {
        if (d == null) return;
        runOnEDT(() -> { d.borderWidth = width; if (!d.screenSpace) markWorldDirty(); repaint(); });
    }
    public void setGeoDotStyle(dian d, Color fill, Color border, Color text) {
        if (d == null) return;
        runOnEDT(() -> {
            d.color = fill; d.borderColor = border; d.textColor = text;
            if (!d.screenSpace) markWorldDirty();
            repaint();
        });
    }
    public void setGeoDotLonLat(dian d, double lon, double lat) {
        if (d == null) return;
        runOnEDT(() -> { d.lon = lon; d.lat = lat; if (!d.screenSpace) markWorldDirty(); repaint(); });
    }
    public void setGeoDotRadius(dian d, double radiusDeg) {
        if (d == null) return;
        runOnEDT(() -> { d.radiusDeg = radiusDeg; if (!d.screenSpace) markWorldDirty(); repaint(); });
    }
    public void setGeoDotVisible(dian d, boolean visible) {
        if (d == null) return;
        runOnEDT(() -> { d.visible = visible; if (!d.screenSpace) markWorldDirty(); repaint(); });
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
        // ★ 从 4 个变量中读取文字缩放限制
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
        // ★ 从 4 个变量中读取按钮缩放限制
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
                ng.minScale         = g.minScale;   // ★ 保留缩放限制
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
            ng.minScale        = old.minScale;   // ★ 保留缩放限制
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
    //  布局：★ 对 scale 做 clamp（使用每对象自身的 min/max）
    // ============================================================
    private void layoutGeoComponents() {
        if (geoComponents.isEmpty()) return;
        int W = getWidth(), H = getHeight();
        if (W <= 0 || H <= 0) return;

        double halfW = W / 2.0;
        double halfH = H / 2.0;

        for (int i = 0, n = geoComponents.size(); i < n; i++) {
            GeoComponent g = geoComponents.get(i);

            int x = (int) Math.round((g.lon - lonCenter) * ppd + halfW);
            int y = (int) Math.round((latCenter - g.lat) * ppd + halfH);

            double scale = 1.0;
            if (g.scaleWithZoom && g.basePpd > 1e-9) {
                scale = ppd / g.basePpd;
                // ★ 关键：限制缩放倍数
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

            // ★ 字体也按 clamp 后的 scale 缩放
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
    //  每帧从 source 拉取最新值
    // ============================================================
    private void syncLive() {
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
            if (dotDirty) markWorldDirty();
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
                    ? Math.max(1, (int) Math.round(fontSize * WORLD_PPD / 6.0))
                    : fontSize;
            g2.setFont(new Font(UI_FONT, Font.PLAIN, fs));
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

        if (worldDirty) rebuildWorldCanvas();
        if (viewChanged()) rebuildVisibleLiveDots();

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            drawGridOnScreen(g2, W, H);
            drawWorldCanvas(g2, W, H);
            drawGeoDotsLive(g2, W, H);
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

        Map<Color, Path2D.Double> batches = new HashMap<>();
        List<dian> withBorderOrText = null;

        int n = visibleLiveDots.size();
        for (int i = 0; i < n; i++) {
            dian d = visibleLiveDots.get(i);
            if (!d.visible || d.color == null) continue;

            double rPx = d.radiusDeg * ppd;
            if (rPx < minLiveDotRadiusPx) rPx = minLiveDotRadiusPx;

            boolean simple = (d.borderWidth <= 0)
                    && (d.text == null || d.text.isEmpty());

            if (simple) {
                Path2D.Double path = batches.get(d.color);
                if (path == null) {
                    path = new Path2D.Double();
                    batches.put(d.color, path);
                }

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
                if (withBorderOrText == null) withBorderOrText = new ArrayList<>();
                withBorderOrText.add(d);
            }
        }

        for (Map.Entry<Color, Path2D.Double> e : batches.entrySet()) {
            g2.setColor(e.getKey());
            g2.fill(e.getValue());
        }

        if (withBorderOrText != null) {
            for (int i = 0, m = withBorderOrText.size(); i < m; i++) {
                dian d = withBorderOrText.get(i);
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
                        g2.setStroke(new BasicStroke(d.borderWidth));
                        g2.draw(new Ellipse2D.Double(
                                sx - rPx, sy - rPx, rPx * 2, rPx * 2));
                    }
                    if (d.text != null && !d.text.isEmpty()) {
                        int fs = (int) Math.max(9, Math.min(200, rPx * 1.5));
                        g2.setFont(new Font(UI_FONT, Font.PLAIN, fs));
                        g2.setColor(d.textColor != null ? d.textColor : Color.WHITE);
                        g2.drawString(d.text, (float)(sx + rPx + 4), (float)(sy + fs / 3));
                    }
                }
            }
        }
    }

    // ============================================================
    //  绘制地理文本（★ 用 clamp 后的 scale 算字体）
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
                // ★ 关键：限制缩放倍数
                if (s < t.minScale) s = t.minScale;
                if (s > t.maxScale) s = t.maxScale;
                fs = (float) (t.baseFontSize * s);
            }
            fs = clampF(fs, MIN_SCREEN_FONT_SIZE, MAX_SCREEN_FONT_SIZE);

            Font font = t.fontFor(fs);
            g2.setFont(font);

            FontMetrics fm = g2.getFontMetrics(font);
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
    //  世界画布（按缩放倍数切换插值）
    // ============================================================
    private void drawWorldCanvas(Graphics2D g2, int W, int H) {
        double scale = ppd / (double) WORLD_PPD;
        double tx = (-180 - lonCenter) * ppd + W / 2.0;
        double ty = (latCenter - 90) * ppd + H / 2.0;

        if (scale >= 2.0) {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g2.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,
                    RenderingHints.VALUE_ALPHA_INTERPOLATION_SPEED);
        } else {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,
                    RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        }

        double panelWidthPx = 360.0 * ppd;
        int copies = computeCopies(W, panelWidthPx);

        AffineTransform at = new AffineTransform();
        for (int k = -copies; k <= copies; k++) {
            double x0 = tx + k * panelWidthPx;
            if (x0 + panelWidthPx < 0) continue;
            if (x0 > W) continue;

            at.setToTranslation(x0, ty);
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
                for (int i = 0, n = shapes.size(); i < n; i++) shapes.get(i).paint(g, this);
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
        for (int i = 0, n = markers.size(); i < n; i++) {
            Marker m = markers.get(i);
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
        List<dian> individual = null;

        for (int i = 0, n = geoDots.size(); i < n; i++) {
            dian d = geoDots.get(i);
            if (d.screenSpace) continue;
            if (!d.visible) continue;

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
                if (individual == null) individual = new ArrayList<>();
                individual.add(d);
            }
        }

        for (Map.Entry<Color, Path2D.Double> e : batches.entrySet()) {
            g.setColor(e.getKey());
            g.fill(e.getValue());
        }

        if (individual != null) {
            for (int i = 0, m = individual.size(); i < m; i++) {
                dian d = individual.get(i);
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
        g2.drawString("拖拽旋转 · 滚轮缩放", 12, 42);

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
}