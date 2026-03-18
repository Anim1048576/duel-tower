package com.example.dueltower.common.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ApiErrorResolver {
    private ApiErrorResolver() {}

    public static ApiErrorResponse fromApiErrorException(ApiErrorException ex, String path) {
        return ApiErrorResponse.of(
                ex.code(),
                ex.category(),
                ex.userMessage(),
                ex.debugMessage(),
                ex.details(),
                ex.status().value(),
                path
        );
    }

    public static ApiErrorResponse fromResponseStatusException(ResponseStatusException ex, String path) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String reason = normalizeMessage(ex.getReason(), status.getReasonPhrase());
        String category = categoryFor(status);
        return ApiErrorResponse.of(
                inferCode(status, reason),
                category,
                reason,
                reason,
                null,
                status.value(),
                path
        );
    }

    public static ApiErrorResponse fromIllegalArgumentException(IllegalArgumentException ex, String path) {
        String message = normalizeMessage(ex.getMessage(), "invalid request");
        return ApiErrorResponse.of("INVALID_REQUEST", "VALIDATION", message, message, null, 400, path);
    }

    public static ApiErrorResponse internal(Throwable ex, String path) {
        return ApiErrorResponse.of(
                "INTERNAL_ERROR",
                "INTERNAL",
                "예상하지 못한 오류가 발생했습니다.",
                normalizeMessage(ex.getMessage(), ex.getClass().getSimpleName()),
                null,
                500,
                path
        );
    }

    public static ApiErrorResponse commandRejection(List<String> errors) {
        List<String> normalized = (errors == null) ? List.of() : errors.stream().map(String::valueOf).toList();
        String primary = normalized.isEmpty() ? "요청을 처리할 수 없습니다." : normalized.get(0);
        return ApiErrorResponse.of(
                inferCommandCode(normalized),
                "RULE",
                userMessageForCommandErrors(normalized, primary),
                String.join("; ", normalized),
                normalized.isEmpty() ? null : Map.of("errors", normalized),
                409,
                null
        );
    }

    private static String inferCommandCode(List<String> errors) {
        if (contains(errors, "pending decision mismatch") || contains(errors, "no pending decision")) return "INVALID_PENDING_DECISION";
        if (contains(errors, "selected id not in candidates") || contains(errors, "selected count mismatch")) return "INVALID_SEARCH_PICK_SELECTION";
        if (contains(errors, "pending decision exists")) return "PENDING_DECISION_REQUIRED";
        if (contains(errors, "not your turn") || contains(errors, "invalid phase")) return "INVALID_COMBAT_ACTION";
        return "COMMAND_REJECTED";
    }

    private static String userMessageForCommandErrors(List<String> errors, String fallback) {
        if (contains(errors, "pending decision mismatch") || contains(errors, "no pending decision")) {
            return "현재 해결할 수 있는 대기 중 결정이 없습니다.";
        }
        if (contains(errors, "selected id not in candidates") || contains(errors, "selected count mismatch")) {
            return "선택 가능한 카드와 선택 개수를 다시 확인해 주세요.";
        }
        if (contains(errors, "pending decision exists")) {
            return "대기 중인 결정을 먼저 해결해야 합니다.";
        }
        if (contains(errors, "not your turn")) {
            return "지금은 해당 행동을 사용할 수 없습니다.";
        }
        return fallback;
    }

    private static boolean contains(List<String> errors, String fragment) {
        return errors.stream().anyMatch(error -> error != null && error.contains(fragment));
    }

    private static String inferCode(HttpStatus status, String reason) {
        if (status == HttpStatus.BAD_REQUEST) {
            if (reason.contains("deck edit invalid")) return "DECK_EDIT_INVALID";
            if (reason.contains("locked-in-deck")) return "CARD_LOCKED_IN_DECK";
            if (reason.contains("cannot forget owned card")) return "CARD_NOT_FORGETTABLE";
            if (reason.contains("cannot resolve forgetting required") || reason.contains("forgetting required")) return "FORGET_REQUIRED";
            if (reason.contains("owned card unavailable")) return "OWNED_CARD_UNAVAILABLE";
            if (reason.contains("pending decision")) return "INVALID_PENDING_DECISION";
            return "INVALID_REQUEST";
        }
        if (status == HttpStatus.FORBIDDEN) {
            if (reason.contains("deck edit unavailable") || reason.contains("deck cannot be edited")) return "DECK_EDIT_FORBIDDEN";
            if (reason.contains("forgetting required")) return "FORGET_REQUIRED";
            return "FORBIDDEN";
        }
        if (status == HttpStatus.NOT_FOUND) return "NOT_FOUND";
        if (status == HttpStatus.CONFLICT) return "CONFLICT";
        if (status == HttpStatus.UNAUTHORIZED) return "UNAUTHORIZED";
        return status.name();
    }

    private static String categoryFor(HttpStatus status) {
        if (status.is4xxClientError()) {
            return switch (status) {
                case NOT_FOUND -> "NOT_FOUND";
                case CONFLICT -> "CONFLICT";
                case FORBIDDEN, UNAUTHORIZED -> "RULE";
                default -> "VALIDATION";
            };
        }
        return "INTERNAL";
    }

    private static String normalizeMessage(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        return value;
    }

    public static Map<String, Object> details(Object... kvPairs) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kvPairs.length; i += 2) {
            out.put(String.valueOf(kvPairs[i]), kvPairs[i + 1]);
        }
        return out;
    }
}
