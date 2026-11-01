package com.url.shortner.exception;

public class ShortCodeCollisionException extends RuntimeException {
    public ShortCodeCollisionException(String message, Throwable cause) { super(message, cause); }
}
