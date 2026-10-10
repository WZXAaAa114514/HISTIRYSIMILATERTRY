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
    public static double kmtojingdu(double km){
        return km/111.32;
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
    /**
     * 浮层宿主：把原来的 contentPane 作为铺底，其余组件叠在上面。
     * 用 null 布局，因此所有子组件的位置和大小由 setBounds 决定，互不干扰。
     */
    private static final class OverlayHost extends JPanel {

        private final Component base;

        OverlayHost(Component base) {
            super(null);                 // 关键：null 布局，才能叠放
            setOpaque(true);
            setBackground(Color.BLACK);
            this.base = base;
            add(base);                   // 先加 → 在下面
        }

        /** 把一个组件叠到最上层。若它还没有尺寸，用 preferredSize 给个默认。 */
        void addFloating(Component c) {
            if (c == null) return;
            if (c.getWidth() <= 0 || c.getHeight() <= 0) {
                Dimension d = c.getPreferredSize();
                int w = (d != null && d.width  > 0) ? d.width  : 320;
                int h = (d != null && d.height > 0) ? d.height : 220;
                c.setBounds(40, 40, w, h);   // 默认位置，调用方可以自己 setBounds 覆盖
            }
            add(c);                      // 后加 → 在上面
            revalidate();
            repaint();
        }

        @Override
        public void doLayout() {
            int W = getWidth(), H = getHeight();
            if (W <= 0 || H <= 0) return;
            base.setBounds(0, 0, W, H);  // 铺底组件永远铺满
            // 叠上去的组件保留各自的 setBounds，不动
        }
    }
    /**
     * 把 JPanel 直接挂到 gameframe 的 contentPane 上（不替换 contentPane）。
     *
     * 尺寸策略（优先级从高到低）：
     *   1. 若 panel 已经被 setSize(w>0,h>0) → 用 panel.getSize()
     *   2. 否则用 panel.getPreferredSize()
     *   3. 都为 0 → 用 fallbackW × fallbackH
     *
     * 位置策略：
     *   - anchor 以 contentPane 为参照系，支持 9 个锚点：
     *       "LT","CT","RT","LC","C","RC","LB","CB","RB"
     *   - 也可传具体像素：x,y >= 0 视为绝对坐标，< 0 视为相对右边/下边
     *     （想用具体像素就直接调 setBounds 的版本，见下面重载）
     *
     * @param panel      要显示的面板（非 null）
     * @param anchor     锚点字符串，默认 "RT"
     * @param margin     距边缘像素，默认 10
     */
    public static void xiucheng(JPanel panel, String anchor, int margin) {
        if (panel == null) return;
        SwingUtilities.invokeLater(() -> {
            JFrame frame = bianliang.gameframe;
            if (frame == null) return;

            Container content = frame.getContentPane();
            content.add(panel);                       // Z 序最高

            if (content.getLayout() == null) {        // 绝对定位分支
                // ---- 1. 决定尺寸：优先 panel.getSize()，再 preferredSize ----
                Dimension sz = panel.getSize();
                if (sz == null || sz.width <= 0 || sz.height <= 0) {
                    sz = panel.getPreferredSize();
                }
                int w = (sz != null && sz.width  > 0) ? sz.width  : 320;
                int h = (sz != null && sz.height > 0) ? sz.height : 220;

                // ---- 2. 决定位置：锚点 + 边距 ----
                int W = content.getWidth();
                int H = content.getHeight();
                if (W <= 0) W = frame.getWidth();
                if (H <= 0) H = frame.getHeight();

                int x, y;
                switch (anchor == null ? "RT" : anchor) {
                    case "LT": x = margin;                 y = margin;              break;
                    case "CT": x = (W - w) / 2;            y = margin;              break;
                    case "RT": x = W - w - margin;         y = margin;              break;
                    case "LC": x = margin;                 y = (H - h) / 2;         break;
                    case "C":  x = (W - w) / 2;            y = (H - h) / 2;         break;
                    case "RC": x = W - w - margin;         y = (H - h) / 2;         break;
                    case "LB": x = margin;                 y = H - h - margin;      break;
                    case "CB": x = (W - w) / 2;            y = H - h - margin;      break;
                    case "RB": x = W - w - margin;         y = H - h - margin;      break;
                    default:   x = W - w - margin;         y = margin;              break;
                }
                panel.setBounds(x, y, w, h);

            } else {                                  // 有布局管理器分支
                content.add(panel, BorderLayout.NORTH);
            }

            content.revalidate();
            content.repaint();
        });
    }

    /** 便捷重载：右上角 + 10px */
    public static void xiucheng(JPanel panel) {
        xiucheng(panel, "RT", 10);
    }

    /** 更自由的版本：直接指定绝对像素位置，尺寸仍取自 panel 自身 */
    public static void tanchuyemian(JPanel panel, int x, int y) {
        SwingUtilities.invokeLater(() -> {
            JPanel glass = new JPanel(null);   // 绝对定位容器
            glass.setOpaque(false);            // 关键：不遮住 board

            panel.setOpaque(false);            // 关键：自身也不要实底(除非你要)
            panel.setBounds(x, y,
                    Math.max(panel.getPreferredSize().width, 100),
                    Math.max(panel.getPreferredSize().height, 40));
            glass.add(panel);

            bianliang.gameframe.setGlassPane(glass);
            glass.setVisible(true);            // ★ 必须
        });
    }


    // ================== 测试 ==================

}
