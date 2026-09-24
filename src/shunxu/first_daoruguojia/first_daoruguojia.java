package shunxu.util;

import shijianjianting.bianliang.guojia.country;
import shijianjianting.gongju.bianliang;
import shunxu.first_daoruguojia.jianting_daoruguojia;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Vector;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 扫描指定包下的所有类（支持目录 + jar）。
 */
public class first_daoruguojia {
    private static final String SCAN_PACKAGE = "shijianjianting.shijian";

    public static void daoruguojia() {
        // 1. 拿到该包下所有类
        for (Class<?> clazz : scan(SCAN_PACKAGE)) {

            // 2. 遍历类中的方法
            for (Method method : clazz.getDeclaredMethods()) {

                // 3. 只处理被 @jianting_daoruguojia 标记的方法
                if (!method.isAnnotationPresent(jianting_daoruguojia.class)) continue;

                try {
                    method.setAccessible(true);

                    Object result;
                    if (Modifier.isStatic(method.getModifiers())) {
                        // 静态方法
                        result = method.invoke(null, new Vector<country>());
                    } else {
                        // 实例方法：先 new 一个对象
                        Object instance = clazz.getDeclaredConstructor().newInstance();
                        result = method.invoke(instance, new Vector<country>());
                    }

                    if (result instanceof Vector) {
                        @SuppressWarnings("unchecked")
                        Vector<country> countries = (Vector<country>) result;
                        if (!countries.isEmpty()) {
                            bianliang.countries.addAll(countries);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("加载国家失败 -> " + clazz.getName() + "#" + method.getName());
                    e.printStackTrace();
                }
            }
        }
    }
    public static List<Class<?>> scan(String packageName) {
        List<Class<?>> classes = new ArrayList<>();
        String path = packageName.replace('.', '/');
        ClassLoader loader = Thread.currentThread().getContextClassLoader();

        try {
            Enumeration<URL> resources = loader.getResources(path);
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                String protocol = url.getProtocol();

                if ("file".equals(protocol)) {
                    String filePath = URLDecoder.decode(url.getFile(), StandardCharsets.UTF_8);
                    findClassesInDir(packageName, filePath, classes, loader);
                } else if ("jar".equals(protocol)) {
                    findClassesInJar(url, path, classes, loader);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("扫描包失败: " + packageName, e);
        }
        return classes;
    }

    private static void findClassesInDir(String pkg, String dir,
                                         List<Class<?>> out, ClassLoader loader) {
        File[] files = new File(dir).listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                findClassesInDir(pkg + "." + f.getName(), f.getAbsolutePath(), out, loader);
            } else if (f.getName().endsWith(".class")) {
                String simple = f.getName().substring(0, f.getName().length() - 6);
                // 过滤掉内部类 / 匿名类
                if (simple.contains("$")) continue;
                String className = pkg + "." + simple;
                try {
                    // initialize=false，避免触发静态代码块
                    out.add(Class.forName(className, false, loader));
                } catch (Throwable ignore) {}
            }
        }
    }

    private static void findClassesInJar(URL url, String path,
                                         List<Class<?>> out, ClassLoader loader) throws IOException {
        JarURLConnection conn = (JarURLConnection) url.openConnection();
        try (JarFile jar = conn.getJarFile()) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry e = entries.nextElement();
                String name = e.getName();
                if (!name.startsWith(path) || !name.endsWith(".class")) continue;
                String simple = name.substring(path.length(), name.length() - 6);
                if (simple.contains("$")) continue;
                String className = name.substring(0, name.length() - 6).replace('/', '.');
                try {
                    out.add(Class.forName(className, false, loader));
                } catch (Throwable ignore) {}
            }
        }
    }
}