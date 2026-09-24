package shijianjianting.bianliang.guojia.ditushengchengqi;

import shijianjianting.bianliang.zuobiao.zuobiao;

import java.util.*;
import java.util.stream.IntStream;

public class china {

    public static Vector<zuobiao> COUNTRY;

    private static final double MIN_LON = 73.0;
    private static final double MAX_LON = 135.5;
    private static final double MIN_LAT = 3.0;
    private static final double MAX_LAT = 54.0;

    private static final double[] STEPS = {
            1.0, 0.8, 0.6, 0.5, 0.4, 0.3, 0.2, 0.1, 0.05, 0.02
    };

    public static Vector<zuobiao> generate(int count) {
        if (count <= 0) {
            return new Vector<>();
        }

        LatInfo[] latInfos = null;
        double chosenStep = 0;
        int totalCandidates = 0;
        int latCount = 0;
        int lonCount = 0;

        // 多线程计算每个 step 的候选数量，选择第一个 >= count 的 step
        for (double step : STEPS) {
            latCount = (int) Math.floor((MAX_LAT - MIN_LAT) / step + 1e-9) + 1;
            lonCount = (int) Math.floor((MAX_LON - MIN_LON) / step + 1e-9) + 1;

            final double s = step;
            final int lc = latCount;
            final int loc = lonCount;
            LatInfo[] infos = new LatInfo[lc];

            IntStream.range(0, lc).parallel().forEach(j -> {
                double lat = MIN_LAT + j * s;
                List<double[]> intervals = ChinaBoundary.getLonIntervals(lat);
                List<int[]> idxIntervals = new ArrayList<>();
                int total = 0;

                for (double[] iv : intervals) {
                    int start = (int) Math.ceil((iv[0] - MIN_LON) / s - 1e-9);
                    int end = (int) Math.floor((iv[1] - MIN_LON) / s + 1e-9);

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
            for (LatInfo info : infos) {
                sum += info.count;
            }

            latInfos = infos;
            chosenStep = step;
            totalCandidates = sum;

            if (sum >= count) {
                break;
            }
        }

        if (latInfos == null) {
            return new Vector<>();
        }

        // 前缀和，用于快速定位第 idx 个候选点
        int[] prefix = new int[latCount + 1];
        for (int j = 0; j < latCount; j++) {
            prefix[j + 1] = prefix[j] + latInfos[j].count;
        }

        // 如果所有 step 的候选总数都不足 count，则返回全部候选点
        if (totalCandidates < count) {
            List<zuobiao> list = new ArrayList<>(totalCandidates);
            for (LatInfo info : latInfos) {
                for (int[] iv : info.intervals) {
                    for (int i = iv[0]; i <= iv[1]; i++) {
                        double lon = MIN_LON + i * chosenStep;
                        double lat = info.lat;
                        lon = Math.round(lon * 10000.0) / 10000.0;
                        lat = Math.round(lat * 10000.0) / 10000.0;
                        list.add(new zuobiao(lon, lat));
                    }
                }
            }
            return new Vector<>(list);
        }

        // 并行生成最终 count 个点
        zuobiao[] result = new zuobiao[count];
        final double s = chosenStep;
        final int tc = totalCandidates;
        final LatInfo[] infos = latInfos;
        final int[] pref = prefix;

        if (count == 1) {
            int idx = tc / 2;
            int j = findLatIndex(pref, idx);
            LatInfo info = infos[j];
            int m = idx - pref[j];
            int i = findLonIndex(info.intervals, m);

            double lon = MIN_LON + i * s;
            double lat = info.lat;
            lon = Math.round(lon * 10000.0) / 10000.0;
            lat = Math.round(lat * 10000.0) / 10000.0;
            result[0] = new zuobiao(lon, lat);
        } else {
            IntStream.range(0, count).parallel().forEach(k -> {
                int idx = (int) Math.round(k * (tc - 1.0) / (count - 1.0));
                int j = findLatIndex(pref, idx);
                LatInfo info = infos[j];
                int m = idx - pref[j];
                int i = findLonIndex(info.intervals, m);

                double lon = MIN_LON + i * s;
                double lat = info.lat;
                lon = Math.round(lon * 10000.0) / 10000.0;
                lat = Math.round(lat * 10000.0) / 10000.0;
                result[k] = new zuobiao(lon, lat);
            });
        }

        return new Vector<>(Arrays.asList(result));
    }

    /**
     * 在 prefix 中二分查找 idx 所属的纬度索引。
     * prefix[j] <= idx < prefix[j+1]
     */
    private static int findLatIndex(int[] prefix, int idx) {
        int lo = 0, hi = prefix.length - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (prefix[mid + 1] <= idx) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    /**
     * 在某个纬度的多个经度索引区间中，找到第 m 个（0-based）经度索引。
     */
    private static int findLonIndex(List<int[]> intervals, int m) {
        for (int[] iv : intervals) {
            int len = iv[1] - iv[0] + 1;
            if (m < len) {
                return iv[0] + m;
            }
            m -= len;
        }
        throw new IllegalStateException("索引越界");
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

    private static class LatInfo {
        final int j;
        final double lat;
        final List<int[]> intervals; // 每个 int[]{startLonIdx, endLonIdx}
        final int count;

        LatInfo(int j, double lat, List<int[]> intervals, int count) {
            this.j = j;
            this.lat = lat;
            this.intervals = intervals;
            this.count = count;
        }
    }
}