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
import java.util.List;

import static neirong.gongju.bianliang.*;

public class bing_caozuo extends JPanel {
    private JButton jButton1;

    public static bing_caozuo caozuodanli = new bing_caozuo();

    // 中键画线专用轨迹 owner（注册到 PaintBoard 后，画线进独立金字塔）
    private static final Object ZHONGJIAN_TRAIL_OWNER = new Object();

    // ===== 中键拖动 / 拦截状态 =====
    private boolean tuodongKaiqi = false;
    private JieliuDuiLie jieliuDuiLie = null;

    // ===== 点击计数：奇数次开启、偶数次关闭并清空中键线 =====
    private long dianjiCishu = 0L;

    // ===== 画轨迹相关状态 =====
    private double shangciJingdu = Double.NaN;
    private double shangciWeidu  = Double.NaN;
    private int    shenhongChengdu = 0;

    // ===== 整条轨迹的点（每点带深红程度 num）=====
    private static class GuijiDian {
        final double lon, lat;
        final int num;
        GuijiDian(double lon, double lat, int num) {
            this.lon = lon; this.lat = lat; this.num = num;
        }
    }
    private final List<GuijiDian> guijiDianList = new ArrayList<>();

    private static final double DANWEI = 0.3;
    private static final int    SHENHONG_BUJIN = 2;

    private static final Color[] YANSE_STOPS = {
            new Color(255, 255, 0),
            new Color(255, 165, 0),
            new Color(220, 20, 20),
            new Color(139, 0, 0)
    };

    private static final int GUIJI_DIAN_ZUIDA = 2000;

    public bing_caozuo() {
        setLayout(null);
        setPreferredSize(new Dimension(200, 150));

        jButton1 = new JButton();
        jButton1.setText("进攻线");
        jButton1.setBounds(6, 3, 186, 144);
        add(jButton1);

        jButton1.addActionListener(e -> {
            dianjiCishu++;
            if ((dianjiCishu & 1L) == 1L) {
                kaiqiTuodong();
            } else {
                guanbiTuodong();
                qingkongZhongjianXianDuan();
            }
        });
    }

    /** 开启：注册专用 owner、push 自定义 EventQueue、重置轨迹与深红程度 */
    private void kaiqiTuodong() {
        if (board != null) {
            board.zhuceZhuanYongTrailOwner(ZHONGJIAN_TRAIL_OWNER);
        }
        if (jieliuDuiLie == null) {
            jieliuDuiLie = new JieliuDuiLie();
            Toolkit.getDefaultToolkit().getSystemEventQueue().push(jieliuDuiLie);
        }
        duanKaiGuiji();
        guijiDianList.clear();
        shenhongChengdu = 0;   // 奇数次点击：从纯黄开始
        tuodongKaiqi = true;
    }

    /** 取消：只关标志位，队列保留（不能 pop 自己） */
    private void guanbiTuodong() {
        duanKaiGuiji();
        tuodongKaiqi = false;
    }

    /** 只清掉中键画的线，保留 BINGPAI 的轨迹 */
    private void qingkongZhongjianXianDuan() {
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

    private class JieliuDuiLie extends EventQueue {
        @Override
        protected void dispatchEvent(AWTEvent event) {
            if (tuodongKaiqi && jieliu(event)) return;
            super.dispatchEvent(event);
        }
    }

    private boolean jieliu(AWTEvent event) {
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

            if (me.getID() == MouseEvent.MOUSE_PRESSED
                    && me.getButton() == MouseEvent.BUTTON2) {
                duanKaiGuiji();
                return true;
            }

            if (me.getID() == MouseEvent.MOUSE_RELEASED
                    && me.getButton() == MouseEvent.BUTTON2) {
                duanKaiGuiji();
                fenpeiBingpai();
                return true;
            }

            if (me.getID() == MouseEvent.MOUSE_DRAGGED
                    && (me.getModifiersEx() & InputEvent.BUTTON2_DOWN_MASK) != 0) {
                Point p = zhuanhuan(me);
                huaxian(p.x, p.y, shenhongChengdu);
                return true;
            }
        }
        return false;
    }

    private Point zhuanhuan(MouseEvent e) {
        Component c = e.getComponent();
        if (c == null || board == null) return e.getPoint();
        try {
            return SwingUtilities.convertPoint(c, e.getPoint(), board);
        } catch (IllegalArgumentException ex) {
            return e.getPoint();
        }
    }

    public void huaxian(double x, double y, int chengdu) {
        PaintBoard b = board;
        if (b == null) return;
        if (b.getWidth() <= 0 || b.getHeight() <= 0) return;

        double[] ll = b.screenToLonLat(Math.round(x), Math.round(y));
        double jd = ll[0], wd = ll[1];

        double t = Math.max(0, Math.min(100, chengdu)) / 100.0;
        Color color = colorFor(t);

        double zoom = b.getZoom();
        float kuanduXiangsu = (float) Math.max(1.5, DANWEI * 2.0 * zoom);

        if (Double.isNaN(shangciJingdu) || Double.isNaN(shangciWeidu)) {
            shangciJingdu = jd;
            shangciWeidu  = wd;
            jiluGuijiDian(jd, wd, chengdu);
            fenpeiBingpai();
            return;
        }

        if (shangciJingdu == jd && shangciWeidu == wd) return;

        // 关键：用专用 owner 画线，和其它轨迹（BINGPAI 的）隔离
        b.addTrailSegment(ZHONGJIAN_TRAIL_OWNER,
                shangciJingdu, shangciWeidu,
                jd, wd,
                color, kuanduXiangsu);

        shangciJingdu = jd;
        shangciWeidu  = wd;

        jiluGuijiDian(jd, wd, chengdu);
        fenpeiBingpai();
    }

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

    private void fenpeiBingpai() {
        if (board == null) return;
        if (guijiDianList.isEmpty()) return;

        List<BINGPAI> bpList = huoquXuanzhongBingpai();
        if (bpList == null || bpList.isEmpty()) return;

        List<GuijiDian> sorted = new ArrayList<>(guijiDianList);
        sorted.sort((a, b) -> Integer.compare(b.num, a.num));

        int m = bpList.size();
        int n = sorted.size();

        for (int i = 0; i < m; i++) {
            GuijiDian pt = sorted.get(Math.min(i, n - 1));
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

    private static List<BINGPAI> huoquXuanzhongBingpai() {
        if (xuandingbianliang == null) return new ArrayList<>();
        return xuandingbianliang.getNeirong();
    }

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
    }
}