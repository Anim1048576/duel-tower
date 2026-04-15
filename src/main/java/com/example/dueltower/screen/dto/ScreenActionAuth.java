package com.example.dueltower.screen.dto;

/**
 * Wire-level auth markers for Screen API actions.
 */
public enum ScreenActionAuth {
    PUBLIC("public"),
    SESSION_READABLE("sessionReadable"),
    PLAYER_TOKEN("playerToken"),
    GM_TOKEN("gmToken"),
    LOGIN_COOKIE("loginCookie");

    private final String wireValue;

    ScreenActionAuth(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static ScreenActionAuth fromWireValue(String wireValue) {
        for (ScreenActionAuth value : values()) {
            if (value.wireValue.equals(wireValue)) {
                return value;
            }
        }
        throw new IllegalArgumentException("unsupported screen action auth: " + wireValue);
    }
}
