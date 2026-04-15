package com.example.dueltower.screen.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Shared Screen API envelope.
 *
 * <p>세부 화면 응답은 이 클래스를 상속하거나 포함해서 {@code screenKey}, 생성 시각,
 * 공통 안내문, action 목록을 재사용한다.</p>
 */
public class ScreenResponseBase {
    private final String screenKey;
    private final OffsetDateTime generatedAt;
    private final List<String> uiNotices;
    private final List<ScreenActionDto> possibleActions;

    public ScreenResponseBase(String screenKey,
                              OffsetDateTime generatedAt,
                              List<String> uiNotices,
                              List<ScreenActionDto> possibleActions) {
        this.screenKey = requireText(screenKey, "screenKey");
        this.generatedAt = Objects.requireNonNull(generatedAt, "generatedAt must not be null");
        this.uiNotices = (uiNotices == null) ? List.of() : List.copyOf(uiNotices);
        this.possibleActions = (possibleActions == null) ? List.of() : List.copyOf(possibleActions);
    }

    public ScreenResponseBase(String screenKey,
                              OffsetDateTime generatedAt,
                              List<ScreenActionDto> possibleActions) {
        this(screenKey, generatedAt, List.of(), possibleActions);
    }

    public String getScreenKey() {
        return screenKey;
    }

    public OffsetDateTime getGeneratedAt() {
        return generatedAt;
    }

    public List<String> getUiNotices() {
        return uiNotices;
    }

    public List<ScreenActionDto> getPossibleActions() {
        return possibleActions;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
