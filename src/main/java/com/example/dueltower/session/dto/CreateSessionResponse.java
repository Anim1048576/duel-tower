package com.example.dueltower.session.dto;

public record CreateSessionResponse(String code, String gmId, String gmToken, SessionStateDto state) {}
