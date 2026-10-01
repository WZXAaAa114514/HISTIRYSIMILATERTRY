package neirong.gongju;

import neirong.bianliang.zuobiao.zuobiao;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

public class gongju {
    public static boolean shifoushixianjiekou(Class<?> a, Class<?> b) {
        if (a == null || b == null || !b.isInterface()) {
            return false;
        }

        return b.isAssignableFrom(a);
    }
    public static JButton quchuquanbujiantingqi(JButton button){
        for (ActionListener l : button.getActionListeners()) {
            button.removeActionListener(l);
        }
        return button;

    }
    private static boolean shiftDown;
    public static void shiftdownstart(){
        KeyEventDispatcher dispatcher = e -> {
            if (e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_SHIFT) {
                shiftDown = true;
            } else if (e.getID() == KeyEvent.KEY_RELEASED && e.getKeyCode() == KeyEvent.VK_SHIFT) {
                shiftDown = false;
            }
            return false; // 不消费事件
        };
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(dispatcher);
    }
    public static boolean isInside(zuobiao s, zuobiao sd,zuobiao f) {

        double x=s.x,y=s.y,a=sd.x,b=sd.y,c=f.x,d=f.y;
        double minX = Math.min(x, a);
        double maxX = Math.max(x, a);
        double minY = Math.min(y, b);
        double maxY = Math.max(y, b);

        return c >= minX && c <= maxX && d >= minY && d <= maxY;
    }

    public static boolean shiftdown(){
        return shiftDown;
    }
    public static double msToKmh(double metersPerSecond) {
        return metersPerSecond * 3.6;
    }
    public static double kmhToMs(double kmPerHour) {
        return kmPerHour / 3.6;
    }
    /**
     * 沿大圆航线，从 start 到 end 方向，取距离 start 为 cKm 的点。
     *
     * @param start 起点（x=经度，y=纬度）
     * @param end   终点（x=经度，y=纬度）
     * @param cKm   距离起点的距离，单位 km
     * @return 新的 zuobiao 对象
     */
    public static zuobiao congstartwangendzouckm(zuobiao start, zuobiao end, double cKm) {
        double a=end.x,b=end.y,x=start.x,y=start.y;
        double dLon = a - x;
        dLon = ((dLon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
        double dLat = b - y;

        // ---- 纬度平均值做经度缩放 ----
        double latAvg = (y + b) * 0.5;
        double cosLat = Math.cos(Math.toRadians(latAvg));

        // ---- 平面近似总距离（km） ----
        double dLatKm = dLat * cKm;
        double dLonKm = dLon * cKm * cosLat;
        double totalKm = Math.sqrt(dLatKm * dLatKm + dLonKm * dLonKm);

        // 起终点重合 或 cKm<=0 → 起点
        if (totalKm < 1e-12 || cKm <= 0.0) {
            return new zuobiao(x, y);
        }
        // cKm 超过总距离 → 终点（折叠后的终点，与画线一致）
        if (cKm >= totalKm) {
            return new zuobiao(x + dLon, b);
        }

        // ---- 沿直线按比例 t 推进 ----
        double t = cKm / totalKm;
        return new zuobiao(x + t * dLon, y + t * dLat);
    }

    /**
     * Haversine 公式计算两点大圆距离，单位 km
     */


    /**
     * 把经度差归一化到 [-180, 180]
     */
    private static double normalizeLonDiff(double diff) {
        while (diff > 180.0) {
            diff -= 360.0;
        }
        while (diff < -180.0) {
            diff += 360.0;
        }
        return diff;
    }
    public static void tanchuyemian(JPanel yemian){
        bianliang.gameframe.getLayeredPane().add(yemian, JLayeredPane.POPUP_LAYER);
    }
    public static void xuanzhan(J)

    // ================== 测试 ==================

}
