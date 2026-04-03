package com.example.dueltower.content.support;

import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.function.Function;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Content 단건 조회 공통 처리:
 * - id 정규화(trim)
 * - not found 응답 일관화(404)
 */
public final class ContentLookupSupport {
    private ContentLookupSupport() {
    }

    public static String normalizeId(String rawId) {
        return rawId == null ? "" : rawId.trim();
    }

    public static <K, T> T requireById(
            Map<K, T> byId,
            String rawId,
            Function<String, K> keyMapper,
            String resourceName
    ) {
        String id = normalizeId(rawId);
        T value = byId.get(keyMapper.apply(id));
        if (value == null) {
            throw new ResponseStatusException(NOT_FOUND, resourceName + " not found: " + rawId);
        }
        return value;
    }
}
