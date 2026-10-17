package neirong.gongju.yemian;

import javax.swing.*;
import java.awt.*;

final class AlphaWrapper extends JPanel {

    private float alpha = 1f;
    private Timer runningTimer;

    /** 目标归位点：danru 打断时滑回这里 */
    private long restX, restY;

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

    float getAlpha() { return alpha; }

    void setRest(long x, long y) { this.restX = x; this.restY = y; }
    long getRestX() { return restX; }
    long getRestY() { return restY; }

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
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        if (alpha < 0.999f) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        }
        super.paint(g2);
        g2.dispose();
    }
}