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

    /**
     * AUTHENTICATED_SESSION_ENTRY:
     * session 생성/참여 진입은 로그인 사용자만 허용한다.
     */
    public static final String[] AUTHENTICATED_SESSION_ENTRY = {
            "/api/sessions",
            "/api/sessions/*/join"
    };

    /**
     * PUBLIC_SESSION_STATE:
     * 공개 상태 조회는 익명 접근을 허용한다.
     */
    public static final String[] PUBLIC_SESSION_STATE = {
            "/api/sessions/*",
            "/api/sessions/*/state"
    };

    /**
     * SESSION_READABLE:
     * 세션 read API는 토큰 또는 조건부 로그인 fallback 으로만 허용한다.
     * 실제 세부 정책은 SessionAccessResolver 에서 판정한다.
     */
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

    /**
     * PLAYER_SELF:
     * 플레이어 본인 토큰으로만 허용되는 변경 API.
     */
    public static final String[] PLAYER_SELF_POST = {
            "/api/sessions/*/leave",
            "/api/sessions/*/players/*/forget",
            "/api/sessions/*/players/*/deck",
            "/api/sessions/*/players/*/loadout",
            "/api/sessions/*/players/*/loadout/from-preset"
    };

    public static final String[] PLAYER_SELF_PUT = {
            "/api/sessions/*/players/*/ready"
    };

    /**
     * GM_ONLY:
     * GM 토큰으로만 허용되는 관리 API.
     */
    public static final String[] GM_ONLY_POST = {
            "/api/sessions/*/players/*/kick",
            "/api/sessions/*/reset"
    };

    public static final String[] GM_ONLY_DELETE = {
            "/api/sessions/*"
    };

    public static final String API_ALL = "/api/**";

    public static final HttpMethod SESSION_READ_METHOD = HttpMethod.GET;
    public static final HttpMethod SESSION_WRITE_METHOD = HttpMethod.POST;
    public static final HttpMethod SESSION_DELETE_METHOD = HttpMethod.DELETE;
    public static final HttpMethod SESSION_UPDATE_METHOD = HttpMethod.PUT;
    public static final HttpMethod CONTENT_READ_METHOD = HttpMethod.GET;
}
