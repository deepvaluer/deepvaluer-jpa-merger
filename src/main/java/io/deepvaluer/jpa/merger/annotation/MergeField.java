package io.deepvaluer.jpa.merger.annotation;

import java.lang.annotation.*;

/**
 * 필드 레벨에서 머지 동작을 제어하는 애노테이션
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface MergeField {
    /** 이 필드를 항상 머지에서 제외할지 여부 */
    boolean exclude() default false;

    /** null 값을 덮어쓸지 여부 */
    boolean overwriteNull() default false;

    /** null 값은 무시할지 여부 */
    boolean ignoreNull() default false;
}
