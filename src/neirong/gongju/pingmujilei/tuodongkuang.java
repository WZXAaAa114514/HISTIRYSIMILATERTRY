package neirong.gongju.pingmujilei;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class tuodongkuang extends JPanel {

    public static final int HEIGHT = 50;

    private final JPanel a;

    private Point dragStartParent;
    private Point dragStartA;

    public tuodongkuang(JPanel a) {
        if (a == null) throw new IllegalArgumentException("a 不能为 null");
        this.a = a;

        setOpaque(true);                                   // ★
        setBackground(new Color(60, 60, 70));              // ★
        setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        setLayout(new BorderLayout());
        setDoubleBuffered(true);

        JLabel title = new JLabel("拖动");
        title.setForeground(Color.WHITE);
        title.setOpaque(false);                            // ★ 标签保持透明
        title.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
        add(title, BorderLayout.CENTER);

        MouseAdapter ma = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                Container p = getParent();
                if (p == null) return;
                dragStartParent = SwingUtilities.convertPoint(tuodongkuang.this, e.getPoint(), p);
                dragStartA = a.getLocation();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragStartParent == null) return;
                Container p = getParent();
                if (p == null) return;

                Point now = SwingUtilities.convertPoint(tuodongkuang.this, e.getPoint(), p);
                int dx = now.x - dragStartParent.x;
                int dy = now.y - dragStartParent.y;

                int newAX = dragStartA.x + dx;
                int newAY = dragStartA.y + dy;

                Dimension pSize = p.getSize();
                int aw = a.getWidth();
                int ah = a.getHeight();
                newAX = Math.max(0, Math.min(newAX, Math.max(0, pSize.width  - aw)));
                newAY = Math.max(HEIGHT, Math.min(newAY, Math.max(HEIGHT, pSize.height - ah)));

                Rectangle oldArea = getBounds().union(a.getBounds());

                a.setLocation(newAX, newAY);
                setBounds(newAX, newAY - HEIGHT, aw, HEIGHT);

                Rectangle newArea = getBounds().union(a.getBounds());
                refresh(p, oldArea.union(newArea));
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragStartParent = null;
                dragStartA = null;
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);

        a.addComponentListener(new ComponentAdapter() {
            @Override public void componentMoved(ComponentEvent e)   { relayout(); }
            @Override public void componentResized(ComponentEvent e) { relayout(); }
        });
    }

    /** ★ 关键：不依赖 UI 的 opaque 判断，直接铺满背景色。 */
    @Override
    protected void paintComponent(Graphics g) {
        g.setColor(getBackground());
        g.fillRect(0, 0, getWidth(), getHeight());
        // 不需要 super.paintComponent(g)，背景已铺满；
        // 若将来想加边框/自定义前景，可在此后自行绘制。
    }

    public void relayout() {
        int aw = a.getWidth();
        if (aw <= 0) {
            Dimension pref = a.getPreferredSize();
            aw = (pref != null && pref.width > 0) ? pref.width : 200;
        }
        int ax = a.getX();
        int ay = a.getY();

        Rectangle oldArea = getBounds();
        setBounds(ax, ay - HEIGHT, aw, HEIGHT);

        Container p = getParent();
        if (p != null) {
            refresh(p, oldArea.union(getBounds()));
        }
    }

    @Override
    public void addNotify() {
        super.addNotify();
        Container p = getParent();
        if (p == null) return;
        if (a.getParent() != p) {
            p.add(a);
        }
        // ★ 先不强制改 Z 序，避免把别的组件挤上来
        // 如果父容器只有 this 和 a，这两行是空操作；
        // 如果有其他组件，按需调整或去掉。
        // p.setComponentZOrder(this, 0);
        // p.setComponentZOrder(a, 1);

        relayout();
    }

    public JPanel getA() { return a; }

    /** 刷新父容器指定区域 + 拖动框自身。 */
    private void refresh(Container p, Rectangle area) {
        if (p != null && area != null && !area.isEmpty()) {
            p.repaint(area.x, area.y, area.width, area.height);
        }
        repaint();
    }
}