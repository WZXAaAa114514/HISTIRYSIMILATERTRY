package neirong.gongju.yemian;


import javax.swing.*;
import java.awt.*;

/**
 * 一行调用：让任意 JPanel 在父容器中“向下滑出 + 由实到虚”，结束后自动移除。
 *
 * <pre>
 *     danchu.danchu(myPanel, parentPanel);
 * </pre>
 *
 * 说明：
 * <ul>
 *   <li>myPanel 当前在哪就从哪开始滑（若之前用 {@link danru} 显示过，
 *       会直接复用那次留下的壳，衔接无跳变）；</li>
 *   <li>终点：滑到父容器下沿之外（y = parent.height），同时透明度降到 0；</li>
 *   <li>动画结束后：壳从父容器移除，myPanel 从壳里取出（父容器变为 null），
 *       因此可以再次交给 {@link danru} 重新淡入；</li>
 *   <li>淡出作用于 myPanel 及它的所有子组件；</li>
 *   <li>父容器请使用 null 布局，避免 LayoutManager 覆盖动画中的坐标。</li>
 * </ul>
 */
public final class danchu {

    /** 默认动画时长（毫秒） */
    public static final int DEFAULT_DURATION = 650;
    /** 默认帧间隔（约 60FPS） */
    public static final int DEFAULT_FRAME_DELAY = 16;

    private danchu() {}

    /* ==================== 对外只暴露这两个方法 ==================== */

    /** 默认参数：650ms。 */
    public static void danchu(JPanel a, JPanel b) {
        show(a, b, DEFAULT_DURATION);
    }

    /**
     * 让 a 在父容器 b 中向下滑出并淡出，结束后移除。
     *
     * @param a          要淡出的面板（任意 JPanel）
     * @param b          父容器（推荐使用 null 布局）
     * @param durationMs 动画时长（毫秒）
     */
    public static void show(final JPanel a, final JPanel b, int durationMs) {
        if (a == null || b == null) return;
        if (b.getWidth() <= 0 || b.getHeight() <= 0) {
            System.err.println("[FadeOutDown] 父容器宽高为 0，请等布局完成后再调用。");
            return;
        }

        /* ---------- 1. 找到当前承载 a 的壳，并取它的当前坐标 ---------- */
        final AlphaWrapper shell;
        final int startX, startY, w, h;

        Container old = a.getParent();

        if (old instanceof AlphaWrapper && old.getParent() == b) {
            // 上一次（淡入或淡出）留下的壳还在，直接续用，位置不跳变
            AlphaWrapper reuse = (AlphaWrapper) old;
            reuse.stopTimer();
            shell  = reuse;
            startX = reuse.getX();
            startY = reuse.getY();
            w      = reuse.getWidth();
            h      = reuse.getHeight();
        } else if (old == b) {
            // a 直接躺在 b 里：包一层壳再动画
            w = a.getWidth()  > 0 ? a.getWidth()  : Math.max(1, a.getPreferredSize().width);
            h = a.getHeight() > 0 ? a.getHeight() : Math.max(1, a.getPreferredSize().height);
            startX = a.getX();
            startY = a.getY();

            b.remove(a);
            AlphaWrapper wrap = new AlphaWrapper(a);
            wrap.setBounds(startX, startY, w, h);
            wrap.setAlpha(1f);
            b.add(wrap);
            shell = wrap;

            b.revalidate();
            b.repaint();
        } else {
            // 压根不在父容器里，无需淡出
            return;
        }

        /* ---------- 2. 帧定时器驱动位置 + 透明度 ---------- */
        final int  endY = b.getHeight();          // 滑到父容器下沿之外
        final long t0   = System.nanoTime();
        final int  dur  = Math.max(1, durationMs);

        final Timer timer = new Timer(DEFAULT_FRAME_DELAY, null);
        timer.addActionListener(e -> {
            float t = (System.nanoTime() - t0) / 1_000_000f / dur;
            if (t > 1f) t = 1f;

            // smoothstep：起步和收尾都柔和，比线性自然
            float ease = t * t * (3f - 2f * t);

            int y = startY + Math.round((endY - startY) * ease);
            shell.setBounds(startX, y, w, h);
            shell.setAlpha(1f - ease);

            if (t >= 1f) {
                ((Timer) e.getSource()).stop();
                shell.setAlpha(0f);

                // 收尾：壳离场，a 取出来（parent 变 null），方便下次再 danru
                b.remove(shell);
                shell.remove(a);
                b.revalidate();
                b.repaint();
            }
        });
        shell.setTimer(timer);
        timer.start();
    }

    /* ==================================================================
     * 内部：把 a 包起来、对整棵子树统一套 alpha 的壳（与 danru 同款）
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