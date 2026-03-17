package io.deepvaluer.jpa.merger.domain;

import io.deepvaluer.jpa.merger.support.MergeUtils;

public interface Mergeable<T> {

    default void mergeAllFields(T source) {
        MergeUtils.mergeAll(this, source);
    }

    default void mergeWithDefaultPolicy(T source) {
        MergeUtils.merge(this, source);
    }

    default void mergeWithPolicy(T source, String policyName) {
        MergeUtils.merge(this, source, policyName);
    }

}
