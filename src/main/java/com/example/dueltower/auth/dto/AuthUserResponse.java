package com.example.dueltower.auth.dto;

import java.util.List;

public record AuthUserResponse(
        String username,
        List<String> roles
) {
}
