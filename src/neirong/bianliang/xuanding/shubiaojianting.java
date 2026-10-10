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
    private static double guifanhuaJingdu(double jingdu) {
        jingdu = jingdu % 360.0;
        if (jingdu >= 180.0) jingdu -= 360.0;
        if (jingdu < -180.0) jingdu += 360.0;
        return jingdu;
    }

    /**
     * 把「经纬度」换算成「当前屏幕坐标」。
     * 与 PaintBoard.lonLatToScreen 的非 baking 分支一致：
     *   x = (lon - lonCenter) * ppd + width  / 2
     *   y = (latCenter - lat) * ppd + height / 2
     * ★ 经度差做环绕归一化，跨 ±180° 时也正确。
     */
    private static double[] jingweiZhuanPingmuZuobiao(double jingdu, double weidu) {
        double jingduZhongxin = board.getShituzhongxinjingdu();
        double weiduZhongxin = board.getShituzhongxinweidu();
        double meiduXiangsu = board.getZoom();
        double bangeKuan = board.getWidth() / 2.0;
        double bangeGao = board.getHeight() / 2.0;

        double dJingdu = guifanhuaJingdu(jingdu - jingduZhongxin);
        double pingmuX = dJingdu * meiduXiangsu + bangeKuan;
        double pingmuY = (weiduZhongxin - weidu) * meiduXiangsu + bangeGao;
        return new double[]{pingmuX, pingmuY};
    }

    public static void kaishijianting() {

        // ============================================================
        // 监听器 1：左键点击 → 取消多选
        // ============================================================
        Toolkit.getDefaultToolkit().addAWTEventListener(shijian -> {
            if (!(shijian instanceof MouseEvent)) return;
            MouseEvent shubiaoShijian = (MouseEvent) shijian;

            if (shubiaoShijian.getID() != MouseEvent.MOUSE_CLICKED) return;
            if (shubiaoShijian.getButton() != MouseEvent.BUTTON1) return;

            if (board.dangqianshubiaojingweidu != null && !xuandingbianliang.neirong.isEmpty()) {
                for (Object duixiang : xuandingbianliang.neirong) {
                    EVENTMAIN.post(new beiquxiao_DUOXUAN((BINGPAI) duixiang));
                }
                xuandingbianliang.neirong.clear();
            }
        }, AWTEvent.MOUSE_EVENT_MASK);

        // ============================================================
        // 监听器 2：右键点击 → 触发多选对象的右键操作
        // ============================================================
        Toolkit.getDefaultToolkit().addAWTEventListener(shijian -> {
            if (!(shijian instanceof MouseEvent)) return;
            MouseEvent shubiaoShijian = (MouseEvent) shijian;

            if (shubiaoShijian.getID() != MouseEvent.MOUSE_CLICKED) return;
            if (!SwingUtilities.isRightMouseButton(shubiaoShijian)) return;

            double[] jingwei = board.shibiaojingweidu();
            if (jingwei == null) return;

            for (Object duixiang : xuandingbianliang.neirong) {
                EVENTMAIN.post(new youjiandianji_DUOXUAN(
                        (BINGPAI) duixiang, jingwei[0], jingwei[1]));
            }

        }, AWTEvent.MOUSE_EVENT_MASK);

        // ============================================================
        // 监听器 3：右键拖拽 → 框选
        // ============================================================
        Toolkit.getDefaultToolkit().addAWTEventListener(shijian -> {
            if (!(shijian instanceof MouseEvent)) return;
            MouseEvent shubiaoShijian = (MouseEvent) shijian;

            switch (shubiaoShijian.getID()) {

                // ① 右键按下 → 开始框选
                case MouseEvent.MOUSE_PRESSED: {
                    if (!SwingUtilities.isRightMouseButton(shubiaoShijian)) break;

                    Component laiyuan = shubiaoShijian.getComponent();
                    if (laiyuan == null) break;

                    Point dian = SwingUtilities.convertPoint(
                            laiyuan, shubiaoShijian.getX(), shubiaoShijian.getY(), board);

                    if (board.contains(dian)) {
                        board.kaishikuangxuan(dian.x, dian.y);
                    }
                    break;
                }

                // ② 拖动 → 更新框
                case MouseEvent.MOUSE_DRAGGED: {
                    if ((shubiaoShijian.getModifiersEx() & MouseEvent.BUTTON3_DOWN_MASK) == 0) break;
                    if (!board.isShifouzhengzaikuangxuan()) break;

                    Component laiyuan = shubiaoShijian.getComponent();
                    if (laiyuan == null) break;

                    Point dian = SwingUtilities.convertPoint(
                            laiyuan, shubiaoShijian.getX(), shubiaoShijian.getY(), board);
                    board.gengxinkuangxuan(dian.x, dian.y);
                    break;
                }

                // ③ 右键松开 → 结束框选
                case MouseEvent.MOUSE_RELEASED: {
                    if (!board.isShifouzhengzaikuangxuan()) break;

                    Rectangle xuanqu = board.jieshukuangxuan();
                    if (xuanqu == null) break;



                    // ============================================================
                    // 打印经纬度范围（仅供调试/显示）
                    // ============================================================
                    double[] zuoshang = board.screenToLonLat(xuanqu.x, xuanqu.y);
                    double[] youxia = board.screenToLonLat(
                            xuanqu.x + xuanqu.width, xuanqu.y + xuanqu.height);



                    // ============================================================
                    // ★ 核心：用「屏幕坐标」判断每个 binpai 是否落在框内
                    // ============================================================
                    xuandingbianliang.clearall();

                    for (country guojia : countries) {
                        for (BINGPAI bingpai : guojia.jundui) {
                            // 把 bingpai 的经纬度换算到当前屏幕上的位置
                            double[] pingmuDian = jingweiZhuanPingmuZuobiao(
                                    bingpai.jingdu, bingpai.weidu);
                            double pingmuX = pingmuDian[0];
                            double pingmuY = pingmuDian[1];

                            // 直接判断屏幕坐标是否在框内
                            if (xuanqu.contains(pingmuX, pingmuY)) {
                                EVENTMAIN.post(new youjiankuangxuan(bingpai));
                            }
                        }
                    }


                    break;
                }
            }

        }, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
    }
}