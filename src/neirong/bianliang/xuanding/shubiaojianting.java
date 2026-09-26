package neirong.bianliang.xuanding;

import neirong.bianliang.bingpai.BINGPAI;
import neirong.bianliang.guojia.country;
import shunxu.third_shijianpaifaqiqidong.shijian.beiquxiao_DUOXUAN;
import shunxu.third_shijianpaifaqiqidong.shijian.youjiandianji_DUOXUAN;
import shunxu.third_shijianpaifaqiqidong.shijian.youjiankuangxuan;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

import static neirong.gongju.bianliang.*;

public class shubiaojianting {

    /**
     * 把一个经度差归一化到 [-180, 180)。
     * 与 PaintBoard.normalizeLon 保持一致。
     */
    private static double normalizeLon(double lon) {
        lon = lon % 360.0;
        if (lon >= 180.0)  lon -= 360.0;
        if (lon < -180.0) lon += 360.0;
        return lon;
    }

    /**
     * 把「经纬度」换算成「当前屏幕坐标」。
     * 与 PaintBoard.lonLatToScreen 的非 baking 分支一致：
     *   x = (lon - lonCenter) * ppd + width  / 2
     *   y = (latCenter - lat) * ppd + height / 2
     * ★ 经度差做环绕归一化，跨 ±180° 时也正确。
     */
    private static double[] lonLatToScreenPoint(double lon, double lat) {
        double lonCenter = board.getLonCenter();
        double latCenter = board.getLatCenter();
        double ppd       = board.getZoom();
        double halfW     = board.getWidth()  / 2.0;
        double halfH     = board.getHeight() / 2.0;

        double dLon = normalizeLon(lon - lonCenter);
        double sx   = dLon * ppd + halfW;
        double sy   = (latCenter - lat) * ppd + halfH;
        return new double[]{ sx, sy };
    }

    public static void kaishijianting() {

        // ============================================================
        // 监听器 1：左键点击 → 取消多选
        // ============================================================
        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (!(event instanceof MouseEvent)) return;
            MouseEvent me = (MouseEvent) event;

            if (me.getID() != MouseEvent.MOUSE_CLICKED) return;
            if (me.getButton() != MouseEvent.BUTTON1) return;

            if (board.mouseSnap != null && !xuandingbianliang.neirong.isEmpty()) {
                for (Object a : xuandingbianliang.neirong) {
                    EVENTMAIN.post(new beiquxiao_DUOXUAN((BINGPAI) a));
                }
                xuandingbianliang.neirong.clear();
            }
        }, AWTEvent.MOUSE_EVENT_MASK);

        // ============================================================
        // 监听器 2：右键点击 → 触发多选对象的右键操作
        // ============================================================
        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (!(event instanceof MouseEvent)) return;
            MouseEvent me = (MouseEvent) event;

            if (me.getID() != MouseEvent.MOUSE_CLICKED) return;
            if (!SwingUtilities.isRightMouseButton(me)) return;

            double[] ll = board.shibiaojingweidu();
            if (ll == null) return;

            for (Object a : xuandingbianliang.neirong) {
                EVENTMAIN.post(new youjiandianji_DUOXUAN(
                        (BINGPAI) a, ll[0], ll[1]));
            }
            System.out.println("右键点击");
        }, AWTEvent.MOUSE_EVENT_MASK);

        // ============================================================
        // 监听器 3：右键拖拽 → 框选
        // ============================================================
        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (!(event instanceof MouseEvent)) return;
            MouseEvent me = (MouseEvent) event;

            switch (me.getID()) {

                // ① 右键按下 → 开始框选
                case MouseEvent.MOUSE_PRESSED: {
                    if (!SwingUtilities.isRightMouseButton(me)) break;

                    Component src = me.getComponent();
                    if (src == null) break;

                    Point p = SwingUtilities.convertPoint(
                            src, me.getX(), me.getY(), board);

                    if (board.contains(p)) {
                        board.beginSelection(p.x, p.y);
                    }
                    break;
                }

                // ② 拖动 → 更新框
                case MouseEvent.MOUSE_DRAGGED: {
                    if ((me.getModifiersEx() & MouseEvent.BUTTON3_DOWN_MASK) == 0) break;
                    if (!board.isSelectionActive()) break;

                    Component src = me.getComponent();
                    if (src == null) break;

                    Point p = SwingUtilities.convertPoint(
                            src, me.getX(), me.getY(), board);
                    board.updateSelection(p.x, p.y);
                    break;
                }

                // ③ 右键松开 → 结束框选
                case MouseEvent.MOUSE_RELEASED: {
                    if (!board.isSelectionActive()) break;

                    Rectangle sel = board.endSelection();
                    if (sel == null) break;

                    System.out.println("右键框选区域: " + sel);

                    // ============================================================
                    // 打印经纬度范围（仅供调试/显示）
                    // ============================================================
                    double[] tl = board.screenToLonLat(sel.x, sel.y);
                    double[] br = board.screenToLonLat(
                            sel.x + sel.width, sel.y + sel.height);

                    System.out.printf(
                            "经纬度范围: lon[%.3f, %.3f]  lat[%.3f, %.3f]%n",
                            Math.min(tl[0], br[0]), Math.max(tl[0], br[0]),
                            Math.min(tl[1], br[1]), Math.max(tl[1], br[1]));

                    // ============================================================
                    // ★ 核心：用「屏幕坐标」判断每个 binpai 是否落在框内
                    //   不再用经纬度范围 min/max，避免经度环绕（±180°）问题，
                    //   也避免负半轴不生效的问题。
                    // ============================================================
                    xuandingbianliang.clearall();

                    for (country c : countries) {
                        for (BINGPAI binpai : c.jundui) {
                            // 把 binpai 的经纬度换算到当前屏幕上的位置
                            double[] sp = lonLatToScreenPoint(binpai.x, binpai.y);
                            double sx = sp[0];
                            double sy = sp[1];

                            // 直接判断屏幕坐标是否在框内
                            if (sel.contains(sx, sy)) {
                                EVENTMAIN.post(new youjiankuangxuan(binpai));
                            }
                        }
                    }

                    System.out.printf(
                            "经纬度范围: lon[%.3f, %.3f]  lat[%.3f, %.3f]%n",
                            Math.min(tl[0], br[0]), Math.max(tl[0], br[0]),
                            Math.min(tl[1], br[1]), Math.max(tl[1], br[1]));
                    break;
                }
            }

        }, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
    }
}