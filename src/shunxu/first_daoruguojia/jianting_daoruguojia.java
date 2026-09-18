package shunxu.first_daoruguojia;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME) // 必须保留到运行时，反射才能读到
@Target(ElementType.METHOD)         // 只能打在方法上
public @interface jianting_daoruguojia {
              // 分派键，例如 "login"、"logout"
      // 优先级，数字越大越先执行
}