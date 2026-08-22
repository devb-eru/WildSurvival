package com.lsc.corp.wsplugin.content;

public final class ContentValidationException extends Exception {
    public ContentValidationException(String message) {
        super(message);
    }

    public ContentValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
