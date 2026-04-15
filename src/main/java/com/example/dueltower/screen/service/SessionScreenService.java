package com.example.dueltower.screen.service;

import com.example.dueltower.session.dto.SessionStateDto;
import com.example.dueltower.session.runtime.StateMapper;
import com.example.dueltower.session.service.SessionQueryService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SessionScreenService {

    private static final List<String> SESSION_STUB_NOTICE = List.of(
            "Screen API scaffold response. Move view-model composition into this service next."
    );

    private final SessionQueryService sessionQueryService;
    private final ScreenResponseFactory screenResponseFactory;

    public SessionScreenService(SessionQueryService sessionQueryService,
                                ScreenResponseFactory screenResponseFactory) {
        this.sessionQueryService = sessionQueryService;
        this.screenResponseFactory = screenResponseFactory;
    }

    public Object getPlayerLobby(String code,
                                 String gmTokenHeader,
                                 String playerTokenHeader,
                                 Authentication authentication) {
        return getSessionReadableScreen(
                ScreenRouteSpec.PLAYER_LOBBY,
                code,
                gmTokenHeader,
                playerTokenHeader,
                authentication
        );
    }

    public Object getGmLobby(String code,
                             String gmTokenHeader,
                             String playerTokenHeader,
                             Authentication authentication) {
        return getSessionReadableScreen(
                ScreenRouteSpec.GM_LOBBY,
                code,
                gmTokenHeader,
                playerTokenHeader,
                authentication
        );
    }

    public Object getCombat(String code,
                            String gmTokenHeader,
                            String playerTokenHeader,
                            Authentication authentication) {
        return getSessionReadableScreen(
                ScreenRouteSpec.COMBAT,
                code,
                gmTokenHeader,
                playerTokenHeader,
                authentication
        );
    }

    private Object getSessionReadableScreen(ScreenRouteSpec route,
                                            String code,
                                            String gmTokenHeader,
                                            String playerTokenHeader,
                                            Authentication authentication) {
        return sessionQueryService.withSessionReadableAccess(
                code,
                gmTokenHeader,
                playerTokenHeader,
                authentication,
                "GET " + route.routeTemplate(),
                rt -> {
                    SessionStateDto state = StateMapper.toDto(rt.code(), rt.state());
                    return screenResponseFactory.sessionSkeleton(route, state, SESSION_STUB_NOTICE);
                }
        );
    }
}
