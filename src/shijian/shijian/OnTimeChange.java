package shijian.shijian;

import java.lang.annotation.*;


@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface OnTimeChange {
    int priority() default 0;
}