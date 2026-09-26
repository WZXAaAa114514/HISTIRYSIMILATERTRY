package neirong.gongju.zhujie;

import neirong.gongju.xuanranqi.PaintBoard;

import javax.swing.Timer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * 全局注册表。
 * 绑定的对象会被定时器每 tick 一次比较所有 @Live 字段，发现变化就 repaint。
 */
public final class LiveRegistry {

    private static final Map<Object, Entry> TABLE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static Timer TIMER;

    private LiveRegistry() {}

    /** 绑定一个数据对象。多次绑定同一对象只会保留最新绑定的 board。 */
    public static void bind(Object target, PaintBoard board) {
        if (target == null || board == null) return;
        TABLE.put(target, new Entry(target, board));
    }

    public static void unbind(Object target) {
        if (target == null) return;
        TABLE.remove(target);
    }

    public static void clear() {
        TABLE.clear();
    }

    /** 手动触发一次检测（一般不用，定时器会调）。 */
    public static void tick() {
        List<Entry> snapshot;
        synchronized (TABLE) {
            snapshot = new ArrayList<>(TABLE.values());
        }
        for (int i = 0; i < snapshot.size(); i++) {
            snapshot.get(i).check();
        }
    }

    /** 启动定时器，periodMs 建议 16~50。重复调用只有第一次生效。 */
    public static synchronized void startTimer(int periodMs) {
        if (TIMER != null) return;
        int p = Math.max(1, periodMs);
        TIMER = new Timer(p, e -> tick());
        TIMER.setRepeats(true);
        TIMER.start();
    }

    public static synchronized void stopTimer() {
        if (TIMER != null) {
            TIMER.stop();
            TIMER = null;
        }
    }

    // ================= 内部 =================

    private static final class Entry {
        final Object target;
        final PaintBoard board;
        final List<Slot> slots = new ArrayList<>();

        Entry(Object target, PaintBoard board) {
            this.target = target;
            this.board  = board;

            Class<?> clazz = target.getClass();
            while (clazz != null && clazz != Object.class) {

                for (Field f : clazz.getDeclaredFields()) {
                    Live live = f.getAnnotation(Live.class);
                    if (live == null) continue;
                    f.setAccessible(true);
                    slots.add(new Slot(f, null, target));
                }

                for (Method m : clazz.getDeclaredMethods()) {
                    Live live = m.getAnnotation(Live.class);
                    if (live == null || m.getParameterCount() != 0) continue;
                    m.setAccessible(true);
                    slots.add(new Slot(null, m, target));
                }

                clazz = clazz.getSuperclass();
            }
        }

        void check() {
            boolean dirty = false;
            for (int i = 0, n = slots.size(); i < n; i++) {
                if (slots.get(i).refresh()) dirty = true;
            }
            if (dirty) board.repaint();
        }
    }

    private static final class Slot {
        final Field f;
        final Method m;
        final Object target;
        Object cached;

        Slot(Field f, Method m, Object target) {
            this.f = f;
            this.m = m;
            this.target = target;
            this.cached = read();
        }

        Object read() {
            try {
                if (f != null) return f.get(target);
                return m.invoke(target);
            } catch (Exception e) {
                return null;
            }
        }

        boolean refresh() {
            Object now = read();
            if (!Objects.equals(now, cached)) {
                cached = now;
                return true;
            }
            return false;
        }
    }
}