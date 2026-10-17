package shijian.shijian;

import java.lang.annotation.*;


@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface OnTimeChange {
    long priority() default 0;
}