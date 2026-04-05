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

    public static final String[] SESSION_AUTH_REQUIRED = {
            "/api/sessions",
            "/api/sessions/*/join"
    };

    public static final String[] SESSION_READ_PUBLIC = {
            "/api/sessions/*",
            "/api/sessions/*/events",
            "/api/sessions/*/logs",
            "/api/sessions/*/recent-results",
            "/api/sessions/*/run",
            "/api/sessions/*/inventory",
            "/api/sessions/*/results",
            "/api/sessions/*/choices"
    };

    public static final String SESSION_COMMAND = "/api/sessions/*/command";
    public static final String SESSION_LEAVE = "/api/sessions/*/leave";
    public static final String SESSION_RESET = "/api/sessions/*/reset";
    public static final String SESSION_DELETE = "/api/sessions/*";
    public static final String SESSION_KICK = "/api/sessions/*/players/*/kick";
    public static final String SESSION_DECK = "/api/sessions/*/players/*/deck";
    public static final String SESSION_LOADOUT = "/api/sessions/*/players/*/loadout";
    public static final String SESSION_LOADOUT_PRESET = "/api/sessions/*/players/*/loadout/from-preset";
    public static final String SESSION_FORGET = "/api/sessions/*/players/*/forget";

    public static final String API_ALL = "/api/**";

    public static final HttpMethod SESSION_READ_METHOD = HttpMethod.GET;
    public static final HttpMethod SESSION_JOIN_METHOD = HttpMethod.POST;
    public static final HttpMethod SESSION_DELETE_METHOD = HttpMethod.DELETE;
    public static final HttpMethod CONTENT_READ_METHOD = HttpMethod.GET;
}
