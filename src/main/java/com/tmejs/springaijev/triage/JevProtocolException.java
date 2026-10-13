package com.tmejs.springaijev.triage;

public class JevProtocolException extends RuntimeException {
    public JevProtocolException(String message) { super(message); }
    public JevProtocolException(String message, Throwable cause) { super(message, cause); }
}
