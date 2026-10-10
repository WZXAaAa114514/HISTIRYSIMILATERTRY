package neirong.gongju.pingmujilei;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 轻量 XY 画板（嵌套版）。
 *
 *  - 世界坐标 (x, y) + 平移/缩放；无经纬度、无烘焙、无后台线程。
 *  - 可挂载任意 JComponent（含 PaintBoard / 另一个 XYBoard），随地图平移/缩放。
 *  - cantiaozhengdaxiao=true 时出现右下角外侧手柄，可拖拽改变大小。
 *  - setAutoZoomOnResize(true) 后，作为子画板时，像素尺寸变化会按比例同步内部 zoom，
 *    使挂在里面的世界内容也跟着一起放大/缩小。
 */
public class XYBoard extends JPanel {

    /* ==================== 视图状态 ==================== */
    private double viewX = 0, viewY = 0;
    private double zoom = 1.0;
    private double minZoom = 0.02, maxZoom = 50.0;

    /* ==================== 拖拽 / 调整大小 ==================== */
    private Point dragStart;
    private double dragViewX, dragViewY;

    private Node resizingNode;
    private Point resizeStart;
    private int resizeStartW, resizeStartH;

    private static final int   HANDLE_PX     = 8;
    private static final Color HANDLE_FILL   = new Color(0, 150, 255, 210);
    private static final Color HANDLE_BORDER = new Color(255, 255, 255, 230);

    /* ==================== 嵌套支持 ==================== */
    private boolean autoZoomOnResize = false;
    private boolean autoBasePending  = false;
    private int     autoBaseW = 0, autoBaseH = 0;
    private double  autoBaseZoom = 0;

    /** 嵌套时建议关掉：避免子画板抢走父画板的滚轮缩放。 */
    private boolean wheelZoomEnabled = true;
    /** 嵌套时建议关掉：避免子画板抢走父画板的拖拽平移。 */
    private boolean panEnabled = true;

    /* ==================== 外部钩子 ==================== */
    public interface WorldPainter { void paintWorld(Graphics2D g, XYBoard b); }
    private WorldPainter worldPainter;
    public void setWorldPainter(WorldPainter p) { this.worldPainter = p; repaint(); }

    public interface MouseWorldListener { void onMouseWorld(double x, double y, boolean inside); }
    private MouseWorldListener mouseListener;
    public void setMouseWorldListener(MouseWorldListener l) { this.mouseListener = l; }

    /* ==================== 节点 ==================== */
    public static final class Node {
        final JComponent comp;
        double x, y;
        int ax, ay;
        int w, h;
        boolean scaleWithZoom;
        double baseZoom;
        float minS = 0.2f, maxS = 5f;
        boolean cantiaozhengdaxiao = false;
        int lastX = Integer.MIN_VALUE, lastY, lastW, lastH;

        Node(JComponent c, double x, double y, int ax, int ay,
             int w, int h, boolean sz, double bz) {
            this.comp = c; this.x = x; this.y = y;
            this.ax = ax; this.ay = ay; this.w = w; this.h = h;
            this.scaleWithZoom = sz; this.baseZoom = bz;
        }
    }

    private final List<Node> nodes = new ArrayList<>(8);

    /* ==================== 构造 ==================== */
    public XYBoard(int i, int i1) {
        setLayout(null);
        setOpaque(true);
        setBackground(Color.BLACK);
        setPreferredSize(new Dimension(i, i1));
        setBounds(100, 100, i, i1);
        MouseAdapter ma = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                Node h = hitHandle(e.getX(), e.getY());
                if (h != null) {
                    resizingNode = h;
                    resizeStart = e.getPoint();
                    resizeStartW = h.w;
                    resizeStartH = h.h;
                    return;
                }
                if (!panEnabled) return;
                dragStart = e.getPoint();
                dragViewX = viewX; dragViewY = viewY;
                setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            }
            @Override public void mouseDragged(MouseEvent e) {
                if (resizingNode != null) {
                    Node n = resizingNode;
                    double ns = 1.0;
                    if (n.scaleWithZoom) {
                        ns = zoom / n.baseZoom;
                        if (ns < n.minS) ns = n.minS; else if (ns > n.maxS) ns = n.maxS;
                    }
                    if (ns <= 0) ns = 1.0;
                    int dw = e.getX() - resizeStart.x;
                    int dh = e.getY() - resizeStart.y;
                    n.w = Math.max(4, (int) Math.round(resizeStartW + dw / ns));
                    n.h = Math.max(4, (int) Math.round(resizeStartH + dh / ns));
                    layoutNode(n);
                    repaint();
                    return;
                }
                if (dragStart != null) {
                    viewX = dragViewX - (e.getX() - dragStart.x) / zoom;
                    viewY = dragViewY - (e.getY() - dragStart.y) / zoom;
                    layoutNodes();
                    repaint();
                }
                fireMouse(e.getX(), e.getY(), true);
            }
            @Override public void mouseReleased(MouseEvent e) {
                resizingNode = null;
                resizeStart = null;
                dragStart = null;
                setCursor(Cursor.getDefaultCursor());
            }
            @Override public void mouseMoved(MouseEvent e) {
                setCursor(hitHandle(e.getX(), e.getY()) != null
                        ? Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR)
                        : Cursor.getDefaultCursor());
                fireMouse(e.getX(), e.getY(), true);
            }
            @Override public void mouseExited(MouseEvent e) { fireMouse(0, 0, false); }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);

        addMouseWheelListener(e -> {
            if (!wheelZoomEnabled) return;
            int W = getWidth(), H = getHeight();
            if (W <= 0 || H <= 0) return;
            double factor = Math.pow(1.15, -e.getWheelRotation());
            double ns = zoom * factor;
            if (ns < minZoom) ns = minZoom; else if (ns > maxZoom) ns = maxZoom;
            if (ns == zoom) return;
            int mx = e.getX(), my = e.getY();
            double wx = viewX + (mx - W * 0.5) / zoom;
            double wy = viewY + (my - H * 0.5) / zoom;
            zoom = ns;
            viewX = wx - (mx - W * 0.5) / zoom;
            viewY = wy - (my - H * 0.5) / zoom;
            layoutNodes();
            repaint();
        });

        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) { onResized(); }
        });
    }

    private void onResized() {
        int W = getWidth(), H = getHeight();
        if (W <= 0 || H <= 0) return;

        if (autoZoomOnResize) {
            if (autoBasePending) {
                autoBaseW = W; autoBaseH = H; autoBaseZoom = zoom;
                autoBasePending = false;
            } else if (autoBaseW > 0 && autoBaseH > 0 && autoBaseZoom > 0) {
                double s = Math.min((double) W / autoBaseW, (double) H / autoBaseH);
                if (s > 0.001) {
                    double nz = autoBaseZoom * s;
                    if (nz < minZoom) nz = minZoom; else if (nz > maxZoom) nz = maxZoom;
                    if (nz != zoom) zoom = nz;
                }
            }
        }
        layoutNodes();
        repaint();
    }

    /* ==================== 坐标转换 ==================== */
    public double[] worldToScreen(double wx, double wy) {
        return new double[]{
                (wx - viewX) * zoom + getWidth()  * 0.5,
                (wy - viewY) * zoom + getHeight() * 0.5
        };
    }
    public double[] screenToWorld(double sx, double sy) {
        return new double[]{
                viewX + (sx - getWidth()  * 0.5) / zoom,
                viewY + (sy - getHeight() * 0.5) / zoom
        };
    }

    /* ==================== 视图控制 ==================== */
    public double getViewX() { return viewX; }
    public double getViewY() { return viewY; }
    public double getZoom()  { return zoom;  }

    public void centerOn(double wx, double wy) { viewX = wx; viewY = wy; layoutNodes(); repaint(); }
    public void setZoom(double z) {
        double nz = z < minZoom ? minZoom : (z > maxZoom ? maxZoom : z);
        if (nz == zoom) return;
        zoom = nz; layoutNodes(); repaint();
    }
    public void zoomBy(double factor) { setZoom(zoom * factor); }
    public void setZoomRange(double lo, double hi) {
        minZoom = Math.max(1e-9, lo);
        maxZoom = Math.max(minZoom, hi);
        if (zoom < minZoom) zoom = minZoom; else if (zoom > maxZoom) zoom = maxZoom;
        layoutNodes(); repaint();
    }
    public void resetView() { viewX = 0; viewY = 0; zoom = 1.0; layoutNodes(); repaint(); }

    public boolean isWheelZoomEnabled() { return wheelZoomEnabled; }
    public void setWheelZoomEnabled(boolean on) { wheelZoomEnabled = on; }
    public boolean isPanEnabled() { return panEnabled; }
    public void setPanEnabled(boolean on) { panEnabled = on; }

    /* ==================== 嵌套支持 ==================== */

    /**
     * 开启后，该画板的像素尺寸一旦变化，就按比例同步 zoom。
     * 用于作为子画板嵌入 PaintBoard / 另一个 XYBoard 时，让内部世界跟随一起缩放。
     * 基准尺寸取调用时刻的 getWidth()/getHeight()；若此刻尺寸为 0，
     * 则在首次获得有效尺寸时自动捕获基准。
     */
    public void setAutoZoomOnResize(boolean on) {
        autoZoomOnResize = on;
        if (on) {
            int W = getWidth(), H = getHeight();
            if (W > 0 && H > 0) {
                autoBaseW = W; autoBaseH = H; autoBaseZoom = zoom;
                autoBasePending = false;
            } else {
                autoBasePending = true;
            }
        } else {
            autoBasePending = false;
        }
    }

    /** 显式指定基准尺寸（推荐在挂载前调用，用 preferredSize 作基准）。 */
    public void setAutoZoomOnResize(boolean on, int baseW, int baseH) {
        autoZoomOnResize = on;
        autoBaseW = Math.max(1, baseW);
        autoBaseH = Math.max(1, baseH);
        autoBaseZoom = zoom;
        autoBasePending = false;
    }

    /* ==================== 组件管理 ==================== */
    public Node addComponent(JComponent c, double wx, double wy) {
        Dimension d = c.getPreferredSize();
        return addComponent(c, wx, wy, -d.width / 2, -d.height / 2, true);
    }

    public Node addComponent(JComponent c, double wx, double wy,
                             int anchorX, int anchorY, boolean scaleWithZoom) {
        if (c == null) return null;
        c.setFocusable(false);
        Dimension d = c.getPreferredSize();
        Node n = new Node(c, wx, wy, anchorX, anchorY,
                Math.max(1, d.width), Math.max(1, d.height),
                scaleWithZoom, zoom);
        nodes.add(n);
        add(c);
        layoutNode(n);
        return n;
    }

    /**
     * 便捷挂载子画板：自动居中 + 可选开启子画板内部同步缩放。
     * @param child         子画板（XYBoard / PaintBoard / 任意 JComponent）
     * @param wx, wy        世界坐标
     * @param scaleWithZoom 是否随本画板 zoom 一起缩放像素尺寸
     * @param autoZoomChild 是否让子画板内部 zoom 也跟随尺寸同步（仅 XYBoard 有效）
     */
    public Node addSubBoard(JComponent child, double wx, double wy,
                            boolean scaleWithZoom, boolean autoZoomChild) {
        if (child == null) return null;
        Dimension d = child.getPreferredSize();
        if (d == null || d.width <= 0 || d.height <= 0) d = new Dimension(300, 200);
        Node n = addComponent(child, wx, wy, -d.width / 2, -d.height / 2, scaleWithZoom);
        if (autoZoomChild && child instanceof XYBoard) {
            ((XYBoard) child).setAutoZoomOnResize(true, d.width, d.height);
        }
        return n;
    }

    public void removeComponent(Node n) {
        if (n == null) return;
        if (nodes.remove(n)) remove(n.comp);
    }
    public void clearComponents() {
        for (int i = nodes.size() - 1; i >= 0; i--) remove(nodes.get(i).comp);
        nodes.clear();
    }
    public int getComponentCount() { return nodes.size(); }

    public void setNodeWorldPos(Node n, double x, double y) {
        if (n == null) return;
        n.x = x; n.y = y; layoutNode(n);
    }
    public void setNodeAnchor(Node n, int ax, int ay) {
        if (n == null) return;
        n.ax = ax; n.ay = ay; layoutNode(n);
    }
    public void setNodeScaleWithZoom(Node n, boolean on) {
        if (n == null) return;
        n.scaleWithZoom = on; layoutNode(n);
    }
    public void setNodeScaleLimit(Node n, float min, float max) {
        if (n == null) return;
        n.minS = Math.max(0.01f, min);
        n.maxS = Math.max(n.minS, max);
        layoutNode(n);
    }
    public void setNodeResizable(Node n, boolean cantiaozhengdaxiao) {
        if (n == null || n.cantiaozhengdaxiao == cantiaozhengdaxiao) return;
        n.cantiaozhengdaxiao = cantiaozhengdaxiao;
        repaint();
    }
    public boolean isNodeResizable(Node n) { return n != null && n.cantiaozhengdaxiao; }

    public void refreshNodeSize(Node n) {
        if (n == null) return;
        Dimension d = n.comp.getPreferredSize();
        if (d != null && d.width > 0 && d.height > 0) { n.w = d.width; n.h = d.height; }
        layoutNode(n);
    }

    /* ==================== 内部布局 ==================== */
    private void layoutNodes() {
        for (int i = 0, s = nodes.size(); i < s; i++) layoutNode(nodes.get(i));
    }
    private void layoutNode(Node n) {
        int W = getWidth(), H = getHeight();
        if (W <= 0 || H <= 0) return;

        double ns = 1.0;
        if (n.scaleWithZoom) {
            ns = zoom / n.baseZoom;
            if (ns < n.minS) ns = n.minS; else if (ns > n.maxS) ns = n.maxS;
        }
        int sw = Math.max(1, (int) Math.round(n.w * ns));
        int sh = Math.max(1, (int) Math.round(n.h * ns));
        int sx = (int) Math.round((n.x - viewX) * zoom + W * 0.5 + n.ax * ns);
        int sy = (int) Math.round((n.y - viewY) * zoom + H * 0.5 + n.ay * ns);

        if (n.lastX != sx || n.lastY != sy || n.lastW != sw || n.lastH != sh) {
            n.lastX = sx; n.lastY = sy; n.lastW = sw; n.lastH = sh;
            n.comp.setBounds(sx, sy, sw, sh);
        }
        n.comp.setVisible(sx + sw > 0 && sx < W && sy + sh > 0 && sy < H);
    }

    /* ==================== 绘制 ==================== */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        WorldPainter p = worldPainter;
        if (p != null) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                p.paintWorld(g2, this);
            } finally {
                g2.dispose();
            }
        }
    }

    @Override
    protected void paintChildren(Graphics g) {
        super.paintChildren(g);
        if (nodes.isEmpty()) return;
        int W = getWidth(), H = getHeight();
        if (W <= 0 || H <= 0) return;
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            for (int i = 0, s = nodes.size(); i < s; i++) {
                Node n = nodes.get(i);
                if (!n.cantiaozhengdaxiao || !n.comp.isVisible()) continue;
                int hx = n.lastX + n.lastW;
                int hy = n.lastY + n.lastH;
                if (hx + HANDLE_PX <= 0 || hy + HANDLE_PX <= 0 || hx >= W || hy >= H) continue;
                g2.setColor(HANDLE_FILL);
                g2.fillRect(hx, hy, HANDLE_PX, HANDLE_PX);
                g2.setColor(HANDLE_BORDER);
                g2.drawRect(hx, hy, HANDLE_PX, HANDLE_PX);
            }
        } finally {
            g2.dispose();
        }
    }

    /* ==================== 内部工具 ==================== */
    private Node hitHandle(int sx, int sy) {
        for (int i = nodes.size() - 1; i >= 0; i--) {
            Node n = nodes.get(i);
            if (!n.cantiaozhengdaxiao || !n.comp.isVisible()) continue;
            int hx = n.lastX + n.lastW;
            int hy = n.lastY + n.lastH;
            if (sx >= hx && sx < hx + HANDLE_PX && sy >= hy && sy < hy + HANDLE_PX) return n;
        }
        return null;
    }

    private void fireMouse(int sx, int sy, boolean inside) {
        MouseWorldListener l = mouseListener;
        if (l == null) return;
        if (!inside) { l.onMouseWorld(0, 0, false); return; }
        int W = getWidth(), H = getHeight();
        if (W <= 0 || H <= 0) { l.onMouseWorld(0, 0, false); return; }
        l.onMouseWorld(viewX + (sx - W * 0.5) / zoom,
                viewY + (sy - H * 0.5) / zoom, true);
    }
}