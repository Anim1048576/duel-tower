package com.example.dueltower.auth.dto;

public record SignupRequest(
        String username,
        String email,
        String password
) {
}
