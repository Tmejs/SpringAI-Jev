package com.tmejs.springaijev.draft;

public class ReplyUnavailableException extends RuntimeException {
    public ReplyUnavailableException(String message) { super(message); }
    public ReplyUnavailableException(String message, Throwable cause) { super(message, cause); }
}
