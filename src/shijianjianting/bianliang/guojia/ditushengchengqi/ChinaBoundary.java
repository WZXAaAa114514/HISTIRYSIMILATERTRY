package shijianjianting.bianliang.guojia.ditushengchengqi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class ChinaBoundary {

    private static final List<PolygonData> POLYGONS = new ArrayList<>();
    private static final String LOCAL_FILE = "china_boundary.geojson";

    static {
        try {
            ObjectMapper mapper = new ObjectMapper();
            File file = new File(LOCAL_FILE);
            JsonNode root;

            if (file.exists()) {
                root = mapper.readTree(file);
            } else {
                InputStream is = ChinaBoundary.class.getResourceAsStream("/" + LOCAL_FILE);
                if (is == null) {
                    throw new RuntimeException("找不到 " + LOCAL_FILE);
                }
                root = mapper.readTree(is);
            }

            parse(root);
        } catch (Exception e) {
            throw new RuntimeException(
                    "加载中国边界失败。请下载 china_boundary.geojson 放到项目根目录或 classpath。", e);
        }
    }

    private static void parse(JsonNode root) {
        JsonNode features = root.get("features");
        if (features == null) return;

        for (JsonNode feature : features) {
            JsonNode geometry = feature.get("geometry");
            if (geometry == null) continue;

            String type = geometry.get("type").asText();
            JsonNode coords = geometry.get("coordinates");

            if ("Polygon".equals(type)) {
                addPolygon(coords.get(0)); // 外环
            } else if ("MultiPolygon".equals(type)) {
                for (JsonNode polygon : coords) {
                    addPolygon(polygon.get(0)); // 每个多边形外环
                }
            }
        }
    }

    private static void addPolygon(JsonNode ring) {
        int n = ring.size();
        double[] poly = new double[n * 2];
        double minLon = Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;
        double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;

        for (int i = 0; i < n; i++) {
            JsonNode p = ring.get(i);
            double lon = p.get(0).asDouble();
            double lat = p.get(1).asDouble();

            poly[i * 2] = lon;
            poly[i * 2 + 1] = lat;

            if (lon < minLon) minLon = lon;
            if (lon > maxLon) maxLon = lon;
            if (lat < minLat) minLat = lat;
            if (lat > maxLat) maxLat = lat;
        }

        POLYGONS.add(new PolygonData(poly, minLon, maxLon, minLat, maxLat));
    }

    public static boolean contains(double lon, double lat) {
        for (PolygonData p : POLYGONS) {
            if (lon < p.minLon || lon > p.maxLon || lat < p.minLat || lat > p.maxLat) {
                continue;
            }
            if (pointInPoly(lon, lat, p.poly)) {
                return true;
            }
        }
        return false;
    }

    private static boolean pointInPoly(double x, double y, double[] poly) {
        boolean inside = false;
        int n = poly.length / 2;

        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = poly[i * 2], yi = poly[i * 2 + 1];
            double xj = poly[j * 2], yj = poly[j * 2 + 1];

            boolean intersect = ((yi > y) != (yj > y)) &&
                    (x < (xj - xi) * (y - yi) / (yj - yi) + xi);

            if (intersect) {
                inside = !inside;
            }
        }
        return inside;
    }

    /**
     * 返回纬度 lat 上的内部经度区间列表。
     * 每个元素为 double[]{lonStart, lonEnd}。
     * 线程安全，只读静态数据。
     */
    public static List<double[]> getLonIntervals(double lat) {
        List<double[]> allIntervals = new ArrayList<>();

        for (PolygonData p : POLYGONS) {
            if (lat < p.minLat || lat > p.maxLat) {
                continue;
            }

            int n = p.poly.length / 2;
            double[] xs = new double[n];
            int k = 0;

            for (int i = 0, j = n - 1; i < n; j = i++) {
                double xi = p.poly[i * 2], yi = p.poly[i * 2 + 1];
                double xj = p.poly[j * 2], yj = p.poly[j * 2 + 1];

                if ((yi > lat) != (yj > lat)) {
                    double x = xi + (lat - yi) * (xj - xi) / (yj - yi);
                    xs[k++] = x;
                }
            }

            if (k < 2) continue;

            Arrays.sort(xs, 0, k);

            for (int i = 0; i + 1 < k; i += 2) {
                double start = xs[i];
                double end = xs[i + 1];
                if (start > end) {
                    double t = start;
                    start = end;
                    end = t;
                }
                allIntervals.add(new double[]{start, end});
            }
        }

        if (allIntervals.isEmpty()) {
            return allIntervals;
        }

        // 合并重叠区间
        allIntervals.sort(Comparator.comparingDouble(a -> a[0]));

        List<double[]> merged = new ArrayList<>();
        double[] cur = allIntervals.get(0);
        double curStart = cur[0];
        double curEnd = cur[1];

        for (int i = 1; i < allIntervals.size(); i++) {
            double[] iv = allIntervals.get(i);
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

    public static List<double[]> getPolygons() {
        List<double[]> result = new ArrayList<>();
        for (PolygonData p : POLYGONS) {
            result.add(p.poly);
        }
        return result;
    }

    private static class PolygonData {
        final double[] poly;
        final double minLon, maxLon, minLat, maxLat;

        PolygonData(double[] poly, double minLon, double maxLon, double minLat, double maxLat) {
            this.poly = poly;
            this.minLon = minLon;
            this.maxLon = maxLon;
            this.minLat = minLat;
            this.maxLat = maxLat;
        }
    }
}