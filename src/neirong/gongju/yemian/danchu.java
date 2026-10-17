package neirong.gongju.yemian;

import javax.swing.*;
import java.awt.*;

public final class danchu {

    public static final long DEFAULT_DURATION = 650;
    public static final long DEFAULT_FRAME_DELAY = 16;

    private danchu() {}

    public static void danchu(JPanel a, JPanel b) {
        show(a, b, DEFAULT_DURATION);
    }

    public static void show(final JPanel a, final JPanel b, long durationMs) {
        if (a == null || b == null) return;
        if (b.getWidth() <= 0 || b.getHeight() <= 0) {
            System.err.println("[FadeOutDown] 父容器宽高为 0，请等布局完成后再调用。");
            return;
        }

        final AlphaWrapper shell;
        final long startX, startY, w, h;
        final float startAlpha;

        Container old = a.getParent();

        if (old instanceof AlphaWrapper && old.getParent() == b) {
            // 壳还在：原地续用，从当前状态继续
            AlphaWrapper reuse = (AlphaWrapper) old;
            reuse.stopTimer();
            shell      = reuse;
            startX     = reuse.getX();
            startY     = reuse.getY();
            startAlpha = reuse.getAlpha();
            w          = reuse.getWidth();
            h          = reuse.getHeight();
        } else if (old == b) {
            // a 直接躺在 b 里：包壳
            w = a.getWidth()  > 0 ? a.getWidth()  : Math.max(1, a.getPreferredSize().width);
            h = a.getHeight() > 0 ? a.getHeight() : Math.max(1, a.getPreferredSize().height);
            startX     = a.getX();
            startY     = a.getY();
            startAlpha = 1f;

            b.remove(a);
            AlphaWrapper wrap = new AlphaWrapper(a);
            wrap.setRest(startX, startY);   // 被 danru 打断时能升回原位
            wrap.setBounds((int) startX, (int) startY, Math.toIntExact(w), Math.toIntExact(h));
            wrap.setAlpha(1f);
            b.add(wrap);
            shell = wrap;

            b.revalidate();
            b.repaint();
        } else {
            return;   // 不在 b 里，无需淡出
        }

        final long  endY = b.getHeight();
        final long t0   = System.nanoTime();
        final long  dur  = Math.max(1, durationMs);

        final Timer timer = new Timer((int) DEFAULT_FRAME_DELAY, null);
        timer.addActionListener(e -> {
            float t = (System.nanoTime() - t0) / 1_000_000f / dur;
            if (t > 1f) t = 1f;

            float ease = t * t * (3f - 2f * t);     // smoothstep

            long y = startY + Math.round((endY - startY) * ease);
            shell.setBounds((int) startX, Math.toIntExact(y), Math.toIntExact(w), Math.toIntExact(h));
            shell.setAlpha(startAlpha + (0f - startAlpha) * ease);

            if (t >= 1f) {
                ((Timer) e.getSource()).stop();
                shell.setAlpha(0f);

                b.remove(shell);
                shell.remove(a);
                b.revalidate();
                b.repaint();
            }
        });
        shell.setTimer(timer);
        timer.start();
    }
}