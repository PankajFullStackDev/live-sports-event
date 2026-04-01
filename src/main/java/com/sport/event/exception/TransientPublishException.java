package com.sport.event.exception;

public class TransientPublishException extends RuntimeException {
    public TransientPublishException(Throwable cause) {
        super(cause);
    }
}

