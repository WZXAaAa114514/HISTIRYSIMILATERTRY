package neirong.bianliang.guojia.ditushengchengqi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import neirong.bianliang.zuobiao.zuobiao;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

/**
 * 通用 GeoJSON 境内点生成工具类。
 *
 * <p>用法：
 * <pre>
 *   // 一行式
 *   Vector&lt;zuobiao&gt; pts = GeoPointGenerator.generate("china_boundary.geojson", 1000);
 *
 *   // 复用实例（带缓存）
 *   GeoPointGenerator gen = GeoPointGenerator.load("china_boundary.geojson");
 *   Vector&lt;zuobiao&gt; a = gen.generate(100);
 *   Vector&lt;zuobiao&gt; b = gen.generate(500);
 * </pre>
 *
 * <p>支持 Polygon / MultiPolygon，包含内环（孔洞）。
 */
public final class huaditudianji {

    /** 基础步长（相对“中国跨度 62.5 度”的比例，保证对中国数据行为不变）。 */
    private static final double[] BASE_STEPS = {
            1.0, 0.8, 0.6, 0.5, 0.4, 0.3, 0.2, 0.1, 0.05, 0.02
    };
    private static final double BASE_SPAN = 62.5;

    private static final Map<String, huaditudianji> CACHE = new ConcurrentHashMap<>();

    private final List<PolygonData> polygons;
    private final double minLon, maxLon, minLat, maxLat;
    private final double[] steps;

    private huaditudianji(List<PolygonData> polygons) {
        this.polygons = Collections.unmodifiableList(polygons);

        double mnLon = Double.MAX_VALUE, mxLon = -Double.MAX_VALUE;
        double mnLat = Double.MAX_VALUE, mxLat = -Double.MAX_VALUE;
        for (PolygonData p : polygons) {
            if (p.minLon < mnLon) mnLon = p.minLon;
            if (p.maxLon > mxLon) mxLon = p.maxLon;
            if (p.minLat < mnLat) mnLat = p.minLat;
            if (p.maxLat > mxLat) mxLat = p.maxLat;
        }
        this.minLon = mnLon;
        this.maxLon = mxLon;
        this.minLat = mnLat;
        this.maxLat = mxLat;

        double maxSpan = Math.max(mxLon - mnLon, mxLat - mnLat);
        double scale = maxSpan / BASE_SPAN;
        this.steps = new double[BASE_STEPS.length];
        for (int i = 0; i < BASE_STEPS.length; i++) {
            steps[i] = BASE_STEPS[i] * scale;
        }
    }

    // ================= 加载 =================

    public static huaditudianji load(String path) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("path 不能为空");
        }
        return CACHE.computeIfAbsent(path, huaditudianji::loadFromPath);
    }

    public static huaditudianji load(File file) {
        if (file == null) throw new IllegalArgumentException("file 不能为空");
        return load(file.getAbsolutePath());
    }

    private static huaditudianji loadFromPath(String path) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            File file = new File(path);
            if (file.isFile()) {
                return parse(mapper.readTree(file));
            }
            String resource = path.startsWith("/") ? path : "/" + path;
            InputStream is = huaditudianji.class.getResourceAsStream(resource);
            if (is == null) {
                throw new RuntimeException("找不到 GeoJSON: " + path);
            }
            try (InputStream in = is) {
                return parse(mapper.readTree(in));
            }
        } catch (IOException e) {
            throw new RuntimeException("加载 GeoJSON 失败: " + path, e);
        }
    }

    /** 清除路径缓存，边界文件被外部修改后调用。 */
    public static void clearCache() {
        CACHE.clear();
    }

    private static huaditudianji parse(JsonNode root) throws IOException {
        JsonNode features = root.get("features");
        if (features == null || !features.isArray()) {
            throw new IOException("GeoJSON 缺少 features 数组");
        }
        List<PolygonData> polygons = new ArrayList<>();
        for (JsonNode feature : features) {
            JsonNode geometry = feature.get("geometry");
            if (geometry == null) continue;
            JsonNode typeNode = geometry.get("type");
            JsonNode coords = geometry.get("coordinates");
            if (typeNode == null || coords == null) continue;

            String type = typeNode.asText();
            if ("Polygon".equals(type)) {
                addPolygon(polygons, coords);
            } else if ("MultiPolygon".equals(type)) {
                for (JsonNode poly : coords) {
                    addPolygon(polygons, poly);
                }
            }
        }
        if (polygons.isEmpty()) {
            throw new IOException("GeoJSON 中未找到 Polygon/MultiPolygon");
        }
        return new huaditudianji(polygons);
    }

    private static void addPolygon(List<PolygonData> list, JsonNode rings) {
        if (rings == null || rings.size() == 0) return;
        double[] outer = ringToArray(rings.get(0));
        List<double[]> holes = new ArrayList<>();
        for (int i = 1; i < rings.size(); i++) {
            holes.add(ringToArray(rings.get(i)));
        }
        list.add(new PolygonData(outer, holes));
    }

    private static double[] ringToArray(JsonNode ring) {
        int n = ring.size();
        double[] arr = new double[n * 2];
        for (int i = 0; i < n; i++) {
            JsonNode p = ring.get(i);
            arr[i * 2] = p.get(0).asDouble();
            arr[i * 2 + 1] = p.get(1).asDouble();
        }
        return arr;
    }

    // ================= 便捷入口 =================

    /**
     * 根据 GeoJSON 路径和目标点数，返回境内经纬度点集。
     */
    public static List<zuobiao> generate(String geojsonPath, int count) {
        return load(geojsonPath).generate(count);
    }

    // ================= 生成 =================

    public List<zuobiao> generate(int count) {
        if (count <= 0) return new ArrayList<>();

        final double baseLon = minLon;
        final double baseLat = minLat;

        LatInfo[] latInfos = null;
        double chosenStep = 0;
        int totalCandidates = 0;
        int latCount = 0;
        int lonCount = 0;

        for (double step : steps) {
            latCount = (int) Math.floor((maxLat - minLat) / step + 1e-9) + 1;
            lonCount = (int) Math.floor((maxLon - minLon) / step + 1e-9) + 1;

            final double s = step;
            final int lc = latCount;
            final int loc = lonCount;
            LatInfo[] infos = new LatInfo[lc];

            IntStream.range(0, lc).parallel().forEach(j -> {
                double lat = baseLat + j * s;
                List<double[]> intervals = getLonIntervals(lat);
                List<int[]> idxIntervals = new ArrayList<>();
                int total = 0;
                for (double[] iv : intervals) {
                    int start = (int) Math.ceil((iv[0] - baseLon) / s - 1e-9);
                    int end = (int) Math.floor((iv[1] - baseLon) / s + 1e-9);
                    if (start < 0) start = 0;
                    if (end >= loc) end = loc - 1;
                    if (start <= end) {
                        idxIntervals.add(new int[]{start, end});
                        total += end - start + 1;
                    }
                }
                infos[j] = new LatInfo(j, lat, idxIntervals, total);
            });

            int sum = 0;
            for (LatInfo info : infos) sum += info.count;

            latInfos = infos;
            chosenStep = step;
            totalCandidates = sum;

            if (sum >= count) break;
        }

        if (latInfos == null || totalCandidates == 0) {
            return new ArrayList<>();
        }

        int[] prefix = new int[latCount + 1];
        for (int j = 0; j < latCount; j++) {
            prefix[j + 1] = prefix[j] + latInfos[j].count;
        }

        // 所有 step 都不够，返回全部候选
        if (totalCandidates < count) {
            List<zuobiao> list = new ArrayList<>(totalCandidates);
            for (LatInfo info : latInfos) {
                for (int[] iv : info.intervals) {
                    for (int i = iv[0]; i <= iv[1]; i++) {
                        list.add(makePoint(baseLon + i * chosenStep, info.lat));
                    }
                }
            }
            return new ArrayList<>(list);
        }

        // 抽取 count 个
        zuobiao[] result = new zuobiao[count];
        final double s = chosenStep;
        final int tc = totalCandidates;
        final LatInfo[] infos = latInfos;
        final int[] pref = prefix;

        if (count == 1) {
            int idx = tc / 2;
            result[0] = pickPoint(idx, pref, infos, s, baseLon);
        } else {
            IntStream.range(0, count).parallel().forEach(k -> {
                int idx = (int) Math.round(k * (tc - 1.0) / (count - 1.0));
                result[k] = pickPoint(idx, pref, infos, s, baseLon);
            });
        }

        return new ArrayList<>(Arrays.asList(result));
    }

    private static zuobiao pickPoint(int idx, int[] pref, LatInfo[] infos,
                                     double step, double baseLon) {
        int j = findLatIndex(pref, idx);
        LatInfo info = infos[j];
        int m = idx - pref[j];
        int i = findLonIndex(info.intervals, m);
        return makePoint(baseLon + i * step, info.lat);
    }

    private static zuobiao makePoint(double lon, double lat) {
        lon = Math.round(lon * 10000.0) / 10000.0;
        lat = Math.round(lat * 10000.0) / 10000.0;
        return new zuobiao(lon, lat);
    }

    private static int findLatIndex(int[] prefix, int idx) {
        int lo = 0, hi = prefix.length - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (prefix[mid + 1] <= idx) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }

    private static int findLonIndex(List<int[]> intervals, int m) {
        for (int[] iv : intervals) {
            int len = iv[1] - iv[0] + 1;
            if (m < len) return iv[0] + m;
            m -= len;
        }
        throw new IllegalStateException("索引越界");
    }

    // ================= 查询 =================

    public boolean contains(double lon, double lat) {
        for (PolygonData p : polygons) {
            if (lon < p.minLon || lon > p.maxLon || lat < p.minLat || lat > p.maxLat) continue;
            if (!pointInPoly(lon, lat, p.outer)) continue;
            boolean inHole = false;
            for (double[] hole : p.holes) {
                if (pointInPoly(lon, lat, hole)) { inHole = true; break; }
            }
            if (!inHole) return true;
        }
        return false;
    }

    public List<double[]> getLonIntervals(double lat) {
        List<double[]> all = new ArrayList<>();
        for (PolygonData p : polygons) {
            if (lat < p.minLat || lat > p.maxLat) continue;
            List<double[]> outerIv = scanIntervals(p.outer, lat);
            if (outerIv.isEmpty()) continue;

            if (p.holes.isEmpty()) {
                all.addAll(outerIv);
            } else {
                List<double[]> holeIv = new ArrayList<>();
                for (double[] hole : p.holes) {
                    holeIv.addAll(scanIntervals(hole, lat));
                }
                all.addAll(subtract(outerIv, holeIv));
            }
        }
        if (all.isEmpty()) return all;

        all.sort(Comparator.comparingDouble(a -> a[0]));
        List<double[]> merged = new ArrayList<>();
        double curStart = all.get(0)[0];
        double curEnd = all.get(0)[1];
        for (int i = 1; i < all.size(); i++) {
            double[] iv = all.get(i);
            if (iv[0] <= curEnd + 1e-9) {
                if (iv[1] > curEnd) curEnd = iv[1];
            } else {
                merged.add(new double[]{curStart, curEnd});
                curStart = iv[0];
                curEnd = iv[1];
            }
        }
        merged.add(new double[]{curStart, curEnd});
        return merged;
    }

    /** 返回每个多边形的外环（顺序保留），便于绘制分组边界。 */
    public List<List<zuobiao>> boundary() {
        List<List<zuobiao>> result = new ArrayList<>(polygons.size());
        for (PolygonData p : polygons) {
            List<zuobiao> ring = new ArrayList<>(p.outer.length / 2);
            for (int i = 0; i < p.outer.length; i += 2) {
                ring.add(new zuobiao(p.outer[i], p.outer[i + 1]));
            }
            result.add(ring);
        }
        return result;
    }

    /** 返回 GeoJSON 的全局包围盒 {minLon, minLat, maxLon, maxLat}。 */
    public double[] bbox() {
        return new double[]{minLon, minLat, maxLon, maxLat};
    }

    // ================= 内部工具 =================

    private static List<double[]> scanIntervals(double[] ring, double lat) {
        int n = ring.length / 2;
        if (n < 2) return Collections.emptyList();
        double[] xs = new double[n];
        int k = 0;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = ring[i * 2], yi = ring[i * 2 + 1];
            double xj = ring[j * 2], yj = ring[j * 2 + 1];
            if ((yi > lat) != (yj > lat)) {
                double x = xi + (lat - yi) * (xj - xi) / (yj - yi);
                xs[k++] = x;
            }
        }
        if (k < 2) return Collections.emptyList();
        Arrays.sort(xs, 0, k);
        List<double[]> result = new ArrayList<>(k / 2);
        for (int i = 0; i + 1 < k; i += 2) {
            double s = xs[i], e = xs[i + 1];
            if (s > e) { double t = s; s = e; e = t; }
            result.add(new double[]{s, e});
        }
        return result;
    }

    private static List<double[]> subtract(List<double[]> base, List<double[]> holes) {
        List<double[]> cur = new ArrayList<>(base);
        for (double[] h : holes) {
            if (cur.isEmpty()) break;
            List<double[]> next = new ArrayList<>();
            for (double[] c : cur) {
                if (h[1] <= c[0] || h[0] >= c[1]) {
                    next.add(c);
                } else {
                    if (h[0] > c[0]) next.add(new double[]{c[0], h[0]});
                    if (h[1] < c[1]) next.add(new double[]{h[1], c[1]});
                }
            }
            cur = next;
        }
        return cur;
    }

    private static boolean pointInPoly(double x, double y, double[] poly) {
        boolean inside = false;
        int n = poly.length / 2;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = poly[i * 2], yi = poly[i * 2 + 1];
            double xj = poly[j * 2], yj = poly[j * 2 + 1];
            boolean intersect = ((yi > y) != (yj > y))
                    && (x < (xj - xi) * (y - yi) / (yj - yi) + xi);
            if (intersect) inside = !inside;
        }
        return inside;
    }

    private static final class PolygonData {
        final double[] outer;
        final List<double[]> holes;
        final double minLon, maxLon, minLat, maxLat;

        PolygonData(double[] outer, List<double[]> holes) {
            this.outer = outer;
            this.holes = holes;
            double mnLon = Double.MAX_VALUE, mxLon = -Double.MAX_VALUE;
            double mnLat = Double.MAX_VALUE, mxLat = -Double.MAX_VALUE;
            for (int i = 0; i < outer.length; i += 2) {
                double lon = outer[i], lat = outer[i + 1];
                if (lon < mnLon) mnLon = lon;
                if (lon > mxLon) mxLon = lon;
                if (lat < mnLat) mnLat = lat;
                if (lat > mxLat) mxLat = lat;
            }
            this.minLon = mnLon;
            this.maxLon = mxLon;
            this.minLat = mnLat;
            this.maxLat = mxLat;
        }
    }

    private static final class LatInfo {
        final int j;
        final double lat;
        final List<int[]> intervals;
        final int count;

        LatInfo(int j, double lat, List<int[]> intervals, int count) {
            this.j = j;
            this.lat = lat;
            this.intervals = intervals;
            this.count = count;
        }
    }
}