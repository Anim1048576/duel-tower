package com.example.dueltower.session.dto;

public record RestoreGmAccessResponse(
        String code,
        String gmToken,
        SessionStateDto state
) {
}
