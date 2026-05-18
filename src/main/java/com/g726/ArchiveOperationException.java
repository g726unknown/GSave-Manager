package com.g726;

public class ArchiveOperationException extends RuntimeException {
    public ArchiveOperationException(String message) {
        super(message);
    }

    public ArchiveOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
