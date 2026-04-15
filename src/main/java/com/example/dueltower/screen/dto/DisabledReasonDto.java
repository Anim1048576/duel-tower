package com.example.dueltower.screen.dto;

import com.example.dueltower.common.api.ApiErrorResponse;

/**
 * Disabled reason keeps the same field axis as {@link ApiErrorResponse} so the frontend can
 * render action-disabled messages and normal API errors with one shape. When the reason is
 * precomputed instead of raised from HTTP, {@code status} and {@code path} may be {@code null}.
 */
public record DisabledReasonDto(
        String code,
        String category,
        String userMessage,
        String debugMessage,
        Object details,
        Integer status,
        String path
) {
    public static DisabledReasonDto fromApiErrorResponse(ApiErrorResponse error) {
        if (error == null) {
            return null;
        }
        return new DisabledReasonDto(
                error.code(),
                error.category(),
                error.userMessage(),
                error.debugMessage(),
                error.details(),
                error.status(),
                error.path()
        );
    }
}
