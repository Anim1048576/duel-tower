package com.example.dueltower.screen.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Screen action contract that the frontend can almost invoke as-is.
 *
 * <p>{@code payloadTemplate == null} means the action does not expect a request body.</p>
 */
public record ScreenActionDto(
        String id,
        String label,
        String method,
        String href,
        String auth,
        boolean enabled,
        DisabledReasonDto disabledReason,
        Map<String, Object> payloadTemplate
) {
    public ScreenActionDto {
        id = requireText(id, "id");
        label = requireText(label, "label");
        method = requireText(method, "method").toUpperCase(Locale.ROOT);
        href = requireText(href, "href");
        auth = ScreenActionAuth.fromWireValue(requireText(auth, "auth")).wireValue();
        payloadTemplate = immutablePayloadTemplate(payloadTemplate);
    }

    public static ScreenActionDto of(String id,
                                     String label,
                                     String method,
                                     String href,
                                     ScreenActionAuth auth,
                                     boolean enabled,
                                     DisabledReasonDto disabledReason,
                                     Map<String, Object> payloadTemplate) {
        return new ScreenActionDto(
                id,
                label,
                method,
                href,
                auth.wireValue(),
                enabled,
                disabledReason,
                payloadTemplate
        );
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static Map<String, Object> immutablePayloadTemplate(Map<String, Object> payloadTemplate) {
        if (payloadTemplate == null) {
            return null;
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(payloadTemplate));
    }
}
