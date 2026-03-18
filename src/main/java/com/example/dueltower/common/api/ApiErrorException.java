package com.example.dueltower.common.api;

import org.springframework.http.HttpStatus;

public class ApiErrorException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final String category;
    private final String userMessage;
    private final String debugMessage;
    private final Object details;

    public ApiErrorException(HttpStatus status,
                             String code,
                             String category,
                             String userMessage,
                             String debugMessage,
                             Object details) {
        super(debugMessage != null && !debugMessage.isBlank() ? debugMessage : userMessage);
        this.status = status;
        this.code = code;
        this.category = category;
        this.userMessage = userMessage;
        this.debugMessage = debugMessage;
        this.details = details;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
    public String category() { return category; }
    public String userMessage() { return userMessage; }
    public String debugMessage() { return debugMessage; }
    public Object details() { return details; }
}
