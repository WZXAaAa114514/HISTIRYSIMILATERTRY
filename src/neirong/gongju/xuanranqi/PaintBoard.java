package neirong.gongju.xuanranqi;

import neirong.gongju.gongju;
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

    private static final String ziti = zidongxuanziti();
    private static String zidongxuanziti() {
        String[] prefer = {"Microsoft YaHei","微软雅黑","PingFang SC","Hiragino Sans GB","Noto Sans CJK SC","Source Han Sans SC","WenQuanYi Micro Hei","SimHei","SimSun"};
        Set<String> avail = new HashSet<>(Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String n : prefer) if (avail.contains(n)) return n;
        return Font.SANS_SERIF;
    }

    private static final Font[] zitihuancun = new Font[256];
    private static Font huoqusizedaxiaodeziti(long size) {
        if (size < 1) size = 1;
        if (size > 255) return new Font(ziti, Font.PLAIN, (int) size);
        Font f = zitihuancun[Math.toIntExact(size)];
        return f != null ? f : (zitihuancun[Math.toIntExact(size)] = new Font(ziti, Font.PLAIN, (int) size));
    }

    private static final Map<Float, BasicStroke> STROKE_CACHE = new ConcurrentHashMap<>(8);
    private static BasicStroke strokeOf(float w) { return STROKE_CACHE.computeIfAbsent(w, BasicStroke::new); }
    private static final BasicStroke huabi = new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

    private float liveanniuzujianzuixiaozhi = 2f, liveanniuzujianzuidazhi = 5f, livedilizujianzuixiaozhi = 2f, livedilizujianzuidazhi = 5f;
    public float getLiveanniuzujianzuixiaozhi() { return liveanniuzujianzuixiaozhi; }
    public void setLiveanniuzujianzuixiaozhi(float v) { liveanniuzujianzuixiaozhi = Math.max(0.01f, v); if (liveanniuzujianzuidazhi < liveanniuzujianzuixiaozhi) liveanniuzujianzuidazhi = liveanniuzujianzuixiaozhi; }
    public float getLiveanniuzujianzuidazhi() { return liveanniuzujianzuidazhi; }
    public void setLiveanniuzujianzuidazhi(float v) { liveanniuzujianzuidazhi = Math.max(liveanniuzujianzuixiaozhi, v); }
    public float getLivedilizujianzuixiaozhi() { return livedilizujianzuixiaozhi; }
    public void setLivedilizujianzuixiaozhi(float v) { livedilizujianzuixiaozhi = Math.max(0.01f, v); if (livedilizujianzuidazhi < livedilizujianzuixiaozhi) livedilizujianzuidazhi = livedilizujianzuixiaozhi; }
    public float getLivedilizujianzuidazhi() { return livedilizujianzuidazhi; }
    public void setLivedilizujianzuidazhi(float v) { livedilizujianzuidazhi = Math.max(livedilizujianzuixiaozhi, v); }
    public void shezhiliveanniuwenbenzuidazuixiaozhi(float bMin, float bMax, float tMin, float tMax) { setLiveanniuzujianzuixiaozhi(bMin); setLiveanniuzujianzuidazhi(bMax); setLivedilizujianzuixiaozhi(tMin); setLivedilizujianzuidazhi(tMax); }
    public void yingyangsuofangdaoquanbulivezujian() {
        runOnEDT(() -> {
            for (long i = 0, n = geoComponents.size(); i < n; i++) { GeoComponent g = geoComponents.get((int) i); g.minScale = liveanniuzujianzuixiaozhi; g.maxScale = liveanniuzujianzuidazhi; }
            for (long i = 0, n = wuliwenbens.size(); i < n; i++) { wuliwenben t = wuliwenbens.get((int) i); t.minScale = livedilizujianzuixiaozhi; t.maxScale = livedilizujianzuidazhi; }
            layoutGeoComponents();
        });
    }

    public interface zidonggengxinhuitiao {
        double getLon(); double getLat();
        default Color getColor() { return null; } default String getText() { return null; }
        default Double getRadius() { return null; } default Float getFontSize() { return null; }
        default Boolean getVisible() { return null; }
    }
    private static final float zuixiaopingmuziti = 8f, zuidapingmuziti = 200f;
    private double livedianzuixiaobanjing = 0.6;
    public void setLivedianzuixiaobanjing(double r) { this.livedianzuixiaobanjing = Math.max(0, r); }
    public double getLivedianzuixiaobanjing() { return livedianzuixiaobanjing; }

    private static final long jichuxiangsushu = 8, ditutingzuta = 5, jingzitaxiangsushu = 4, guijijingzitashu = 4;
    private static final long gaofenbianlvhuancunkuan = 360 * jichuxiangsushu, gaofenbianlvhuancungao = 180 * jichuxiangsushu;
    private MappedPyramid ditujingzita;
    private File lingshijingzitamulu;
    private BufferedImage huancungaofenbianlv;
    private BufferedImage ensureHighBakeBuffer() { if (huancungaofenbianlv == null) huancungaofenbianlv = new BufferedImage((int) gaofenbianlvhuancunkuan, Math.toIntExact(gaofenbianlvhuancungao), BufferedImage.TYPE_INT_ARGB_PRE); return huancungaofenbianlv; }
    private static final Object GLOBAL_TRAIL_OWNER = new Object();

    private static final class guijixianduanmoxing {
        final double lon1, lat1, lon2, lat2; final Color color; final float screenWidthPx;
        guijixianduanmoxing(double lon1, double lat1, double lon2, double lat2, Color color, float screenWidthPx) {
            this.lon1 = lon1; this.lat1 = lat1; this.lon2 = lon2; this.lat2 = lat2; this.color = color; this.screenWidthPx = screenWidthPx;
        }
    }
    private static final class OwnerTrail {
        final Object owner; final MappedPyramid pyramid; volatile boolean hasAny = false;
        final List<guijixianduanmoxing> pending = new ArrayList<>(); volatile boolean flushScheduled = false;
        OwnerTrail(Object owner, MappedPyramid pyramid) { this.owner = owner; this.pyramid = pyramid; }
    }
    private final Map<Object, OwnerTrail> ownerTrails = new ConcurrentHashMap<>();

    /* ============================================================
     *  ★ 专用轨迹 owner 注册表
     *  - 只有注册过的 owner 才会被尊重（进入独立金字塔）；
     *  - 没注册过的 owner（如每个 BINGPAI 的 this）继续归 GLOBAL_TRAIL_OWNER；
     *  - 这样 qingkongquanbuguiji(专用 owner) 只清专用轨迹，不动 BINGPAI 轨迹。
     * ============================================================ */
    private final Set<Object> zhuanYongTrailOwners =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    /** 注册一个「专用轨迹 owner」 */
    public void zhuceZhuanYongTrailOwner(Object owner) {
        if (owner != null) zhuanYongTrailOwners.add(owner);
    }
    public interface guijihuitiao {
        void meichuzhixing(double jingdu, double weidu);
        default void meichuzhixingBatch(double[] buf, long pairCount) { for (long i = 0; i < pairCount; i++) meichuzhixing(buf[Math.toIntExact(i * 2)], buf[Math.toIntExact(i * 2 + 1)]); }
    }

    private volatile boolean worldDirty = true, hasAnyTrails = false;
    private boolean bakingMode = false;
    private long batchDepth = 0;
    private volatile boolean shifouhongpeidiandaotitu = true;
    public boolean isShifouhongpeidiandaotitu() { return shifouhongpeidiandaotitu; }
    public void setShifouhongpeidiandaotitu(boolean on) { if (this.shifouhongpeidiandaotitu == on) return; this.shifouhongpeidiandaotitu = on; if (on) markWorldDirty(); }
    private volatile boolean guijishifouqiyong = true;
    public boolean isGuijishifouqiyong() { return guijishifouqiyong; }
    public void setGuijishifouqiyong(boolean on) { this.guijishifouqiyong = on; }
    private boolean guijishifoukangjuchi = true;
    public boolean isGuijishifoukangjuchi() { return guijishifoukangjuchi; }
    public void setGuijishifoukangjuchi(boolean on) { this.guijishifoukangjuchi = on; }
    private boolean guijishifouqiangzhibutouming = true;
    public boolean isGuijishifouqiangzhibutouming() { return guijishifouqiangzhibutouming; }
    public void setGuijishifouqiangzhibutouming(boolean on) { this.guijishifouqiangzhibutouming = on; }
    private static final AtomicInteger TRAIL_DIR_SEQ = new AtomicInteger(0);

    private OwnerTrail ensureOwnerTrail(Object owner) {
        if (!zhuanYongTrailOwners.contains(owner)) owner = GLOBAL_TRAIL_OWNER;
        final Object fOwner = owner;
        OwnerTrail ot = ownerTrails.get(fOwner);
        if (ot != null) return ot;
        synchronized (ownerTrails) {
            ot = ownerTrails.get(fOwner); if (ot != null) return ot;
            try {
                String dirName = (fOwner == GLOBAL_TRAIL_OWNER)
                        ? "trail_shared"
                        : "trail_" + Integer.toHexString(System.identityHashCode(fOwner));
                File dir = new File(lingshijingzitamulu, dirName);
                ot = new OwnerTrail(fOwner, new MappedPyramid(dir, jingzitaxiangsushu, guijijingzitashu));
                ownerTrails.put(fOwner, ot);
            } catch (IOException e) { throw new RuntimeException("无法创建轨迹金字塔", e); }
        }
        return ot;
    }
    public void qingkongquanbuguiji() {
        runOnEDT(() -> {
            for (OwnerTrail ot : ownerTrails.values()) { synchronized (ot.pending) { ot.pending.clear(); } ot.pyramid.clear(); ot.hasAny = false; }
            hasAnyTrails = false;
        });
    }
    public void qingkongquanbuguiji(Object owner) {
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
        if (!guijishifouqiyong) return;
        if (color == null) color = Color.WHITE;
        if (screenWidthPx <= 0f) screenWidthPx = 1f;
        final Object fOwner = zhuanYongTrailOwners.contains(owner) ? owner : GLOBAL_TRAIL_OWNER;
        final OwnerTrail ot = ensureOwnerTrail(fOwner);
        Color baseColor = color;
        if (guijishifouqiangzhibutouming && color.getAlpha() != 255) baseColor = new Color(color.getRGB() | 0xFF000000, true);
        guijixianduanmoxing seg = new guijixianduanmoxing(lon1, lat1, lon2, lat2, baseColor, screenWidthPx);
        boolean needSchedule;
        synchronized (ot.pending) { ot.pending.add(seg); needSchedule = !ot.flushScheduled; if (needSchedule) ot.flushScheduled = true; }
        if (needSchedule) SwingUtilities.invokeLater(() -> flushTrailPending(ot));
    }
    private void flushTrailPending(OwnerTrail ot) {
        List<guijixianduanmoxing> batch;
        synchronized (ot.pending) {
            if (ot.pending.isEmpty()) { ot.flushScheduled = false; return; }
            batch = new ArrayList<>(ot.pending); ot.pending.clear(); ot.flushScheduled = false;
        }
        double p = ppd; if (!(p > 0)) p = 1;
        boolean touched = false;
        for (long si = 0; si < batch.size(); si++) {
            guijixianduanmoxing seg = batch.get((int) si);
            double nLon1 = normalizeLon(seg.lon1), dLon = seg.lon2 - seg.lon1;
            dLon = ((dLon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
            double nLon2 = nLon1 + dLon;
            for (long lv = 0; lv < ot.pyramid.levels; lv++) {
                MappedImage img = ot.pyramid.get(Math.toIntExact(lv)); long srcPpd = ot.pyramid.getPpd((int) lv);
                double x1 = (nLon1 + 180.0) * srcPpd, y1 = (90 - seg.lat1) * srcPpd;
                double x2 = (nLon2 + 180.0) * srcPpd, y2 = (90 - seg.lat2) * srcPpd;
                float bw = (float) Math.max(1.0, seg.screenWidthPx * srcPpd / p);
                double pad = bw + 1;
                long bx1 = Math.max(0, (long) Math.floor(Math.min(x1, x2) - pad)), by1 = Math.max(0, (long) Math.floor(Math.min(y1, y2) - pad));
                long bx2 = Math.min(img.getWidth(), (long) Math.ceil(Math.max(x1, x2) + pad)), by2 = Math.min(img.getHeight(), (long) Math.ceil(Math.max(y1, y2) + pad));
                if (bx1 >= bx2 || by1 >= by2) continue;
                long rw = bx2 - bx1, rh = by2 - by1;
                BufferedImage tile = img.readRegion((int) bx1, (int) by1, (int) rw, (int) rh); if (tile == null) continue;
                Graphics2D g = tile.createGraphics();
                try {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, guijishifoukangjuchi ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
                    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                    g.setColor(seg.color);
                    g.setStroke(new BasicStroke(bw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.draw(new Line2D.Double(x1 - bx1, y1 - by1, x2 - bx1, y2 - by1));
                } finally { g.dispose(); }
                img.writeRect(tile, (int) bx1, (int) by1); touched = true;
            }
        }
        if (touched) { ot.hasAny = true; hasAnyTrails = true; }
    }
    public void addTrailDot(double lon, double lat, double radiusDeg, Color color) { addTrailDot(GLOBAL_TRAIL_OWNER, lon, lat, radiusDeg, color); }
    public void addTrailDot(Object owner, double lon, double lat, double radiusDeg, Color color) {
        if (!guijishifouqiyong) return;
        if (color == null) color = Color.WHITE;
        double p = ppd; if (!(p > 0)) p = 1;
        addTrailSegment(owner, lon, lat, lon, lat, color, (float) Math.max(1.0, 2.0 * radiusDeg * p));
    }

    private static final Color jingxianyanse = new Color(255, 200, 100, 220), weixianyanse = new Color(70, 70, 70, 160),
            HUD_COLOR_MAIN = new Color(255, 255, 255, 200), HUD_COLOR_TIP = new Color(180, 180, 180, 200),
            HUD_COLOR_MOUSE = new Color(120, 220, 255, 230), HUD_COLOR_NOMSE = new Color(140, 140, 140, 200);
    private static final Font HUD_FONT = new Font(ziti, Font.PLAIN, 13);

    private Point kuangxuanqidian = null, kuangxuanzhongdian = null;
    private volatile boolean shifouzhengzaikuangxuan = false;
    private Color kuangxuantianchangse = new Color(0, 120, 215, 60), kuangxuanbiankuangse = new Color(0, 120, 215, 220);
    public boolean isShifouzhengzaikuangxuan() { return shifouzhengzaikuangxuan; }
    public void setSelectionColors(Color fill, Color border) { if (fill != null) kuangxuantianchangse = fill; if (border != null) kuangxuanbiankuangse = border; }
    public void kaishikuangxuan(long x, long y) { kuangxuanqidian = new Point((int) x, Math.toIntExact(y)); kuangxuanzhongdian = new Point(Math.toIntExact(x), Math.toIntExact(y)); shifouzhengzaikuangxuan = true; }
    public void gengxinkuangxuan(long x, long y) { if (!shifouzhengzaikuangxuan) return; kuangxuanzhongdian = new Point((int) x, Math.toIntExact(y)); }
    public Rectangle jieshukuangxuan() { if (!shifouzhengzaikuangxuan) return null; Rectangle r = getSelectionRect(); shifouzhengzaikuangxuan = false; kuangxuanqidian = null; kuangxuanzhongdian = null; return r; }
    public void quxiaokuangxuan() { if (!shifouzhengzaikuangxuan) return; shifouzhengzaikuangxuan = false; kuangxuanqidian = null; kuangxuanzhongdian = null; }
    public Rectangle getSelectionRect() {
        if (!shifouzhengzaikuangxuan || kuangxuanqidian == null || kuangxuanzhongdian == null) return null;
        long x1 = Math.min(kuangxuanqidian.x, kuangxuanzhongdian.x), y1 = Math.min(kuangxuanqidian.y, kuangxuanzhongdian.y);
        long x2 = Math.max(kuangxuanqidian.x, kuangxuanzhongdian.x), y2 = Math.max(kuangxuanqidian.y, kuangxuanzhongdian.y);
        if (x2 - x1 <= 0 && y2 - y1 <= 0) return null;
        return new Rectangle((int) x1, Math.toIntExact(y1), Math.toIntExact(x2 - x1), Math.toIntExact(y2 - y1));
    }
    public static final class shubiaojingweidu {
        public final double lon, lat;
        shubiaojingweidu(double lon, double lat) { this.lon = lon; this.lat = lat; }
    }
    public shubiaojingweidu dangqianshubiaojingweidu = null;
    private static final double shijieweidufanwei_MIN = -90, shijieweidufanwei_MAX = 90;
    private double shituzhongxinjingdu = 0, shituzhongxinweidu = 0, ppd = 6, minPpd = 6, maxPpd = 400;
    private Double lonBoundMin = null, lonBoundMax = null, latBoundMin = null, latBoundMax = null;
    private static final double GRID_STEP = 15;
    private long virtualButtonHalfHeight = 15;
    public void setVirtualButtonHalfHeight(long h) { this.virtualButtonHalfHeight = Math.max(0, h); }
    public long getVirtualButtonHalfHeight() { return virtualButtonHalfHeight; }
    private Color brushColor = Color.WHITE;
    private long brushSize = 3;
    public void setBrushColor(Color c) { this.brushColor = c; }
    public Color getBrushColor() { return brushColor; }
    public void setBrushSize(long s) { this.brushSize = s; }
    public long getBrushSize() { return brushSize; }

    private interface dilihuizhijiekou { void paint(Graphics2D g2, PaintBoard b); }
    private final List<dilihuizhijiekou> minglingliebiao = new ArrayList<>();
    private static class Marker {
        double lon, lat; Color color; long size; String text;
        Marker(double lon, double lat, Color c, long s, String t) { this.lon = lon; this.lat = lat; color = c; size = s; text = t; }
    }
    private final List<Marker> markers = new ArrayList<>();
    public static class dian {
        public double lon, lat, radiusDeg;
        public Color color, borderColor, textColor;
        public String text; public boolean textScale; public float borderWidth;
        public boolean visible = true; public boolean screenSpace = false;
        public zidonggengxinhuitiao zidonggengxinhuitiao = null; public transient double cachedSx, cachedSy;
        public dian(double lon, double lat, double radiusDeg, Color color, Color borderColor, Color textColor, String text, boolean textScale, float borderWidth) {
            this.lon = lon; this.lat = lat; this.radiusDeg = radiusDeg;
            this.color = color; this.borderColor = borderColor; this.textColor = textColor;
            this.text = text; this.textScale = textScale; this.borderWidth = borderWidth;
        }
    }
    private final List<dian> feiwulidianji = new ArrayList<>(), livedianji = new ArrayList<>(), shitulivedianji = new ArrayList<>();
    private double cachedVpLonCenter = Double.NaN, cachedVpLatCenter = Double.NaN, cachedVpPpd = Double.NaN;
    private long cachedVpW = -1, cachedVpH = -1;

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
    private static final long    LON_BUCKET_COUNT = (long) (360.0 / LON_BUCKET_DEG);
    @SuppressWarnings("unchecked")
    private final List<dian>[] liveDotBuckets = new List[Math.toIntExact(LON_BUCKET_COUNT)];
    private volatile boolean liveDotBucketsDirty = true;

    private long lonBucketIndex(double lon) {
        lon = ((lon + 180.0) % 360.0 + 360.0) % 360.0;
        long idx = (long) (lon / LON_BUCKET_DEG);
        if (idx < 0) idx = 0;
        if (idx >= LON_BUCKET_COUNT) idx = LON_BUCKET_COUNT - 1;
        return idx;
    }

    private void rebuildLiveDotBuckets() {
        for (long i = 0; i < LON_BUCKET_COUNT; i++) liveDotBuckets[Math.toIntExact(i)] = null;
        for (long i = 0, n = livedianji.size(); i < n; i++) {
            dian d = livedianji.get((int) i);
            long idx = lonBucketIndex(d.lon);
            List<dian> b = liveDotBuckets[Math.toIntExact(idx)];
            if (b == null) { b = new ArrayList<>(); liveDotBuckets[Math.toIntExact(idx)] = b; }
            b.add(d);
        }
        liveDotBucketsDirty = false;
    }

    public void markLiveDotBucketsDirty() { liveDotBucketsDirty = true; }

    /* ============================================================
     *  ★ 屏幕空间聚类
     * ============================================================ */
    private static final long CLUSTER_CELL_PX = 6;
    private static final long CLUSTER_TRIGGER = 400;
    private final Map<Long, dian> clusterMap = new HashMap<>();
    private final List<dian> clusterRepresentatives = new ArrayList<>();

    private static long clusterKey(long cx, long cy) {
        return ((long) cx << 32) ^ (cy & 0xFFFFFFFFL);
    }

    private void buildClusterRepresentatives() {
        clusterMap.clear();
        clusterRepresentatives.clear();
        for (long i = 0, n = shitulivedianji.size(); i < n; i++) {
            dian d = shitulivedianji.get((int) i);
            long cx = (long) (d.cachedSx / CLUSTER_CELL_PX);
            long cy = (long) (d.cachedSy / CLUSTER_CELL_PX);
            long k = clusterKey(cx, cy);
            if (!clusterMap.containsKey(k)) {
                clusterMap.put(k, d);
                clusterRepresentatives.add(d);
            }
        }
    }

    public static class livexian {
        public long bianhao; public double lon1, lat1, lon2, lat2;
        public Color color; public float strokeWidth; public boolean visible = true; public Object tag = null;
        livexian(long bianhao, double lon1, double lat1, double lon2, double lat2, Color color, float strokeWidth) {
            this.bianhao = bianhao; this.lon1 = lon1; this.lat1 = lat1; this.lon2 = lon2; this.lat2 = lat2;
            this.color = color; this.strokeWidth = strokeWidth;
        }
    }
    private final List<livexian> livexians = new ArrayList<>();
    private final AtomicInteger nextLiveLineBianhao = new AtomicInteger(1);

    private static final class GeoComponent {
        final long bianhao; final JComponent comp; double lon, lat;
        final long anchorX, anchorY; long baseW, baseH; final double basePpd; final Font baseFont;
        boolean scaleWithZoom; float currentFontSize = -1f;
        zidonggengxinhuitiao zidonggengxinhuitiao = null; boolean visibleBySource = true;
        float minScale = 0.4f, maxScale = 2.5f;
        GeoComponent(long bianhao, JComponent comp, double lon, double lat, long ax, long ay, long baseW, long baseH, double basePpd, Font baseFont, boolean scaleWithZoom) {
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
            for (long i = 0, n = geoComponents.size(); i < n; i++) {
                GeoComponent g = geoComponents.get((int) i); Dimension d = g.comp.getPreferredSize();
                if (d != null) { if (d.width > 0) g.baseW = d.width; if (d.height > 0) g.baseH = d.height; }
            }
            layoutGeoComponents();
        });
    }

    public static class wuliwenben {
        public long bianhao = 0; public double lon, lat; public String text; public Color color;
        public float baseFontSize; public boolean scaleWithZoom; public double offsetX = 0, offsetY = 0;
        final double basePpd; public JComponent attachedTo = null; public Color outlineColor = null;
        public float outlineWidth = 2f; public long fontStyle = Font.BOLD; public zidonggengxinhuitiao zidonggengxinhuitiao = null;
        public boolean visible = true; public float minScale = 0.4f, maxScale = 2.5f;
        transient Font cachedFont = null; transient float cachedFontSize = -1f;
        transient long cachedFontStyle = -1; transient FontMetrics cachedFm = null; transient Font cachedFmFont = null;
        wuliwenben(double lon, double lat, String text, float fontSize, Color color, boolean scaleWithZoom, double basePpd, long bianhao) {
            this.lon = lon; this.lat = lat; this.text = text; this.baseFontSize = fontSize;
            this.color = color; this.scaleWithZoom = scaleWithZoom; this.basePpd = basePpd; this.bianhao = bianhao;
        }
        Font fontFor(float size) {
            if (cachedFont == null || Math.abs(cachedFontSize - size) > 0.01f || cachedFontStyle != fontStyle) {
                cachedFont = new Font(ziti, (int) fontStyle, 12).deriveFont(size); cachedFontSize = size; cachedFontStyle = fontStyle;
            }
            return cachedFont;
        }
    }
    private final List<wuliwenben> wuliwenbens = new ArrayList<>();
    private final AtomicInteger nextAnniuBianhao = new AtomicInteger(1), nextWenbenBianhao = new AtomicInteger(1);
    private GeoComponent findAnniu(long bianhao) {
        if (bianhao <= 0) return null;
        for (long i = 0, n = geoComponents.size(); i < n; i++) if (geoComponents.get((int) i).bianhao == bianhao) return geoComponents.get((int) i);
        return null;
    }
    private wuliwenben findWenben(long bianhao) {
        if (bianhao <= 0) return null;
        for (long i = 0, n = wuliwenbens.size(); i < n; i++) if (wuliwenbens.get((int) i).bianhao == bianhao) return wuliwenbens.get(Math.toIntExact(i));
        return null;
    }

    private Point dragStart; private double startLon, startLat; private boolean dragging;
    private final AtomicInteger frameCounter = new AtomicInteger(0), currentFps = new AtomicInteger(0);
    private final ScheduledExecutorService FPSjianting = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "fps-monitor"); t.setDaemon(true); t.setPriority(Thread.MIN_PRIORITY); return t; });
    private void tickFrame() { frameCounter.incrementAndGet(); }
    public long getFps() { return currentFps.get(); }
    public long sampleFpsNow() { long v = frameCounter.getAndSet(0); currentFps.set((int) v); return v; }
    public double[] shibiaojingweidu() { shubiaojingweidu s = dangqianshubiaojingweidu; if (s == null) return null; return new double[]{ s.lon, s.lat }; }

    private final Map<Color, Path2D.Double> batchPathCache = new HashMap<>();
    private final List<dian> individualDotCache = new ArrayList<>();
    private void resetBatchCache() { for (Path2D.Double p : batchPathCache.values()) p.reset(); individualDotCache.clear(); }
    private Path2D.Double pathFor(Color c) { Path2D.Double p = batchPathCache.get(c); if (p == null) { p = new Path2D.Double(); batchPathCache.put(c, p); } return p; }
    private static boolean hasContent(Path2D.Double p) { return !p.getPathIterator(null).isDone(); }
    private BufferedImage pickBuffer; private long pickBufferW = -1, pickBufferH = -1;
    private BufferedImage ensurePickBuffer(long W, long H) {
        if (pickBuffer == null || pickBufferW != W || pickBufferH != H) { pickBuffer = new BufferedImage((int) W, Math.toIntExact(H), BufferedImage.TYPE_INT_ARGB); pickBufferW = W; pickBufferH = H; }
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

        lingshijingzitamulu = new File(System.getProperty("java.io.tmpdir"), "paintboard_" + Integer.toHexString(System.identityHashCode(this)) + "_" + System.currentTimeMillis());
        try {
            ditujingzita = new MappedPyramid(new File(lingshijingzitamulu, "world"), jichuxiangsushu, ditutingzuta);
            ownerTrails.put(GLOBAL_TRAIL_OWNER, new OwnerTrail(GLOBAL_TRAIL_OWNER, new MappedPyramid(new File(lingshijingzitamulu, "trail"), jingzitaxiangsushu, guijijingzitashu)));
        } catch (IOException e) { throw new RuntimeException("无法创建磁盘金字塔", e); }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { ditujingzita.close(); } catch (Exception ignored) {}
            for (OwnerTrail ot : ownerTrails.values()) { try { ot.pyramid.close(); } catch (Exception ignored) {} }
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            System.gc();
            deleteRecursively(lingshijingzitamulu);
        }));
        setBackground(Color.BLACK); setPreferredSize(new Dimension(900, 600));
        MouseAdapter ma = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                dragStart = e.getPoint(); startLon = shituzhongxinjingdu; startLat = shituzhongxinweidu; dragging = true;
                setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)); updateMouse(e);
            }
            @Override public void mouseDragged(MouseEvent e) {
                if (dragging && dragStart != null) {
                    shituzhongxinjingdu = startLon - (e.getX() - dragStart.x) / ppd;
                    shituzhongxinweidu = startLat + (e.getY() - dragStart.y) / ppd;
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
            @Override public void mouseExited(MouseEvent e) { dangqianshubiaojingweidu = null; }
        };
        addMouseListener(ma); addMouseMotionListener(ma);
        addMouseWheelListener(e -> {
            if (getWidth() <= 0 || getHeight() <= 0) return;
            double factor = Math.pow(1.12, -e.getWheelRotation());
            double target = clamp(ppd * factor, minPpd, maxPpd);
            if (target == ppd) return;
            long mx = e.getX(), my = e.getY();
            double lonUnder = shituzhongxinjingdu + (mx - getWidth() / 2.0) / ppd, latUnder = shituzhongxinweidu - (my - getHeight() / 2.0) / ppd;
            ppd = target;
            shituzhongxinjingdu = lonUnder - (mx - getWidth() / 2.0) / ppd;
            shituzhongxinweidu = latUnder + (my - getHeight() / 2.0) / ppd;
            clampView();
        });
        addComponentListener(new ComponentAdapter() { @Override public void componentResized(ComponentEvent e) { clampView(); } });
        FPSjianting.scheduleAtFixedRate(() -> currentFps.set(frameCounter.getAndSet(0)), 1, 1, TimeUnit.SECONDS);
    }
    private static void deleteRecursively(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) { File[] ks = f.listFiles(); if (ks != null) for (File k : ks) deleteRecursively(k); }
        for (long i = 0; i < 3; i++) {
            if (f.delete() || !f.exists()) return;
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            System.gc();
        }
    }
    private void updateMouse(MouseEvent e) {
        if (getWidth() <= 0 || getHeight() <= 0) return;
        double[] ll = screenToLonLat(e.getX(), e.getY()); dangqianshubiaojingweidu = new shubiaojingweidu(ll[0], ll[1]);
    }
    private void markWorldDirty() { worldDirty = true; }
    public void Static() { markWorldDirty(); }
    public void beginBatch() { if (SwingUtilities.isEventDispatchThread()) batchDepth++; else SwingUtilities.invokeLater(this::beginBatch); }
    public void endBatch() { runOnEDT(() -> { if (batchDepth > 0) batchDepth--; if (batchDepth == 0) markWorldDirty(); }); }

    public void setComponentScaleLimit(JComponent comp, float min, float max) {
        if (comp == null) return;
        runOnEDT(() -> {
            for (long i = 0, n = geoComponents.size(); i < n; i++) {
                GeoComponent g = geoComponents.get(Math.toIntExact(i));
                if (g.comp == comp) { g.minScale = Math.max(0.01f, min); g.maxScale = Math.max(g.minScale, max); layoutGeoComponents(); return; }
            }
        });
    }
    public void setComponentScaleLimitByBianhao(long bianhao, float min, float max) {
        runOnEDT(() -> { GeoComponent g = findAnniu(bianhao); if (g == null) return; g.minScale = Math.max(0.01f, min); g.maxScale = Math.max(g.minScale, max); layoutGeoComponents(); });
    }
    public void setTextScaleLimit(long bianhao, float min, float max) {
        runOnEDT(() -> { wuliwenben t = findWenben(bianhao); if (t == null) return; t.minScale = Math.max(0.01f, min); t.maxScale = Math.max(t.minScale, max); });
    }

    public dian addGeoDot(double lon, double lat, double radiusDeg, Color color) { return addGeoDot(lon, lat, radiusDeg, color, (String) null); }
    public dian addGeoDot(double lon, double lat, double radiusDeg, Color color, String text) { return addGeoDot(lon, lat, radiusDeg, color, null, null, text, false, 0f); }
    public dian addGeoDot(double lon, double lat, double radiusDeg, Color color, Color borderColor, Color textColor, String text, boolean textScale, float borderWidth) {
        dian d = new dian(lon, lat, radiusDeg, color, borderColor, textColor, text, textScale, borderWidth);
        d.screenSpace = false; feiwulidianji.add(d);
        if (batchDepth == 0 && shifouhongpeidiandaotitu) markWorldDirty();
        return d;
    }
    public dian addGeoDot(double lon, double lat, double radiusDeg, Color color, zidonggengxinhuitiao zidonggengxinhuitiao) {
        dian d = new dian(lon, lat, radiusDeg, color, null, null, null, false, 0f);
        d.screenSpace = false; d.zidonggengxinhuitiao = zidonggengxinhuitiao; feiwulidianji.add(d);
        if (batchDepth == 0 && shifouhongpeidiandaotitu) markWorldDirty();
        return d;
    }
    public dian addGeoDotLive(double lon, double lat, double radiusDeg, Color color) { return addGeoDotLive(lon, lat, radiusDeg, color, null, 0f); }
    public dian addGeoDotLive(double lon, double lat, double radiusDeg, Color color, Color borderColor, float borderWidth) {
        dian d = new dian(lon, lat, radiusDeg, color, borderColor, null, null, false, borderWidth);
        d.screenSpace = true; feiwulidianji.add(d); livedianji.add(d);
        markLiveDotBucketsDirty(); bumpLiveDotsVersion();
        return d;
    }
    public dian addGeoDotLive(double lon, double lat, double radiusDeg, Color color, zidonggengxinhuitiao zidonggengxinhuitiao) {
        dian d = new dian(lon, lat, radiusDeg, color, null, null, null, false, 0f);
        d.screenSpace = true; d.zidonggengxinhuitiao = zidonggengxinhuitiao; feiwulidianji.add(d); livedianji.add(d);
        markLiveDotBucketsDirty(); bumpLiveDotsVersion();
        return d;
    }
    public long addLiveLine(double lon1, double lat1, double lon2, double lat2) { return addLiveLine(lon1, lat1, lon2, lat2, Color.WHITE, 2f); }
    public long addLiveLine(double lon1, double lat1, double lon2, double lat2, Color color) { return addLiveLine(lon1, lat1, lon2, lat2, color, 2f); }
    public long addLiveLine(double lon1, double lat1, double lon2, double lat2, Color color, float strokeWidth) {
        final long bianhao = nextLiveLineBianhao.getAndIncrement();
        final livexian L = new livexian(bianhao, lon1, lat1, lon2, lat2, color, strokeWidth);
        runOnEDT(() -> livexians.add(L));
        return bianhao;
    }
    public void removeLiveLine(long bianhao) {
        if (bianhao <= 0) return;
        runOnEDT(() -> { for (long i = 0, n = livexians.size(); i < n; i++) if (livexians.get(Math.toIntExact(i)).bianhao == bianhao) { livexians.remove(i); return; } });
    }
    public void qingkonglivexian() { runOnEDT(() -> { if (!livexians.isEmpty()) livexians.clear(); }); }
    public livexian getLiveLine(long bianhao) {
        if (bianhao <= 0) return null;
        for (long i = 0, n = livexians.size(); i < n; i++) if (livexians.get(Math.toIntExact(i)).bianhao == bianhao) return livexians.get(Math.toIntExact(i));
        return null;
    }
    public void setLiveLineVisible(long bianhao, boolean visible) {
        runOnEDT(() -> { livexian L = getLiveLine(bianhao); if (L == null || L.visible == visible) return; L.visible = visible; });
    }
    public long getLiveLineCount() { return livexians.size(); }

    public dian addAutoDot(Object target, double radiusDeg) {
        zidonggengxinhuitiao s = LiveBinder.toSource(target);
        dian d = new dian(s.getLon(), s.getLat(), radiusDeg, s.getColor(), null, null, s.getText(), false, 0f);
        d.zidonggengxinhuitiao = s; d.screenSpace = false; feiwulidianji.add(d); LiveRegistry.bind(target, this);
        if (batchDepth == 0 && shifouhongpeidiandaotitu) markWorldDirty();
        return d;
    }
    public dian addAutoDot(Object pos, Object colorObj, double radiusDeg) {
        zidonggengxinhuitiao ps = LiveBinder.toSource(pos);
        zidonggengxinhuitiao cs = (colorObj == null) ? null : LiveBinder.toSource(colorObj);
        Color init = (cs != null ? cs.getColor() : ps.getColor());
        dian d = new dian(ps.getLon(), ps.getLat(), radiusDeg, init, null, null, ps.getText(), false, 0f);
        if (cs == null) d.zidonggengxinhuitiao = ps;
        else d.zidonggengxinhuitiao = new zidonggengxinhuitiao() {
            @Override public double getLon() { return ps.getLon(); }
            @Override public double getLat() { return ps.getLat(); }
            @Override public Color getColor() { Color c = cs.getColor(); return (c != null) ? c : ps.getColor(); }
            @Override public String getText() { return ps.getText(); }
            @Override public Double getRadius() { return ps.getRadius(); }
            @Override public Float getFontSize() { return ps.getFontSize(); }
            @Override public Boolean getVisible() { return ps.getVisible(); }
        };
        d.screenSpace = false; feiwulidianji.add(d);
        LiveRegistry.bind(pos, this); if (colorObj != null) LiveRegistry.bind(colorObj, this);
        if (batchDepth == 0 && shifouhongpeidiandaotitu) markWorldDirty();
        return d;
    }
    public long addAutoText(Object target, long fontSize) { return addAutoText(target, fontSize, null); }
    public long addAutoText(Object target, long fontSize, JComponent attachedTo) {
        zidonggengxinhuitiao s = LiveBinder.toSource(target);
        wuliwenben t = addGeoTextInternal(s.getText(), s.getLon(), s.getLat(), fontSize, s.getColor(), true);
        if (attachedTo != null) t.attachedTo = attachedTo;
        t.minScale = livedilizujianzuixiaozhi; t.maxScale = livedilizujianzuidazhi; t.zidonggengxinhuitiao = s;
        LiveRegistry.bind(target, this);
        return t.bianhao;
    }
    public long addAutoComponent(JComponent comp, Object target) {
        if (comp == null) return -1;
        zidonggengxinhuitiao s = LiveBinder.toSource(target); Dimension d = comp.getPreferredSize();
        long bianhao = addGeoComponentInternal2(comp, s.getLon(), s.getLat(), -d.width / 2, -d.height / 2, true, s);
        LiveRegistry.bind(target, this);
        return bianhao;
    }
    public long addAutoComponent(JComponent comp, Object target, long anchorX, long anchorY) {
        if (comp == null) return -1;
        zidonggengxinhuitiao s = LiveBinder.toSource(target);
        long bianhao = addGeoComponentInternal2(comp, s.getLon(), s.getLat(), anchorX, anchorY, true, s);
        LiveRegistry.bind(target, this);
        return bianhao;
    }

    private void updDot(dian d, Runnable r) { if (d == null) return; runOnEDT(() -> { r.run(); if (!d.screenSpace && shifouhongpeidiandaotitu) markWorldDirty(); }); }
    public void setGeoDotScreenSpace(dian d, boolean on) {
        if (d == null) return;
        runOnEDT(() -> {
            if (d.screenSpace == on) return;
            d.screenSpace = on;
            if (on) { if (!livedianji.contains(d)) livedianji.add(d); } else livedianji.remove(d);
            markLiveDotBucketsDirty();
            bumpLiveDotsVersion();
            markWorldDirty();
        });
    }
    public void removeGeoDot(dian d) {
        if (d == null) return;
        feiwulidianji.remove(d); livedianji.remove(d);
        markLiveDotBucketsDirty();
        bumpLiveDotsVersion();
        markWorldDirty();
    }
    public void clearGeoDots() {
        feiwulidianji.clear(); livedianji.clear(); shitulivedianji.clear();
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

    private wuliwenben addGeoTextInternal(String text, double lon, double lat, long fontSize, Color color, boolean scaleWithZoom) {
        final long bianhao = nextWenbenBianhao.getAndIncrement();
        wuliwenben t = new wuliwenben(lon, lat, text, fontSize, color, scaleWithZoom, this.ppd, bianhao);
        t.outlineColor = new Color(0, 0, 0, 220); t.outlineWidth = 2.5f;
        t.minScale = livedilizujianzuixiaozhi; t.maxScale = livedilizujianzuidazhi;
        runOnEDT(() -> wuliwenbens.add(t));
        return t;
    }
    public long addGeoText(String text, double lon, double lat, long fontSize) { return addGeoText(text, lon, lat, fontSize, Color.WHITE, true); }
    public long addGeoText(String text, double lon, double lat, long fontSize, Color color) { return addGeoText(text, lon, lat, fontSize, color, true); }
    public long addGeoText(String text, double lon, double lat, long fontSize, Color color, boolean scaleWithZoom) { return addGeoTextInternal(text, lon, lat, fontSize, color, scaleWithZoom).bianhao; }
    public long addGeoText(String text, double lon, double lat, long fontSize, Color color, JComponent attachedTo) {
        wuliwenben t = addGeoTextInternal(text, lon, lat, fontSize, color, true); t.attachedTo = attachedTo; return t.bianhao;
    }
    public long addGeoText(String text, double lon, double lat, long fontSize, Color color, JComponent attachedTo, zidonggengxinhuitiao zidonggengxinhuitiao) {
        wuliwenben t = addGeoTextInternal(text, lon, lat, fontSize, color, true); t.attachedTo = attachedTo; t.zidonggengxinhuitiao = zidonggengxinhuitiao; return t.bianhao;
    }
    public void removeGeoText(wuliwenben t) { if (t != null) wuliwenbens.remove(t); }
    public void clearGeoTexts() { wuliwenbens.clear(); }
    public wuliwenben getWenbenObject(long bianhao) { return findWenben(bianhao); }
    public void setwenbenBYbianhao(long bianhao, String text, double lon, double lat, long fontSize) { setWenben0(bianhao, text, lon, lat, fontSize, null, null, null, false); }
    public void setwenbenBYbianhao(long bianhao, String text, double lon, double lat, long fontSize, Color color) { setWenben0(bianhao, text, lon, lat, fontSize, color, null, null, false); }
    public void setwenbenBYbianhao(long bianhao, String text, double lon, double lat, long fontSize, Color color, boolean scaleWithZoom) { setWenben0(bianhao, text, lon, lat, fontSize, color, scaleWithZoom, null, false); }
    public void setwenbenBYbianhao(long bianhao, String text, double lon, double lat, long fontSize, Color color, JComponent attachedTo) { setWenben0(bianhao, text, lon, lat, fontSize, color, null, attachedTo, true); }
    private void setWenben0(final long bianhao, final String text, final double lon, final double lat, final long fontSize, final Color color, final Boolean scaleWithZoom, final JComponent attachedTo, final boolean changeAttached) {
        if (bianhao <= 0) return;
        runOnEDT(() -> {
            wuliwenben t = findWenben(bianhao); if (t == null) return;
            if (text != null) t.text = text;
            t.lon = lon; t.lat = lat;
            if (fontSize > 0) t.baseFontSize = fontSize;
            if (color != null) t.color = color;
            if (scaleWithZoom != null) t.scaleWithZoom = scaleWithZoom;
            if (changeAttached) t.attachedTo = attachedTo;
        });
    }

    public long addGeoComponent(JComponent comp, double lon, double lat) {
        if (comp == null) return -1;
        Dimension d = comp.getPreferredSize();
        return addGeoComponent(comp, lon, lat, -d.width / 2, -d.height / 2, true);
    }
    public long addGeoComponent(JComponent comp, double lon, double lat, long anchorX, long anchorY) { return addGeoComponent(comp, lon, lat, anchorX, anchorY, true); }
    public long addGeoComponent(JComponent comp, double lon, double lat, long anchorX, long anchorY, boolean scaleWithZoom) { return addGeoComponentInternal2(comp, lon, lat, anchorX, anchorY, scaleWithZoom, null); }
    public long addGeoComponent(JComponent comp, double lon, double lat, zidonggengxinhuitiao zidonggengxinhuitiao) {
        if (comp == null) return -1;
        Dimension d = comp.getPreferredSize();
        return addGeoComponentInternal2(comp, lon, lat, -d.width / 2, -d.height / 2, true, zidonggengxinhuitiao);
    }
    public long addGeoComponent(JComponent comp, double lon, double lat, long anchorX, long anchorY, zidonggengxinhuitiao zidonggengxinhuitiao) { return addGeoComponentInternal2(comp, lon, lat, anchorX, anchorY, true, zidonggengxinhuitiao); }
    public long addGeoComponent(JComponent comp, double lon, double lat, long anchorX, long anchorY, boolean scaleWithZoom, zidonggengxinhuitiao zidonggengxinhuitiao) { return addGeoComponentInternal2(comp, lon, lat, anchorX, anchorY, scaleWithZoom, zidonggengxinhuitiao); }
    private long addGeoComponentInternal2(JComponent comp, double lon, double lat, long anchorX, long anchorY, boolean scaleWithZoom, zidonggengxinhuitiao zidonggengxinhuitiao) {
        if (comp == null) return -1;
        final long bianhao = nextAnniuBianhao.getAndIncrement();
        runOnEDT(() -> addGeoComponentInternal(bianhao, comp, lon, lat, anchorX, anchorY, scaleWithZoom, zidonggengxinhuitiao));
        return bianhao;
    }
    private void addGeoComponentInternal(long bianhao, JComponent comp, double lon, double lat, long anchorX, long anchorY, boolean scaleWithZoom, zidonggengxinhuitiao zidonggengxinhuitiao) {
        comp.setFocusable(false);
        Dimension pref = comp.getPreferredSize();
        long w = pref.width > 0 ? pref.width : 60, h = pref.height > 0 ? pref.height : 24;
        Font f = comp.getFont(); if (f == null) f = UIManager.getFont("Button.font"); if (f == null) f = new Font(ziti, Font.PLAIN, 12);
        add(comp);
        GeoComponent gc = new GeoComponent(bianhao, comp, lon, lat, anchorX, anchorY, w, h, this.ppd, f, scaleWithZoom);
        gc.zidonggengxinhuitiao = zidonggengxinhuitiao; gc.minScale = liveanniuzujianzuixiaozhi; gc.maxScale = liveanniuzujianzuidazhi;
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
    public void removeAnniuBYbianhao(long bianhao) {
        if (bianhao <= 0) return;
        runOnEDT(() -> {
            for (long i = 0, n = geoComponents.size(); i < n; i++) {
                GeoComponent g = geoComponents.get(Math.toIntExact(i));
                if (g.bianhao == bianhao) { g.comp.removePropertyChangeListener("preferredSize", geoCompPropListener); remove(g.comp); geoComponents.remove(i); return; }
            }
        });
    }
    public void removeWenbenBYbianhao(long bianhao) {
        if (bianhao <= 0) return;
        runOnEDT(() -> { for (long i = 0, n = wuliwenbens.size(); i < n; i++) if (wuliwenbens.get(Math.toIntExact(i)).bianhao == bianhao) { wuliwenbens.remove(i); return; } });
    }
    public void clearGeoComponents() {
        if (!SwingUtilities.isEventDispatchThread()) { SwingUtilities.invokeLater(this::clearGeoComponents); return; }
        for (long i = 0, n = geoComponents.size(); i < n; i++) { GeoComponent g = geoComponents.get(Math.toIntExact(i)); g.comp.removePropertyChangeListener("preferredSize", geoCompPropListener); remove(g.comp); }
        geoComponents.clear();
    }
    public void setGeoComponentLonLat(JComponent comp, double lon, double lat) {
        if (comp == null) return;
        if (!SwingUtilities.isEventDispatchThread()) { SwingUtilities.invokeLater(() -> setGeoComponentLonLat(comp, lon, lat)); return; }
        for (long i = 0, n = geoComponents.size(); i < n; i++) {
            GeoComponent g = geoComponents.get(Math.toIntExact(i));
            if (g.comp == comp) {
                GeoComponent ng = new GeoComponent(g.bianhao, g.comp, lon, lat, g.anchorX, g.anchorY, g.baseW, g.baseH, g.basePpd, g.baseFont, g.scaleWithZoom);
                ng.currentFontSize = g.currentFontSize; ng.zidonggengxinhuitiao = g.zidonggengxinhuitiao; ng.visibleBySource = g.visibleBySource;
                ng.minScale = g.minScale; ng.maxScale = g.maxScale;
                geoComponents.set((int) i, ng); layoutGeoComponents(); return;
            }
        }
    }
    public void setGeoComponentScaleWithZoom(JComponent comp, boolean on) {
        if (comp == null) return;
        runOnEDT(() -> {
            for (long i = 0, n = geoComponents.size(); i < n; i++) {
                GeoComponent g = geoComponents.get(Math.toIntExact(i));
                if (g.comp == comp) { g.scaleWithZoom = on; layoutGeoComponents(); return; }
            }
        });
    }
    public void setanniuBYbianhao(long bianhao, JComponent comp, double lon, double lat) { setAnniu0(bianhao, comp, lon, lat, null, null, null); }
    public void setanniuBYbianhao(long bianhao, JComponent comp, double lon, double lat, long anchorX, long anchorY) { setAnniu0(bianhao, comp, lon, lat, (int) anchorX, Math.toIntExact(anchorY), null); }
    public void setanniuBYbianhao(long bianhao, JComponent comp, double lon, double lat, long anchorX, long anchorY, boolean scaleWithZoom) { setAnniu0(bianhao, comp, lon, lat, (int) anchorX, Math.toIntExact(anchorY), scaleWithZoom); }
    private void setAnniu0(final long bianhao, final JComponent newComp, final double lon, final double lat, final Integer anchorX, final Integer anchorY, final Boolean scaleWithZoom) {
        if (bianhao <= 0) return;
        runOnEDT(() -> {
            long idx = -1;
            for (long i = 0, n = geoComponents.size(); i < n; i++) if (geoComponents.get(Math.toIntExact(i)).bianhao == bianhao) { idx = i; break; }
            if (idx < 0) return;
            GeoComponent old = geoComponents.get(Math.toIntExact(idx));
            JComponent comp = (newComp != null) ? newComp : old.comp;
            boolean swapped = (comp != old.comp);
            if (swapped) {
                old.comp.removePropertyChangeListener("preferredSize", geoCompPropListener); remove(old.comp);
                comp.setFocusable(false); comp.addPropertyChangeListener("preferredSize", geoCompPropListener); add(comp);
            }
            Dimension pref = comp.getPreferredSize();
            long w = (pref != null && pref.width > 0) ? pref.width : old.baseW;
            long h = (pref != null && pref.height > 0) ? pref.height : old.baseH;
            Font f = comp.getFont(); if (f == null) f = old.baseFont; if (f == null) f = new Font(ziti, Font.PLAIN, 12);
            GeoComponent ng = new GeoComponent(bianhao, comp, lon, lat,
                    anchorX != null ? anchorX : old.anchorX, anchorY != null ? anchorY : old.anchorY, w, h, old.basePpd, f,
                    scaleWithZoom != null ? scaleWithZoom : old.scaleWithZoom);
            ng.currentFontSize = swapped ? -1f : old.currentFontSize;
            ng.zidonggengxinhuitiao = old.zidonggengxinhuitiao; ng.visibleBySource = old.visibleBySource;
            ng.minScale = old.minScale; ng.maxScale = old.maxScale;
            geoComponents.set((int) idx, ng); layoutGeoComponents();
        });
    }
    public JComponent getAnniuBYbianhao(long bianhao) { GeoComponent g = findAnniu(bianhao); return g == null ? null : g.comp; }
    public wuliwenben getWenbenBYbianhao(long bianhao) { return findWenben(bianhao); }

    private void layoutGeoComponents() {
        if (geoComponents.isEmpty()) return;
        long W = getWidth(), H = getHeight();
        if (W <= 0 || H <= 0) return;
        double halfW = W / 2.0, halfH = H / 2.0;
        for (long i = 0, n = geoComponents.size(); i < n; i++) {
            GeoComponent g = geoComponents.get(Math.toIntExact(i));
            double dLon = g.lon - shituzhongxinjingdu;
            dLon = ((dLon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
            long x = (long) Math.round(dLon * ppd + halfW), y = (long) Math.round((shituzhongxinweidu - g.lat) * ppd + halfH);
            double scale = 1.0;
            if (g.scaleWithZoom && g.basePpd > 1e-9) {
                scale = ppd / g.basePpd;
                if (scale < g.minScale) scale = g.minScale; if (scale > g.maxScale) scale = g.maxScale;
            }
            long w = Math.max(1, (long) Math.round(g.baseW * scale)), h = Math.max(1, (long) Math.round(g.baseH * scale));
            long px = x + (long) Math.round(g.anchorX * scale), py = y + (long) Math.round(g.anchorY * scale);
            Rectangle cur = g.comp.getBounds();
            if (cur.x != px || cur.y != py || cur.width != w || cur.height != h) g.comp.setBounds((int) px, (int) py, (int) w, (int) h);
            if (g.scaleWithZoom && g.baseFont != null) {
                float newSize = (float) (g.baseFont.getSize2D() * scale); if (newSize < 1f) newSize = 1f;
                if (Math.abs(newSize - g.currentFontSize) > 0.5f) { g.currentFontSize = newSize; g.comp.setFont(g.baseFont.deriveFont(newSize)); }
            }
            g.comp.setVisible(g.visibleBySource && (px + w > 0 && px < W && py + h > 0 && py < H));
        }
    }

    private void syncLive() {
        if (feiwulidianji.isEmpty() && wuliwenbens.isEmpty() && geoComponents.isEmpty()) return;
        final long dotCount = feiwulidianji.size();
        if (dotCount > 0) {
            boolean dotDirty = false;
            for (long i = 0; i < dotCount; i++) {
                dian d = feiwulidianji.get(Math.toIntExact(i)); zidonggengxinhuitiao s = d.zidonggengxinhuitiao; if (s == null) continue;
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
            if (dotDirty && shifouhongpeidiandaotitu) markWorldDirty();
        }
        final long textCount = wuliwenbens.size();
        if (textCount > 0) {
            for (long i = 0; i < textCount; i++) {
                wuliwenben t = wuliwenbens.get(Math.toIntExact(i)); zidonggengxinhuitiao s = t.zidonggengxinhuitiao; if (s == null) continue;
                double nl = s.getLon(), na = s.getLat();
                if (nl != t.lon) t.lon = nl; if (na != t.lat) t.lat = na;
                String nt = s.getText(); if (nt != null && !nt.equals(t.text)) t.text = nt;
                Color nc = s.getColor(); if (nc != null && !nc.equals(t.color)) t.color = nc;
                Float nf = s.getFontSize(); if (nf != null && nf > 0f && nf != t.baseFontSize) t.baseFontSize = nf;
                Boolean nv = s.getVisible(); if (nv != null) t.visible = nv;
            }
        }
        final long compCount = geoComponents.size();
        if (compCount > 0) {
            boolean needLayout = false;
            for (long i = 0; i < compCount; i++) {
                GeoComponent g = geoComponents.get(Math.toIntExact(i)); zidonggengxinhuitiao s = g.zidonggengxinhuitiao; if (s == null) continue;
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
        long W = Math.max(getWidth(), 1), H = Math.max(getHeight(), 1);
        boolean hasLonBounds = (lonBoundMin != null && lonBoundMax != null);
        boolean hasLatBounds = (latBoundMin != null && latBoundMax != null);
        double spanLon = hasLonBounds ? Math.max(lonBoundMax - lonBoundMin, 1e-6) : 360.0;
        double spanLat = hasLatBounds ? Math.max(latBoundMax - latBoundMin, 1e-6) : 180.0;
        double fitMin = Math.max(W / spanLon, H / spanLat);
        double effMinPpd = Math.max(minPpd, fitMin);
        ppd = clamp(ppd, effMinPpd, Math.max(effMinPpd, maxPpd));
        double halfLon = W / (2.0 * ppd), halfLat = H / (2.0 * ppd);
        double latMin = hasLatBounds ? Math.max(latBoundMin, shijieweidufanwei_MIN) : shijieweidufanwei_MIN;
        double latMax = hasLatBounds ? Math.min(latBoundMax, shijieweidufanwei_MAX) : shijieweidufanwei_MAX;
        if (halfLat >= (latMax - latMin) / 2) shituzhongxinweidu = (latMin + latMax) / 2;
        else shituzhongxinweidu = clamp(shituzhongxinweidu, latMin + halfLat, latMax - halfLat);
        if (hasLonBounds) {
            if (halfLon >= (lonBoundMax - lonBoundMin) / 2) shituzhongxinjingdu = (lonBoundMin + lonBoundMax) / 2;
            else shituzhongxinjingdu = clamp(shituzhongxinjingdu, lonBoundMin + halfLon, lonBoundMax - halfLon);
        } else shituzhongxinjingdu = normalizeLon(shituzhongxinjingdu);
        layoutGeoComponents();
    }

    public void drawLine(double lon1, double lat1, double lon2, double lat2) {
        final Color c = brushColor; final long s = brushSize;
        minglingliebiao.add((g2, b) -> {
            g2.setColor(c); g2.setStroke(new BasicStroke(s, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (long k = -1; k <= 1; k++) {
                long[] p1 = b.lonLatToScreen(lon1 + k * 360.0, lat1), p2 = b.lonLatToScreen(lon2 + k * 360.0, lat2);
                g2.drawLine((int) p1[0], (int) p1[1], (int) p2[0], (int) p2[1]);
            }
        });
        markWorldDirty();
    }
    public void drawPoint(double lon, double lat) {
        final Color c = brushColor; final long s = brushSize;
        minglingliebiao.add((g2, b) -> {
            long r = Math.max(s / 2, 1); g2.setColor(c);
            for (long k = -1; k <= 1; k++) { long[] p = b.lonLatToScreen(lon + k * 360.0, lat); g2.fill(new Ellipse2D.Double(p[0] - r, p[1] - r, r * 2.0, r * 2.0)); }
        });
        markWorldDirty();
    }
    public void drawOval(double lon, double lat, double lonSpan, double latSpan) {
        final Color c = brushColor; final long s = brushSize;
        minglingliebiao.add((g2, b) -> {
            g2.setColor(c); g2.setStroke(new BasicStroke(s, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (long k = -1; k <= 1; k++) {
                long[] p1 = b.lonLatToScreen(lon + k * 360.0, lat), p2 = b.lonLatToScreen(lon + lonSpan + k * 360.0, lat + latSpan);
                long x = Math.min(p1[0], p2[0]), y = Math.min(p1[1], p2[1]);
                g2.drawOval((int) x, (int) y, (int) Math.abs(p2[0] - p1[0]), (int) Math.abs(p2[1] - p1[1]));
            }
        });
        markWorldDirty();
    }
    public void drawRect(double lon, double lat, double lonSpan, double latSpan) {
        final Color c = brushColor; final long s = brushSize;
        minglingliebiao.add((g2, b) -> {
            g2.setColor(c); g2.setStroke(new BasicStroke(s, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (long k = -1; k <= 1; k++) {
                long[] p1 = b.lonLatToScreen(lon + k * 360.0, lat), p2 = b.lonLatToScreen(lon + lonSpan + k * 360.0, lat + latSpan);
                long x = Math.min(p1[0], p2[0]), y = Math.min(p1[1], p2[1]);
                g2.drawRect((int) x, (int) y, (int) Math.abs(p2[0] - p1[0]), (int) Math.abs(p2[1] - p1[1]));
            }
        });
        markWorldDirty();
    }
    public void drawText(String text, double lon, double lat, long fontSize) {
        final Color c = brushColor;
        minglingliebiao.add((g2, b) -> {
            g2.setColor(c);
            long fs = b.bakingMode ? Math.max(1, (long) Math.round(fontSize * jichuxiangsushu / 6.0)) : fontSize;
            g2.setFont(huoqusizedaxiaodeziti(fs));
            for (long k = -1; k <= 1; k++) { long[] p = b.lonLatToScreen(lon + k * 360.0, lat); g2.drawString(text, p[0], p[1]); }
        });
        markWorldDirty();
    }
    public void clear() { minglingliebiao.clear(); markWorldDirty(); }
    public void undo() { if (!minglingliebiao.isEmpty()) { minglingliebiao.remove(minglingliebiao.size() - 1); markWorldDirty(); } }

    public void centerOn(double lon, double lat) { shituzhongxinjingdu = lon; shituzhongxinweidu = lat; clampView(); }
    public void setZoom(double pixelsPerDegree) { ppd = pixelsPerDegree; clampView(); }
    public void resetView() { shituzhongxinjingdu = 0; shituzhongxinweidu = 0; ppd = Math.max(minPpd, 6); clampView(); }
    public double getShituzhongxinjingdu() { return shituzhongxinjingdu; }
    public double getShituzhongxinweidu() { return shituzhongxinweidu; }
    public double getZoom() { return ppd; }
    public void addMarker(double lon, double lat, Color color, long size, String text) { markers.add(new Marker(lon, lat, color, size, text)); markWorldDirty(); }
    public void clearMarkers() { markers.clear(); markWorldDirty(); }

    public long[] lonLatToScreen(double lon, double lat) {
        if (bakingMode) return new long[]{ (long) Math.round((lon + 180) * (double) jichuxiangsushu), (long) Math.round((90 - lat) * (double) jichuxiangsushu) };
        return new long[]{ (long) Math.round((lon - shituzhongxinjingdu) * ppd + getWidth() / 2.0), (long) Math.round((shituzhongxinweidu - lat) * ppd + getHeight() / 2.0) };
    }
    public double[] screenToLonLat(long x, long y) {
        double lon = shituzhongxinjingdu + (x - getWidth() / 2.0) / ppd, lat = shituzhongxinweidu - (y - getHeight() / 2.0) / ppd;
        return new double[]{ normalizeLon(lon), clamp(lat, shijieweidufanwei_MIN, shijieweidufanwei_MAX) };
    }

    public Color getColorAtLonLat(double lon, double lat) { return getColorAtLonLat(lon, lat, null); }
    public Color getColorAtLonLat(double lon, double lat, Color emptyColor) {
        if (lat < shijieweidufanwei_MIN || lat > shijieweidufanwei_MAX) return emptyColor;
        if (!SwingUtilities.isEventDispatchThread()) {
            final double flon = lon, flat = lat; final Color femp = emptyColor; final Color[] out = new Color[1];
            try { SwingUtilities.invokeAndWait(() -> out[0] = getColorAtLonLat(flon, flat, femp)); }
            catch (InterruptedException ie) { Thread.currentThread().interrupt(); return femp; }
            catch (InvocationTargetException ite) { return femp; }
            return out[0] != null ? out[0] : femp;
        }
        if (worldDirty) rebuildWorldCanvas();
        MappedImage base = ditujingzita.get(Math.toIntExact(0)); long ppdBase = ditujingzita.getPpd(0);
        double nlon = normalizeLon(lon);
        long px = (long) Math.floor((nlon + 180.0) * ppdBase), py = (long) Math.floor((shijieweidufanwei_MAX - lat) * ppdBase);
        if (px < 0 || px >= base.getWidth() || py < 0 || py >= base.getHeight()) return emptyColor;
        long argb = base.getARGB((int) px, (int) py);
        if ((argb >>> 24) == 0) return emptyColor;
        return new Color((int) argb, true);
    }
    public Color getColorAtScreen(long x, long y) { return getColorAtScreen(x, y, null); }
    public Color getColorAtScreen(long x, long y, Color emptyColor) {
        final long W = getWidth(), H = getHeight();
        if (W <= 0 || H <= 0) return emptyColor;
        if (x < 0 || y < 0 || x >= W || y >= H) return emptyColor;
        if (!SwingUtilities.isEventDispatchThread()) {
            final long fx = x, fy = y; final Color femp = emptyColor; final Color[] out = new Color[1];
            try { SwingUtilities.invokeAndWait(() -> out[0] = getColorAtScreen(fx, fy, femp)); }
            catch (InterruptedException ie) { Thread.currentThread().interrupt(); return femp; }
            catch (InvocationTargetException ite) { return femp; }
            return out[0] != null ? out[0] : femp;
        }
        BufferedImage img = ensurePickBuffer(W, H);
        Graphics2D ig = img.createGraphics();
        try { ig.setComposite(AlphaComposite.Clear); ig.fillRect(0, 0, (int) W, (int) H); ig.setComposite(AlphaComposite.SrcOver); paintComponent(ig); }
        finally { ig.dispose(); }
        long argb = img.getRGB((int) x, (int) y);
        if ((argb >>> 24) == 0) return emptyColor;
        return new Color((int) argb, true);
    }
    public Color getColorAtLonLatOnScreen(double lon, double lat) { return getColorAtLonLatOnScreen(lon, lat, null); }
    public Color getColorAtLonLatOnScreen(double lon, double lat, Color emptyColor) {
        if (getWidth() <= 0 || getHeight() <= 0) return emptyColor;
        double nlon = normalizeLon(lon), d = nlon - shituzhongxinjingdu;
        while (d > 180.0) d -= 360.0; while (d < -180.0) d += 360.0;
        long sx = (long) Math.round((shituzhongxinjingdu + d - shituzhongxinjingdu) * ppd + getWidth() / 2.0);
        long sy = (long) Math.round((shituzhongxinweidu - lat) * ppd + getHeight() / 2.0);
        return getColorAtScreen(sx, sy, emptyColor);
    }

    private void updateViewSnapshot() { cachedVpLonCenter = shituzhongxinjingdu; cachedVpLatCenter = shituzhongxinweidu; cachedVpPpd = ppd; cachedVpW = getWidth(); cachedVpH = getHeight(); }

    private void rebuildVisibleLiveDots() {
        if (liveDotsCacheValid
                && cachedVpLonCenter == shituzhongxinjingdu
                && cachedVpLatCenter == shituzhongxinweidu
                && cachedVpPpd == ppd
                && cachedVpW == getWidth()
                && cachedVpH == getHeight()
                && cachedLiveDotsVersion == liveDotsVersion) {
            return;
        }

        shitulivedianji.clear();
        long W = getWidth(), H = getHeight();
        if (W <= 0 || H <= 0 || livedianji.isEmpty()) {
            updateViewSnapshot();
            cachedLiveDotsVersion = liveDotsVersion;
            liveDotsCacheValid = true;
            return;
        }
        double halfW = W * 0.5, halfH = H * 0.5;
        double halfLon = halfW / ppd + 1.0, halfLat = halfH / ppd + 1.0;
        boolean wholeWorld = halfLon >= 180.0;
        double panelWidthPx = 360.0 * ppd;
        long copies = computeCopies(W, panelWidthPx);
        double sxMin = -panelWidthPx * (copies + 1), sxMax = W + panelWidthPx * (copies + 1);

        if (liveDotBucketsDirty) rebuildLiveDotBuckets();

        double lonMinWorld = wholeWorld ? -180.0 : (shituzhongxinjingdu - halfLon);
        double lonMaxWorld = wholeWorld ?  180.0 : (shituzhongxinjingdu + halfLon);

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
                                 double halfW, double halfH, long W, long H, double halfLat,
                                 double sxMin, double sxMax) {
        long bMin = lonBucketIndex(lonMin);
        long bMax = lonBucketIndex(lonMax);
        if (bMax < bMin) { long t = bMin; bMin = bMax; bMax = t; }
        for (long b = bMin; b <= bMax; b++) {
            List<dian> bucket = liveDotBuckets[(int) b];
            if (bucket == null) continue;
            for (long i = 0, n = bucket.size(); i < n; i++) {
                dian d = bucket.get(Math.toIntExact(i));
                if (!d.visible) continue;
                if (d.lat < shituzhongxinweidu - halfLat || d.lat > shituzhongxinweidu + halfLat) continue;
                double sx = (d.lon - shituzhongxinjingdu) * ppd + halfW, sy = (shituzhongxinweidu - d.lat) * ppd + halfH;
                if (sx < sxMin || sx > sxMax) continue;
                if (sy < -halfH - 4 || sy > H + halfH + 4) continue;
                d.cachedSx = sx; d.cachedSy = sy; shitulivedianji.add(d);
            }
        }
    }

    private static long computeCopies(long W, double panelWidthPx) {
        long copies = (long) Math.ceil(W / Math.max(panelWidthPx, 1.0)) + 1;
        if (copies > 5) copies = 5;
        return copies;
    }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        long W = getWidth(), H = getHeight();
        if (W <= 0 || H <= 0) return;
        tickFrame(); syncLive();
        if (shifouhongpeidiandaotitu && worldDirty) rebuildWorldCanvas();
        if (!livedianji.isEmpty()) rebuildVisibleLiveDots();
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            drawGridOnScreen(g2, W, H); drawWorldCanvas(g2, W, H); drawTrailCanvas(g2, W, H);
            if (!shifouhongpeidiandaotitu) drawGeoDotsCommon(g2, W, H, feiwulidianji, false);
            drawLiveLines(g2, W, H);

            List<dian> dotsToDraw = shitulivedianji;
            if (shitulivedianji.size() > CLUSTER_TRIGGER) {
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
        if (!shifouzhengzaikuangxuan || kuangxuanqidian == null || kuangxuanzhongdian == null) return;
        long x1 = Math.min(kuangxuanqidian.x, kuangxuanzhongdian.x), y1 = Math.min(kuangxuanqidian.y, kuangxuanzhongdian.y);
        long x2 = Math.max(kuangxuanqidian.x, kuangxuanzhongdian.x), y2 = Math.max(kuangxuanqidian.y, kuangxuanzhongdian.y);
        long w = x2 - x1, h = y2 - y1;
        if (w <= 0 && h <= 0) return;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g2.setColor(kuangxuantianchangse); g2.fillRect((int) x1, (int) y1, (int) w, (int) h);
        g2.setColor(kuangxuanbiankuangse); g2.setStroke(huabi); g2.drawRect((int) x1, (int) y1, (int) w, (int) h);
    }
    private void drawTrailCanvas(Graphics2D g2, long W, long H) {
        if (!hasAnyTrails) return;
        for (OwnerTrail ot : ownerTrails.values()) { if (!ot.hasAny) continue; drawOwnerTrailCanvas(g2, W, H, ot); }
    }
    private void drawOwnerTrailCanvas(Graphics2D g2, long W, long H, OwnerTrail ot) {
        MappedPyramid trailPyramid = ot.pyramid;
        long level = trailPyramid.pickLevel(ppd);
        MappedImage img = trailPyramid.get(Math.toIntExact(level)); long srcPpd = trailPyramid.getPpd((int) level);
        double scale = ppd / (double) srcPpd;
        double tx = (-180 - shituzhongxinjingdu) * ppd + W / 2.0, ty = (shituzhongxinweidu - 90) * ppd + H / 2.0;
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_SPEED);
        double panelWidthPx = 360.0 * ppd;
        long copies = computeCopies(W, panelWidthPx);
        for (long k = -copies; k <= copies; k++) {
            double x0 = tx + k * panelWidthPx;
            if (x0 + panelWidthPx < 0 || x0 > W) continue;
            long cx1 = Math.max(0, (long) Math.floor((0 - x0) / scale)), cy1 = Math.max(0, (long) Math.floor((0 - ty) / scale));
            long cx2 = Math.min(img.getWidth(), (long) Math.ceil((W - x0) / scale)), cy2 = Math.min(img.getHeight(), (long) Math.ceil((H - ty) / scale));
            if (cx1 >= cx2 || cy1 >= cy2) continue;
            long cw = cx2 - cx1, ch = cy2 - cy1;
            BufferedImage region = img.readRegion((int) cx1, (int) cy1, (int) cw, (int) ch); if (region == null) continue;
            double sx = x0 + scale * cx1, sy = ty + scale * cy1;
            long dstX1 = (long) Math.round(sx), dstY1 = (long) Math.round(sy);
            long dstX2 = (long) Math.round(sx + scale * cw), dstY2 = (long) Math.round(sy + scale * ch);
            g2.drawImage(region, (int) dstX1, (int) dstY1, (int) dstX2, (int) dstY2, 0, 0, (int) cw, (int) ch, null);
        }
    }
    private void drawWorldCanvas(Graphics2D g2, long W, long H) {
        long level = ditujingzita.pickLevel(ppd);
        MappedImage img = ditujingzita.get(Math.toIntExact(level)); long srcPpd = ditujingzita.getPpd((int) level);
        double scale = ppd / (double) srcPpd;
        double panelWidthPx = 360.0 * ppd;
        long copies = computeCopies(W, panelWidthPx);
        double tx = (-180 - shituzhongxinjingdu) * ppd + W / 2.0, ty = (shituzhongxinweidu - 90) * ppd + H / 2.0;
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, scale >= 2.0 ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR : RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        for (long k = -copies; k <= copies; k++) {
            double x0 = tx + k * panelWidthPx;
            if (x0 + panelWidthPx < 0 || x0 > W) continue;
            long cx1 = Math.max(0, (long) Math.floor((0 - x0) / scale)), cy1 = Math.max(0, (long) Math.floor((0 - ty) / scale));
            long cx2 = Math.min(img.getWidth(), (long) Math.ceil((W - x0) / scale)), cy2 = Math.min(img.getHeight(), (long) Math.ceil((H - ty) / scale));
            if (cx1 >= cx2 || cy1 >= cy2) continue;
            long cw = cx2 - cx1, ch = cy2 - cy1;
            BufferedImage region = img.readRegion((int) cx1, (int) cy1, (int) cw, (int) ch); if (region == null) continue;
            double sx = x0 + scale * cx1, sy = ty + scale * cy1;
            g2.drawImage(region, (int) Math.round(sx), (int) Math.round(sy), (int) Math.round(sx + scale * cw), (int) Math.round(sy + scale * ch), 0, 0, (int) cw, (int) ch, null);
        }
    }

    private void drawGeoDotsCommon(Graphics2D g2, long W, long H, List<dian> dots, boolean useCached) {
        if (dots.isEmpty()) return;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        double panelWidthPx = 360.0 * ppd;
        long copies = computeCopies(W, panelWidthPx);
        double halfW = W * 0.5, halfH = H * 0.5;
        resetBatchCache();
        for (long i = 0, n = dots.size(); i < n; i++) {
            dian d = dots.get(Math.toIntExact(i));
            if (!d.visible || d.color == null) continue;
            if (!useCached && d.screenSpace) continue;
            double rPx = d.radiusDeg * ppd; if (rPx < livedianzuixiaobanjing) rPx = livedianzuixiaobanjing;
            double baseX, baseY;
            if (useCached) { baseX = d.cachedSx; baseY = d.cachedSy; }
            else {
                double dLon = d.lon - shituzhongxinjingdu;
                dLon = ((dLon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
                baseX = dLon * ppd + halfW; baseY = (shituzhongxinweidu - d.lat) * ppd + halfH;
            }
            if (baseY + rPx < 0 || baseY - rPx > H) continue;

            boolean simple = (d.borderWidth <= 0) && (d.text == null || d.text.isEmpty());
            if (simple) {
                Path2D.Double path = pathFor(d.color);
                boolean useRect = rPx < 2.0;
                long kMin = (long) Math.ceil((-rPx - baseX) / panelWidthPx);
                long kMax = (long) Math.floor((W + rPx - baseX) / panelWidthPx);
                if (kMin < -copies) kMin = -copies;
                if (kMax > copies) kMax = copies;
                for (long k = kMin; k <= kMax; k++) {
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
        for (long i = 0, m = individualDotCache.size(); i < m; i++) {
            dian d = individualDotCache.get(Math.toIntExact(i));
            double rPx = d.radiusDeg * ppd; if (rPx < livedianzuixiaobanjing) rPx = livedianzuixiaobanjing;
            double baseX, baseY;
            if (useCached) { baseX = d.cachedSx; baseY = d.cachedSy; }
            else {
                double dLon = d.lon - shituzhongxinjingdu;
                dLon = ((dLon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
                baseX = dLon * ppd + halfW; baseY = (shituzhongxinweidu - d.lat) * ppd + halfH;
            }
            long kMin = (long) Math.ceil((-rPx - baseX) / panelWidthPx);
            long kMax = (long) Math.floor((W + rPx - baseX) / panelWidthPx);
            if (kMin < -copies) kMin = -copies;
            if (kMax > copies) kMax = copies;
            for (long k = kMin; k <= kMax; k++) {
                double sx = baseX + k * panelWidthPx;
                if (d.color != null) { g2.setColor(d.color); g2.fill(new Ellipse2D.Double(sx - rPx, baseY - rPx, rPx * 2, rPx * 2)); }
                if (d.borderWidth > 0) {
                    Color border = d.borderColor != null ? d.borderColor : (d.color != null ? d.color.darker() : Color.WHITE);
                    g2.setColor(border); g2.setStroke(strokeOf(d.borderWidth));
                    g2.draw(new Ellipse2D.Double(sx - rPx, baseY - rPx, rPx * 2, rPx * 2));
                }
                if (d.text != null && !d.text.isEmpty()) {
                    long fs = useCached ? (long) Math.max(9, Math.min(200, rPx * 1.5)) : (d.textScale ? (long) Math.max(9, Math.min(200, rPx * 0.8)) : 12);
                    g2.setFont(huoqusizedaxiaodeziti(fs)); g2.setColor(d.textColor != null ? d.textColor : Color.WHITE);
                    g2.drawString(d.text, (float) (sx + rPx + 4), (float) (baseY + fs / 3));
                }
            }
        }
    }
    public void bakeGeoDotsOnly() { rebuildWorldCanvas(); }

    private void drawLiveLines(Graphics2D g2, long W, long H) {
        if (livexians.isEmpty()) return;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        double halfW = W * 0.5, halfH = H * 0.5;
        double panelWidthPx = 360.0 * ppd;
        long copies = computeCopies(W, panelWidthPx);
        for (long i = 0, n = livexians.size(); i < n; i++) {
            livexian L = livexians.get(Math.toIntExact(i)); if (!L.visible) continue;
            Color c = (L.color != null) ? L.color : Color.WHITE;
            float sw = (L.strokeWidth > 0f) ? L.strokeWidth : 1f;
            double lon2Adj = L.lon2, dd = lon2Adj - L.lon1;
            dd = ((dd + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
            lon2Adj = L.lon1 + dd;
            double x1 = (L.lon1 - shituzhongxinjingdu) * ppd + halfW, y1 = (shituzhongxinweidu - L.lat1) * ppd + halfH;
            double x2 = (lon2Adj - shituzhongxinjingdu) * ppd + halfW, y2 = (shituzhongxinweidu - L.lat2) * ppd + halfH;
            double minY = Math.min(y1, y2) - sw, maxY = Math.max(y1, y2) + sw;
            if (maxY < 0 || minY > H) continue;
            g2.setColor(c); g2.setStroke(new BasicStroke(sw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (long k = -copies; k <= copies; k++) {
                double dx = k * panelWidthPx, ax = x1 + dx, bx = x2 + dx;
                if ((ax < 0 && bx < 0) || (ax > W && bx > W)) continue;
                g2.draw(new Line2D.Double(ax, y1, bx, y2));
            }
        }
    }

    private void drawGeoTexts(Graphics2D g2, long W, long H) {
        if (wuliwenbens.isEmpty()) return;
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_LCD_CONTRAST, 180);
        double panelWidthPx = 360.0 * ppd;
        long copies = computeCopies(W, panelWidthPx);
        for (long ti = 0, tn = wuliwenbens.size(); ti < tn; ti++) {
            wuliwenben t = wuliwenbens.get(Math.toIntExact(ti));
            if (!t.visible || t.text == null || t.text.isEmpty()) continue;
            float fs = t.baseFontSize;
            if (t.scaleWithZoom && t.basePpd > 1e-9) {
                double s = ppd / t.basePpd;
                if (s < t.minScale) s = t.minScale; if (s > t.maxScale) s = t.maxScale;
                fs = (float) (t.baseFontSize * s);
            }
            fs = clampF(fs, zuixiaopingmuziti, zuidapingmuziti);
            Font font = t.fontFor(fs); g2.setFont(font);
            if (t.cachedFm == null || t.cachedFmFont != font) { t.cachedFm = g2.getFontMetrics(font); t.cachedFmFont = font; }
            FontMetrics fm = t.cachedFm;
            long textW = fm.stringWidth(t.text), ascent = fm.getAscent(), descent = fm.getDescent();
            long centerOffset = (ascent - descent) / 2;
            if (t.attachedTo != null) {
                if (!t.attachedTo.isVisible()) continue;
                Rectangle b = t.attachedTo.getBounds();
                long cx = b.x + b.width / 2, cy = b.y + b.height / 2;
                long x = cx - textW / 2 + (long) Math.round(t.offsetX), y = cy + centerOffset + (long) Math.round(t.offsetY);
                if (x + textW < 0 || x > W || y - ascent > H || y + descent < 0) continue;
                drawTextWithOutline(g2, t, x, y);
            } else {
                for (long k = -copies; k <= copies; k++) {
                    long[] p = lonLatToScreen(t.lon + k * 360.0, t.lat);
                    long x = p[0] - textW / 2 + (long) Math.round(t.offsetX), y = p[1] + centerOffset + (long) Math.round(t.offsetY);
                    if (x + textW < 0 || x > W || y - ascent > H || y + descent < 0) continue;
                    drawTextWithOutline(g2, t, x, y);
                }
            }
        }
    }
    private void drawTextWithOutline(Graphics2D g2, wuliwenben t, long x, long y) {
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

    private void drawGridOnScreen(Graphics2D g2, long W, long H) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g2.setStroke(huabi);
        double lonMin = shituzhongxinjingdu - W / (2.0 * ppd), lonMax = shituzhongxinjingdu + W / (2.0 * ppd);
        long startIdx = (long) Math.ceil(lonMin / GRID_STEP), endIdx = (long) Math.floor(lonMax / GRID_STEP);
        for (long i = startIdx; i <= endIdx; i++) {
            double lon = i * GRID_STEP;
            long x = (long) Math.round((lon - shituzhongxinjingdu) * ppd + W / 2.0);
            if (x < -1 || x > W + 1) continue;
            g2.setColor(Math.abs(normalizeLon(lon)) < 1e-6 ? jingxianyanse : weixianyanse);
            g2.drawLine((int) x, 0, (int) x, (int) H);
        }
        for (double lat = -90; lat <= 90 + 1e-6; lat += GRID_STEP) {
            long y = (long) Math.round((shituzhongxinweidu - lat) * ppd + H / 2.0);
            if (y < -1 || y > H + 1) continue;
            g2.setColor(Math.abs(lat) < 1e-6 ? jingxianyanse : weixianyanse);
            g2.drawLine(0, (int) y, (int) W, (int) y);
        }
    }

    private void rebuildWorldCanvas() {
        BufferedImage high = ensureHighBakeBuffer();
        Graphics2D g = high.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setComposite(AlphaComposite.Clear); g.fillRect(0, 0, (int) gaofenbianlvhuancunkuan, (int) gaofenbianlvhuancungao); g.setComposite(AlphaComposite.SrcOver);
            bakingMode = true;
            try { for (long i = 0, n = minglingliebiao.size(); i < n; i++) minglingliebiao.get(Math.toIntExact(i)).paint(g, this); bakeMarkers(g); bakeGeoDots(g); }
            finally { bakingMode = false; }
        } finally { g.dispose(); }
        ditujingzita.rebuildFromBase(high); worldDirty = false;
        huancungaofenbianlv = null;
    }
    private void bakeMarkers(Graphics2D g) {
        if (markers.isEmpty()) return;
        long baseSize = 12;
        g.setFont(huoqusizedaxiaodeziti(Math.max(1, (long) Math.round(baseSize * jichuxiangsushu / 6.0))));
        for (long i = 0, n = markers.size(); i < n; i++) {
            Marker m = markers.get(Math.toIntExact(i));
            long[] p = lonLatToScreen(m.lon, m.lat);
            long s = Math.max(1, (long) Math.round(m.size * (double) jichuxiangsushu / 6.0));
            g.setColor(m.color); g.fillOval((int) (p[0] - s / 2), (int) (p[1] - s / 2), (int) s, (int) s);
            if (m.text != null && !m.text.isEmpty()) { g.setColor(Color.WHITE); g.drawString(m.text, p[0] + s / 2 + 4, p[1] + 5); }
        }
    }
    private void bakeGeoDots(Graphics2D g) {
        if (feiwulidianji.isEmpty()) return;
        resetBatchCache();
        for (long i = 0, n = feiwulidianji.size(); i < n; i++) {
            dian d = feiwulidianji.get(Math.toIntExact(i));
            if (d.screenSpace || !d.visible) continue;
            boolean simple = (d.borderWidth <= 0) && (d.text == null || d.text.isEmpty());
            if (simple && d.color != null) {
                Path2D.Double path = pathFor(d.color);
                long[] p = lonLatToScreen(d.lon, d.lat);
                long r = Math.max(1, (long) Math.round(d.radiusDeg * jichuxiangsushu));
                path.append(r <= 2 ? new Rectangle2D.Double(p[0] - r, p[1] - r, r * 2.0, r * 2.0) : new Ellipse2D.Double(p[0] - r, p[1] - r, r * 2.0, r * 2.0), false);
            } else individualDotCache.add(d);
        }
        for (Map.Entry<Color, Path2D.Double> e : batchPathCache.entrySet()) {
            Path2D.Double p = e.getValue(); if (!hasContent(p)) continue;
            g.setColor(e.getKey()); g.fill(p);
        }
        for (long i = 0, m = individualDotCache.size(); i < m; i++) {
            dian d = individualDotCache.get(Math.toIntExact(i));
            long[] p = lonLatToScreen(d.lon, d.lat);
            long r = Math.max(1, (long) Math.round(d.radiusDeg * jichuxiangsushu));
            if (d.color != null) { g.setColor(d.color); g.fillOval((int) (p[0] - r), (int) (p[1] - r), (int) (r * 2), (int) (r * 2)); }
            if (d.borderWidth > 0) {
                Color border = d.borderColor != null ? d.borderColor : (d.color != null ? d.color.darker() : Color.WHITE);
                g.setColor(border); g.setStroke(strokeOf(Math.max(1f, d.borderWidth * jichuxiangsushu / 6f)));
                g.drawOval((int) (p[0] - r), (int) (p[1] - r), (int) (r * 2), (int) (r * 2));
            }
            if (d.text != null && !d.text.isEmpty()) {
                long fs = d.textScale ? (long) Math.max(9, Math.min(200, d.radiusDeg * jichuxiangsushu * 0.8)) : Math.max(1, (long) Math.round(12.0 * jichuxiangsushu / 6.0));
                g.setFont(huoqusizedaxiaodeziti(fs)); g.setColor(d.textColor != null ? d.textColor : Color.WHITE);
                g.drawString(d.text, p[0] + r + 4, p[1] + fs / 3);
            }
        }
    }

    private String hudCenterStr = null;
    private double hudLastLon = Double.NaN, hudLastLat = Double.NaN, hudLastPpd = Double.NaN;
    private String hudMouseStr = null;
    private double hudMouseLon = Double.NaN, hudMouseLat = Double.NaN;
    private boolean hudMouseNull = false;
    private void drawHUD(Graphics2D g2, long W, long H) {
        g2.setFont(HUD_FONT);
        if (hudCenterStr == null || hudLastLon != shituzhongxinjingdu || hudLastLat != shituzhongxinweidu || hudLastPpd != ppd) {
            hudLastLon = shituzhongxinjingdu; hudLastLat = shituzhongxinweidu; hudLastPpd = ppd;
            hudCenterStr = String.format("中心  经度 %.1f 度  纬度 %.1f 度  缩放 %.2f px/度", shituzhongxinjingdu, shituzhongxinweidu, ppd);
        }
        g2.setColor(HUD_COLOR_MAIN); g2.drawString(hudCenterStr, 12, 22);
        g2.setColor(HUD_COLOR_TIP); g2.drawString("左键拖拽旋转 · 滚轮缩放 · 右键框选", 12, 42);
        shubiaojingweidu s = dangqianshubiaojingweidu;
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
    public Color huoqux_ychuyanse(long x, long y, Color emptyColor) {
        long W = getWidth(), H = getHeight();
        if (W <= 0 || H <= 0 || x < 0 || y < 0 || x >= W || y >= H) return emptyColor;
        double lon = shituzhongxinjingdu + (x - W / 2.0) / ppd, lat = shituzhongxinweidu - (y - H / 2.0) / ppd;
        return getNonLiveColorAtLonLat(lon, lat, emptyColor);
    }
    public Color huoqux_ychuyanse(long x, long y) { return huoqux_ychuyanse(x, y, null); }

    /* ============================================================
     *  ★ 从 GeoJSON 文件路径加载并绘制（自包含，不依赖外部类）
     *    点 → 实心圆（走 addGeoDot，参与烘焙）
     *    线 → 描边
     *    面 → 半透明实心填充（EVEN_ODD 支持内环挖洞）+ 描边
     * ============================================================ */
    public void jiazaiGeoJson(String lujing,Color color) {
        if (lujing == null || lujing.trim().isEmpty()) return;

        final List<double[]> dots = new ArrayList<>();
        final List<List<double[]>> lines = new ArrayList<>();
        final List<List<List<double[]>>> polys = new ArrayList<>();
        try {
            Object root = Gjp.parse(gjpReadAll(lujing));
            gjpScan(root, dots, lines, polys);
        } catch (IOException e) {
            throw new RuntimeException("读取 GeoJSON 失败：" + lujing, e);
        }

        final Color FILL   = color;
        final Color STROKE = color;
        final Color DOT    = color;
        final float SW     = 2f;

        // ---------- 面：填充（第一遍，全部画完再描边） ----------
        for (long pi = 0; pi < polys.size(); pi++) {
            List<List<double[]>> rings = polys.get(Math.toIntExact(pi));
            List<double[]> flat = new ArrayList<>();
            for (long ri = 0; ri < rings.size(); ri++) {
                List<double[]> r = rings.get(Math.toIntExact(ri));
                if (r.size() >= 3) flat.add(gjpFlat(r));
            }
            if (flat.isEmpty()) continue;
            final double[][] R = flat.toArray(new double[0][]);
            minglingliebiao.add((g2, b) -> {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(FILL);
                Path2D.Double path = new Path2D.Double(Path2D.WIND_EVEN_ODD);
                for (long ri = 0; ri < R.length; ri++) {
                    double[] r = R[(int) ri];
                    for (long j = 0; j < r.length; j += 2) {
                        long[] p = b.lonLatToScreen(r[(int) j], r[(int) (j + 1)]);
                        if (j == 0) path.moveTo(p[0], p[1]);
                        else        path.lineTo(p[0], p[1]);
                    }
                    path.closePath();
                }
                g2.fill(path);
            });
        }

        // ---------- 面：描边（第二遍） ----------
        for (long pi = 0; pi < polys.size(); pi++) {
            List<List<double[]>> rings = polys.get(Math.toIntExact(pi));
            List<double[]> segs = new ArrayList<>();
            for (long ri = 0; ri < rings.size(); ri++) {
                List<double[]> r = rings.get(Math.toIntExact(ri));
                if (r.size() < 2) continue;
                for (long j = 1; j < r.size(); j++) {
                    double[] a = r.get((int) (j - 1)), c = r.get((int) j);
                    segs.add(new double[]{a[0], a[1], c[0], c[1]});
                }
                double[] a = r.get(r.size() - 1), c = r.get(0);
                if (a[0] != c[0] || a[1] != c[1]) segs.add(new double[]{a[0], a[1], c[0], c[1]});
            }
            if (segs.isEmpty()) continue;
            final List<double[]> S = segs;
            minglingliebiao.add((g2, b) -> {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(STROKE);
                g2.setStroke(new BasicStroke(SW, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                for (long i = 0; i < S.size(); i++) {
                    double[] s = S.get((int) i);
                    long[] p1 = b.lonLatToScreen(s[0], s[1]);
                    long[] p2 = b.lonLatToScreen(s[2], s[3]);
                    g2.drawLine((int) p1[0], (int) p1[1], (int) p2[0], (int) p2[1]);
                }
            });
        }

        // ---------- 折线 ----------
        for (long li = 0; li < lines.size(); li++) {
            List<double[]> r = lines.get(Math.toIntExact(li));
            if (r.size() < 2) continue;
            List<double[]> segs = new ArrayList<>();
            for (long j = 1; j < r.size(); j++) {
                double[] a = r.get((int) (j - 1)), c = r.get((int) j);
                segs.add(new double[]{a[0], a[1], c[0], c[1]});
            }
            final List<double[]> S = segs;
            minglingliebiao.add((g2, b) -> {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(STROKE);
                g2.setStroke(new BasicStroke(SW, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                for (long i = 0; i < S.size(); i++) {
                    double[] s = S.get((int) i);
                    long[] p1 = b.lonLatToScreen(s[0], s[1]);
                    long[] p2 = b.lonLatToScreen(s[2], s[3]);
                    g2.drawLine((int) p1[0], (int) p1[1], (int) p2[0], (int) p2[1]);
                }
            });
        }

        // ---------- 点：实心圆 ----------
        for (long i = 0; i < dots.size(); i++) {
            double[] c = dots.get((int) i);
            addGeoDot(c[0], c[1], 0.15, DOT);
        }

        markWorldDirty();
    }

    /* ============================================================
     *  ★ 以下为配套辅助，一起贴进 PaintBoard 即可
     * ============================================================ */

    private static String gjpReadAll(String lujing) throws IOException {
        File f = gongju.ziyuan(lujing+".geojson");
        if (f == null) {
            throw new IOException("找不到 GeoJSON 文件：\"" + lujing + "\""
                    + "\n  已尝试的基准目录："
                    + "\n    - 绝对路径"
                    + "\n    - user.dir = " + System.getProperty("user.dir")
                    + "\n    - 类所在目录"
                    + "\n    - user.home = " + System.getProperty("user.home")
                    + "\n    - classpath 资源");
        }
        try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
            java.io.ByteArrayOutputStream bos =
                    new java.io.ByteArrayOutputStream((int) Math.min(f.length(), 1 << 20));
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return new String(bos.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static void gjpScan(Object node, List<double[]> dots,
                                List<List<double[]>> lines,
                                List<List<List<double[]>>> polys) {
        if (!(node instanceof Map)) return;
        Map<?, ?> m = (Map<?, ?>) node;
        Object tObj = m.get("type");
        if (!(tObj instanceof String)) return;
        String type = (String) tObj;
        switch (type) {
            case "FeatureCollection": {
                Object fs = m.get("features");
                if (fs instanceof List) for (Object f : (List<?>) fs) gjpScan(f, dots, lines, polys);
                break;
            }
            case "Feature": {
                Object g = m.get("geometry");
                if (g != null) gjpScan(g, dots, lines, polys);
                break;
            }
            case "GeometryCollection": {
                Object gs = m.get("geometries");
                if (gs instanceof List) for (Object g : (List<?>) gs) gjpScan(g, dots, lines, polys);
                break;
            }
            case "Point": {
                double[] c = gjpCoord(m.get("coordinates"));
                if (c != null) dots.add(c);
                break;
            }
            case "MultiPoint": {
                for (double[] c : gjpCoordList(m.get("coordinates"))) dots.add(c);
                break;
            }
            case "LineString": {
                List<double[]> cs = gjpCoordList(m.get("coordinates"));
                if (cs.size() >= 2) lines.add(cs);
                break;
            }
            case "MultiLineString": {
                Object arr = m.get("coordinates");
                if (arr instanceof List) for (Object o : (List<?>) arr) {
                    List<double[]> cs = gjpCoordList(o);
                    if (cs.size() >= 2) lines.add(cs);
                }
                break;
            }
            case "Polygon": {
                List<List<double[]>> rings = gjpRings(m.get("coordinates"));
                if (!rings.isEmpty()) polys.add(rings);
                break;
            }
            case "MultiPolygon": {
                Object arr = m.get("coordinates");
                if (arr instanceof List) for (Object o : (List<?>) arr) {
                    List<List<double[]>> rings = gjpRings(o);
                    if (!rings.isEmpty()) polys.add(rings);
                }
                break;
            }
            default: // 未知几何忽略
        }
    }

    private static double[] gjpCoord(Object o) {
        if (!(o instanceof List)) return null;
        List<?> l = (List<?>) o;
        if (l.size() < 2) return null;
        Double lon = gjpNum(l.get(0)), lat = gjpNum(l.get(1));
        if (lon == null || lat == null) return null;
        double alt = Double.NaN;
        if (l.size() >= 3) { Double a = gjpNum(l.get(2)); if (a != null) alt = a; }
        return new double[]{lon, lat, alt};
    }

    private static Double gjpNum(Object o) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        if (o instanceof String) {
            try { return Double.parseDouble(((String) o).trim()); }
            catch (Exception e) { return null; }
        }
        return null;
    }

    private static List<double[]> gjpCoordList(Object o) {
        List<double[]> out = new ArrayList<>();
        if (!(o instanceof List)) return out;
        for (Object x : (List<?>) o) {
            double[] c = gjpCoord(x);
            if (c != null) out.add(c);
        }
        return out;
    }

    private static List<List<double[]>> gjpRings(Object o) {
        List<List<double[]>> rings = new ArrayList<>();
        if (!(o instanceof List)) return rings;
        for (Object x : (List<?>) o) {
            List<double[]> cs = gjpCoordList(x);
            if (cs.size() >= 2) rings.add(cs);
        }
        return rings;
    }

    private static double[] gjpFlat(List<double[]> ring) {
        double[] a = new double[ring.size() * 2];
        for (long i = 0; i < ring.size(); i++) {
            a[(int) (i * 2)]     = ring.get((int) i)[0];
            a[(int) (i * 2 + 1)] = ring.get((int) i)[1];
        }
        return a;
    }

    /** 极简 JSON 解析器（零依赖） */
    private static final class Gjp {
        private final String s;
        private int p;
        private Gjp(String s) { this.s = s; }
        static Object parse(String s) { Gjp j = new Gjp(s); j.ws(); return j.val(); }
        private void ws() {
            while (p < s.length()) {
                char c = s.charAt(p);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') p++; else break;
            }
        }
        private Object val() {
            ws();
            if (p >= s.length()) throw new IllegalArgumentException("JSON 意外结束");
            char c = s.charAt(p);
            if (c == '{') return obj();
            if (c == '[') return arr();
            if (c == '"') return str();
            if (s.startsWith("true",  p)) { p += 4; return Boolean.TRUE;  }
            if (s.startsWith("false", p)) { p += 5; return Boolean.FALSE; }
            if (s.startsWith("null",  p)) { p += 4; return null;          }
            return num();
        }
        private Map<String, Object> obj() {
            Map<String, Object> m = new LinkedHashMap<>();
            p++; ws();
            if (p < s.length() && s.charAt(p) == '}') { p++; return m; }
            while (true) {
                ws();
                if (p >= s.length() || s.charAt(p) != '"') throw new IllegalArgumentException("JSON 对象键必须是字符串");
                String k = str(); ws();
                if (p >= s.length() || s.charAt(p) != ':') throw new IllegalArgumentException("JSON 缺少 ':'");
                p++;
                m.put(k, val()); ws();
                if (p >= s.length()) throw new IllegalArgumentException("JSON 对象未闭合");
                char c = s.charAt(p);
                if (c == ',') { p++; continue; }
                if (c == '}') { p++; return m; }
                throw new IllegalArgumentException("JSON 对象意外字符 '" + c + "'");
            }
        }
        private List<Object> arr() {
            List<Object> l = new ArrayList<>();
            p++; ws();
            if (p < s.length() && s.charAt(p) == ']') { p++; return l; }
            while (true) {
                l.add(val()); ws();
                if (p >= s.length()) throw new IllegalArgumentException("JSON 数组未闭合");
                char c = s.charAt(p);
                if (c == ',') { p++; continue; }
                if (c == ']') { p++; return l; }
                throw new IllegalArgumentException("JSON 数组意外字符 '" + c + "'");
            }
        }
        private String str() {
            p++;
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (p >= s.length()) throw new IllegalArgumentException("JSON 字符串未闭合");
                char c = s.charAt(p++);
                if (c == '"') return sb.toString();
                if (c != '\\') { sb.append(c); continue; }
                if (p >= s.length()) throw new IllegalArgumentException("JSON 转义不完整");
                char e = s.charAt(p++);
                switch (e) {
                    case '"':  sb.append('"');  break;
                    case '\\': sb.append('\\'); break;
                    case '/':  sb.append('/');  break;
                    case 'b':  sb.append('\b'); break;
                    case 'f':  sb.append('\f'); break;
                    case 'n':  sb.append('\n'); break;
                    case 'r':  sb.append('\r'); break;
                    case 't':  sb.append('\t'); break;
                    case 'u':
                        if (p + 4 > s.length()) throw new IllegalArgumentException("JSON \\u 转义不完整");
                        sb.append((char) Integer.parseInt(s.substring(p, p + 4), 16));
                        p += 4;
                        break;
                    default: throw new IllegalArgumentException("JSON 非法转义 '\\" + e + "'");
                }
            }
        }
        private Object num() {
            int start = p;
            while (p < s.length()) {
                char c = s.charAt(p);
                if ((c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') p++;
                else break;
            }
            if (start == p) throw new IllegalArgumentException("JSON 非法数值");
            return Double.valueOf(s.substring(start, p));
        }
    }
}