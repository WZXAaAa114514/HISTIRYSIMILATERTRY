package shunxu.first_daoruguojia;

import com.sun.tools.javac.Main;
import shijianjianting.bianliang.guojia.country;
import shijianjianting.gongju.bianliang;
import shijianjianting.shijian.shijian;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Vector;

public class first_daoruguojia {
    public static void daoruguojia(){
        Class<shijian> clazz = shijian.class;

        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(jianting_daoruguojia.class)) {
                jianting_daoruguojia anno = method.getAnnotation(jianting_daoruguojia.class);
                try {
                    Vector<country> countries= (Vector<country>) method.invoke(null,new Vector<country>());
                    bianliang.countries.addAll(countries);




                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                } catch (InvocationTargetException e) {
                    throw new RuntimeException(e);
                }

            }
        }
    }
}
