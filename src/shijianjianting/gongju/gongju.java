package shijianjianting.gongju;

public class gongju {
    public static boolean shifoushixianjiekou(Class<?> a, Class<?> b) {
        if (a == null || b == null || !b.isInterface()) {
            return false;
        }

        return b.isAssignableFrom(a);
    }
}
