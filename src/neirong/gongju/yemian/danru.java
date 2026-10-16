package neirong.gongju.yemian;

import javax.swing.*;
import java.awt.*;

/**
 * 一行调用：让任意 JPanel 在父容器中“从下方升起 + 由虚到实”。
 *
 * <pre>
 *     SlideUpFade.show(myPanel, parentPanel);
 * </pre>
 *
 * 说明：
 * <ul>
 *   <li>自动把 myPanel 加到 parentPanel（若已在别处会先摘下来）；</li>
 *   <li>停靠位置：如果 myPanel 之前已放在 parentPanel 中，用它原来的位置；
 *       否则按 preferredSize 水平居中、底部留白 {@link #DEFAULT_BOTTOM_GAP} 像素；</li>
 *   <li>淡入作用于 myPanel 及它的所有子组件；</li>
 *   <li>父容器请使用 null 布局，避免 LayoutManager 覆盖动画中的坐标。</li>
 * </ul>
 */
public final class danru {

    /** 默认动画时长（毫秒） */
    public static final int DEFAULT_DURATION = 650;
    /** 默认停靠时距父容器底部留白（像素） */
    public static final int DEFAULT_BOTTOM_GAP = 40;
    /** 默认帧间隔（约 60FPS） */
    public static final int DEFAULT_FRAME_DELAY = 16;

    private danru() {}

    /* ==================== 对外只暴露这两个方法 ==================== */

    /** 默认参数：650ms、底部留白 40px。 */
    public static void danru(JPanel a, JPanel b) {
        show(a, b, DEFAULT_DURATION, DEFAULT_BOTTOM_GAP);
    }

    /**
     * 让 a 在父容器 b 中从底部升起并淡入。
     *
     * @param a          要显示的面板（任意 JPanel）
     * @param b          父容器（推荐使用 null 布局）
     * @param durationMs 动画时长（毫秒）
     * @param bottomGap  停靠时距父容器底部的留白（像素）
     */
    public static void show(final JPanel a, final JPanel b,
                            int durationMs, int bottomGap) {
        if (a == null || b == null) return;
        if (b.getWidth() <= 0 || b.getHeight() <= 0) {
            System.err.println("[SlideUpFade] 父容器宽高为 0，请等布局完成后再调用。");
            return;
        }

        /* ---------- 1. 计算停靠位置 ---------- */
        final int restX, restY, w, h;
        Container old = a.getParent();

        if (old instanceof AlphaWrapper && old.getParent() == b) {
            // 上一次动画留下的壳还在，复用它的位置
            AlphaWrapper reuse = (AlphaWrapper) old;
            restX = reuse.getX();
            restY = reuse.getY();
            w = reuse.getWidth();
            h = reuse.getHeight();
            reuse.stopTimer();
            b.remove(reuse);
            reuse.remove(a);                 // 把 a 从旧壳取出
        } else if (old == b && a.getWidth() > 0 && a.getHeight() > 0) {
            // a 已经直接躺在 b 里：沿用它的位置和尺寸
            restX = a.getX();
            restY = a.getY();
            w = a.getWidth();
            h = a.getHeight();
            b.remove(a);
        } else {
            // 从未放置过：按 preferredSize 居中、底部留白
            Dimension pref = a.getPreferredSize();
            w = pref.width  > 0 ? pref.width  : 300;
            h = pref.height > 0 ? pref.height : 150;
            restX = Math.max(0, (b.getWidth()  - w) / 2);
            restY = Math.max(0, b.getHeight() - h - bottomGap);
            if (old != null) old.remove(a);
        }

        /* ---------- 2. 装入带 alpha 的壳，先藏到父容器下沿之外 ---------- */
        final AlphaWrapper shell = new AlphaWrapper(a);
        shell.setBounds(restX, b.getHeight(), w, h);
        shell.setAlpha(0f);
        b.add(shell);
        b.revalidate();
        b.repaint();

        /* ---------- 3. 帧定时器驱动位置 + 透明度 ---------- */
        final int startY = b.getHeight();
        final int endY   = restY;
        final long t0    = System.nanoTime();
        final int dur    = Math.max(1, durationMs);

        final Timer timer = new Timer(DEFAULT_FRAME_DELAY, null);
        timer.addActionListener(e -> {
            float t = (System.nanoTime() - t0) / 1_000_000f / dur;
            if (t > 1f) t = 1f;

            // easeOutCubic：先快后慢
            float p    = 1f - t;
            float ease = 1f - p * p * p;

            int y = startY + Math.round((endY - startY) * ease);
            shell.setBounds(restX, y, w, h);
            shell.setAlpha(ease);

            if (t >= 1f) {
                ((Timer) e.getSource()).stop();
                shell.setBounds(restX, endY, w, h);
                shell.setAlpha(1f);
            }
        });
        shell.setTimer(timer);
        timer.start();
    }

    /* ==================================================================
     * 内部：把 a 包起来、对整棵子树统一套 alpha 的壳
     * ================================================================== */
    private static class AlphaWrapper extends JPanel {

        private float alpha = 1f;
        private Timer runningTimer;

        AlphaWrapper(JPanel child) {
            super(new BorderLayout());
            setOpaque(false);
            add(child, BorderLayout.CENTER);
        }

        void setAlpha(float v) {
            if (v < 0f) v = 0f;
            if (v > 1f) v = 1f;
            this.alpha = v;
            repaint();
        }

        void setTimer(Timer t) { this.runningTimer = t; }

        void stopTimer() {
            if (runningTimer != null) {
                runningTimer.stop();
                runningTimer = null;
            }
        }

        @Override
        public void paint(Graphics g) {
            if (alpha <= 0.001f) return;

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);

            if (alpha < 0.999f) {
                g2.setComposite(AlphaComposite.getInstance(
                        AlphaComposite.SRC_OVER, alpha));
            }
            super.paint(g2);   // 子面板 + 它的子组件一起被套上 alpha
            g2.dispose();
        }
    }
}