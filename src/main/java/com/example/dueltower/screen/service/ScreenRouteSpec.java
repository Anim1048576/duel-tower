package com.example.dueltower.screen.service;

public enum ScreenRouteSpec {
    PLAYER_LOBBY(
            "PlayerLobby",
            "/api/screens/sessions/{code}/player-lobby",
            ScreenEndpointAuth.SESSION_READABLE
    ),
    GM_LOBBY(
            "GmLobby",
            "/api/screens/sessions/{code}/gm-lobby",
            ScreenEndpointAuth.SESSION_READABLE
    ),
    COMBAT(
            "Combat",
            "/api/screens/sessions/{code}/combat",
            ScreenEndpointAuth.SESSION_READABLE
    ),
    DECK_EDITOR(
            "DeckEditor",
            "/api/screens/decks/{id}/editor",
            ScreenEndpointAuth.AUTHENTICATED_WEB
    ),
    NEW_DECK_EDITOR(
            "DeckEditor",
            "/api/screens/decks/new/editor",
            ScreenEndpointAuth.AUTHENTICATED_WEB
    );

    private final String screenKey;
    private final String routeTemplate;
    private final ScreenEndpointAuth readAuth;

    ScreenRouteSpec(String screenKey,
                    String routeTemplate,
                    ScreenEndpointAuth readAuth) {
        this.screenKey = screenKey;
        this.routeTemplate = routeTemplate;
        this.readAuth = readAuth;
    }

    public String screenKey() {
        return screenKey;
    }

    public String routeTemplate() {
        return routeTemplate;
    }

    public ScreenEndpointAuth readAuth() {
        return readAuth;
    }
}
