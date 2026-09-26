package shunxu.anjianhuoqu;


import java.awt.GraphicsEnvironment;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 键盘按键状态工具类（支持回调式监听 + 拉取式查询）。
 *
 * <p>通过 AWT 全局 {@link KeyEventDispatcher} 实时记录当前按下的键，
 * 并提供以下能力：</p>
 * <ul>
 *     <li>拉取式：{@link #getPressedKeys()} / {@link #isKeyPressed(int)}</li>
 *     <li>状态变化回调：{@link #addListener(KeyStateListener)}</li>
 *     <li>单键事件回调：{@link #addKeyListener(SimpleKeyListener)}</li>
 *     <li>阻塞等待：{@link #awaitKey(long)}</li>
 * </ul>
 *
 * <p><b>使用前提：</b>程序运行在非 headless 的图形环境下，
 * 并且存在至少一个能获得键盘焦点的窗口（Swing / AWT）。</p>
 *
 * <p><b>注意：</b>本工具只能监听当前 Java 进程内的键盘事件。
 * 若需要监听系统全局键盘（即使程序不在前台），请使用 JNativeHook 等第三方库。</p>
 */
public final class anjianhuoqu {

    // ==================== 回调接口 ====================

    /**
     * 按键状态变化监听器。
     * 每当有键被按下或释放时触发，参数为变化后的完整按下键集合。
     */
    @FunctionalInterface
    public interface KeyStateListener {
        /**
         * @param pressedKeys 变化后当前所有处于按下状态的键码集合（不可修改）
         */
        void onKeyStateChanged(Set<Integer> pressedKeys);
    }

    /**
     * 单键事件监听器。
     * 分别对「按下」和「释放」提供回调，可在键码层面精确处理。
     */
    public interface SimpleKeyListener {
        /** 某个键被按下时回调 */
        default void onKeyPressed(int keyCode) {}

        /** 某个键被释放时回调 */
        default void onKeyReleased(int keyCode) {}
    }

    // ==================== 内部状态 ====================

    /** 当前处于按下状态的所有键码 */
    private static final Set<Integer> PRESSED_KEYS = ConcurrentHashMap.newKeySet();

    /** 用于阻塞等待按键的监视器 */
    private static final Object KEY_MONITOR = new Object();

    /** 最近一次按下的键码 */
    private static volatile int lastPressedKey = -1;

    /** 按键计数器，用于区分"同一个键被重复按下的两次事件" */
    private static volatile long pressCount = 0L;

    /** 是否已安装监听器 */
    private static volatile boolean installed = false;

    /** 保存 dispatcher 引用，便于卸载 */
    private static KeyEventDispatcher dispatcher;

    /** 状态变化监听器列表（线程安全，支持回调中增删） */
    private static final CopyOnWriteArrayList<KeyStateListener> STATE_LISTENERS = new CopyOnWriteArrayList<>();

    /** 单键事件监听器列表 */
    private static final CopyOnWriteArrayList<SimpleKeyListener> SIMPLE_LISTENERS = new CopyOnWriteArrayList<>();

    private anjianhuoqu() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    // ==================== 拉取式 API ====================

    /**
     * 获取当前按下的所有键码（KeyEvent.VK_* 常量值）。
     *
     * @return 当前按下的键码集合的快照；没有任何键按下时返回空集合
     */
    public static Set<Integer> getPressedKeys() {
        ensureInstalled();
        return Collections.unmodifiableSet(new HashSet<>(PRESSED_KEYS));
    }

    /**
     * 判断指定键当前是否被按下。
     *
     * @param keyCode 键码，例如 {@link KeyEvent#VK_SHIFT}、{@link KeyEvent#VK_A}
     */
    public static boolean isKeyPressed(int keyCode) {
        ensureInstalled();
        return PRESSED_KEYS.contains(keyCode);
    }

    /**
     * 获取最近一次按下的键码。
     *
     * @return 键码；如果至今没有任何按键事件，返回 -1
     */
    public static int getLastPressedKey() {
        ensureInstalled();
        return lastPressedKey;
    }

    /**
     * 获取当前按下的键的可读描述，例如 "Shift + A"。
     *
     * @return 描述字符串；没有按键时返回 "无"
     */
    public static String describePressedKeys() {
        ensureInstalled();
        if (PRESSED_KEYS.isEmpty()) {
            return "无";
        }
        return PRESSED_KEYS.stream()
                .map(KeyEvent::getKeyText)
                .sorted()
                .collect(Collectors.joining(" + "));
    }

    /**
     * 阻塞等待用户按下任意一个键。
     *
     * @param timeoutMillis 超时毫秒数；&lt;= 0 表示一直等待
     * @return 按下的键码；超时返回 -1
     * @throws InterruptedException 当前线程在等待时被中断
     */
    public static int awaitKey(long timeoutMillis) throws InterruptedException {
        ensureInstalled();
        long deadline = timeoutMillis > 0
                ? System.currentTimeMillis() + timeoutMillis
                : Long.MAX_VALUE;

        synchronized (KEY_MONITOR) {
            long startCount = pressCount;
            while (pressCount == startCount) {
                if (deadline == Long.MAX_VALUE) {
                    KEY_MONITOR.wait();
                } else {
                    long remain = deadline - System.currentTimeMillis();
                    if (remain <= 0) {
                        return -1;
                    }
                    KEY_MONITOR.wait(remain);
                }
            }
            return lastPressedKey;
        }
    }

    // ==================== 回调式 API ====================

    /**
     * 注册按键状态变化监听器。
     *
     * @param listener 监听器，不允许为 null
     */
    public static void addListener(KeyStateListener listener) {
        if (listener == null) {
            throw new NullPointerException("listener 不能为 null");
        }
        ensureInstalled();
        STATE_LISTENERS.add(listener);
    }

    /**
     * 移除按键状态变化监听器。
     */
    public static void removeListener(KeyStateListener listener) {
        STATE_LISTENERS.remove(listener);
    }

    /**
     * 注册单键事件监听器（可分别处理按下/释放）。
     *
     * @param listener 监听器，不允许为 null
     */
    public static void addKeyListener(SimpleKeyListener listener) {
        if (listener == null) {
            throw new NullPointerException("listener 不能为 null");
        }
        ensureInstalled();
        SIMPLE_LISTENERS.add(listener);
    }

    /**
     * 移除单键事件监听器。
     */
    public static void removeKeyListener(SimpleKeyListener listener) {
        SIMPLE_LISTENERS.remove(listener);
    }

    /**
     * 清空所有监听器。
     */
    public static void clearListeners() {
        STATE_LISTENERS.clear();
        SIMPLE_LISTENERS.clear();
    }

    // ==================== 生命周期 ====================

    /**
     * 手动清空按键状态。
     * 适用于窗口失焦后按键状态卡住的场景。
     */
    public static void clear() {
        PRESSED_KEYS.clear();
        lastPressedKey = -1;
        fireStateChanged();
    }

    /**
     * 卸载键盘监听器，释放资源。
     */
    public static synchronized void uninstall() {
        if (!installed) {
            return;
        }
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .removeKeyEventDispatcher(dispatcher);
        dispatcher = null;
        PRESSED_KEYS.clear();
        lastPressedKey = -1;
        installed = false;
        clearListeners();
    }

    // ==================== 内部实现 ====================

    /**
     * 懒加载安装全局键盘监听器。
     */
    private static synchronized void ensureInstalled() {
        if (installed) {
            return;
        }
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("当前运行环境为 headless，无法监听键盘事件");
        }

        dispatcher = event -> {
            int id = event.getID();
            int code = event.getKeyCode();

            if (id == KeyEvent.KEY_PRESSED) {
                // 有些系统的 auto-repeat 会反复发 PRESSED，
                // 用 add 的返回值判断是否为新按下，避免重复回调。
                boolean isNewPress = PRESSED_KEYS.add(code);

                synchronized (KEY_MONITOR) {
                    lastPressedKey = code;
                    pressCount++;
                    KEY_MONITOR.notifyAll();
                }

                if (isNewPress) {
                    fireKeyPressed(code);
                    fireStateChanged();
                }
            } else if (id == KeyEvent.KEY_RELEASED) {
                boolean removed = PRESSED_KEYS.remove(code);
                if (removed) {
                    fireKeyReleased(code);
                    fireStateChanged();
                }
            }
            // 返回 false，不消费事件，继续向下传递
            return false;
        };

        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(dispatcher);
        installed = true;
    }

    private static void fireStateChanged() {
        if (STATE_LISTENERS.isEmpty()) {
            return;
        }
        Set<Integer> snapshot = getPressedKeys();
        for (KeyStateListener l : STATE_LISTENERS) {
            try {
                l.onKeyStateChanged(snapshot);
            } catch (Exception ex) {
                // 隔离单个监听器的异常，避免影响其它监听器
                ex.printStackTrace();
            }
        }
    }

    private static void fireKeyPressed(int keyCode) {
        for (SimpleKeyListener l : SIMPLE_LISTENERS) {
            try {
                l.onKeyPressed(keyCode);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    private static void fireKeyReleased(int keyCode) {
        for (SimpleKeyListener l : SIMPLE_LISTENERS) {
            try {
                l.onKeyReleased(keyCode);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }
}