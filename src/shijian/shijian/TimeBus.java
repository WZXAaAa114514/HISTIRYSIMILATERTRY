package shijian.shijian;

import shijian.shijian.OnTimeChange;

import java.lang.invoke.*;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 时间变化总线。
 *
 * 用法：
 * <pre>
 *   TimeBus bus = new TimeBus();
 *   bus.register(new Foo());         // 扫描 @OnTimeChange 方法
 *
 *   // 每当游戏时钟推进时：
 *   bus.fire(Duration.ofSeconds(60));
 * </pre>
 *
 * 设计：
 *  - 注册时用 LambdaMetafactory 把方法编译成 Consumer&lt;Duration&gt;，运行时零反射；
 *  - 维护一个按 priority 降序排列的快照，fire 时只读快照，无需加锁；
 *  - 单个监听器抛异常不影响其它监听器。
 */
public class TimeBus {

    private static final class Node {
        final int priority;
        final Consumer<Duration> consumer;
        final Object owner;
        final String name;
        Node(int priority, Consumer<Duration> consumer, Object owner, String name) {
            this.priority = priority;
            this.consumer = consumer;
            this.owner = owner;
            this.name = name;
        }
    }

    /** 监听器快照：注册/反注册时整体替换，fire 时只读 */
    private volatile Node[] nodes = new Node[0];

    private final AtomicLong fireCount = new AtomicLong(0);

    public long getFireCount() { return fireCount.get(); }
    public int  getListenerCount() { return nodes.length; }

    /** 注册：扫描 target 上所有带 @OnTimeChange 的方法 */
    public synchronized void register(Object target) {
        if (target == null) return;

        List<Node> list = new ArrayList<>(Arrays.asList(nodes));

        for (Method method : target.getClass().getDeclaredMethods()) {
            OnTimeChange ann = method.getAnnotation(OnTimeChange.class);
            if (ann == null) continue;

            if (method.getParameterCount() != 1
                    || method.getParameterTypes()[0] != Duration.class) {
                throw new IllegalArgumentException(
                        "@OnTimeChange 方法必须形如 void m(java.time.Duration delta): " + method);
            }
            if (method.getReturnType() != void.class) {
                throw new IllegalArgumentException("@OnTimeChange 方法必须返回 void: " + method);
            }

            Consumer<Duration> c = toConsumer(target, method);
            String name = target.getClass().getSimpleName() + "#" + method.getName();
            list.add(new Node(ann.priority(), c, target, name));
        }

        list.sort((a, b) -> Integer.compare(b.priority, a.priority));
        nodes = list.toArray(new Node[0]);
    }

    /** 反注册：移除 target 注册过的所有 @OnTimeChange 监听器 */
    public synchronized void unregister(Object target) {
        List<Node> list = new ArrayList<>(nodes.length);
        for (Node n : nodes) {
            if (n.owner != target) list.add(n);
        }
        nodes = list.toArray(new Node[0]);
    }

    public synchronized void clear() {
        nodes = new Node[0];
    }

    private static Consumer<Duration> toConsumer(Object target, Method method) {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                    target.getClass(), MethodHandles.lookup());
            MethodHandle mh = lookup.unreflect(method);

            CallSite site = LambdaMetafactory.metafactory(
                    lookup,
                    "accept",
                    MethodType.methodType(Consumer.class, target.getClass()),
                    MethodType.methodType(void.class, Object.class),
                    mh,
                    MethodType.methodType(void.class, Duration.class)
            );

            @SuppressWarnings("unchecked")
            Consumer<Duration> consumer =
                    (Consumer<Duration>) site.getTarget().invoke(target);
            return consumer;
        } catch (Throwable e) {
            throw new RuntimeException("无法为方法生成时间监听器: " + method, e);
        }
    }

    /** ★ 触发：把时间增量广播给所有监听器 */
    public void fire(Duration delta) {
        if (delta == null || delta.isZero()) return;

        Node[] snapshot = nodes;   // 一次读取，全程使用这一份
        for (Node n : snapshot) {
            try {
                n.consumer.accept(delta);
            } catch (Throwable t) {
                // 单个监听器崩溃不影响其它监听器
                System.err.println("[TimeBus] 时间监听器抛出异常: " + n.name);
                t.printStackTrace();
            }
        }
        fireCount.incrementAndGet();
    }

    /** 便捷重载：按秒推进 */
    public void fireSeconds(long seconds) {
        if (seconds != 0L) fire(Duration.ofSeconds(seconds));
    }
}