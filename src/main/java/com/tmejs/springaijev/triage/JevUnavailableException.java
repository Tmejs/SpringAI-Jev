package com.tmejs.springaijev.triage;

public class JevUnavailableException extends RuntimeException {
    public JevUnavailableException(String message) { super(message); }
    public JevUnavailableException(String message, Throwable cause) { super(message, cause); }
}
