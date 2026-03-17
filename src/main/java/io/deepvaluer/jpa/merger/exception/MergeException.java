package io.deepvaluer.jpa.merger.exception;

/**
 * Unified exception for all merge-related operations.
 * Use the {@link #getErrorCode()} to distinguish the specific error scenario.
 */
public class MergeException extends RuntimeException {

    private final MergeErrorCode errorCode;

    public MergeException(MergeErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public MergeException(MergeErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public MergeErrorCode getErrorCode() {
        return errorCode;
    }
}
