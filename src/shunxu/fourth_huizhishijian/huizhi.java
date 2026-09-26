package shunxu.fourth_huizhishijian;

import shijian.shijian.TimeBus;
import shijian.time;
import neirong.gongju.bianliang;
import neirong.shijian.MyListener;


import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;


public class huizhi extends JPanel {

    /* ==================== 配色：运行（暗金） ==================== */
    private static final Color TOP_BG      = new Color(0x24, 0x22, 0x1B);
    private static final Color BOTTOM_BG   = new Color(0x11, 0x10, 0x0D);
    private static final Color BORDER_GOLD = new Color(0x5A, 0x4B, 0x28);
    private static final Color GOLD        = new Color(0xC9, 0xA9, 0x5C);
    private static final Color GOLD_DIM    = new Color(0x4A, 0x40, 0x22);
    private static final Color SLOT_BG     = new Color(0x28, 0x26, 0x1E);
    private static final Color SLOT_HOVER  = new Color(0x3C, 0x36, 0x1E);

    /* ==================== 配色：暂停（蓝） ==================== */
    private static final Color TOP_BG_PAUSE      = new Color(0x1B, 0x22, 0x33);
    private static final Color BOTTOM_BG_PAUSE   = new Color(0x0D, 0x11, 0x1A);
    private static final Color BORDER_GOLD_PAUSE = new Color(0x28, 0x48, 0x6A);
    private static final Color GOLD_PAUSE        = new Color(0x5C, 0xA9, 0xD6);
    private static final Color GOLD_DIM_PAUSE    = new Color(0x22, 0x3A, 0x4A);
    private static final Color SLOT_BG_PAUSE     = new Color(0x1E, 0x26, 0x30);
    private static final Color SLOT_HOVER_PAUSE  = new Color(0x1E, 0x36, 0x4C);

    /* ==================== 速度表 ==================== */
    private static final double[] SECONDS_PER_TICK = {0, 1, 3, 10, 60, 600};
    private static final int TICK_MS = 100;

    /* ==================== 模型 ==================== */
    private final time clock;

    /** 时间变化总线：每次推进都会 fire 一次增量 */
    private final TimeBus timeBus = new TimeBus();

    private volatile int speed = 0;
    private volatile int lastSpeed = 3;

    private double carry = 0;
    private final AtomicLong pendingSeconds = new AtomicLong(0);
    private final AtomicBoolean flushScheduled = new AtomicBoolean(false);

    private final ScheduledExecutorService ticker =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "huizhi-ticker");
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY);
                return t;
            });

    private ScheduledFuture<?> tickTask;

    /* ==================== 视图 ==================== */
    private final JLabel dateLabel = new JLabel();
    private final JLabel timeLabel = new JLabel();
    private final SpeedBar speedBar = new SpeedBar();

    private GradientPaint cachedGradient;
    private int cachedGradientH = -1;
    private boolean cachedGradientPaused = false;

    private String lastDateText = null;
    private String lastTimeText = null;

    /* ==================== 动态取色 ==================== */

    private boolean paused() { return speed == 0; }

    private Color colTopBg()      { return paused() ? TOP_BG_PAUSE      : TOP_BG; }
    private Color colBottomBg()   { return paused() ? BOTTOM_BG_PAUSE   : BOTTOM_BG; }
    private Color colBorder()     { return paused() ? BORDER_GOLD_PAUSE : BORDER_GOLD; }
    private Color colAccent()     { return paused() ? GOLD_PAUSE        : GOLD; }
    private Color colAccentDim()  { return paused() ? GOLD_DIM_PAUSE    : GOLD_DIM; }
    private Color colSlotBg()     { return paused() ? SLOT_BG_PAUSE     : SLOT_BG; }
    private Color colSlotHover()  { return paused() ? SLOT_HOVER_PAUSE  : SLOT_HOVER; }

    /* ==================== 构造 ==================== */

    public huizhi() {
        this(new time(1936, 1, 1, 0, 0, 0));
    }

    public huizhi(time clock) {
        this.clock = Objects.requireNonNull(clock, "clock 不能为 null");
        buildUI();
        applyPalette();             // 初始化颜色（构造时 speed = 0 → 蓝色，随后 setSpeed(1) 变暗金）
        refreshLabels();
        installSpaceKeyBinding();   // 空格 = 暂停 / 恢复
        setSpeed(1);
    }

    /* ==================== 对外接口 ==================== */

    public JComponent getComponent() { return this; }

    /** 时间变化总线：外部用 {@code getTimeBus().register(obj)} 注册 @OnTimeChange 方法 */
    public TimeBus getTimeBus() { return timeBus; }

    public void setSpeed(int s) {
        if (s < 0 || s > 5) {
            throw new IllegalArgumentException("速度只能是 0~5，当前传入：" + s);
        }
        synchronized (this) {
            if (s > 0 && ticker.isShutdown()) {  // dispose 之后不再启动
                return;
            }
            if (s > 0) lastSpeed = s;
            speed = s;

            if (s == 0) {
                if (tickTask != null) {
                    tickTask.cancel(false);
                    tickTask = null;
                }
            } else {
                if (tickTask == null || tickTask.isCancelled()) {
                    tickTask = ticker.scheduleAtFixedRate(
                            this::onTickBackground,
                            0, TICK_MS, TimeUnit.MILLISECONDS);
                }
            }
        }

        runOnEDT(() -> {
            applyPalette();     // 重新取色 + 更新边框 + 更新标签颜色
            repaint();
        });
    }

    public int getSpeed() { return speed; }
    public boolean isRunning() { return speed > 0; }

    public void togglePause() {
        if (speed == 0) {
            int ls = lastSpeed;
            setSpeed(ls == 0 ? 1 : ls);
        } else {
            setSpeed(0);
        }
    }

    public void dispose() {
        synchronized (this) {
            speed = 0;
            if (tickTask != null) {
                tickTask.cancel(false);
                tickTask = null;
            }
            ticker.shutdownNow();
        }
    }

    public time getClock() { return clock; }

    /* ==================== 快捷键 ==================== */

    /** 窗口内任意组件获得焦点时，按空格都能暂停 / 恢复 */
    private void installSpaceKeyBinding() {
        final String actionKey = "huizhi.togglePause";
        InputMap im = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getActionMap();
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), actionKey);
        am.put(actionKey, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                togglePause();
            }
        });
    }

    /* ==================== 界面搭建 ==================== */

    private void buildUI() {
        setLayout(new BorderLayout(18, 0));
        setOpaque(true);

        dateLabel.setFont(new Font("Serif", Font.BOLD, 20));
        timeLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 18));

        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        dateLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        timeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        textPanel.add(dateLabel);
        textPanel.add(Box.createVerticalStrut(2));
        textPanel.add(timeLabel);

        add(textPanel, BorderLayout.CENTER);
        add(speedBar, BorderLayout.EAST);
    }

    /**
     * 根据当前 speed 应用配色：
     *  - 标签前景色
     *  - 边框颜色
     *  - 触发重绘
     */
    private void applyPalette() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::applyPalette);
            return;
        }
        Color accent = colAccent();
        dateLabel.setForeground(accent);
        timeLabel.setForeground(accent);

        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(colBorder(), 1),
                new EmptyBorder(8, 14, 8, 12)));

        revalidate();          // 边框变化 → 内部可用区域变化
        speedBar.repaint();
        repaint();
    }

    /** 渐变缓存：高度变化或暂停状态变化时重建 */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        boolean p = paused();
        if (cachedGradient == null
                || cachedGradientH != h
                || cachedGradientPaused != p) {
            cachedGradient = new GradientPaint(
                    0, 0, p ? TOP_BG_PAUSE : TOP_BG,
                    0, h, p ? BOTTOM_BG_PAUSE : BOTTOM_BG);
            cachedGradientH = h;
            cachedGradientPaused = p;
        }

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setPaint(cachedGradient);
            g2.fillRect(0, 0, w, h);
        } finally {
            g2.dispose();
        }
    }

    /* ==================== 心跳 ==================== */

    private void onTickBackground() {
        int s = speed;
        if (s <= 0) return;

        carry += SECONDS_PER_TICK[s];
        long whole = (long) carry;
        if (whole <= 0L) return;
        carry -= whole;

        pendingSeconds.addAndGet(whole);

        if (flushScheduled.compareAndSet(false, true)) {
            SwingUtilities.invokeLater(this::flushOnEDT);
        }
    }

    /** EDT：批量推进 + 广播时间增量 + 刷新标签 */
    private void flushOnEDT() {
        try {
            long sec = pendingSeconds.getAndSet(0);
            if (sec > 0L) {
                clock.addSeconds(sec);       // 1) 推进游戏时钟
                timeBus.fireSeconds(sec);    // 2) 广播本次增量给 @OnTimeChange 监听器
            }
            refreshLabels();                 // 3) 刷新 UI
        } finally {
            flushScheduled.set(false);
            if (pendingSeconds.get() > 0L
                    && flushScheduled.compareAndSet(false, true)) {
                SwingUtilities.invokeLater(this::flushOnEDT);
            }
        }
    }

    private void refreshLabels() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::refreshLabels);
            return;
        }

        time.fengzhuangshijian t = clock.getDateTime();

        StringBuilder sb = new StringBuilder(20);
        sb.append(t.year()).append('年')
                .append(t.month()).append('月')
                .append(t.day()).append('日');
        String d = sb.toString();

        sb.setLength(0);
        append2(sb, t.hour());
        sb.append(':');
        append2(sb, t.minute());
        sb.append(':');
        append2(sb, t.second());
        String tm = sb.toString();

        if (!d.equals(lastDateText)) {
            dateLabel.setText(d);
            lastDateText = d;
        }
        if (!tm.equals(lastTimeText)) {
            timeLabel.setText(tm);
            lastTimeText = tm;
        }
    }

    private static void append2(StringBuilder sb, int v) {
        if (v < 10) sb.append('0');
        sb.append(v);
    }

    private static void runOnEDT(Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) r.run();
        else SwingUtilities.invokeLater(r);
    }

    /* ==================== 速度条 ==================== */

    private class SpeedBar extends JComponent {

        private static final int GAP      = 5;
        private static final int SLOT_W   = 9;
        private static final int PAUSE_W  = 20;
        private static final int BAR_H    = 26;

        private int hover = -1;

        SpeedBar() {
            int totalW = PAUSE_W + GAP + 5 * SLOT_W + 4 * GAP;
            Dimension d = new Dimension(totalW, BAR_H);
            setPreferredSize(d);
            setMinimumSize(d);
            setMaximumSize(d);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            MouseAdapter ma = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    int idx = hitTest(e.getX());
                    if (idx == 0) togglePause();
                    else if (idx > 0) setSpeed(idx);
                }

                @Override
                public void mouseMoved(MouseEvent e) {
                    int idx = hitTest(e.getX());
                    if (idx != hover) {
                        hover = idx;
                        repaint();
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    if (hover != -1) {
                        hover = -1;
                        repaint();
                    }
                }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
        }

        private int hitTest(int x) {
            if (x >= 0 && x < PAUSE_W) return 0;
            for (int i = 1; i <= 5; i++) {
                int x0 = PAUSE_W + GAP + (i - 1) * (SLOT_W + GAP);
                if (x >= x0 && x < x0 + SLOT_W) return i;
            }
            return -1;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                        RenderingHints.VALUE_STROKE_PURE);

                int bottom = getHeight() - 3;
                paintPlayPause(g2, bottom);

                for (int i = 1; i <= 5; i++) {
                    int x0 = PAUSE_W + GAP + (i - 1) * (SLOT_W + GAP);
                    int barH = 7 + i * 3;
                    int y0 = bottom - barH;

                    boolean active  = i <= speed;
                    boolean hovered = (hover == i);

                    if (active) {
                        g2.setColor(colAccent());
                    } else if (hovered) {
                        g2.setColor(colSlotHover());
                    } else {
                        g2.setColor(colAccentDim());
                    }
                    g2.fillRoundRect(x0, y0, SLOT_W, barH, 3, 3);

                    if (hovered && !active) {
                        g2.setColor(colAccent());
                        g2.drawRoundRect(x0, y0, SLOT_W, barH, 3, 3);
                    }
                }
            } finally {
                g2.dispose();
            }
        }

        private void paintPlayPause(Graphics2D g2, int bottom) {
            int size = 20;
            int y0 = bottom - size;
            boolean hov = (hover == 0);

            g2.setColor(hov ? colSlotHover() : colSlotBg());
            g2.fillRoundRect(0, y0, size, size, 4, 4);
            g2.setColor(hov ? colAccent() : colBorder());
            g2.drawRoundRect(0, y0, size, size, 4, 4);

            g2.setColor(colAccent());
            if (speed == 0) {
                // 暂停中 → 显示 ▶
                g2.fillPolygon(
                        new int[]{7, 7, 15},
                        new int[]{y0 + 5, y0 + 15, y0 + 10},
                        3);
            } else {
                // 运行中 → 显示 ‖
                g2.fillRoundRect(6, y0 + 5, 3, 10, 1, 1);
                g2.fillRoundRect(11, y0 + 5, 3, 10, 1, 1);
            }
        }
    }

    /* ==================== 演示 ==================== */
    public static void draw() {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = bianliang.gameframe;
            if (frame == null) return;

            huizhi panel = new huizhi(new time(1936, 1, 1, 0, 0, 0));
            panel.getTimeBus().register(new MyListener());
            Container content = frame.getContentPane();
            if (content.getLayout() == null) {
                content.add(panel.getComponent());
                Dimension d = panel.getComponent().getPreferredSize();
                panel.getComponent().setBounds(
                        frame.getWidth() - d.width - 10, 10,
                        d.width, d.height);
            } else {
                frame.add(panel.getComponent(), BorderLayout.NORTH);
            }

            frame.revalidate();
            frame.repaint();
        });
    }
}