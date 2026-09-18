package shijianjianting.gongju.ditushengchengqi;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class ChinaBoundary {

    private static final List<double[]> POLYGONS = new ArrayList<>();

    private static final String DEFAULT_URL =
            "china_boundary.geojson";

    private static final String LOCAL_FILE = "china_boundary.geojson";

    static {
        try {
            ObjectMapper mapper = new ObjectMapper();
            File file = new File(LOCAL_FILE);
            JsonNode root;

            if (file.exists()) {
                root = mapper.readTree(file);
            } else {
                root = mapper.readTree(new URL(DEFAULT_URL));
            }

            parse(root);
        } catch (Exception e) {
            throw new RuntimeException(
                    "加载中国边界失败。请下载 china_boundary.geojson 放到项目根目录。", e);
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
                addRing(coords.get(0)); // 外环
            } else if ("MultiPolygon".equals(type)) {
                for (JsonNode polygon : coords) {
                    addRing(polygon.get(0)); // 每个多边形外环
                }
            }
        }
    }

    private static void addRing(JsonNode ring) {
        int n = ring.size();
        double[] poly = new double[n * 2];

        for (int i = 0; i < n; i++) {
            JsonNode p = ring.get(i);
            poly[i * 2] = p.get(0).asDouble();     // lon
            poly[i * 2 + 1] = p.get(1).asDouble(); // lat
        }

        POLYGONS.add(poly);
    }

    public static boolean contains(double lon, double lat) {
        for (double[] poly : POLYGONS) {
            if (pointInPoly(lon, lat, poly)) {
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

    public static List<double[]> getPolygons() {
        return POLYGONS;
    }
}
