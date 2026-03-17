package io.deepvaluer.jpa.merger.annotation;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface MergePolicies {
    MergePolicy[] value();
}
