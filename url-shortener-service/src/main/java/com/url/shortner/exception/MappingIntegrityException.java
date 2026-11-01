package com.url.shortner.exception;

public class MappingIntegrityException extends RuntimeException {
    public MappingIntegrityException(String message) { super(message); }
    public MappingIntegrityException(String message, Throwable cause) { super(message, cause); }
}
