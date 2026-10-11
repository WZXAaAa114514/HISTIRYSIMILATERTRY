package neirong.gongju.pingmujilei;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class tuodongkuang extends JPanel {

    public static final int HEIGHT = 50;

    private static final int RESIZE_MARGIN = 6;
    private static final int MIN_W = 40;
    private static final int MIN_H = 30;

    private static final int EDGE_LEFT   = 1;
    private static final int EDGE_RIGHT  = 2;
    private static final int EDGE_TOP    = 4;
    private static final int EDGE_BOTTOM = 8;

    private final JPanel a;

    private Point dragStartParent;
    private Point dragStartA;

    /** 非 null 表示当前处于“全屏”状态，里面保存的是进入全屏前 a 的原始 bounds */
    private Rectangle savedBounds;
    private JButton fullBtn;

    private int resizeEdges = 0;
    private Point resizeStartParent;
    private Rectangle resizeStartBounds;

    /** 抑制 setBounds 触发的 componentMoved/Resized 回调，避免递归 */
    private boolean adjusting = false;

    /* ==================== ghost 拖动模式 ==================== */
    private boolean ghostDragEnabled = true;

    private JWindow ghostWindow;
    private Rectangle ghostStartBounds;
    private int ghostAX, ghostAY;

    /* ==================== 父容器尺寸监听（全屏跟随） ==================== */
    private final ComponentAdapter parentResizeListener = new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
            if (savedBounds == null || adjusting) return;
            Container p = getParent();
            if (p == null) return;

            adjusting = true;
            try {
                // 全屏状态下父容器被拉伸：
                // a 下移 HEIGHT，高度占满剩余空间
                a.setBounds(0, HEIGHT, p.getWidth(), p.getHeight() - HEIGHT);
                applyBounds();
            } finally {
                adjusting = false;
            }
        }
    };

    public tuodongkuang(JPanel a) {
        if (a == null) throw new IllegalArgumentException("a 不能为 null");
        this.a = a;

        setOpaque(true);
        setBackground(new Color(60, 60, 70));
        setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        setLayout(new BorderLayout());
        setDoubleBuffered(true);

        JLabel title = new JLabel("拖动");
        title.setForeground(Color.WHITE);
        title.setOpaque(false);
        title.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
        add(title, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 4));
        buttons.setOpaque(false);

        fullBtn = makeButton("全屏");
        JButton delBtn = makeButton("✕");

        fullBtn.addActionListener(e -> toggleFullscreen());
        delBtn.addActionListener(e -> doDelete());

        buttons.add(fullBtn);
        buttons.add(delBtn);
        add(buttons, BorderLayout.EAST);

        // ===== 拖动框自身拖动 =====
        MouseAdapter ma = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                Container p = getParent();
                if (p == null) return;
                if (savedBounds != null) return; // 全屏时禁止拖动
                dragStartParent = SwingUtilities.convertPoint(tuodongkuang.this, e.getPoint(), p);
                dragStartA = a.getLocation();

                if (ghostDragEnabled) {
                    ghostStartBounds = a.getBounds();
                    ghostAX = dragStartA.x;
                    ghostAY = dragStartA.y;
                    ensureGhost();
                }
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
                newAY = Math.max(0, Math.min(newAY, Math.max(0, pSize.height - ah)));

                if (ghostDragEnabled) {
                    if (newAX == ghostAX && newAY == ghostAY) return;
                    ghostAX = newAX;
                    ghostAY = newAY;
                    updateGhost();
                    applyGhostBounds();
                } else {
                    if (newAX == a.getX() && newAY == a.getY()) return;
                    adjusting = true;
                    try {
                        a.setLocation(newAX, newAY);
                        applyBounds();
                    } finally {
                        adjusting = false;
                    }
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (ghostDragEnabled && ghostWindow != null && ghostStartBounds != null) {
                    ghostWindow.setVisible(false);
                    adjusting = true;
                    try {
                        a.setLocation(ghostAX, ghostAY);
                        applyBounds();
                    } finally {
                        adjusting = false;
                    }
                }
                ghostStartBounds = null;
                dragStartParent = null;
                dragStartA = null;
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);

        // ===== a 边缘缩放 =====
        MouseAdapter ra = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                if (savedBounds != null) {
                    a.setCursor(Cursor.getDefaultCursor());
                    return;
                }
                applyResizeCursor(detectEdges(e));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (resizeEdges == 0) a.setCursor(Cursor.getDefaultCursor());
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (savedBounds != null) return; // 全屏时禁止缩放
                if (!SwingUtilities.isLeftMouseButton(e)) return;

                int edges = detectEdges(e);
                if (edges == 0) return;

                Container p = getParent();
                if (p == null) return;

                resizeEdges = edges;
                resizeStartParent = SwingUtilities.convertPoint(a, e.getPoint(), p);
                resizeStartBounds = a.getBounds();
                e.consume();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (resizeEdges == 0 || resizeStartParent == null) return;
                Container p = getParent();
                if (p == null) return;

                Point now = SwingUtilities.convertPoint(a, e.getPoint(), p);
                int dx = now.x - resizeStartParent.x;
                int dy = now.y - resizeStartParent.y;

                Rectangle b = new Rectangle(resizeStartBounds);

                if ((resizeEdges & EDGE_LEFT) != 0) {
                    int nw = b.width - dx;
                    if (nw >= MIN_W) { b.x += dx; b.width = nw; }
                    else { b.x = b.x + b.width - MIN_W; b.width = MIN_W; }
                }
                if ((resizeEdges & EDGE_RIGHT) != 0) {
                    b.width = Math.max(MIN_W, b.width + dx);
                }
                if ((resizeEdges & EDGE_TOP) != 0) {
                    int nh = b.height - dy;
                    if (nh >= MIN_H) { b.y += dy; b.height = nh; }
                    else { b.y = b.y + b.height - MIN_H; b.height = MIN_H; }
                }
                if ((resizeEdges & EDGE_BOTTOM) != 0) {
                    b.height = Math.max(MIN_H, b.height + dy);
                }

                if (b.x < 0) { b.width += b.x; b.x = 0; }
                if (b.y < 0) { b.height += b.y; b.y = 0; }
                if (b.x + b.width > p.getWidth())   b.width  = p.getWidth()  - b.x;
                if (b.y + b.height > p.getHeight()) b.height = p.getHeight() - b.y;
                if (b.width  < MIN_W) b.width  = MIN_W;
                if (b.height < MIN_H) b.height = MIN_H;

                if (b.equals(a.getBounds())) return;

                adjusting = true;
                try {
                    a.setBounds(b);
                    applyBounds();
                } finally {
                    adjusting = false;
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                resizeEdges = 0;
                resizeStartParent = null;
                resizeStartBounds = null;
                applyResizeCursor(detectEdges(e));
            }
        };
        a.addMouseListener(ra);
        a.addMouseMotionListener(ra);

        a.addComponentListener(new ComponentAdapter() {
            @Override public void componentMoved(ComponentEvent e)   {
                if (adjusting) return;
                relayout();
            }
            @Override public void componentResized(ComponentEvent e) {
                if (adjusting) return;
                relayout();
            }
        });

        // a 被用户拖动重绘时，重新把拖动框刷到最上层
        a.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                ensureTopmost();
            }
        });
    }

    /* ==================== 拖动框置顶 ==================== */

    private void ensureTopmost() {
        Container p = getParent();
        if (p == null) return;
        if (p.getComponentZOrder(this) != 0) {
            p.setComponentZOrder(this, 0);
        }
        repaint();
    }

    /* ==================== ghost 相关 ==================== */

    public boolean isGhostDrag() { return ghostDragEnabled; }

    public void setGhostDrag(boolean on) { this.ghostDragEnabled = on; }

    private void ensureGhost() {
        if (ghostWindow != null) return;
        Window owner = SwingUtilities.getWindowAncestor(this);
        ghostWindow = new JWindow(owner);
        try {
            ghostWindow.setBackground(new Color(0, 0, 0, 0));
        } catch (Exception ignored) { }
        ghostWindow.setFocusableWindowState(false);

        JPanel ghost = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0, 150, 255, 60));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(new Color(0, 150, 255, 230));
                g2.setStroke(new BasicStroke(2f));
                g2.drawRect(1, 1, Math.max(0, getWidth() - 3), Math.max(0, getHeight() - 3));
                g2.dispose();
            }
        };
        ghost.setOpaque(false);
        ghostWindow.setContentPane(ghost);
    }

    private void updateGhost() {
        if (ghostWindow == null || ghostStartBounds == null) return;
        try {
            Point screen = a.getLocationOnScreen();
            int dx = ghostAX - ghostStartBounds.x;
            int dy = ghostAY - ghostStartBounds.y;
            ghostWindow.setBounds(
                    screen.x + dx, screen.y + dy,
                    ghostStartBounds.width, ghostStartBounds.height);
            ghostWindow.setVisible(true);
        } catch (IllegalComponentStateException ignored) {
        }
    }

    private void applyGhostBounds() {
        int aw = a.getWidth();
        if (aw <= 0) {
            Dimension pref = a.getPreferredSize();
            aw = (pref != null && pref.width > 0) ? pref.width : 200;
        }
        int ty = ghostAY - HEIGHT;
        if (ty < 0) ty = ghostAY;
        setBounds(ghostAX, ty, aw, HEIGHT);
    }

    /* ==================== 绘制 ==================== */

    @Override
    protected void paintComponent(Graphics g) {
        g.setColor(getBackground());
        g.fillRect(0, 0, getWidth(), getHeight());
    }

    /* ==================== 边缘检测 ==================== */

    private int detectEdges(MouseEvent e) {
        int w = a.getWidth();
        int h = a.getHeight();
        int x = e.getX();
        int y = e.getY();

        int edges = 0;
        if (x < RESIZE_MARGIN)             edges |= EDGE_LEFT;
        if (x > w - RESIZE_MARGIN - 1)     edges |= EDGE_RIGHT;
        if (y < RESIZE_MARGIN)             edges |= EDGE_TOP;
        if (y > h - RESIZE_MARGIN - 1)     edges |= EDGE_BOTTOM;

        if (a.getY() < HEIGHT && (edges & EDGE_TOP) != 0) {
            edges &= ~EDGE_TOP;
        }
        return edges;
    }

    private void applyResizeCursor(int edges) {
        int type;
        boolean left  = (edges & EDGE_LEFT)   != 0;
        boolean right = (edges & EDGE_RIGHT)  != 0;
        boolean top   = (edges & EDGE_TOP)    != 0;
        boolean bottom= (edges & EDGE_BOTTOM) != 0;

        if ((left || right) && (top || bottom)) {
            if (left  && top)         type = Cursor.NW_RESIZE_CURSOR;
            else if (right && top)    type = Cursor.NE_RESIZE_CURSOR;
            else if (left  && bottom) type = Cursor.SW_RESIZE_CURSOR;
            else                      type = Cursor.SE_RESIZE_CURSOR;
        } else if (left || right) {
            type = Cursor.E_RESIZE_CURSOR;
        } else if (top || bottom) {
            type = Cursor.N_RESIZE_CURSOR;
        } else {
            type = Cursor.DEFAULT_CURSOR;
        }
        a.setCursor(Cursor.getPredefinedCursor(type));
    }

    /* ==================== 全屏 / 还原 ==================== */

    private void toggleFullscreen() {
        Container p = getParent();
        if (p == null) return;

        // 记录旧的可视区域，用于局部重绘
        Rectangle oldArea = getBounds().union(a.getBounds());

        adjusting = true;
        try {
            if (savedBounds == null) {
                /* ---------- 进入全屏 ---------- */
                // ① 备份 a 进入全屏前的原始 bounds（副本）
                savedBounds = a.getBounds();

                // ② a 下移一个 HEIGHT，高度占满剩余空间：
                //    y = HEIGHT，height = p.getHeight() - HEIGHT
                //    拖动框由 applyBounds() 定位，ty = HEIGHT - HEIGHT = 0，
                //    仍停在父容器顶部，不受影响
                a.setBounds(0, HEIGHT, p.getWidth(), p.getHeight() - HEIGHT);

                fullBtn.setText("还原");
            } else {
                /* ---------- 还原 ---------- */
                // 把备份的原始 bounds 原样还回去（位置 + 尺寸）
                a.setBounds(savedBounds);
                savedBounds = null;
                fullBtn.setText("全屏");
            }

            // 拖动框跟着 a 重新定位（此式在 a.y==HEIGHT 时得到 ty==0，位置不变）
            applyBounds();
        } finally {
            adjusting = false;
        }

        // 拖动框置顶，避免被 a 的重绘盖掉
        p.setComponentZOrder(this, 0);

        // 只重绘受影响区域
        repaintArea(p, oldArea.union(getBounds()).union(a.getBounds()));
    }

    private void doDelete() {
        Container p = getParent();
        if (p == null) return;
        disposeGhost();
        p.remove(this);
        p.remove(a);
        p.revalidate();
        p.repaint();
    }

    private void disposeGhost() {
        if (ghostWindow != null) {
            ghostWindow.dispose();
            ghostWindow = null;
        }
    }

    /* ==================== 布局 ==================== */

    public void relayout() {
        Container p = getParent();
        Rectangle oldArea = getBounds();
        applyBounds();
        if (p != null) {
            repaintArea(p, oldArea.union(getBounds()));
        }
    }

    /**
     * 拖动框定位：
     *   ty = a.y - HEIGHT
     *   - 常规 a.y >= HEIGHT：拖动框贴在 a 上方
     *   - a.y <  HEIGHT    ：钳到 a.y
     * 全屏时 a.y == HEIGHT → ty == 0，拖动框停在父容器顶部不动。
     */
    private void applyBounds() {
        int aw = a.getWidth();
        if (aw <= 0) {
            Dimension pref = a.getPreferredSize();
            aw = (pref != null && pref.width > 0) ? pref.width : 200;
        }
        int ax = a.getX();
        int ay = a.getY();

        int ty = ay - HEIGHT;
        if (ty < 0) ty = ay;

        setBounds(ax, ty, aw, HEIGHT);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        Container p = getParent();
        if (p == null) return;
        if (a.getParent() != p) {
            p.add(a);
        }
        a.setDoubleBuffered(true);
        p.setComponentZOrder(this, 0);
        // 注册父容器尺寸监听：全屏期间父容器被拉伸时，a 跟着铺满
        p.addComponentListener(parentResizeListener);
        relayout();
    }

    @Override
    public void removeNotify() {
        Container p = getParent();
        if (p != null) {
            p.removeComponentListener(parentResizeListener);
        }
        disposeGhost();
        super.removeNotify();
    }

    public JPanel getA() { return a; }

    public boolean isFullscreen() { return savedBounds != null; }

    /* ==================== 工具方法 ==================== */

    private void repaintArea(Container p, Rectangle r) {
        if (p == null || r == null || r.isEmpty()) return;
        Rectangle clip = r.intersection(new Rectangle(0, 0, p.getWidth(), p.getHeight()));
        if (clip.isEmpty()) return;
        p.repaint(clip.x, clip.y, clip.width, clip.height);
    }

    private static JButton makeButton(String text) {
        JButton b = new JButton(text);
        b.setFocusable(false);
        b.setFocusPainted(false);
        b.setMargin(new Insets(2, 8, 2, 8));
        b.setFont(b.getFont().deriveFont(Font.PLAIN, 12f));
        b.setBackground(new Color(80, 80, 92));
        b.setForeground(Color.WHITE);
        b.setBorder(BorderFactory.createLineBorder(new Color(120, 120, 132)));
        return b;
    }
}