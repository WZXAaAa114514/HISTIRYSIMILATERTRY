package shunxu.third_shijianpaifaqiqidong;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SubscribeEvent {
    int priority() default 0; // 数值越大越先执行
}

