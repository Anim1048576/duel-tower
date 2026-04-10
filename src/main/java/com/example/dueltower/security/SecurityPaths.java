package com.example.dueltower.security;

import org.springframework.http.HttpMethod;

public final class SecurityPaths {

    private SecurityPaths() {
    }

    public static final String[] PUBLIC_WEB = {
            "/",
            "/favicon.ico",
            "/ui/**"
    };

    public static final String API_CONTENT = "/api/content/**";

    public static final String[] AUTH_PUBLIC = {
            "/api/auth/signup",
            "/api/auth/login"
    };

    public static final String[] AUTH_REQUIRED = {
            "/api/auth/me",
            "/api/auth/logout",
            "/api/me/presets/**"
    };

    public static final String[] SESSION_LOGIN_REQUIRED = {
            "/api/sessions",
            "/api/sessions/*/join"
    };

    public static final String[] SESSION_PUBLIC = {
            "/api/sessions/*",
            "/api/sessions/*/state"
    };

    public static final String[] SESSION_READABLE = {
            "/api/sessions/*/events",
            "/api/sessions/*/logs",
            "/api/sessions/*/recent-results",
            "/api/sessions/*/run",
            "/api/sessions/*/inventory",
            "/api/sessions/*/results",
            "/api/sessions/*/choices"
    };

    public static final String SESSION_COMMAND = "/api/sessions/*/command";

    public static final String[] SESSION_PLAYER_SELF_POST = {
            "/api/sessions/*/leave",
            "/api/sessions/*/players/*/forget",
            "/api/sessions/*/players/*/deck",
            "/api/sessions/*/players/*/loadout",
            "/api/sessions/*/players/*/loadout/from-preset"
    };

    public static final String[] SESSION_PLAYER_SELF_PUT = {
            "/api/sessions/*/players/*/ready"
    };

    public static final String[] SESSION_GM_ONLY_POST = {
            "/api/sessions/*/players/*/kick",
            "/api/sessions/*/reset"
    };

    public static final String[] SESSION_GM_ONLY_DELETE = {
            "/api/sessions/*"
    };

    public static final String API_ALL = "/api/**";

    public static final HttpMethod SESSION_READ_METHOD = HttpMethod.GET;
    public static final HttpMethod SESSION_WRITE_METHOD = HttpMethod.POST;
    public static final HttpMethod SESSION_DELETE_METHOD = HttpMethod.DELETE;
    public static final HttpMethod SESSION_UPDATE_METHOD = HttpMethod.PUT;
    public static final HttpMethod CONTENT_READ_METHOD = HttpMethod.GET;
}
