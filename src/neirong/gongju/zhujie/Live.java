package neirong.gongju.zhujie;



import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记数据对象中需要"实时映射"到画板的字段 / 无参 getter。
 * 支持在字段和方法上使用。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface Live {

    Role value();

    enum Role {
        LON, LAT,            // 位置
        COLOR, TEXT,         // 外观
        RADIUS, FONT_SIZE,   // 尺寸
        VISIBLE,             // 可见性
        BORDER_COLOR,
        BORDER_WIDTH
    }
}