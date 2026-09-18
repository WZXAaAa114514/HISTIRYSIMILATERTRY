package shijianjianting.gongju.ditushengchengqi;

import shijianjianting.gongju.zuobiao;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

public class china {
    public static Vector<zuobiao> COUNTRY;

    public static Vector<zuobiao> generate(int count) {
        if (count <= 0) {
            return new Vector<>();
        }

        List<double[]> candidates = new ArrayList<>();
        double[] steps = {1.0, 0.8, 0.6, 0.5, 0.4, 0.3, 0.2, 0.1, 0.05, 0.02};

        for (double step : steps) {
            candidates = generateCandidates(step);
            if (candidates.size() >= count) {
                break;
            }
        }

        Vector<zuobiao> result = new Vector<>(count);

        if (candidates.size() < count) {
            for (double[] p : candidates) {
                result.add(new zuobiao(p[0], p[1]));
            }
            return result;
        }

        candidates.sort((a, b) -> {
            if (Math.abs(a[1] - b[1]) > 1e-9) {
                return Double.compare(a[1], b[1]);
            }
            return Double.compare(a[0], b[0]);
        });

        int n = candidates.size();

        if (count == 1) {
            double[] p = candidates.get(n / 2);
            result.add(new zuobiao(p[0], p[1]));
            return result;
        }

        for (int i = 0; i < count; i++) {
            int idx = (int) Math.round(i * (n - 1.0) / (count - 1.0));
            double[] p = candidates.get(idx);
            result.add(new zuobiao(p[0], p[1]));
        }

        return result;
    }

    /**
     * 直接返回真实边界顶点，用来“围出中国地图”。
     */
    public static Vector<zuobiao> boundary() {
        Vector<zuobiao> result = new Vector<>();

        for (double[] poly : ChinaBoundary.getPolygons()) {
            for (int i = 0; i < poly.length; i += 2) {
                result.add(new zuobiao(poly[i], poly[i + 1]));
            }
        }

        return result;
    }

    private static List<double[]> generateCandidates(double step) {
        List<double[]> pts = new ArrayList<>();

        // 如果要含南海诸岛，纬度从 3.0 开始；
        // 只要大陆+台湾+海南，改成 18.0。
        for (double lon = 73.0; lon <= 135.5; lon += step) {
            for (double lat = 3.0; lat <= 54.0; lat += step) {
                if (ChinaBoundary.contains(lon, lat)) {
                    pts.add(new double[]{
                            Math.round(lon * 10000.0) / 10000.0,
                            Math.round(lat * 10000.0) / 10000.0
                    });
                }
            }
        }

        return pts;
    }
}