package io.deepvaluer.jpa.merger.exception;

/**
 * Categorizes the types of errors that can occur during a merge operation.
 */
public enum MergeErrorCode {
    DUPLICATE_ENTITY,
    VALIDATION_FAILED,
    ENTITY_NOT_FOUND,
    POLICY_NOT_FOUND,
    ACCESS_ERROR
}
