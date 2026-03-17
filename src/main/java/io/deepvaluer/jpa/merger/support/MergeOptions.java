package io.deepvaluer.jpa.merger.support;

import java.util.*;

public class MergeOptions {
    private final boolean overwriteNullForced;
    private final boolean overwriteNull;
    private final Set<String> overwriteNullFields;
    private final Set<String> excludeFields;

    private MergeOptions(Builder b) {
        this.overwriteNullForced = b.overwriteNullForced;
        this.overwriteNull = b.overwriteNull;
        this.overwriteNullFields = Collections.unmodifiableSet(b.overwriteNullFields);
        this.excludeFields = Collections.unmodifiableSet(b.excludeFields);
    }

    public boolean isOverwriteForced() {
        return overwriteNullForced;
    }

    public boolean isOverwriteNull() {
        return overwriteNull;
    }

    public Set<String> getOverwriteNullFields() {
        return overwriteNullFields;
    }

    public Set<String> getExcludeFields() {
        return excludeFields;
    }

    public static class Builder {
        private boolean overwriteNullForced = false;
        private boolean overwriteNull = false;
        private Set<String> overwriteNullFields = new HashSet<>();
        private Set<String> excludeFields = new HashSet<>();

        public Builder overwriteNullForced(boolean yes) {
            this.overwriteNullForced = yes;
            return this;
        }

        public Builder overwriteNull(boolean yes) {
            this.overwriteNull = yes;
            return this;
        }

        public Builder overwriteNullFor(String... fields) {
            Collections.addAll(this.overwriteNullFields, fields);
            return this;
        }

        public Builder excludeFields(String... fields) {
            Collections.addAll(this.excludeFields, fields);
            return this;
        }

        public MergeOptions build() {
            return new MergeOptions(this);
        }
    }
}
