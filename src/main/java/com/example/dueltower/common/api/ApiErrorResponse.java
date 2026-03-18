package com.example.dueltower.common.api;

import java.util.Map;

public record ApiErrorResponse(
        String code,
        String category,
        String userMessage,
        String debugMessage,
        Object details,
        int status,
        String path
) {
    public static ApiErrorResponse of(String code,
                                      String category,
                                      String userMessage,
                                      String debugMessage,
                                      Object details,
                                      int status,
                                      String path) {
        return new ApiErrorResponse(code, category, userMessage, debugMessage, details, status, path);
    }

    public Map<String, Object> detailMap() {
        if (details instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> casted = (Map<String, Object>) map;
            return casted;
        }
        return Map.of();
    }
}
