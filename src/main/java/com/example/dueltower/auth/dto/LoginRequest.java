package com.example.dueltower.auth.dto;

public record LoginRequest(
        String username,
        String password
) {
}
