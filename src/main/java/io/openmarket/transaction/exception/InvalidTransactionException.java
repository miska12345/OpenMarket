package io.openmarket.transaction.exception;

public class InvalidTransactionException extends RuntimeException {
    private final String msg;
    private final Exception exception;

    public InvalidTransactionException(final String msg) {
        this(msg, null);
    }

    public InvalidTransactionException(final String msg, Exception e) {
        this.msg = msg;
        this.exception = e;
    }

    @Override
    public String toString() {
        return String.format("InvalidTransactionException: %s, root cause: %s", msg, exception);
    }
}
