package neirong.gongju.zhujie;


import neirong.gongju.xuanranqi.PaintBoard;

import java.awt.Color;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.Map;

/**
 * 扫描对象上所有 @Live 字段 / 无参方法，生成一个 PaintBoard.Source。
 * Source 每次 getXxx() 都直接从目标对象读取最新值。
 */
public final class LiveBinder {

    private LiveBinder() {}

    public static PaintBoard.Source toSource(Object target) {
        if (target == null) throw new IllegalArgumentException("target == null");

        Map<Live.Role, Accessor> map = new EnumMap<>(Live.Role.class);

        Class<?> clazz = target.getClass();
        while (clazz != null && clazz != Object.class) {

            for (Field f : clazz.getDeclaredFields()) {
                Live live = f.getAnnotation(Live.class);
                if (live == null) continue;
                f.setAccessible(true);
                map.putIfAbsent(live.value(), Accessor.ofField(f));
            }

            for (Method m : clazz.getDeclaredMethods()) {
                Live live = m.getAnnotation(Live.class);
                if (live == null) continue;
                if (m.getParameterCount() != 0) continue;
                m.setAccessible(true);
                map.putIfAbsent(live.value(), Accessor.ofMethod(m));
            }

            clazz = clazz.getSuperclass();
        }

        return new PaintBoard.Source() {
            @Override public double getLon() {
                return readDouble(Live.Role.LON, map, target, 0);
            }
            @Override public double getLat() {
                return readDouble(Live.Role.LAT, map, target, 0);
            }
            @Override public Color getColor() {
                Object v = read(Live.Role.COLOR, map, target);
                return v instanceof Color ? (Color) v : null;
            }
            @Override public String getText() {
                Object v = read(Live.Role.TEXT, map, target);
                return v == null ? null : v.toString();
            }
            @Override public Double getRadius() {
                Object v = read(Live.Role.RADIUS, map, target);
                return v instanceof Number ? ((Number) v).doubleValue() : null;
            }
            @Override public Float getFontSize() {
                Object v = read(Live.Role.FONT_SIZE, map, target);
                return v instanceof Number ? ((Number) v).floatValue() : null;
            }
            @Override public Boolean getVisible() {
                Object v = read(Live.Role.VISIBLE, map, target);
                return v instanceof Boolean ? (Boolean) v : null;
            }
        };
    }

    // ---------- 内部工具 ----------

    private static Object read(Live.Role role,
                               Map<Live.Role, Accessor> map,
                               Object target) {
        Accessor a = map.get(role);
        return a == null ? null : a.read(target);
    }

    private static double readDouble(Live.Role role,
                                     Map<Live.Role, Accessor> map,
                                     Object target, double def) {
        Object v = read(role, map, target);
        return v instanceof Number ? ((Number) v).doubleValue() : def;
    }

    private static final class Accessor {
        final Field f;
        final Method m;

        private Accessor(Field f, Method m) { this.f = f; this.m = m; }

        static Accessor ofField(Field f)  { return new Accessor(f, null); }
        static Accessor ofMethod(Method m){ return new Accessor(null, m); }

        Object read(Object target) {
            try {
                if (f != null) return f.get(target);
                return m.invoke(target);
            } catch (Exception e) {
                return null;
            }
        }
    }
}