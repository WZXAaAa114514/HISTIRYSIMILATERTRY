package caozuo;

import neirong.bianliang.bingpai.BINGPAI;
import neirong.bianliang.zuobiao.zuobiao;
import neirong.gongju.bianliang;
import neirong.gongju.yemian.danchu;
import neirong.gongju.yemian.danru;
import neirong.gongju.xuanranqi.PaintBoard;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static neirong.gongju.bianliang.*;

public class bing_caozuo extends JPanel {
    private JButton jButton1;

    public static bing_caozuo caozuodanli = new bing_caozuo();

    // 中键画线专用轨迹 owner（注册到 PaintBoard 后，画线进独立金字塔）
    private static final Object ZHONGJIAN_TRAIL_OWNER = new Object();

    // 长按判定阈值（毫秒）
    private static final long CHANGAN_YUZHI_HAOMIAO = 600L;

    // 分配去重用的屏幕网格大小（像素）
    private static final double FENPEI_WANGLUO_PX = 16.0;

    // ===== 中键拖动 / 拦截状态 =====
    private boolean tuodongKaiqi = false;
    private JieliuDuiLie jieliuDuiLie = null;

    // ===== 点击计数：奇数次开启、偶数次关闭并清空中键线 =====
    private long dianjiCishu = 0L;

    // ===== 画轨迹相关状态 =====
    private double shangciJingdu = Double.NaN;
    private double shangciWeidu  = Double.NaN;
    private int    shenhongChengdu = 0;   // 0 = 纯黄, 100 = 最深红

    // ===== 整条轨迹的点（每点带深红程度 num）=====
    private static class GuijiDian {
        final double lon, lat;
        final int num;
        GuijiDian(double lon, double lat, int num) {
            this.lon = lon; this.lat = lat; this.num = num;
        }
    }
    private final List<GuijiDian> guijiDianList = new ArrayList<>();

    // 上一次拖动留下的轨迹备份（偶数次点击时保存，长按奇数次点击时恢复）
    private final List<GuijiDian> shangciGuijiDianList = new ArrayList<>();

    // 对应 bingmoshuju.danwei = 0.3
    private static final double DANWEI = 0.3;

    // 滚轮每格改变多少「深红程度」
    private static final int SHENHONG_BUJIN = 2;

    // 4 段色阶：黄 → 橙 → 红 → 深红
    private static final Color[] YANSE_STOPS = {
            new Color(255, 255, 0),
            new Color(255, 165, 0),
            new Color(220, 20, 20),
            new Color(139, 0, 0)
    };

    // 轨迹点列表上限（超长时做一次均匀降采样）
    private static final int GUIJI_DIAN_ZUIDA = 2000;

    public bing_caozuo() {
        setLayout(null);
        setPreferredSize(new Dimension(200, 150));

        jButton1 = new JButton();
        jButton1.setText("进攻线");
        jButton1.setBounds(6, 3, 186, 144);
        add(jButton1);

        // 用 MouseListener 代替 ActionListener，才能判断长按
        jButton1.addMouseListener(new MouseAdapter() {
            private long anxiaShijian = 0L;

            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    anxiaShijian = System.currentTimeMillis();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1) return;
                long changdu = System.currentTimeMillis() - anxiaShijian;
                boolean changan = changdu >= CHANGAN_YUZHI_HAOMIAO;

                dianjiCishu++;
                if ((dianjiCishu & 1L) == 1L) {
                    // 奇数次：开启；长按则恢复上次轨迹（保留原色），短按则全新开始
                    kaiqiTuodong(changan);
                } else {
                    // 偶数次：关闭 + 只清掉中键画的线（BINGPAI 轨迹保留）
                    chuliOushuci();
                }
            }
        });
    }

    /** 偶数次点击：关闭拖动并清空中键画的线（BINGPAI 轨迹保留） */
    private void chuliOushuci() {
        guanbiTuodong();
        qingkongZhongjianXianDuan();
    }

    /**
     * 开启：
     * - 注册专用 owner、push 自定义 EventQueue
     * - 清空工作点列表
     * - 若 changan 为 true 且存在上一次轨迹备份，则恢复轨迹（保留原色）
     * - 若短按，则深红程度归零，从纯黄开始
     */
    private void kaiqiTuodong(boolean changan) {
        if (board != null) {
            board.zhuceZhuanYongTrailOwner(ZHONGJIAN_TRAIL_OWNER);
        }
        if (jieliuDuiLie == null) {
            jieliuDuiLie = new JieliuDuiLie();
            Toolkit.getDefaultToolkit().getSystemEventQueue().push(jieliuDuiLie);
        }
        duanKaiGuiji();
        guijiDianList.clear();

        if (changan) {
            huifuShangciGuiji();   // 保留原色 + 恢复 shenhongChengdu
        } else {
            shenhongChengdu = 0;   // 短按：全新开始，纯黄
        }

        tuodongKaiqi = true;
    }

    /** 取消：只关标志位，队列保留（不能 pop 自己） */
    private void guanbiTuodong() {
        duanKaiGuiji();
        tuodongKaiqi = false;
    }

    /**
     * 恢复上一次拖动轨迹（保留原色）：
     * - 每个点用其原始 num 对应的颜色重画
     * - 工作列表同步为原始 num 的点
     * - shenhongChengdu 恢复为上次最后一个点的 num，继续画时颜色衔接
     * - 立即按去重后的颜色分桶重新分配 BINGPAI
     */
    private void huifuShangciGuiji() {
        if (shangciGuijiDianList.isEmpty()) {
            shenhongChengdu = 0;
            return;
        }
        PaintBoard b = board;
        if (b == null) return;

        double zoom = b.getZoom();
        float kuanduXiangsu = (float) Math.max(1.5, DANWEI * 2.0 * zoom);

        double prevLon = Double.NaN, prevLat = Double.NaN;
        for (GuijiDian p : shangciGuijiDianList) {
            guijiDianList.add(new GuijiDian(p.lon, p.lat, p.num));
            if (!Double.isNaN(prevLon) && (prevLon != p.lon || prevLat != p.lat)) {
                Color c = colorFor(Math.max(0, Math.min(100, p.num)) / 100.0);
                b.addTrailSegment(ZHONGJIAN_TRAIL_OWNER,
                        prevLon, prevLat, p.lon, p.lat,
                        c, kuanduXiangsu);
            }
            prevLon = p.lon;
            prevLat = p.lat;
        }

        shenhongChengdu = shangciGuijiDianList.get(shangciGuijiDianList.size() - 1).num;
        fenpeiBingpai();
    }

    /**
     * 只清掉中键画的线：
     * - 先把当前工作列表备份到 shangciGuijiDianList（供下次长按恢复）
     * - 再清空工作列表和专用金字塔
     */
    private void qingkongZhongjianXianDuan() {
        if (!guijiDianList.isEmpty()) {
            shangciGuijiDianList.clear();
            shangciGuijiDianList.addAll(guijiDianList);
        }
        guijiDianList.clear();
        if (board != null) {
            board.qingkongquanbuguiji(ZHONGJIAN_TRAIL_OWNER);
        }
    }

    /** 断开当前段：清空上一次点，防止跨段连线（不清空轨迹列表） */
    private void duanKaiGuiji() {
        shangciJingdu = Double.NaN;
        shangciWeidu  = Double.NaN;
    }

    /** 自定义事件队列：分发前拦截中键拖动与滚轮 */
    private class JieliuDuiLie extends EventQueue {
        @Override
        protected void dispatchEvent(AWTEvent event) {
            if (tuodongKaiqi && jieliu(event)) return;
            super.dispatchEvent(event);
        }
    }

    /** 判断并处理需要独占的事件；返回 true 表示已拦截 */
    private boolean jieliu(AWTEvent event) {
        // 滚轮：累积「深红程度」并独占
        if (event instanceof MouseWheelEvent) {
            int rot = ((MouseWheelEvent) event).getWheelRotation();
            if (rot != 0) {
                shenhongChengdu += rot * SHENHONG_BUJIN;
                if (shenhongChengdu < 0)   shenhongChengdu = 0;
                if (shenhongChengdu > 100) shenhongChengdu = 100;
            }
            return true;
        }

        if (event instanceof MouseEvent) {
            MouseEvent me = (MouseEvent) event;

            // 中键按下：开始新一段（不连线）
            if (me.getID() == MouseEvent.MOUSE_PRESSED
                    && me.getButton() == MouseEvent.BUTTON2) {
                duanKaiGuiji();
                return true;
            }

            // 中键松开：结束当前段（不连线），并补一次分配
            if (me.getID() == MouseEvent.MOUSE_RELEASED
                    && me.getButton() == MouseEvent.BUTTON2) {
                duanKaiGuiji();
                fenpeiBingpai();
                return true;
            }

            // 中键按住拖动：把「深红程度 0-100」传给 huaxian
            if (me.getID() == MouseEvent.MOUSE_DRAGGED
                    && (me.getModifiersEx() & InputEvent.BUTTON2_DOWN_MASK) != 0) {
                Point p = zhuanhuan(me);
                huaxian(p.x, p.y, shenhongChengdu);
                return true;
            }
        }
        return false;
    }

    /** 把任意组件上的鼠标坐标转换为 board 的坐标 */
    private Point zhuanhuan(MouseEvent e) {
        Component c = e.getComponent();
        if (c == null || board == null) return e.getPoint();
        try {
            return SwingUtilities.convertPoint(c, e.getPoint(), board);
        } catch (IllegalArgumentException ex) {
            return e.getPoint();
        }
    }

    /**
     * 中键拖动时绘制轨迹并实时把 BINGPAI 分配到整条轨迹上
     *
     * @param x       board 坐标系下的鼠标 x
     * @param y       board 坐标系下的鼠标 y
     * @param chengdu 深红程度，0 = 纯黄，100 = 最深红
     */
    public void huaxian(double x, double y, int chengdu) {
        PaintBoard b = board;
        if (b == null) return;
        if (b.getWidth() <= 0 || b.getHeight() <= 0) return;

        // 1) 当前鼠标经纬度
        double[] ll = b.screenToLonLat(Math.round(x), Math.round(y));
        double jd = ll[0], wd = ll[1];

        // 2) 颜色由 0-100 深红程度决定
        double t = Math.max(0, Math.min(100, chengdu)) / 100.0;
        Color color = colorFor(t);

        // 3) 轨迹粗细：与 BINGPAI 一致 —— max(1.5, 半径 * 2 * zoom)
        double zoom = b.getZoom();
        float kuanduXiangsu = (float) Math.max(1.5, DANWEI * 2.0 * zoom);

        // 4) 新段起点：只记录、不连线
        if (Double.isNaN(shangciJingdu) || Double.isNaN(shangciWeidu)) {
            shangciJingdu = jd;
            shangciWeidu  = wd;
            jiluGuijiDian(jd, wd, chengdu);
            fenpeiBingpai();
            return;
        }

        if (shangciJingdu == jd && shangciWeidu == wd) return;

        // 5) 用专用 owner 画一段：与 BINGPAI 的轨迹金字塔隔离
        b.addTrailSegment(ZHONGJIAN_TRAIL_OWNER,
                shangciJingdu, shangciWeidu,
                jd, wd,
                color, kuanduXiangsu);

        shangciJingdu = jd;
        shangciWeidu  = wd;

        // 6) 记录轨迹点 + 按颜色加权分配 BINGPAI
        jiluGuijiDian(jd, wd, chengdu);
        fenpeiBingpai();
    }

    /** 记录轨迹点；超长时做一次均匀降采样 */
    private void jiluGuijiDian(double lon, double lat, int num) {
        guijiDianList.add(new GuijiDian(lon, lat, num));
        if (guijiDianList.size() > GUIJI_DIAN_ZUIDA) {
            int step = guijiDianList.size() / (GUIJI_DIAN_ZUIDA / 2);
            List<GuijiDian> jianhua = new ArrayList<>(GUIJI_DIAN_ZUIDA / 2);
            for (int i = 0; i < guijiDianList.size(); i += step) {
                jianhua.add(guijiDianList.get(i));
            }
            guijiDianList.clear();
            guijiDianList.addAll(jianhua);
        }
    }

    /**
     * 按颜色把 BINGPAI 分配到整条轨迹上：
     *  1) 用 (num, 空间网格) 作为去重键（屏幕 16px 一格）：
     *     同一颜色在同一位置重复涂，只算一个代表点；
     *     不同颜色（不同 num）在同一位置仍分别保留。
     *  2) 去重后的点按 num 降序：num 越大（越红）越优先获得 BINGPAI。
     *  3) BINGPAI 数 > 点数时，多余的落到 num 最小的点。
     */
    private void fenpeiBingpai() {
        if (board == null) return;
        if (guijiDianList.isEmpty()) return;

        List<BINGPAI> bpList = huoquXuanzhongBingpai();
        if (bpList == null || bpList.isEmpty()) return;

        double zoom = board.getZoom();
        if (!(zoom > 0)) zoom = 1.0;
        double lonStep = FENPEI_WANGLUO_PX / zoom;
        double latStep = FENPEI_WANGLUO_PX / zoom;

        // 按 num 分桶 + 桶内按空间网格去重
        Map<Integer, Map<Long, GuijiDian>> quchong = new HashMap<>();
        for (GuijiDian p : guijiDianList) {
            long gx = (long) Math.floor(p.lon / lonStep);
            long gy = (long) Math.floor(p.lat / latStep);
            long key = (gx << 32) ^ (gy & 0xFFFFFFFFL);
            quchong.computeIfAbsent(p.num, k -> new HashMap<>()).putIfAbsent(key, p);
        }

        List<GuijiDian> quchongList = new ArrayList<>();
        for (Map<Long, GuijiDian> inner : quchong.values()) {
            quchongList.addAll(inner.values());
        }

        // 按 num 降序：深红优先
        quchongList.sort((a, b) -> Integer.compare(b.num, a.num));

        int m = bpList.size();
        int n = quchongList.size();

        for (int i = 0; i < m; i++) {
            GuijiDian pt = quchongList.get(Math.min(i, n - 1));
            BINGPAI bp = bpList.get(i);
            if (bp == null) continue;
            if (bp.tasktogo == null) {
                bp.tasktogo = new zuobiao(pt.lon, pt.lat);
            } else {
                bp.tasktogo.x = pt.lon;
                bp.tasktogo.y = pt.lat;
            }
        }
    }

    /** 取 xuanding 中选中的 BINGPAI 列表（直接调用 getNeirong()） */
    private static List<BINGPAI> huoquXuanzhongBingpai() {
        if (xuandingbianliang == null) return new ArrayList<>();
        return xuandingbianliang.getNeirong();
    }

    /** 按 t∈[0,1] 在 4 段色阶间插值 */
    private static Color colorFor(double t) {
        if (t <= 0) return YANSE_STOPS[0];
        if (t >= 1) return YANSE_STOPS[YANSE_STOPS.length - 1];
        double pos = t * (YANSE_STOPS.length - 1);
        int i = (int) Math.floor(pos);
        double f = pos - i;
        Color a = YANSE_STOPS[i], c = YANSE_STOPS[i + 1];
        int r  = (int) Math.round(a.getRed()   + (c.getRed()   - a.getRed())   * f);
        int g  = (int) Math.round(a.getGreen() + (c.getGreen() - a.getGreen()) * f);
        int bl = (int) Math.round(a.getBlue()  + (c.getBlue()  - a.getBlue())  * f);
        return new Color(r, g, bl, 255);
    }

    public static void kai() {
        danru.danru(caozuodanli, bianliang.diejiaceng);
    }
    public static void guan() {
        danchu.danchu(caozuodanli, bianliang.diejiaceng);
        caozuodanli.chuliOushuci();
    }
}