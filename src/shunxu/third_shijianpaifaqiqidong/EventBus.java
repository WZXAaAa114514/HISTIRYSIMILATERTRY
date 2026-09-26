package shunxu.third_shijianpaifaqiqidong;

import java.lang.invoke.*;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class EventBus {

    private static final class ListenerNode {
        final int priority;
        final Consumer<Object> consumer;
        ListenerNode(int priority, Consumer<Object> consumer) {
            this.priority = priority;
            this.consumer = consumer;
        }
    }

    // 事件类型 -> 监听器数组（注册后数组不再修改，读时无锁）
    private final Map<Class<?>, ListenerNode[]> listeners = new ConcurrentHashMap<>();

    // 缓存事件类的继承链，避免每次 post 都反射 getSuperclass
    private static final ClassValue<Class<?>[]> HIERARCHY = new ClassValue<>() {
        @Override
        protected Class<?>[] computeValue(Class<?> type) {
            List<Class<?>> list = new ArrayList<>();
            for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
                list.add(c);
            }
            return list.toArray(new Class<?>[0]);
        }
    };

    /** 注册：扫描目标对象所有带 @SubscribeEvent 的方法 */
    public void register(Object target) {
        for (Method method : target.getClass().getDeclaredMethods()) {
            SubscribeEvent ann = method.getAnnotation(SubscribeEvent.class);
            if (ann == null) continue;
            if (method.getParameterCount() != 1) {
                throw new IllegalArgumentException("@SubscribeEvent 方法必须只有一个参数: " + method);
            }

            Class<?> eventType = method.getParameterTypes()[0];
            Consumer<Object> consumer = toConsumer(target, method);
            ListenerNode node = new ListenerNode(ann.priority(), consumer);

            listeners.compute(eventType, (k, oldArr) -> {
                List<ListenerNode> list = oldArr == null
                        ? new ArrayList<>()
                        : new ArrayList<>(Arrays.asList(oldArr));
                list.add(node);
                list.sort((a, b) -> Integer.compare(b.priority, a.priority));
                return list.toArray(new ListenerNode[0]);
            });
        }
    }

    /** 核心：把 Method 编译成 Consumer，运行时不再有反射开销 */
    private static Consumer<Object> toConsumer(Object target, Method method) {
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
                    MethodType.methodType(void.class, method.getParameterTypes()[0])
            );

            @SuppressWarnings("unchecked")
            Consumer<Object> consumer = (Consumer<Object>) site.getTarget().invoke(target);
            return consumer;
        } catch (Throwable e) {
            throw new RuntimeException("无法为方法生成监听器: " + method, e);
        }
    }

    /** 派发 */
    public void post(Object event) {
        for (Class<?> cls : HIERARCHY.get(event.getClass())) {
            ListenerNode[] arr = listeners.get(cls);
            if (arr == null) continue;
            for (ListenerNode node : arr) {
                node.consumer.accept(event);
                if (event instanceof kequxiao c && c.isCanceled()) {
                    return; // 已取消，停止传播
                }
            }
        }
    }
}