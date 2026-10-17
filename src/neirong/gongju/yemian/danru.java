package neirong.gongju.yemian;

import javax.swing.*;
import java.awt.*;

public final class danru {

    public static final long DEFAULT_DURATION = 650;
    public static final long DEFAULT_BOTTOM_GAP = 40;
    public static final long DEFAULT_FRAME_DELAY = 16;

    private danru() {}

    public static void danru(JPanel a, JPanel b) {
        show(a, b, DEFAULT_DURATION, DEFAULT_BOTTOM_GAP);
    }

    public static void show(final JPanel a, final JPanel b, long durationMs, long bottomGap) {
        if (a == null || b == null) return;
        if (b.getWidth() <= 0 || b.getHeight() <= 0) {
            System.err.println("[SlideUpFade] 父容器宽高为 0，请等布局完成后再调用。");
            return;
        }

        final AlphaWrapper shell;
        final long startX, startY, restX, restY, w, h;
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
            restX      = reuse.getRestX();
            restY      = reuse.getRestY();
            w          = reuse.getWidth();
            h          = reuse.getHeight();

            // 已经停在终点，无需再动
            if (startY == restY && startAlpha >= 0.999f) return;
        } else {
            // 新建壳
            long rx, ry, ww, hh;
            if (old == b && a.getWidth() > 0 && a.getHeight() > 0) {
                rx = a.getX();
                ry = a.getY();
                ww = a.getWidth();
                hh = a.getHeight();
                b.remove(a);
            } else {
                Dimension pref = a.getPreferredSize();
                ww = pref.width  > 0 ? pref.width  : 300;
                hh = pref.height > 0 ? pref.height : 150;
                rx = Math.max(0, (b.getWidth()  - ww) / 2);
                ry = Math.max(0, b.getHeight() - hh - bottomGap);
                if (old != null) old.remove(a);
            }
            restX = rx; restY = ry; w = ww; h = hh;
            startX = restX;
            startY = b.getHeight();
            startAlpha = 0f;

            AlphaWrapper wrap = new AlphaWrapper(a);
            wrap.setRest(restX, restY);
            wrap.setBounds((int) startX, Math.toIntExact(startY), Math.toIntExact(w), Math.toIntExact(h));
            wrap.setAlpha(0f);
            b.add(wrap);
            b.revalidate();
            b.repaint();
            shell = wrap;
        }

        final long   endY = restY;
        final long  t0   = System.nanoTime();
        final long   dur  = Math.max(1, durationMs);

        final Timer timer = new Timer((int) DEFAULT_FRAME_DELAY, null);
        timer.addActionListener(e -> {
            float t = (System.nanoTime() - t0) / 1_000_000f / dur;
            if (t > 1f) t = 1f;

            float p    = 1f - t;
            float ease = 1f - p * p * p;            // easeOutCubic

            long y = startY + Math.round((endY - startY) * ease);
            shell.setBounds((int) startX, Math.toIntExact(y), Math.toIntExact(w), Math.toIntExact(h));
            shell.setAlpha(startAlpha + (1f - startAlpha) * ease);

            if (t >= 1f) {
                ((Timer) e.getSource()).stop();
                shell.setBounds((int) startX, Math.toIntExact(endY), Math.toIntExact(w), Math.toIntExact(h));
                shell.setAlpha(1f);
            }
        });
        shell.setTimer(timer);
        timer.start();
    }
}