package neirong.gongju.xuanranqi;

import neirong.gongju.zhujie.LiveBinder;
import neirong.gongju.zhujie.LiveRegistry;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class PaintBoard extends JPanel {

    private static final String UI_FONT = pickUIFont();
    private static String pickUIFont() {
        String[] prefer = {"Microsoft YaHei","微软雅黑","PingFang SC","Hiragino Sans GB","Noto Sans CJK SC","Source Han Sans SC","WenQuanYi Micro Hei","SimHei","SimSun"};
        Set<String> avail = new HashSet<>(Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String n : prefer) if (avail.contains(n)) return n;
        return Font.SANS_SERIF;
    }

    private static final Font[] FONT_CACHE = new Font[256];
    private static Font uiFont(int size) {
        if (size < 1) size = 1;
        if (size > 255) return new Font(UI_FONT, Font.PLAIN, size);
        Font f = FONT_CACHE[size];
        return f != null ? f : (FONT_CACHE[size] = new Font(UI_FONT, Font.PLAIN, size));
    }

    private static final Map<Float, BasicStroke> STROKE_CACHE = new ConcurrentHashMap<>(8);
    private static BasicStroke strokeOf(float w) { return STROKE_CACHE.computeIfAbsent(w, BasicStroke::new); }
    private static final BasicStroke STROKE_1F = new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

    private float buttonMinScale = 2f, buttonMaxScale = 5f, textMinScale = 2f, textMaxScale = 5f;
    public float getButtonMinScale() { return buttonMinScale; }
    public void setButtonMinScale(float v) { buttonMinScale = Math.max(0.01f, v); if (buttonMaxScale < buttonMinScale) buttonMaxScale = buttonMinScale; }
    public float getButtonMaxScale() { return buttonMaxScale; }
    public void setButtonMaxScale(float v) { buttonMaxScale = Math.max(buttonMinScale, v); }
    public float getTextMinScale() { return textMinScale; }
    public void setTextMinScale(float v) { textMinScale = Math.max(0.01f, v); if (textMaxScale < textMinScale) textMaxScale = textMinScale; }
    public float getTextMaxScale() { return textMaxScale; }
    public void setTextMaxScale(float v) { textMaxScale = Math.max(textMinScale, v); }
    public void setScaleLimits(float bMin, float bMax, float tMin, float tMax) { setButtonMinScale(bMin); setButtonMaxScale(bMax); setTextMinScale(tMin); setTextMaxScale(tMax); }
    public void applyScaleLimitsToAll() {
        runOnEDT(() -> {
            for (int i = 0, n = geoComponents.size(); i < n; i++) { GeoComponent g = geoComponents.get(i); g.minScale = buttonMinScale; g.maxScale = buttonMaxScale; }
            for (int i = 0, n = geoTexts.size(); i < n; i++) { GeoText t = geoTexts.get(i); t.minScale = textMinScale; t.maxScale = textMaxScale; }
            layoutGeoComponents();
        });
    }

    public interface Source {
        double getLon(); double getLat();
        default Color getColor() { return null; } default String getText() { return null; }
        default Double getRadius() { return null; } default Float getFontSize() { return null; }
        default Boolean getVisible() { return null; }
    }
    private static final float MIN_SCREEN_FONT_SIZE = 8f, MAX_SCREEN_FONT_SIZE = 200f;
    private double minLiveDotRadiusPx = 0.6;
    public void setMinLiveDotRadiusPx(double r) { this.minLiveDotRadiusPx = Math.max(0, r); }
    public double getMinLiveDotRadiusPx() { return minLiveDotRadiusPx; }

    private static final int WORLD_BASE_PPD = 8, WORLD_LEVELS = 5, TRAIL_BASE_PPD = 4, TRAIL_LEVELS = 4;
    private static final int WORLD_W_HIGH = 360 * WORLD_BASE_PPD, WORLD_H_HIGH = 180 * WORLD_BASE_PPD;
    private MappedPyramid worldPyramid;
    private File pyramidDir;
    private BufferedImage highBakeBuffer;
    private BufferedImage ensureHighBakeBuffer() { if (highBakeBuffer == null) highBakeBuffer = new BufferedImage(WORLD_W_HIGH, WORLD_H_HIGH, BufferedImage.TYPE_INT_ARGB_PRE); return highBakeBuffer; }
    private static final Object GLOBAL_TRAIL_OWNER = new Object();

    private static final class TrailSeg {
        final double lon1, lat1, lon2, lat2; final Color color; final float screenWidthPx;
        TrailSeg(double lon1, double lat1, double lon2, double lat2, Color color, float screenWidthPx) {
            this.lon1 = lon1; this.lat1 = lat1; this.lon2 = lon2; this.lat2 = lat2; this.color = color; this.screenWidthPx = screenWidthPx;
        }
    }
    private static final class OwnerTrail {
        final Object owner; final MappedPyramid pyramid; volatile boolean hasAny = false;
        final List<TrailSeg> pending = new ArrayList<>(); volatile boolean flushScheduled = false;
        OwnerTrail(Object owner, MappedPyramid pyramid) { this.owner = owner; this.pyramid = pyramid; }
    }
    private final Map<Object, OwnerTrail> ownerTrails = new ConcurrentHashMap<>();

    public interface TrailPixelSink {
        void meichuzhixing(double jingdu, double weidu);
        default void meichuzhixingBatch(double[] buf, int pairCount) { for (int i = 0; i < pairCount; i++) meichuzhixing(buf[i * 2], buf[i * 2 + 1]); }
    }

    private volatile boolean worldDirty = true, hasAnyTrails = false;
    private boolean bakingMode = false;
    private int batchDepth = 0;
    private volatile boolean bakeStaticDots = true;
    public boolean isBakeStaticDots() { return bakeStaticDots; }
    public void setBakeStaticDots(boolean on) { if (this.bakeStaticDots == on) return; this.bakeStaticDots = on; if (on) markWorldDirty(); }
    private volatile boolean trailEnabled = true;
    public boolean isTrailEnabled() { return trailEnabled; }
    public void setTrailEnabled(boolean on) { this.trailEnabled = on; }
    private boolean trailAntialias = true;
    public boolean isTrailAntialias() { return trailAntialias; }
    public void setTrailAntialias(boolean on) { this.trailAntialias = on; }
    private boolean trailForceOpaque = true;
    public boolean isTrailForceOpaque() { return trailForceOpaque; }
    public void setTrailForceOpaque(boolean on) { this.trailForceOpaque = on; }
    private static final AtomicInteger TRAIL_DIR_SEQ = new AtomicInteger(0);

    private OwnerTrail ensureOwnerTrail(Object owner) {
        owner = GLOBAL_TRAIL_OWNER;
        OwnerTrail ot = ownerTrails.get(owner);
        if (ot != null) return ot;
        synchronized (ownerTrails) {
            ot = ownerTrails.get(owner); if (ot != null) return ot;
            try {
                File dir = new File(pyramidDir, "trail_shared");
                ot = new OwnerTrail(owner, new MappedPyramid(dir, TRAIL_BASE_PPD, TRAIL_LEVELS));
                ownerTrails.put(owner, ot);
            } catch (IOException e) { throw new RuntimeException("无法创建轨迹金字塔", e); }
        }
        return ot;
    }
    public void clearTrails() {
        runOnEDT(() -> {
            for (OwnerTrail ot : ownerTrails.values()) { synchronized (ot.pending) { ot.pending.clear(); } ot.pyramid.clear(); ot.hasAny = false; }
            hasAnyTrails = false;
        });
    }
    public void clearTrails(Object owner) {
        final Object fOwner = (owner != null) ? owner : GLOBAL_TRAIL_OWNER;
        runOnEDT(() -> {
            OwnerTrail ot = ownerTrails.get(fOwner); if (ot == null) return;
            synchronized (ot.pending) { ot.pending.clear(); }
            ot.pyramid.clear(); ot.hasAny = false;
            boolean any = false; for (OwnerTrail x : ownerTrails.values()) if (x.hasAny) { any = true; break; }
            hasAnyTrails = any;
        });
    }
    public void removeOwnerTrails(Object owner) { /* 共享金字塔，不删 */ }
    public void addTrailSegment(double lon1, double lat1, double lon2, double lat2, Color color, float screenWidthPx) { addTrailSegment(GLOBAL_TRAIL_OWNER, lon1, lat1, lon2, lat2, color, screenWidthPx); }
    public void addTrailSegment(Object owner, double lon1, double lat1, double lon2, double lat2, Color color, float screenWidthPx) {
        if (!trailEnabled) return;
        if (color == null) color = Color.WHITE;
        if (screenWidthPx <= 0f) screenWidthPx = 1f;
        final Object fOwner = GLOBAL_TRAIL_OWNER;
        final OwnerTrail ot = ensureOwnerTrail(fOwner);
        Color baseColor = color;
        if (trailForceOpaque && color.getAlpha() != 255) baseColor = new Color(color.getRGB() | 0xFF000000, true);
        TrailSeg seg = new TrailSeg(lon1, lat1, lon2, lat2, baseColor, screenWidthPx);
        boolean needSchedule;
        synchronized (ot.pending) { ot.pending.add(seg); needSchedule = !ot.flushScheduled; if (needSchedule) ot.flushScheduled = true; }
        if (needSchedule) SwingUtilities.invokeLater(() -> flushTrailPending(ot));
    }
    private void flushTrailPending(OwnerTrail ot) {
        List<TrailSeg> batch;
        synchronized (ot.pending) {
            if (ot.pending.isEmpty()) { ot.flushScheduled = false; return; }
            batch = new ArrayList<>(ot.pending); ot.pending.clear(); ot.flushScheduled = false;
        }
        double p = ppd; if (!(p > 0)) p = 1;
        boolean touched = false;
        for (int si = 0; si < batch.size(); si++) {
            TrailSeg seg = batch.get(si);
            double nLon1 = normalizeLon(seg.lon1), dLon = seg.lon2 - seg.lon1;
            dLon = ((dLon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
            double nLon2 = nLon1 + dLon;
            for (int lv = 0; lv < ot.pyramid.levels; lv++) {
                MappedImage img = ot.pyramid.get(lv); int srcPpd = ot.pyramid.getPpd(lv);
                double x1 = (nLon1 + 180.0) * srcPpd, y1 = (90 - seg.lat1) * srcPpd;
                double x2 = (nLon2 + 180.0) * srcPpd, y2 = (90 - seg.lat2) * srcPpd;
                float bw = (float) Math.max(1.0, seg.screenWidthPx * srcPpd / p);
                double pad = bw + 1;
                int bx1 = Math.max(0, (int) Math.floor(Math.min(x1, x2) - pad)), by1 = Math.max(0, (int) Math.floor(Math.min(y1, y2) - pad));
                int bx2 = Math.min(img.getWidth(), (int) Math.ceil(Math.max(x1, x2) + pad)), by2 = Math.min(img.getHeight(), (int) Math.ceil(Math.max(y1, y2) + pad));
                if (bx1 >= bx2 || by1 >= by2) continue;
                int rw = bx2 - bx1, rh = by2 - by1;
                BufferedImage tile = img.readRegion(bx1, by1, rw, rh); if (tile == null) continue;
                Graphics2D g = tile.createGraphics();
                try {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, trailAntialias ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
                    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                    g.setColor(seg.color);
                    g.setStroke(new BasicStroke(bw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.draw(new Line2D.Double(x1 - bx1, y1 - by1, x2 - bx1, y2 - by1));
                } finally { g.dispose(); }
                img.writeRect(tile, bx1, by1); touched = true;
            }
        }
        if (touched) { ot.hasAny = true; hasAnyTrails = true; }
    }
    public void addTrailDot(double lon, double lat, double radiusDeg, Color color) { addTrailDot(GLOBAL_TRAIL_OWNER, lon, lat, radiusDeg, color); }
    public void addTrailDot(Object owner, double lon, double lat, double radiusDeg, Color color) {
        if (!trailEnabled) return;
        if (color == null) color = Color.WHITE;
        double p = ppd; if (!(p > 0)) p = 1;
        addTrailSegment(owner, lon, lat, lon, lat, color, (float) Math.max(1.0, 2.0 * radiusDeg * p));
    }

    private static final Color GRID_COLOR_PRIME = new Color(255, 200, 100, 220), GRID_COLOR_NORMAL = new Color(70, 70, 70, 160),
            HUD_COLOR_MAIN = new Color(255, 255, 255, 200), HUD_COLOR_TIP = new Color(180, 180, 180, 200),
            HUD_COLOR_MOUSE = new Color(120, 220, 255, 230), HUD_COLOR_NOMSE = new Color(140, 140, 140, 200);
    private static final Font HUD_FONT = new Font(UI_FONT, Font.PLAIN, 13);

    private Point selectionStart = null, selectionEnd = null;
    private volatile boolean selectionActive = false;
    private Color selectionFillColor = new Color(0, 120, 215, 60), selectionBorderColor = new Color(0, 120, 215, 220);
    public boolean isSelectionActive() { return selectionActive; }
    public void setSelectionColors(Color fill, Color border) { if (fill != null) selectionFillColor = fill; if (border != null) selectionBorderColor = border; }
    public void beginSelection(int x, int y) { selectionStart = new Point(x, y); selectionEnd = new Point(x, y); selectionActive = true; }
    public void updateSelection(int x, int y) { if (!selectionActive) return; selectionEnd = new Point(x, y); }
    public Rectangle endSelection() { if (!selectionActive) return null; Rectangle r = getSelectionRect(); selectionActive = false; selectionStart = null; selectionEnd = null; return r; }
    public void cancelSelection() { if (!selectionActive) return; selectionActive = false; selectionStart = null; selectionEnd = null; }
    public Rectangle getSelectionRect() {
        if (!selectionActive || selectionStart == null || selectionEnd == null) return null;
        int x1 = Math.min(selectionStart.x, selectionEnd.x), y1 = Math.min(selectionStart.y, selectionEnd.y);
        int x2 = Math.max(selectionStart.x, selectionEnd.x), y2 = Math.max(selectionStart.y, selectionEnd.y);
        if (x2 - x1 <= 0 && y2 - y1 <= 0) return null;
        return new Rectangle(x1, y1, x2 - x1, y2 - y1);
    }
    public static final class MouseSnapshot {
        public final double lon, lat;
        MouseSnapshot(double lon, double lat) { this.lon = lon; this.lat = lat; }
    }
    public MouseSnapshot mouseSnap = null;
    private static final double WORLD_LAT_MIN = -90, WORLD_LAT_MAX = 90;
    private double lonCenter = 0, latCenter = 0, ppd = 6, minPpd = 6, maxPpd = 400;
    private Double lonBoundMin = null, lonBoundMax = null, latBoundMin = null, latBoundMax = null;
    private static final double GRID_STEP = 15;
    private int virtualButtonHalfHeight = 15;
    public void setVirtualButtonHalfHeight(int h) { this.virtualButtonHalfHeight = Math.max(0, h); }
    public int getVirtualButtonHalfHeight() { return virtualButtonHalfHeight; }
    private Color brushColor = Color.WHITE;
    private int brushSize = 3;
    public void setBrushColor(Color c) { this.brushColor = c; }
    public Color getBrushColor() { return brushColor; }
    public void setBrushSize(int s) { this.brushSize = s; }
    public int getBrushSize() { return brushSize; }

    private interface GeoShape { void paint(Graphics2D g2, PaintBoard b); }
    private final List<GeoShape> shapes = new ArrayList<>();
    private static class Marker {
        double lon, lat; Color color; int size; String text;
        Marker(double lon, double lat, Color c, int s, String t) { this.lon = lon; this.lat = lat; color = c; size = s; text = t; }
    }
    private final List<Marker> markers = new ArrayList<>();
    public static class dian {
        public double lon, lat, radiusDeg;
        public Color color, borderColor, textColor;
        public String text; public boolean textScale; public float borderWidth;
        public boolean visible = true; public boolean screenSpace = false;
        public Source source = null; public transient double cachedSx, cachedSy;
        public dian(double lon, double lat, double radiusDeg, Color color, Color borderColor, Color textColor, String text, boolean textScale, float borderWidth) {
            this.lon = lon; this.lat = lat; this.radiusDeg = radiusDeg;
            this.color = color; this.borderColor = borderColor; this.textColor = textColor;
            this.text = text; this.textScale = textScale; this.borderWidth = borderWidth;
        }
    }
    private final List<dian> geoDots = new ArrayList<>(), liveDots = new ArrayList<>(), visibleLiveDots = new ArrayList<>();
    private double cachedVpLonCenter = Double.NaN, cachedVpLatCenter = Double.NaN, cachedVpPpd = Double.NaN;
    private int cachedVpW = -1, cachedVpH = -1;

    /* ============================================================
     *  ★ 可见 live 点缓存版本号
     * ============================================================ */
    private volatile long liveDotsVersion = 0L;
    private long cachedLiveDotsVersion = -1L;
    private boolean liveDotsCacheValid = false;

    public void bumpLiveDotsVersion() { liveDotsVersion++; }

    /* ============================================================
     *  ★ liveDots 经度分桶索引
     * ============================================================ */
    private static final double LON_BUCKET_DEG = 2.0;
    private static final int    LON_BUCKET_COUNT = (int) (360.0 / LON_BUCKET_DEG);
    @SuppressWarnings("unchecked")
    private final List<dian>[] liveDotBuckets = new List[LON_BUCKET_COUNT];
    private volatile boolean liveDotBucketsDirty = true;

    private int lonBucketIndex(double lon) {
        lon = ((lon + 180.0) % 360.0 + 360.0) % 360.0;
        int idx = (int) (lon / LON_BUCKET_DEG);
        if (idx < 0) idx = 0;
        if (idx >= LON_BUCKET_COUNT) idx = LON_BUCKET_COUNT - 1;
        return idx;
    }

    private void rebuildLiveDotBuckets() {
        for (int i = 0; i < LON_BUCKET_COUNT; i++) liveDotBuckets[i] = null;
        for (int i = 0, n = liveDots.size(); i < n; i++) {
            dian d = liveDots.get(i);
            int idx = lonBucketIndex(d.lon);
            List<dian> b = liveDotBuckets[idx];
            if (b == null) { b = new ArrayList<>(); liveDotBuckets[idx] = b; }
            b.add(d);
        }
        liveDotBucketsDirty = false;
    }

    public void markLiveDotBucketsDirty() { liveDotBucketsDirty = true; }

    /* ============================================================
     *  ★ 屏幕空间聚类
     * ============================================================ */
    private static final int CLUSTER_CELL_PX = 6;
    private static final int CLUSTER_TRIGGER = 400;
    private final Map<Long, dian> clusterMap = new HashMap<>();
    private final List<dian> clusterRepresentatives = new ArrayList<>();

    private static long clusterKey(int cx, int cy) {
        return ((long) cx << 32) ^ (cy & 0xFFFFFFFFL);
    }

    private void buildClusterRepresentatives() {
        clusterMap.clear();
        clusterRepresentatives.clear();
        for (int i = 0, n = visibleLiveDots.size(); i < n; i++) {
            dian d = visibleLiveDots.get(i);
            int cx = (int) (d.cachedSx / CLUSTER_CELL_PX);
            int cy = (int) (d.cachedSy / CLUSTER_CELL_PX);
            long k = clusterKey(cx, cy);
            if (!clusterMap.containsKey(k)) {
                clusterMap.put(k, d);
                clusterRepresentatives.add(d);
            }
        }
    }

    public static class LiveLine {
        public int bianhao; public double lon1, lat1, lon2, lat2;
        public Color color; public float strokeWidth; public boolean visible = true; public Object tag = null;
        LiveLine(int bianhao, double lon1, double lat1, double lon2, double lat2, Color color, float strokeWidth) {
            this.bianhao = bianhao; this.lon1 = lon1; this.lat1 = lat1; this.lon2 = lon2; this.lat2 = lat2;
            this.color = color; this.strokeWidth = strokeWidth;
        }
    }
    private final List<LiveLine> liveLines = new ArrayList<>();
    private final AtomicInteger nextLiveLineBianhao = new AtomicInteger(1);

    private static final class GeoComponent {
        final int bianhao; final JComponent comp; double lon, lat;
        final int anchorX, anchorY; int baseW, baseH; final double basePpd; final Font baseFont;
        boolean scaleWithZoom; float currentFontSize = -1f;
        Source source = null; boolean visibleBySource = true;
        float minScale = 0.4f, maxScale = 2.5f;
        GeoComponent(int bianhao, JComponent comp, double lon, double lat, int ax, int ay, int baseW, int baseH, double basePpd, Font baseFont, boolean scaleWithZoom) {
            this.bianhao = bianhao; this.comp = comp; this.lon = lon; this.lat = lat;
            this.anchorX = ax; this.anchorY = ay; this.baseW = baseW; this.baseH = baseH;
            this.basePpd = basePpd; this.baseFont = baseFont; this.scaleWithZoom = scaleWithZoom;
        }
    }
    private final List<GeoComponent> geoComponents = new ArrayList<>();
    private volatile boolean geoLayoutScheduled = false;
    private final PropertyChangeListener geoCompPropListener = new PropertyChangeListener() {
        @Override public void propertyChange(PropertyChangeEvent evt) { if ("preferredSize".equals(evt.getPropertyName())) scheduleGeoLayout(); }
    };
    private void scheduleGeoLayout() {
        if (geoLayoutScheduled) return;
        geoLayoutScheduled = true;
        SwingUtilities.invokeLater(() -> {
            geoLayoutScheduled = false;
            for (int i = 0, n = geoComponents.size(); i < n; i++) {
                GeoComponent g = geoComponents.get(i); Dimension d = g.comp.getPreferredSize();
                if (d != null) { if (d.width > 0) g.baseW = d.width; if (d.height > 0) g.baseH = d.height; }
            }
            layoutGeoComponents();
        });
    }

    public static class GeoText {
        public int bianhao = 0; public double lon, lat; public String text; public Color color;
        public float baseFontSize; public boolean scaleWithZoom; public double offsetX = 0, offsetY = 0;
        final double basePpd; public JComponent attachedTo = null; public Color outlineColor = null;
        public float outlineWidth = 2f; public int fontStyle = Font.BOLD; public Source source = null;
        public boolean visible = true; public float minScale = 0.4f, maxScale = 2.5f;
        transient Font cachedFont = null; transient float cachedFontSize = -1f;
        transient int cachedFontStyle = -1; transient FontMetrics cachedFm = null; transient Font cachedFmFont = null;
        GeoText(double lon, double lat, String text, float fontSize, Color color, boolean scaleWithZoom, double basePpd, int bianhao) {
            this.lon = lon; this.lat = lat; this.text = text; this.baseFontSize = fontSize;
            this.color = color; this.scaleWithZoom = scaleWithZoom; this.basePpd = basePpd; this.bianhao = bianhao;
        }
        Font fontFor(float size) {
            if (cachedFont == null || Math.abs(cachedFontSize - size) > 0.01f || cachedFontStyle != fontStyle) {
                cachedFont = new Font(UI_FONT, fontStyle, 12).deriveFont(size); cachedFontSize = size; cachedFontStyle = fontStyle;
            }
            return cachedFont;
        }
    }
    private final List<GeoText> geoTexts = new ArrayList<>();
    private final AtomicInteger nextAnniuBianhao = new AtomicInteger(1), nextWenbenBianhao = new AtomicInteger(1);
    private GeoComponent findAnniu(int bianhao) {
        if (bianhao <= 0) return null;
        for (int i = 0, n = geoComponents.size(); i < n; i++) if (geoComponents.get(i).bianhao == bianhao) return geoComponents.get(i);
        return null;
    }
    private GeoText findWenben(int bianhao) {
        if (bianhao <= 0) return null;
        for (int i = 0, n = geoTexts.size(); i < n; i++) if (geoTexts.get(i).bianhao == bianhao) return geoTexts.get(i);
        return null;
    }

    private Point dragStart; private double startLon, startLat; private boolean dragging;
    private final AtomicInteger frameCounter = new AtomicInteger(0), currentFps = new AtomicInteger(0);
    private final ScheduledExecutorService fpsMonitor = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "fps-monitor"); t.setDaemon(true); t.setPriority(Thread.MIN_PRIORITY); return t; });
    private void tickFrame() { frameCounter.incrementAndGet(); }
    public int getFps() { return currentFps.get(); }
    public int sampleFpsNow() { int v = frameCounter.getAndSet(0); currentFps.set(v); return v; }
    public double[] shibiaojingweidu() { MouseSnapshot s = mouseSnap; if (s == null) return null; return new double[]{ s.lon, s.lat }; }

    private final Map<Color, Path2D.Double> batchPathCache = new HashMap<>();
    private final List<dian> individualDotCache = new ArrayList<>();
    private void resetBatchCache() { for (Path2D.Double p : batchPathCache.values()) p.reset(); individualDotCache.clear(); }
    private Path2D.Double pathFor(Color c) { Path2D.Double p = batchPathCache.get(c); if (p == null) { p = new Path2D.Double(); batchPathCache.put(c, p); } return p; }
    private static boolean hasContent(Path2D.Double p) { return !p.getPathIterator(null).isDone(); }
    private BufferedImage pickBuffer; private int pickBufferW = -1, pickBufferH = -1;
    private BufferedImage ensurePickBuffer(int W, int H) {
        if (pickBuffer == null || pickBufferW != W || pickBufferH != H) { pickBuffer = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB); pickBufferW = W; pickBufferH = H; }
        return pickBuffer;
    }

    public PaintBoard() {
        setLayout(null);

        File tmpRoot = new File(System.getProperty("java.io.tmpdir"));
        File[] oldDirs = tmpRoot.listFiles((d, name) -> name.startsWith("paintboard_"));
        if (oldDirs != null) {
            for (File d : oldDirs) {
                try { deleteRecursively(d); } catch (Exception ignored) {}
            }
        }

        pyramidDir = new File(System.getProperty("java.io.tmpdir"), "paintboard_" + Integer.toHexString(System.identityHashCode(this)) + "_" + System.currentTimeMillis());
        try {
            worldPyramid = new MappedPyramid(new File(pyramidDir, "world"), WORLD_BASE_PPD, WORLD_LEVELS);
            ownerTrails.put(GLOBAL_TRAIL_OWNER, new OwnerTrail(GLOBAL_TRAIL_OWNER, new MappedPyramid(new File(pyramidDir, "trail"), TRAIL_BASE_PPD, TRAIL_LEVELS)));
        } catch (IOException e) { throw new RuntimeException("无法创建磁盘金字塔", e); }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { worldPyramid.close(); } catch (Exception ignored) {}
            for (OwnerTrail ot : ownerTrails.values()) { try { ot.pyramid.close(); } catch (Exception ignored) {} }
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            System.gc();
            deleteRecursively(pyramidDir);
        }));
        setBackground(Color.BLACK); setPreferredSize(new Dimension(900, 600));
        MouseAdapter ma = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                dragStart = e.getPoint(); startLon = lonCenter; startLat = latCenter; dragging = true;
                setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)); updateMouse(e);
            }
            @Override public void mouseDragged(MouseEvent e) {
                if (dragging && dragStart != null) {
                    lonCenter = startLon - (e.getX() - dragStart.x) / ppd;
                    latCenter = startLat + (e.getY() - dragStart.y) / ppd;
                    clampView();
                }
                updateMouse(e);
            }
            @Override public void mouseReleased(MouseEvent e) {
                if (!dragging) { updateMouse(e); return; }
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                dragging = false; dragStart = null; setCursor(Cursor.getDefaultCursor()); updateMouse(e);
            }
            @Override public void mouseMoved(MouseEvent e) { updateMouse(e); }
            @Override public void mouseExited(MouseEvent e) { mouseSnap = null; }
        };
        addMouseListener(ma); addMouseMotionListener(ma);
        addMouseWheelListener(e -> {
            if (getWidth() <= 0 || getHeight() <= 0) return;
            double factor = Math.pow(1.12, -e.getWheelRotation());
            double target = clamp(ppd * factor, minPpd, maxPpd);
            if (target == ppd) return;
            int mx = e.getX(), my = e.getY();
            double lonUnder = lonCenter + (mx - getWidth() / 2.0) / ppd, latUnder = latCenter - (my - getHeight() / 2.0) / ppd;
            ppd = target;
            lonCenter = lonUnder - (mx - getWidth() / 2.0) / ppd;
            latCenter = latUnder + (my - getHeight() / 2.0) / ppd;
            clampView();
        });
        addComponentListener(new ComponentAdapter() { @Override public void componentResized(ComponentEvent e) { clampView(); } });
        fpsMonitor.scheduleAtFixedRate(() -> currentFps.set(frameCounter.getAndSet(0)), 1, 1, TimeUnit.SECONDS);
    }
    private static void deleteRecursively(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) { File[] ks = f.listFiles(); if (ks != null) for (File k : ks) deleteRecursively(k); }
        for (int i = 0; i < 3; i++) {
            if (f.delete() || !f.exists()) return;
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            System.gc();
        }
    }
    private void updateMouse(MouseEvent e) {
        if (getWidth() <= 0 || getHeight() <= 0) return;
        double[] ll = screenToLonLat(e.getX(), e.getY()); mouseSnap = new MouseSnapshot(ll[0], ll[1]);
    }
    private void markWorldDirty() { worldDirty = true; }
    public void Static() { markWorldDirty(); }
    public void beginBatch() { if (SwingUtilities.isEventDispatchThread()) batchDepth++; else SwingUtilities.invokeLater(this::beginBatch); }
    public void endBatch() { runOnEDT(() -> { if (batchDepth > 0) batchDepth--; if (batchDepth == 0) markWorldDirty(); }); }

    public void setComponentScaleLimit(JComponent comp, float min, float max) {
        if (comp == null) return;
        runOnEDT(() -> {
            for (int i = 0, n = geoComponents.size(); i < n; i++) {
                GeoComponent g = geoComponents.get(i);
                if (g.comp == comp) { g.minScale = Math.max(0.01f, min); g.maxScale = Math.max(g.minScale, max); layoutGeoComponents(); return; }
            }
        });
    }
    public void setComponentScaleLimitByBianhao(int bianhao, float min, float max) {
        runOnEDT(() -> { GeoComponent g = findAnniu(bianhao); if (g == null) return; g.minScale = Math.max(0.01f, min); g.maxScale = Math.max(g.minScale, max); layoutGeoComponents(); });
    }
    public void setTextScaleLimit(int bianhao, float min, float max) {
        runOnEDT(() -> { GeoText t = findWenben(bianhao); if (t == null) return; t.minScale = Math.max(0.01f, min); t.maxScale = Math.max(t.minScale, max); });
    }

    public dian addGeoDot(double lon, double lat, double radiusDeg, Color color) { return addGeoDot(lon, lat, radiusDeg, color, (String) null); }
    public dian addGeoDot(double lon, double lat, double radiusDeg, Color color, String text) { return addGeoDot(lon, lat, radiusDeg, color, null, null, text, false, 0f); }
    public dian addGeoDot(double lon, double lat, double radiusDeg, Color color, Color borderColor, Color textColor, String text, boolean textScale, float borderWidth) {
        dian d = new dian(lon, lat, radiusDeg, color, borderColor, textColor, text, textScale, borderWidth);
        d.screenSpace = false; geoDots.add(d);
        if (batchDepth == 0 && bakeStaticDots) markWorldDirty();
        return d;
    }
    public dian addGeoDot(double lon, double lat, double radiusDeg, Color color, Source source) {
        dian d = new dian(lon, lat, radiusDeg, color, null, null, null, false, 0f);
        d.screenSpace = false; d.source = source; geoDots.add(d);
        if (batchDepth == 0 && bakeStaticDots) markWorldDirty();
        return d;
    }
    public dian addGeoDotLive(double lon, double lat, double radiusDeg, Color color) { return addGeoDotLive(lon, lat, radiusDeg, color, null, 0f); }
    public dian addGeoDotLive(double lon, double lat, double radiusDeg, Color color, Color borderColor, float borderWidth) {
        dian d = new dian(lon, lat, radiusDeg, color, borderColor, null, null, false, borderWidth);
        d.screenSpace = true; geoDots.add(d); liveDots.add(d);
        markLiveDotBucketsDirty(); bumpLiveDotsVersion();
        return d;
    }
    public dian addGeoDotLive(double lon, double lat, double radiusDeg, Color color, Source source) {
        dian d = new dian(lon, lat, radiusDeg, color, null, null, null, false, 0f);
        d.screenSpace = true; d.source = source; geoDots.add(d); liveDots.add(d);
        markLiveDotBucketsDirty(); bumpLiveDotsVersion();
        return d;
    }
    public int addLiveLine(double lon1, double lat1, double lon2, double lat2) { return addLiveLine(lon1, lat1, lon2, lat2, Color.WHITE, 2f); }
    public int addLiveLine(double lon1, double lat1, double lon2, double lat2, Color color) { return addLiveLine(lon1, lat1, lon2, lat2, color, 2f); }
    public int addLiveLine(double lon1, double lat1, double lon2, double lat2, Color color, float strokeWidth) {
        final int bianhao = nextLiveLineBianhao.getAndIncrement();
        final LiveLine L = new LiveLine(bianhao, lon1, lat1, lon2, lat2, color, strokeWidth);
        runOnEDT(() -> liveLines.add(L));
        return bianhao;
    }
    public void removeLiveLine(int bianhao) {
        if (bianhao <= 0) return;
        runOnEDT(() -> { for (int i = 0, n = liveLines.size(); i < n; i++) if (liveLines.get(i).bianhao == bianhao) { liveLines.remove(i); return; } });
    }
    public void clearLiveLines() { runOnEDT(() -> { if (!liveLines.isEmpty()) liveLines.clear(); }); }
    public LiveLine getLiveLine(int bianhao) {
        if (bianhao <= 0) return null;
        for (int i = 0, n = liveLines.size(); i < n; i++) if (liveLines.get(i).bianhao == bianhao) return liveLines.get(i);
        return null;
    }
    public void setLiveLineVisible(int bianhao, boolean visible) {
        runOnEDT(() -> { LiveLine L = getLiveLine(bianhao); if (L == null || L.visible == visible) return; L.visible = visible; });
    }
    public int getLiveLineCount() { return liveLines.size(); }

    public dian addAutoDot(Object target, double radiusDeg) {
        Source s = LiveBinder.toSource(target);
        dian d = new dian(s.getLon(), s.getLat(), radiusDeg, s.getColor(), null, null, s.getText(), false, 0f);
        d.source = s; d.screenSpace = false; geoDots.add(d); LiveRegistry.bind(target, this);
        if (batchDepth == 0 && bakeStaticDots) markWorldDirty();
        return d;
    }
    public dian addAutoDot(Object pos, Object colorObj, double radiusDeg) {
        Source ps = LiveBinder.toSource(pos);
        Source cs = (colorObj == null) ? null : LiveBinder.toSource(colorObj);
        Color init = (cs != null ? cs.getColor() : ps.getColor());
        dian d = new dian(ps.getLon(), ps.getLat(), radiusDeg, init, null, null, ps.getText(), false, 0f);
        if (cs == null) d.source = ps;
        else d.source = new Source() {
            @Override public double getLon() { return ps.getLon(); }
            @Override public double getLat() { return ps.getLat(); }
            @Override public Color getColor() { Color c = cs.getColor(); return (c != null) ? c : ps.getColor(); }
            @Override public String getText() { return ps.getText(); }
            @Override public Double getRadius() { return ps.getRadius(); }
            @Override public Float getFontSize() { return ps.getFontSize(); }
            @Override public Boolean getVisible() { return ps.getVisible(); }
        };
        d.screenSpace = false; geoDots.add(d);
        LiveRegistry.bind(pos, this); if (colorObj != null) LiveRegistry.bind(colorObj, this);
        if (batchDepth == 0 && bakeStaticDots) markWorldDirty();
        return d;
    }
    public int addAutoText(Object target, int fontSize) { return addAutoText(target, fontSize, null); }
    public int addAutoText(Object target, int fontSize, JComponent attachedTo) {
        Source s = LiveBinder.toSource(target);
        GeoText t = addGeoTextInternal(s.getText(), s.getLon(), s.getLat(), fontSize, s.getColor(), true);
        if (attachedTo != null) t.attachedTo = attachedTo;
        t.minScale = textMinScale; t.maxScale = textMaxScale; t.source = s;
        LiveRegistry.bind(target, this);
        return t.bianhao;
    }
    public int addAutoComponent(JComponent comp, Object target) {
        if (comp == null) return -1;
        Source s = LiveBinder.toSource(target); Dimension d = comp.getPreferredSize();
        int bianhao = addGeoComponentInternal2(comp, s.getLon(), s.getLat(), -d.width / 2, -d.height / 2, true, s);
        LiveRegistry.bind(target, this);
        return bianhao;
    }
    public int addAutoComponent(JComponent comp, Object target, int anchorX, int anchorY) {
        if (comp == null) return -1;
        Source s = LiveBinder.toSource(target);
        int bianhao = addGeoComponentInternal2(comp, s.getLon(), s.getLat(), anchorX, anchorY, true, s);
        LiveRegistry.bind(target, this);
        return bianhao;
    }

    private void updDot(dian d, Runnable r) { if (d == null) return; runOnEDT(() -> { r.run(); if (!d.screenSpace && bakeStaticDots) markWorldDirty(); }); }
    public void setGeoDotScreenSpace(dian d, boolean on) {
        if (d == null) return;
        runOnEDT(() -> {
            if (d.screenSpace == on) return;
            d.screenSpace = on;
            if (on) { if (!liveDots.contains(d)) liveDots.add(d); } else liveDots.remove(d);
            markLiveDotBucketsDirty();
            bumpLiveDotsVersion();
            markWorldDirty();
        });
    }
    public void removeGeoDot(dian d) {
        if (d == null) return;
        geoDots.remove(d); liveDots.remove(d);
        markLiveDotBucketsDirty();
        bumpLiveDotsVersion();
        markWorldDirty();
    }
    public void clearGeoDots() {
        geoDots.clear(); liveDots.clear(); visibleLiveDots.clear();
        markLiveDotBucketsDirty();
        bumpLiveDotsVersion();
        markWorldDirty();
    }
    public void setGeoDotColor(dian d, Color color) { updDot(d, () -> d.color = color); }
    public void setGeoDotBorderColor(dian d, Color color) { updDot(d, () -> d.borderColor = color); }
    public void setGeoDotTextColor(dian d, Color color) { updDot(d, () -> d.textColor = color); }
    public void setGeoDotText(dian d, String text) { updDot(d, () -> d.text = text); }
    public void setGeoDotBorderWidth(dian d, float width) { updDot(d, () -> d.borderWidth = width); }
    public void setGeoDotStyle(dian d, Color fill, Color border, Color text) { updDot(d, () -> { d.color = fill; d.borderColor = border; d.textColor = text; }); }
    public void setGeoDotLonLat(dian d, double lon, double lat) { updDot(d, () -> { d.lon = lon; d.lat = lat; }); }
    public void setGeoDotRadius(dian d, double radiusDeg) { updDot(d, () -> d.radiusDeg = radiusDeg); }
    public void setGeoDotVisible(dian d, boolean visible) { updDot(d, () -> d.visible = visible); }

    private GeoText addGeoTextInternal(String text, double lon, double lat, int fontSize, Color color, boolean scaleWithZoom) {
        final int bianhao = nextWenbenBianhao.getAndIncrement();
        GeoText t = new GeoText(lon, lat, text, fontSize, color, scaleWithZoom, this.ppd, bianhao);
        t.outlineColor = new Color(0, 0, 0, 220); t.outlineWidth = 2.5f;
        t.minScale = textMinScale; t.maxScale = textMaxScale;
        runOnEDT(() -> geoTexts.add(t));
        return t;
    }
    public int addGeoText(String text, double lon, double lat, int fontSize) { return addGeoText(text, lon, lat, fontSize, Color.WHITE, true); }
    public int addGeoText(String text, double lon, double lat, int fontSize, Color color) { return addGeoText(text, lon, lat, fontSize, color, true); }
    public int addGeoText(String text, double lon, double lat, int fontSize, Color color, boolean scaleWithZoom) { return addGeoTextInternal(text, lon, lat, fontSize, color, scaleWithZoom).bianhao; }
    public int addGeoText(String text, double lon, double lat, int fontSize, Color color, JComponent attachedTo) {
        GeoText t = addGeoTextInternal(text, lon, lat, fontSize, color, true); t.attachedTo = attachedTo; return t.bianhao;
    }
    public int addGeoText(String text, double lon, double lat, int fontSize, Color color, JComponent attachedTo, Source source) {
        GeoText t = addGeoTextInternal(text, lon, lat, fontSize, color, true); t.attachedTo = attachedTo; t.source = source; return t.bianhao;
    }
    public void removeGeoText(GeoText t) { if (t != null) geoTexts.remove(t); }
    public void clearGeoTexts() { geoTexts.clear(); }
    public GeoText getWenbenObject(int bianhao) { return findWenben(bianhao); }
    public void setwenbenBYbianhao(int bianhao, String text, double lon, double lat, int fontSize) { setWenben0(bianhao, text, lon, lat, fontSize, null, null, null, false); }
    public void setwenbenBYbianhao(int bianhao, String text, double lon, double lat, int fontSize, Color color) { setWenben0(bianhao, text, lon, lat, fontSize, color, null, null, false); }
    public void setwenbenBYbianhao(int bianhao, String text, double lon, double lat, int fontSize, Color color, boolean scaleWithZoom) { setWenben0(bianhao, text, lon, lat, fontSize, color, scaleWithZoom, null, false); }
    public void setwenbenBYbianhao(int bianhao, String text, double lon, double lat, int fontSize, Color color, JComponent attachedTo) { setWenben0(bianhao, text, lon, lat, fontSize, color, null, attachedTo, true); }
    private void setWenben0(final int bianhao, final String text, final double lon, final double lat, final int fontSize, final Color color, final Boolean scaleWithZoom, final JComponent attachedTo, final boolean changeAttached) {
        if (bianhao <= 0) return;
        runOnEDT(() -> {
            GeoText t = findWenben(bianhao); if (t == null) return;
            if (text != null) t.text = text;
            t.lon = lon; t.lat = lat;
            if (fontSize > 0) t.baseFontSize = fontSize;
            if (color != null) t.color = color;
            if (scaleWithZoom != null) t.scaleWithZoom = scaleWithZoom;
            if (changeAttached) t.attachedTo = attachedTo;
        });
    }

    public int addGeoComponent(JComponent comp, double lon, double lat) {
        if (comp == null) return -1;
        Dimension d = comp.getPreferredSize();
        return addGeoComponent(comp, lon, lat, -d.width / 2, -d.height / 2, true);
    }
    public int addGeoComponent(JComponent comp, double lon, double lat, int anchorX, int anchorY) { return addGeoComponent(comp, lon, lat, anchorX, anchorY, true); }
    public int addGeoComponent(JComponent comp, double lon, double lat, int anchorX, int anchorY, boolean scaleWithZoom) { return addGeoComponentInternal2(comp, lon, lat, anchorX, anchorY, scaleWithZoom, null); }
    public int addGeoComponent(JComponent comp, double lon, double lat, Source source) {
        if (comp == null) return -1;
        Dimension d = comp.getPreferredSize();
        return addGeoComponentInternal2(comp, lon, lat, -d.width / 2, -d.height / 2, true, source);
    }
    public int addGeoComponent(JComponent comp, double lon, double lat, int anchorX, int anchorY, Source source) { return addGeoComponentInternal2(comp, lon, lat, anchorX, anchorY, true, source); }
    public int addGeoComponent(JComponent comp, double lon, double lat, int anchorX, int anchorY, boolean scaleWithZoom, Source source) { return addGeoComponentInternal2(comp, lon, lat, anchorX, anchorY, scaleWithZoom, source); }
    private int addGeoComponentInternal2(JComponent comp, double lon, double lat, int anchorX, int anchorY, boolean scaleWithZoom, Source source) {
        if (comp == null) return -1;
        final int bianhao = nextAnniuBianhao.getAndIncrement();
        runOnEDT(() -> addGeoComponentInternal(bianhao, comp, lon, lat, anchorX, anchorY, scaleWithZoom, source));
        return bianhao;
    }
    private void addGeoComponentInternal(int bianhao, JComponent comp, double lon, double lat, int anchorX, int anchorY, boolean scaleWithZoom, Source source) {
        comp.setFocusable(false);
        Dimension pref = comp.getPreferredSize();
        int w = pref.width > 0 ? pref.width : 60, h = pref.height > 0 ? pref.height : 24;
        Font f = comp.getFont(); if (f == null) f = UIManager.getFont("Button.font"); if (f == null) f = new Font(UI_FONT, Font.PLAIN, 12);
        add(comp);
        GeoComponent gc = new GeoComponent(bianhao, comp, lon, lat, anchorX, anchorY, w, h, this.ppd, f, scaleWithZoom);
        gc.source = source; gc.minScale = buttonMinScale; gc.maxScale = buttonMaxScale;
        geoComponents.add(gc);
        comp.addPropertyChangeListener("preferredSize", geoCompPropListener);
        layoutGeoComponents();
    }
    public void removeGeoComponent(JComponent comp) {
        if (comp == null) return;
        if (!SwingUtilities.isEventDispatchThread()) { SwingUtilities.invokeLater(() -> removeGeoComponent(comp)); return; }
        geoComponents.removeIf(g -> { if (g.comp == comp) { g.comp.removePropertyChangeListener("preferredSize", geoCompPropListener); return true; } return false; });
        remove(comp);
    }
    public void removeAnniuBYbianhao(int bianhao) {
        if (bianhao <= 0) return;
        runOnEDT(() -> {
            for (int i = 0, n = geoComponents.size(); i < n; i++) {
                GeoComponent g = geoComponents.get(i);
                if (g.bianhao == bianhao) { g.comp.removePropertyChangeListener("preferredSize", geoCompPropListener); remove(g.comp); geoComponents.remove(i); return; }
            }
        });
    }
    public void removeWenbenBYbianhao(int bianhao) {
        if (bianhao <= 0) return;
        runOnEDT(() -> { for (int i = 0, n = geoTexts.size(); i < n; i++) if (geoTexts.get(i).bianhao == bianhao) { geoTexts.remove(i); return; } });
    }
    public void clearGeoComponents() {
        if (!SwingUtilities.isEventDispatchThread()) { SwingUtilities.invokeLater(this::clearGeoComponents); return; }
        for (int i = 0, n = geoComponents.size(); i < n; i++) { GeoComponent g = geoComponents.get(i); g.comp.removePropertyChangeListener("preferredSize", geoCompPropListener); remove(g.comp); }
        geoComponents.clear();
    }
    public void setGeoComponentLonLat(JComponent comp, double lon, double lat) {
        if (comp == null) return;
        if (!SwingUtilities.isEventDispatchThread()) { SwingUtilities.invokeLater(() -> setGeoComponentLonLat(comp, lon, lat)); return; }
        for (int i = 0, n = geoComponents.size(); i < n; i++) {
            GeoComponent g = geoComponents.get(i);
            if (g.comp == comp) {
                GeoComponent ng = new GeoComponent(g.bianhao, g.comp, lon, lat, g.anchorX, g.anchorY, g.baseW, g.baseH, g.basePpd, g.baseFont, g.scaleWithZoom);
                ng.currentFontSize = g.currentFontSize; ng.source = g.source; ng.visibleBySource = g.visibleBySource;
                ng.minScale = g.minScale; ng.maxScale = g.maxScale;
                geoComponents.set(i, ng); layoutGeoComponents(); return;
            }
        }
    }
    public void setGeoComponentScaleWithZoom(JComponent comp, boolean on) {
        if (comp == null) return;
        runOnEDT(() -> {
            for (int i = 0, n = geoComponents.size(); i < n; i++) {
                GeoComponent g = geoComponents.get(i);
                if (g.comp == comp) { g.scaleWithZoom = on; layoutGeoComponents(); return; }
            }
        });
    }
    public void setanniuBYbianhao(int bianhao, JComponent comp, double lon, double lat) { setAnniu0(bianhao, comp, lon, lat, null, null, null); }
    public void setanniuBYbianhao(int bianhao, JComponent comp, double lon, double lat, int anchorX, int anchorY) { setAnniu0(bianhao, comp, lon, lat, anchorX, anchorY, null); }
    public void setanniuBYbianhao(int bianhao, JComponent comp, double lon, double lat, int anchorX, int anchorY, boolean scaleWithZoom) { setAnniu0(bianhao, comp, lon, lat, anchorX, anchorY, scaleWithZoom); }
    private void setAnniu0(final int bianhao, final JComponent newComp, final double lon, final double lat, final Integer anchorX, final Integer anchorY, final Boolean scaleWithZoom) {
        if (bianhao <= 0) return;
        runOnEDT(() -> {
            int idx = -1;
            for (int i = 0, n = geoComponents.size(); i < n; i++) if (geoComponents.get(i).bianhao == bianhao) { idx = i; break; }
            if (idx < 0) return;
            GeoComponent old = geoComponents.get(idx);
            JComponent comp = (newComp != null) ? newComp : old.comp;
            boolean swapped = (comp != old.comp);
            if (swapped) {
                old.comp.removePropertyChangeListener("preferredSize", geoCompPropListener); remove(old.comp);
                comp.setFocusable(false); comp.addPropertyChangeListener("preferredSize", geoCompPropListener); add(comp);
            }
            Dimension pref = comp.getPreferredSize();
            int w = (pref != null && pref.width > 0) ? pref.width : old.baseW;
            int h = (pref != null && pref.height > 0) ? pref.height : old.baseH;
            Font f = comp.getFont(); if (f == null) f = old.baseFont; if (f == null) f = new Font(UI_FONT, Font.PLAIN, 12);
            GeoComponent ng = new GeoComponent(bianhao, comp, lon, lat,
                    anchorX != null ? anchorX : old.anchorX, anchorY != null ? anchorY : old.anchorY, w, h, old.basePpd, f,
                    scaleWithZoom != null ? scaleWithZoom : old.scaleWithZoom);
            ng.currentFontSize = swapped ? -1f : old.currentFontSize;
            ng.source = old.source; ng.visibleBySource = old.visibleBySource;
            ng.minScale = old.minScale; ng.maxScale = old.maxScale;
            geoComponents.set(idx, ng); layoutGeoComponents();
        });
    }
    public JComponent getAnniuBYbianhao(int bianhao) { GeoComponent g = findAnniu(bianhao); return g == null ? null : g.comp; }
    public GeoText getWenbenBYbianhao(int bianhao) { return findWenben(bianhao); }

    private void layoutGeoComponents() {
        if (geoComponents.isEmpty()) return;
        int W = getWidth(), H = getHeight();
        if (W <= 0 || H <= 0) return;
        double halfW = W / 2.0, halfH = H / 2.0;
        for (int i = 0, n = geoComponents.size(); i < n; i++) {
            GeoComponent g = geoComponents.get(i);
            double dLon = g.lon - lonCenter;
            dLon = ((dLon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
            int x = (int) Math.round(dLon * ppd + halfW), y = (int) Math.round((latCenter - g.lat) * ppd + halfH);
            double scale = 1.0;
            if (g.scaleWithZoom && g.basePpd > 1e-9) {
                scale = ppd / g.basePpd;
                if (scale < g.minScale) scale = g.minScale; if (scale > g.maxScale) scale = g.maxScale;
            }
            int w = Math.max(1, (int) Math.round(g.baseW * scale)), h = Math.max(1, (int) Math.round(g.baseH * scale));
            int px = x + (int) Math.round(g.anchorX * scale), py = y + (int) Math.round(g.anchorY * scale);
            Rectangle cur = g.comp.getBounds();
            if (cur.x != px || cur.y != py || cur.width != w || cur.height != h) g.comp.setBounds(px, py, w, h);
            if (g.scaleWithZoom && g.baseFont != null) {
                float newSize = (float) (g.baseFont.getSize2D() * scale); if (newSize < 1f) newSize = 1f;
                if (Math.abs(newSize - g.currentFontSize) > 0.5f) { g.currentFontSize = newSize; g.comp.setFont(g.baseFont.deriveFont(newSize)); }
            }
            g.comp.setVisible(g.visibleBySource && (px + w > 0 && px < W && py + h > 0 && py < H));
        }
    }

    private void syncLive() {
        if (geoDots.isEmpty() && geoTexts.isEmpty() && geoComponents.isEmpty()) return;
        final int dotCount = geoDots.size();
        if (dotCount > 0) {
            boolean dotDirty = false;
            for (int i = 0; i < dotCount; i++) {
                dian d = geoDots.get(i); Source s = d.source; if (s == null) continue;
                double nl = s.getLon(), na = s.getLat();
                if (nl != d.lon || na != d.lat) { d.lon = nl; d.lat = na; if (!d.screenSpace) dotDirty = true; }
                Color nc = s.getColor();
                if (nc != null && !nc.equals(d.color)) { d.color = nc; if (!d.screenSpace) dotDirty = true; }
                String nt = s.getText();
                if (nt != null && !nt.equals(d.text)) { d.text = nt; if (!d.screenSpace) dotDirty = true; }
                Double nr = s.getRadius();
                if (nr != null && nr != d.radiusDeg) { d.radiusDeg = nr; if (!d.screenSpace) dotDirty = true; }
                Boolean nv = s.getVisible();
                if (nv != null && nv != d.visible) { d.visible = nv; if (!d.screenSpace) dotDirty = true; }
            }
            if (dotDirty && bakeStaticDots) markWorldDirty();
        }
        final int textCount = geoTexts.size();
        if (textCount > 0) {
            for (int i = 0; i < textCount; i++) {
                GeoText t = geoTexts.get(i); Source s = t.source; if (s == null) continue;
                double nl = s.getLon(), na = s.getLat();
                if (nl != t.lon) t.lon = nl; if (na != t.lat) t.lat = na;
                String nt = s.getText(); if (nt != null && !nt.equals(t.text)) t.text = nt;
                Color nc = s.getColor(); if (nc != null && !nc.equals(t.color)) t.color = nc;
                Float nf = s.getFontSize(); if (nf != null && nf > 0f && nf != t.baseFontSize) t.baseFontSize = nf;
                Boolean nv = s.getVisible(); if (nv != null) t.visible = nv;
            }
        }
        final int compCount = geoComponents.size();
        if (compCount > 0) {
            boolean needLayout = false;
            for (int i = 0; i < compCount; i++) {
                GeoComponent g = geoComponents.get(i); Source s = g.source; if (s == null) continue;
                double nl = s.getLon(), na = s.getLat();
                if (nl != g.lon || na != g.lat) { g.lon = nl; g.lat = na; needLayout = true; }
                Boolean nv = s.getVisible(); if (nv != null && nv != g.visibleBySource) { g.visibleBySource = nv; needLayout = true; }
            }
            if (needLayout) layoutGeoComponents();
        }
    }

    public void setZoomRange(double minPixelsPerDegree, double maxPixelsPerDegree) {
        if (minPixelsPerDegree <= 0) minPixelsPerDegree = 0.001;
        this.minPpd = minPixelsPerDegree; this.maxPpd = Math.max(minPixelsPerDegree, maxPixelsPerDegree);
        clampView();
    }
    public void setLonBounds(double lonMin, double lonMax) {
        if (lonMin > lonMax) { double t = lonMin; lonMin = lonMax; lonMax = t; }
        this.lonBoundMin = lonMin; this.lonBoundMax = lonMax; clampView();
    }
    public void setLatBounds(double latMin, double latMax) {
        if (latMin > latMax) { double t = latMin; latMin = latMax; latMax = t; }
        this.latBoundMin = latMin; this.latBoundMax = latMax; clampView();
    }
    public void clearPanBounds() { this.lonBoundMin = null; this.lonBoundMax = null; this.latBoundMin = null; this.latBoundMax = null; clampView(); }
    private void clampView() {
        int W = Math.max(getWidth(), 1), H = Math.max(getHeight(), 1);
        boolean hasLonBounds = (lonBoundMin != null && lonBoundMax != null);
        boolean hasLatBounds = (latBoundMin != null && latBoundMax != null);
        double spanLon = hasLonBounds ? Math.max(lonBoundMax - lonBoundMin, 1e-6) : 360.0;
        double spanLat = hasLatBounds ? Math.max(latBoundMax - latBoundMin, 1e-6) : 180.0;
        double fitMin = Math.max(W / spanLon, H / spanLat);
        double effMinPpd = Math.max(minPpd, fitMin);
        ppd = clamp(ppd, effMinPpd, Math.max(effMinPpd, maxPpd));
        double halfLon = W / (2.0 * ppd), halfLat = H / (2.0 * ppd);
        double latMin = hasLatBounds ? Math.max(latBoundMin, WORLD_LAT_MIN) : WORLD_LAT_MIN;
        double latMax = hasLatBounds ? Math.min(latBoundMax, WORLD_LAT_MAX) : WORLD_LAT_MAX;
        if (halfLat >= (latMax - latMin) / 2) latCenter = (latMin + latMax) / 2;
        else latCenter = clamp(latCenter, latMin + halfLat, latMax - halfLat);
        if (hasLonBounds) {
            if (halfLon >= (lonBoundMax - lonBoundMin) / 2) lonCenter = (lonBoundMin + lonBoundMax) / 2;
            else lonCenter = clamp(lonCenter, lonBoundMin + halfLon, lonBoundMax - halfLon);
        } else lonCenter = normalizeLon(lonCenter);
        layoutGeoComponents();
    }

    public void drawLine(double lon1, double lat1, double lon2, double lat2) {
        final Color c = brushColor; final int s = brushSize;
        shapes.add((g2, b) -> {
            g2.setColor(c); g2.setStroke(new BasicStroke(s, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int k = -1; k <= 1; k++) {
                int[] p1 = b.lonLatToScreen(lon1 + k * 360.0, lat1), p2 = b.lonLatToScreen(lon2 + k * 360.0, lat2);
                g2.drawLine(p1[0], p1[1], p2[0], p2[1]);
            }
        });
        markWorldDirty();
    }
    public void drawPoint(double lon, double lat) {
        final Color c = brushColor; final int s = brushSize;
        shapes.add((g2, b) -> {
            int r = Math.max(s / 2, 1); g2.setColor(c);
            for (int k = -1; k <= 1; k++) { int[] p = b.lonLatToScreen(lon + k * 360.0, lat); g2.fill(new Ellipse2D.Double(p[0] - r, p[1] - r, r * 2.0, r * 2.0)); }
        });
        markWorldDirty();
    }
    public void drawOval(double lon, double lat, double lonSpan, double latSpan) {
        final Color c = brushColor; final int s = brushSize;
        shapes.add((g2, b) -> {
            g2.setColor(c); g2.setStroke(new BasicStroke(s, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int k = -1; k <= 1; k++) {
                int[] p1 = b.lonLatToScreen(lon + k * 360.0, lat), p2 = b.lonLatToScreen(lon + lonSpan + k * 360.0, lat + latSpan);
                int x = Math.min(p1[0], p2[0]), y = Math.min(p1[1], p2[1]);
                g2.drawOval(x, y, Math.abs(p2[0] - p1[0]), Math.abs(p2[1] - p1[1]));
            }
        });
        markWorldDirty();
    }
    public void drawRect(double lon, double lat, double lonSpan, double latSpan) {
        final Color c = brushColor; final int s = brushSize;
        shapes.add((g2, b) -> {
            g2.setColor(c); g2.setStroke(new BasicStroke(s, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int k = -1; k <= 1; k++) {
                int[] p1 = b.lonLatToScreen(lon + k * 360.0, lat), p2 = b.lonLatToScreen(lon + lonSpan + k * 360.0, lat + latSpan);
                int x = Math.min(p1[0], p2[0]), y = Math.min(p1[1], p2[1]);
                g2.drawRect(x, y, Math.abs(p2[0] - p1[0]), Math.abs(p2[1] - p1[1]));
            }
        });
        markWorldDirty();
    }
    public void drawText(String text, double lon, double lat, int fontSize) {
        final Color c = brushColor;
        shapes.add((g2, b) -> {
            g2.setColor(c);
            int fs = b.bakingMode ? Math.max(1, (int) Math.round(fontSize * WORLD_BASE_PPD / 6.0)) : fontSize;
            g2.setFont(uiFont(fs));
            for (int k = -1; k <= 1; k++) { int[] p = b.lonLatToScreen(lon + k * 360.0, lat); g2.drawString(text, p[0], p[1]); }
        });
        markWorldDirty();
    }
    public void clear() { shapes.clear(); markWorldDirty(); }
    public void undo() { if (!shapes.isEmpty()) { shapes.remove(shapes.size() - 1); markWorldDirty(); } }

    public void centerOn(double lon, double lat) { lonCenter = lon; latCenter = lat; clampView(); }
    public void setZoom(double pixelsPerDegree) { ppd = pixelsPerDegree; clampView(); }
    public void resetView() { lonCenter = 0; latCenter = 0; ppd = Math.max(minPpd, 6); clampView(); }
    public double getLonCenter() { return lonCenter; }
    public double getLatCenter() { return latCenter; }
    public double getZoom() { return ppd; }
    public void addMarker(double lon, double lat, Color color, int size, String text) { markers.add(new Marker(lon, lat, color, size, text)); markWorldDirty(); }
    public void clearMarkers() { markers.clear(); markWorldDirty(); }

    public int[] lonLatToScreen(double lon, double lat) {
        if (bakingMode) return new int[]{ (int) Math.round((lon + 180) * (double) WORLD_BASE_PPD), (int) Math.round((90 - lat) * (double) WORLD_BASE_PPD) };
        return new int[]{ (int) Math.round((lon - lonCenter) * ppd + getWidth() / 2.0), (int) Math.round((latCenter - lat) * ppd + getHeight() / 2.0) };
    }
    public double[] screenToLonLat(int x, int y) {
        double lon = lonCenter + (x - getWidth() / 2.0) / ppd, lat = latCenter - (y - getHeight() / 2.0) / ppd;
        return new double[]{ normalizeLon(lon), clamp(lat, WORLD_LAT_MIN, WORLD_LAT_MAX) };
    }

    public Color getColorAtLonLat(double lon, double lat) { return getColorAtLonLat(lon, lat, null); }
    public Color getColorAtLonLat(double lon, double lat, Color emptyColor) {
        if (lat < WORLD_LAT_MIN || lat > WORLD_LAT_MAX) return emptyColor;
        if (!SwingUtilities.isEventDispatchThread()) {
            final double flon = lon, flat = lat; final Color femp = emptyColor; final Color[] out = new Color[1];
            try { SwingUtilities.invokeAndWait(() -> out[0] = getColorAtLonLat(flon, flat, femp)); }
            catch (InterruptedException ie) { Thread.currentThread().interrupt(); return femp; }
            catch (InvocationTargetException ite) { return femp; }
            return out[0] != null ? out[0] : femp;
        }
        if (worldDirty) rebuildWorldCanvas();
        MappedImage base = worldPyramid.get(0); int ppdBase = worldPyramid.getPpd(0);
        double nlon = normalizeLon(lon);
        int px = (int) Math.floor((nlon + 180.0) * ppdBase), py = (int) Math.floor((WORLD_LAT_MAX - lat) * ppdBase);
        if (px < 0 || px >= base.getWidth() || py < 0 || py >= base.getHeight()) return emptyColor;
        int argb = base.getARGB(px, py);
        if ((argb >>> 24) == 0) return emptyColor;
        return new Color(argb, true);
    }
    public Color getColorAtScreen(int x, int y) { return getColorAtScreen(x, y, null); }
    public Color getColorAtScreen(int x, int y, Color emptyColor) {
        final int W = getWidth(), H = getHeight();
        if (W <= 0 || H <= 0) return emptyColor;
        if (x < 0 || y < 0 || x >= W || y >= H) return emptyColor;
        if (!SwingUtilities.isEventDispatchThread()) {
            final int fx = x, fy = y; final Color femp = emptyColor; final Color[] out = new Color[1];
            try { SwingUtilities.invokeAndWait(() -> out[0] = getColorAtScreen(fx, fy, femp)); }
            catch (InterruptedException ie) { Thread.currentThread().interrupt(); return femp; }
            catch (InvocationTargetException ite) { return femp; }
            return out[0] != null ? out[0] : femp;
        }
        BufferedImage img = ensurePickBuffer(W, H);
        Graphics2D ig = img.createGraphics();
        try { ig.setComposite(AlphaComposite.Clear); ig.fillRect(0, 0, W, H); ig.setComposite(AlphaComposite.SrcOver); paintComponent(ig); }
        finally { ig.dispose(); }
        int argb = img.getRGB(x, y);
        if ((argb >>> 24) == 0) return emptyColor;
        return new Color(argb, true);
    }
    public Color getColorAtLonLatOnScreen(double lon, double lat) { return getColorAtLonLatOnScreen(lon, lat, null); }
    public Color getColorAtLonLatOnScreen(double lon, double lat, Color emptyColor) {
        if (getWidth() <= 0 || getHeight() <= 0) return emptyColor;
        double nlon = normalizeLon(lon), d = nlon - lonCenter;
        while (d > 180.0) d -= 360.0; while (d < -180.0) d += 360.0;
        int sx = (int) Math.round((lonCenter + d - lonCenter) * ppd + getWidth() / 2.0);
        int sy = (int) Math.round((latCenter - lat) * ppd + getHeight() / 2.0);
        return getColorAtScreen(sx, sy, emptyColor);
    }

    private void updateViewSnapshot() { cachedVpLonCenter = lonCenter; cachedVpLatCenter = latCenter; cachedVpPpd = ppd; cachedVpW = getWidth(); cachedVpH = getHeight(); }

    private void rebuildVisibleLiveDots() {
        if (liveDotsCacheValid
                && cachedVpLonCenter == lonCenter
                && cachedVpLatCenter == latCenter
                && cachedVpPpd == ppd
                && cachedVpW == getWidth()
                && cachedVpH == getHeight()
                && cachedLiveDotsVersion == liveDotsVersion) {
            return;
        }

        visibleLiveDots.clear();
        int W = getWidth(), H = getHeight();
        if (W <= 0 || H <= 0 || liveDots.isEmpty()) {
            updateViewSnapshot();
            cachedLiveDotsVersion = liveDotsVersion;
            liveDotsCacheValid = true;
            return;
        }
        double halfW = W * 0.5, halfH = H * 0.5;
        double halfLon = halfW / ppd + 1.0, halfLat = halfH / ppd + 1.0;
        boolean wholeWorld = halfLon >= 180.0;
        double panelWidthPx = 360.0 * ppd;
        int copies = computeCopies(W, panelWidthPx);
        double sxMin = -panelWidthPx * (copies + 1), sxMax = W + panelWidthPx * (copies + 1);

        if (liveDotBucketsDirty) rebuildLiveDotBuckets();

        double lonMinWorld = wholeWorld ? -180.0 : (lonCenter - halfLon);
        double lonMaxWorld = wholeWorld ?  180.0 : (lonCenter + halfLon);

        if (!wholeWorld && lonMinWorld < -180.0) {
            scanBucketRange(lonMinWorld + 360.0, 180.0, halfW, halfH, W, H, halfLat, sxMin, sxMax);
            scanBucketRange(-180.0, lonMaxWorld, halfW, halfH, W, H, halfLat, sxMin, sxMax);
        } else if (!wholeWorld && lonMaxWorld > 180.0) {
            scanBucketRange(lonMinWorld, 180.0, halfW, halfH, W, H, halfLat, sxMin, sxMax);
            scanBucketRange(-180.0, lonMaxWorld - 360.0, halfW, halfH, W, H, halfLat, sxMin, sxMax);
        } else {
            scanBucketRange(lonMinWorld, lonMaxWorld, halfW, halfH, W, H, halfLat, sxMin, sxMax);
        }
        updateViewSnapshot();
        cachedLiveDotsVersion = liveDotsVersion;
        liveDotsCacheValid = true;
    }

    private void scanBucketRange(double lonMin, double lonMax,
                                 double halfW, double halfH, int W, int H, double halfLat,
                                 double sxMin, double sxMax) {
        int bMin = lonBucketIndex(lonMin);
        int bMax = lonBucketIndex(lonMax);
        if (bMax < bMin) { int t = bMin; bMin = bMax; bMax = t; }
        for (int b = bMin; b <= bMax; b++) {
            List<dian> bucket = liveDotBuckets[b];
            if (bucket == null) continue;
            for (int i = 0, n = bucket.size(); i < n; i++) {
                dian d = bucket.get(i);
                if (!d.visible) continue;
                if (d.lat < latCenter - halfLat || d.lat > latCenter + halfLat) continue;
                double sx = (d.lon - lonCenter) * ppd + halfW, sy = (latCenter - d.lat) * ppd + halfH;
                if (sx < sxMin || sx > sxMax) continue;
                if (sy < -halfH - 4 || sy > H + halfH + 4) continue;
                d.cachedSx = sx; d.cachedSy = sy; visibleLiveDots.add(d);
            }
        }
    }

    private static int computeCopies(int W, double panelWidthPx) {
        int copies = (int) Math.ceil(W / Math.max(panelWidthPx, 1.0)) + 1;
        if (copies > 5) copies = 5;
        return copies;
    }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int W = getWidth(), H = getHeight();
        if (W <= 0 || H <= 0) return;
        tickFrame(); syncLive();
        if (bakeStaticDots && worldDirty) rebuildWorldCanvas();
        if (!liveDots.isEmpty()) rebuildVisibleLiveDots();
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            drawGridOnScreen(g2, W, H); drawWorldCanvas(g2, W, H); drawTrailCanvas(g2, W, H);
            if (!bakeStaticDots) drawGeoDotsCommon(g2, W, H, geoDots, false);
            drawLiveLines(g2, W, H);

            List<dian> dotsToDraw = visibleLiveDots;
            if (visibleLiveDots.size() > CLUSTER_TRIGGER) {
                buildClusterRepresentatives();
                dotsToDraw = clusterRepresentatives;
            }
            drawGeoDotsCommon(g2, W, H, dotsToDraw, true);
            drawGeoTexts(g2, W, H);

            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            drawHUD(g2, W, H); drawSelectionBox(g2);
        } finally { g2.dispose(); }
    }
    private void drawSelectionBox(Graphics2D g2) {
        if (!selectionActive || selectionStart == null || selectionEnd == null) return;
        int x1 = Math.min(selectionStart.x, selectionEnd.x), y1 = Math.min(selectionStart.y, selectionEnd.y);
        int x2 = Math.max(selectionStart.x, selectionEnd.x), y2 = Math.max(selectionStart.y, selectionEnd.y);
        int w = x2 - x1, h = y2 - y1;
        if (w <= 0 && h <= 0) return;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g2.setColor(selectionFillColor); g2.fillRect(x1, y1, w, h);
        g2.setColor(selectionBorderColor); g2.setStroke(STROKE_1F); g2.drawRect(x1, y1, w, h);
    }
    private void drawTrailCanvas(Graphics2D g2, int W, int H) {
        if (!hasAnyTrails) return;
        for (OwnerTrail ot : ownerTrails.values()) { if (!ot.hasAny) continue; drawOwnerTrailCanvas(g2, W, H, ot); }
    }
    private void drawOwnerTrailCanvas(Graphics2D g2, int W, int H, OwnerTrail ot) {
        MappedPyramid trailPyramid = ot.pyramid;
        int level = trailPyramid.pickLevel(ppd);
        MappedImage img = trailPyramid.get(level); int srcPpd = trailPyramid.getPpd(level);
        double scale = ppd / (double) srcPpd;
        double tx = (-180 - lonCenter) * ppd + W / 2.0, ty = (latCenter - 90) * ppd + H / 2.0;
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_SPEED);
        double panelWidthPx = 360.0 * ppd;
        int copies = computeCopies(W, panelWidthPx);
        for (int k = -copies; k <= copies; k++) {
            double x0 = tx + k * panelWidthPx;
            if (x0 + panelWidthPx < 0 || x0 > W) continue;
            int cx1 = Math.max(0, (int) Math.floor((0 - x0) / scale)), cy1 = Math.max(0, (int) Math.floor((0 - ty) / scale));
            int cx2 = Math.min(img.getWidth(), (int) Math.ceil((W - x0) / scale)), cy2 = Math.min(img.getHeight(), (int) Math.ceil((H - ty) / scale));
            if (cx1 >= cx2 || cy1 >= cy2) continue;
            int cw = cx2 - cx1, ch = cy2 - cy1;
            BufferedImage region = img.readRegion(cx1, cy1, cw, ch); if (region == null) continue;
            double sx = x0 + scale * cx1, sy = ty + scale * cy1;
            int dstX1 = (int) Math.round(sx), dstY1 = (int) Math.round(sy);
            int dstX2 = (int) Math.round(sx + scale * cw), dstY2 = (int) Math.round(sy + scale * ch);
            g2.drawImage(region, dstX1, dstY1, dstX2, dstY2, 0, 0, cw, ch, null);
        }
    }
    private void drawWorldCanvas(Graphics2D g2, int W, int H) {
        int level = worldPyramid.pickLevel(ppd);
        MappedImage img = worldPyramid.get(level); int srcPpd = worldPyramid.getPpd(level);
        double scale = ppd / (double) srcPpd;
        double panelWidthPx = 360.0 * ppd;
        int copies = computeCopies(W, panelWidthPx);
        double tx = (-180 - lonCenter) * ppd + W / 2.0, ty = (latCenter - 90) * ppd + H / 2.0;
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, scale >= 2.0 ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR : RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        for (int k = -copies; k <= copies; k++) {
            double x0 = tx + k * panelWidthPx;
            if (x0 + panelWidthPx < 0 || x0 > W) continue;
            int cx1 = Math.max(0, (int) Math.floor((0 - x0) / scale)), cy1 = Math.max(0, (int) Math.floor((0 - ty) / scale));
            int cx2 = Math.min(img.getWidth(), (int) Math.ceil((W - x0) / scale)), cy2 = Math.min(img.getHeight(), (int) Math.ceil((H - ty) / scale));
            if (cx1 >= cx2 || cy1 >= cy2) continue;
            int cw = cx2 - cx1, ch = cy2 - cy1;
            BufferedImage region = img.readRegion(cx1, cy1, cw, ch); if (region == null) continue;
            double sx = x0 + scale * cx1, sy = ty + scale * cy1;
            g2.drawImage(region, (int) Math.round(sx), (int) Math.round(sy), (int) Math.round(sx + scale * cw), (int) Math.round(sy + scale * ch), 0, 0, cw, ch, null);
        }
    }

    private void drawGeoDotsCommon(Graphics2D g2, int W, int H, List<dian> dots, boolean useCached) {
        if (dots.isEmpty()) return;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        double panelWidthPx = 360.0 * ppd;
        int copies = computeCopies(W, panelWidthPx);
        double halfW = W * 0.5, halfH = H * 0.5;
        resetBatchCache();
        for (int i = 0, n = dots.size(); i < n; i++) {
            dian d = dots.get(i);
            if (!d.visible || d.color == null) continue;
            if (!useCached && d.screenSpace) continue;
            double rPx = d.radiusDeg * ppd; if (rPx < minLiveDotRadiusPx) rPx = minLiveDotRadiusPx;
            double baseX, baseY;
            if (useCached) { baseX = d.cachedSx; baseY = d.cachedSy; }
            else {
                double dLon = d.lon - lonCenter;
                dLon = ((dLon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
                baseX = dLon * ppd + halfW; baseY = (latCenter - d.lat) * ppd + halfH;
            }
            if (baseY + rPx < 0 || baseY - rPx > H) continue;

            boolean simple = (d.borderWidth <= 0) && (d.text == null || d.text.isEmpty());
            if (simple) {
                Path2D.Double path = pathFor(d.color);
                boolean useRect = rPx < 2.0;
                int kMin = (int) Math.ceil((-rPx - baseX) / panelWidthPx);
                int kMax = (int) Math.floor((W + rPx - baseX) / panelWidthPx);
                if (kMin < -copies) kMin = -copies;
                if (kMax > copies) kMax = copies;
                for (int k = kMin; k <= kMax; k++) {
                    double sx = baseX + k * panelWidthPx;
                    path.append(useRect
                            ? new Rectangle2D.Double(sx - rPx, baseY - rPx, rPx * 2, rPx * 2)
                            : new Ellipse2D.Double(sx - rPx, baseY - rPx, rPx * 2, rPx * 2), false);
                }
            } else individualDotCache.add(d);
        }
        for (Map.Entry<Color, Path2D.Double> e : batchPathCache.entrySet()) {
            Path2D.Double p = e.getValue(); if (!hasContent(p)) continue;
            g2.setColor(e.getKey()); g2.fill(p);
        }
        for (int i = 0, m = individualDotCache.size(); i < m; i++) {
            dian d = individualDotCache.get(i);
            double rPx = d.radiusDeg * ppd; if (rPx < minLiveDotRadiusPx) rPx = minLiveDotRadiusPx;
            double baseX, baseY;
            if (useCached) { baseX = d.cachedSx; baseY = d.cachedSy; }
            else {
                double dLon = d.lon - lonCenter;
                dLon = ((dLon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
                baseX = dLon * ppd + halfW; baseY = (latCenter - d.lat) * ppd + halfH;
            }
            int kMin = (int) Math.ceil((-rPx - baseX) / panelWidthPx);
            int kMax = (int) Math.floor((W + rPx - baseX) / panelWidthPx);
            if (kMin < -copies) kMin = -copies;
            if (kMax > copies) kMax = copies;
            for (int k = kMin; k <= kMax; k++) {
                double sx = baseX + k * panelWidthPx;
                if (d.color != null) { g2.setColor(d.color); g2.fill(new Ellipse2D.Double(sx - rPx, baseY - rPx, rPx * 2, rPx * 2)); }
                if (d.borderWidth > 0) {
                    Color border = d.borderColor != null ? d.borderColor : (d.color != null ? d.color.darker() : Color.WHITE);
                    g2.setColor(border); g2.setStroke(strokeOf(d.borderWidth));
                    g2.draw(new Ellipse2D.Double(sx - rPx, baseY - rPx, rPx * 2, rPx * 2));
                }
                if (d.text != null && !d.text.isEmpty()) {
                    int fs = useCached ? (int) Math.max(9, Math.min(200, rPx * 1.5)) : (d.textScale ? (int) Math.max(9, Math.min(200, rPx * 0.8)) : 12);
                    g2.setFont(uiFont(fs)); g2.setColor(d.textColor != null ? d.textColor : Color.WHITE);
                    g2.drawString(d.text, (float) (sx + rPx + 4), (float) (baseY + fs / 3));
                }
            }
        }
    }
    public void bakeGeoDotsOnly() { rebuildWorldCanvas(); }

    private void drawLiveLines(Graphics2D g2, int W, int H) {
        if (liveLines.isEmpty()) return;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        double halfW = W * 0.5, halfH = H * 0.5;
        double panelWidthPx = 360.0 * ppd;
        int copies = computeCopies(W, panelWidthPx);
        for (int i = 0, n = liveLines.size(); i < n; i++) {
            LiveLine L = liveLines.get(i); if (!L.visible) continue;
            Color c = (L.color != null) ? L.color : Color.WHITE;
            float sw = (L.strokeWidth > 0f) ? L.strokeWidth : 1f;
            double lon2Adj = L.lon2, dd = lon2Adj - L.lon1;
            dd = ((dd + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
            lon2Adj = L.lon1 + dd;
            double x1 = (L.lon1 - lonCenter) * ppd + halfW, y1 = (latCenter - L.lat1) * ppd + halfH;
            double x2 = (lon2Adj - lonCenter) * ppd + halfW, y2 = (latCenter - L.lat2) * ppd + halfH;
            double minY = Math.min(y1, y2) - sw, maxY = Math.max(y1, y2) + sw;
            if (maxY < 0 || minY > H) continue;
            g2.setColor(c); g2.setStroke(new BasicStroke(sw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int k = -copies; k <= copies; k++) {
                double dx = k * panelWidthPx, ax = x1 + dx, bx = x2 + dx;
                if ((ax < 0 && bx < 0) || (ax > W && bx > W)) continue;
                g2.draw(new Line2D.Double(ax, y1, bx, y2));
            }
        }
    }

    private void drawGeoTexts(Graphics2D g2, int W, int H) {
        if (geoTexts.isEmpty()) return;
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_LCD_CONTRAST, 180);
        double panelWidthPx = 360.0 * ppd;
        int copies = computeCopies(W, panelWidthPx);
        for (int ti = 0, tn = geoTexts.size(); ti < tn; ti++) {
            GeoText t = geoTexts.get(ti);
            if (!t.visible || t.text == null || t.text.isEmpty()) continue;
            float fs = t.baseFontSize;
            if (t.scaleWithZoom && t.basePpd > 1e-9) {
                double s = ppd / t.basePpd;
                if (s < t.minScale) s = t.minScale; if (s > t.maxScale) s = t.maxScale;
                fs = (float) (t.baseFontSize * s);
            }
            fs = clampF(fs, MIN_SCREEN_FONT_SIZE, MAX_SCREEN_FONT_SIZE);
            Font font = t.fontFor(fs); g2.setFont(font);
            if (t.cachedFm == null || t.cachedFmFont != font) { t.cachedFm = g2.getFontMetrics(font); t.cachedFmFont = font; }
            FontMetrics fm = t.cachedFm;
            int textW = fm.stringWidth(t.text), ascent = fm.getAscent(), descent = fm.getDescent();
            int centerOffset = (ascent - descent) / 2;
            if (t.attachedTo != null) {
                if (!t.attachedTo.isVisible()) continue;
                Rectangle b = t.attachedTo.getBounds();
                int cx = b.x + b.width / 2, cy = b.y + b.height / 2;
                int x = cx - textW / 2 + (int) Math.round(t.offsetX), y = cy + centerOffset + (int) Math.round(t.offsetY);
                if (x + textW < 0 || x > W || y - ascent > H || y + descent < 0) continue;
                drawTextWithOutline(g2, t, x, y);
            } else {
                for (int k = -copies; k <= copies; k++) {
                    int[] p = lonLatToScreen(t.lon + k * 360.0, t.lat);
                    int x = p[0] - textW / 2 + (int) Math.round(t.offsetX), y = p[1] + centerOffset + (int) Math.round(t.offsetY);
                    if (x + textW < 0 || x > W || y - ascent > H || y + descent < 0) continue;
                    drawTextWithOutline(g2, t, x, y);
                }
            }
        }
    }
    private void drawTextWithOutline(Graphics2D g2, GeoText t, int x, int y) {
        Font font = g2.getFont();
        FontRenderContext frc = g2.getFontRenderContext();
        TextLayout layout = new TextLayout(t.text, font, frc);
        Shape outline = layout.getOutline(AffineTransform.getTranslateInstance(x, y));
        if (t.outlineColor != null && t.outlineWidth > 0f) {
            g2.setColor(t.outlineColor);
            g2.setStroke(new BasicStroke(t.outlineWidth * 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(outline);
        }
        g2.setColor(t.color != null ? t.color : Color.WHITE);
        g2.fill(outline);
    }

    private void drawGridOnScreen(Graphics2D g2, int W, int H) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g2.setStroke(STROKE_1F);
        double lonMin = lonCenter - W / (2.0 * ppd), lonMax = lonCenter + W / (2.0 * ppd);
        int startIdx = (int) Math.ceil(lonMin / GRID_STEP), endIdx = (int) Math.floor(lonMax / GRID_STEP);
        for (int i = startIdx; i <= endIdx; i++) {
            double lon = i * GRID_STEP;
            int x = (int) Math.round((lon - lonCenter) * ppd + W / 2.0);
            if (x < -1 || x > W + 1) continue;
            g2.setColor(Math.abs(normalizeLon(lon)) < 1e-6 ? GRID_COLOR_PRIME : GRID_COLOR_NORMAL);
            g2.drawLine(x, 0, x, H);
        }
        for (double lat = -90; lat <= 90 + 1e-6; lat += GRID_STEP) {
            int y = (int) Math.round((latCenter - lat) * ppd + H / 2.0);
            if (y < -1 || y > H + 1) continue;
            g2.setColor(Math.abs(lat) < 1e-6 ? GRID_COLOR_PRIME : GRID_COLOR_NORMAL);
            g2.drawLine(0, y, W, y);
        }
    }

    private void rebuildWorldCanvas() {
        BufferedImage high = ensureHighBakeBuffer();
        Graphics2D g = high.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setComposite(AlphaComposite.Clear); g.fillRect(0, 0, WORLD_W_HIGH, WORLD_H_HIGH); g.setComposite(AlphaComposite.SrcOver);
            bakingMode = true;
            try { for (int i = 0, n = shapes.size(); i < n; i++) shapes.get(i).paint(g, this); bakeMarkers(g); bakeGeoDots(g); }
            finally { bakingMode = false; }
        } finally { g.dispose(); }
        worldPyramid.rebuildFromBase(high); worldDirty = false;
        highBakeBuffer = null;
    }
    private void bakeMarkers(Graphics2D g) {
        if (markers.isEmpty()) return;
        int baseSize = 12;
        g.setFont(uiFont(Math.max(1, (int) Math.round(baseSize * WORLD_BASE_PPD / 6.0))));
        for (int i = 0, n = markers.size(); i < n; i++) {
            Marker m = markers.get(i);
            int[] p = lonLatToScreen(m.lon, m.lat);
            int s = Math.max(1, (int) Math.round(m.size * (double) WORLD_BASE_PPD / 6.0));
            g.setColor(m.color); g.fillOval(p[0] - s / 2, p[1] - s / 2, s, s);
            if (m.text != null && !m.text.isEmpty()) { g.setColor(Color.WHITE); g.drawString(m.text, p[0] + s / 2 + 4, p[1] + 5); }
        }
    }
    private void bakeGeoDots(Graphics2D g) {
        if (geoDots.isEmpty()) return;
        resetBatchCache();
        for (int i = 0, n = geoDots.size(); i < n; i++) {
            dian d = geoDots.get(i);
            if (d.screenSpace || !d.visible) continue;
            boolean simple = (d.borderWidth <= 0) && (d.text == null || d.text.isEmpty());
            if (simple && d.color != null) {
                Path2D.Double path = pathFor(d.color);
                int[] p = lonLatToScreen(d.lon, d.lat);
                int r = Math.max(1, (int) Math.round(d.radiusDeg * WORLD_BASE_PPD));
                path.append(r <= 2 ? new Rectangle2D.Double(p[0] - r, p[1] - r, r * 2.0, r * 2.0) : new Ellipse2D.Double(p[0] - r, p[1] - r, r * 2.0, r * 2.0), false);
            } else individualDotCache.add(d);
        }
        for (Map.Entry<Color, Path2D.Double> e : batchPathCache.entrySet()) {
            Path2D.Double p = e.getValue(); if (!hasContent(p)) continue;
            g.setColor(e.getKey()); g.fill(p);
        }
        for (int i = 0, m = individualDotCache.size(); i < m; i++) {
            dian d = individualDotCache.get(i);
            int[] p = lonLatToScreen(d.lon, d.lat);
            int r = Math.max(1, (int) Math.round(d.radiusDeg * WORLD_BASE_PPD));
            if (d.color != null) { g.setColor(d.color); g.fillOval(p[0] - r, p[1] - r, r * 2, r * 2); }
            if (d.borderWidth > 0) {
                Color border = d.borderColor != null ? d.borderColor : (d.color != null ? d.color.darker() : Color.WHITE);
                g.setColor(border); g.setStroke(strokeOf(Math.max(1f, d.borderWidth * WORLD_BASE_PPD / 6f)));
                g.drawOval(p[0] - r, p[1] - r, r * 2, r * 2);
            }
            if (d.text != null && !d.text.isEmpty()) {
                int fs = d.textScale ? (int) Math.max(9, Math.min(200, d.radiusDeg * WORLD_BASE_PPD * 0.8)) : Math.max(1, (int) Math.round(12.0 * WORLD_BASE_PPD / 6.0));
                g.setFont(uiFont(fs)); g.setColor(d.textColor != null ? d.textColor : Color.WHITE);
                g.drawString(d.text, p[0] + r + 4, p[1] + fs / 3);
            }
        }
    }

    private String hudCenterStr = null;
    private double hudLastLon = Double.NaN, hudLastLat = Double.NaN, hudLastPpd = Double.NaN;
    private String hudMouseStr = null;
    private double hudMouseLon = Double.NaN, hudMouseLat = Double.NaN;
    private boolean hudMouseNull = false;
    private void drawHUD(Graphics2D g2, int W, int H) {
        g2.setFont(HUD_FONT);
        if (hudCenterStr == null || hudLastLon != lonCenter || hudLastLat != latCenter || hudLastPpd != ppd) {
            hudLastLon = lonCenter; hudLastLat = latCenter; hudLastPpd = ppd;
            hudCenterStr = String.format("中心  经度 %.1f 度  纬度 %.1f 度  缩放 %.2f px/度", lonCenter, latCenter, ppd);
        }
        g2.setColor(HUD_COLOR_MAIN); g2.drawString(hudCenterStr, 12, 22);
        g2.setColor(HUD_COLOR_TIP); g2.drawString("左键拖拽旋转 · 滚轮缩放 · 右键框选", 12, 42);
        MouseSnapshot s = mouseSnap;
        if (s != null) {
            if (hudMouseStr == null || hudMouseNull || hudMouseLon != s.lon || hudMouseLat != s.lat) {
                hudMouseLon = s.lon; hudMouseLat = s.lat; hudMouseNull = false;
                hudMouseStr = String.format("鼠标  经度 %.2f 度  纬度 %.2f 度", s.lon, s.lat);
            }
            g2.setColor(HUD_COLOR_MOUSE); g2.drawString(hudMouseStr, 12, 62);
        } else {
            if (hudMouseStr == null || !hudMouseNull) { hudMouseNull = true; hudMouseStr = "鼠标  移出画板"; }
            g2.setColor(HUD_COLOR_NOMSE); g2.drawString(hudMouseStr, 12, 62);
        }
        g2.setColor(HUD_COLOR_MAIN); g2.drawString(String.valueOf(getFps()), 40, 92);
    }

    private static double normalizeLon(double lon) { lon = lon % 360; if (lon >= 180) lon -= 360; if (lon < -180) lon += 360; return lon; }
    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
    private static float clampF(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
    private static void runOnEDT(Runnable r) { if (SwingUtilities.isEventDispatchThread()) r.run(); else SwingUtilities.invokeLater(r); }
    public Color getNonLiveColorAtLonLat(double lon, double lat, Color emptyColor) { return getColorAtLonLat(lon, lat, emptyColor); }
    public Color getNonLiveColorAtLonLat(double lon, double lat) { return getNonLiveColorAtLonLat(lon, lat, null); }
    public Color huoqux_ychuyanse(int x, int y, Color emptyColor) {
        int W = getWidth(), H = getHeight();
        if (W <= 0 || H <= 0 || x < 0 || y < 0 || x >= W || y >= H) return emptyColor;
        double lon = lonCenter + (x - W / 2.0) / ppd, lat = latCenter - (y - H / 2.0) / ppd;
        return getNonLiveColorAtLonLat(lon, lat, emptyColor);
    }
    public Color huoqux_ychuyanse(int x, int y) { return huoqux_ychuyanse(x, y, null); }
}