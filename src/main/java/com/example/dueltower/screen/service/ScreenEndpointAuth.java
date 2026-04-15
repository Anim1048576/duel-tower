package com.example.dueltower.screen.service;

import com.example.dueltower.screen.dto.ScreenActionAuth;

public record ScreenEndpointAuth(
        String policyGroup,
        ScreenActionAuth requiredAuth
) {
    public static final ScreenEndpointAuth SESSION_READABLE =
            new ScreenEndpointAuth("SESSION_READABLE", ScreenActionAuth.SESSION_READABLE);
    public static final ScreenEndpointAuth PLAYER_SELF =
            new ScreenEndpointAuth("PLAYER_SELF", ScreenActionAuth.PLAYER_TOKEN);
    public static final ScreenEndpointAuth GM_ONLY =
            new ScreenEndpointAuth("GM_ONLY", ScreenActionAuth.GM_TOKEN);
    public static final ScreenEndpointAuth AUTHENTICATED_WEB =
            new ScreenEndpointAuth("AUTHENTICATED_WEB", ScreenActionAuth.LOGIN_COOKIE);

    public ScreenEndpointAuth {
        if (policyGroup == null || policyGroup.isBlank()) {
            throw new IllegalArgumentException("policyGroup must not be blank");
        }
        if (requiredAuth == null) {
            throw new IllegalArgumentException("requiredAuth must not be null");
        }
    }
}
