package io.deepvaluer.jpa.merger.annotation;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(MergePolicies.class)
public @interface MergePolicy {
    String name();

    boolean isDefault() default false;

    boolean overwriteNullForced() default false;

    boolean overwriteNull() default false;

    String[] overwriteNullFor() default {};

    String[] excludeFields() default {};

}
