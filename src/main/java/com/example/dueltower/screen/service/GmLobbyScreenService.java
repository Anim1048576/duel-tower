package com.example.dueltower.screen.service;

import com.example.dueltower.character.dto.CharacterProfileResponse;
import com.example.dueltower.character.service.CharacterProfileService;
import com.example.dueltower.content.card.service.CardService;
import com.example.dueltower.content.passive.service.PassiveService;
import com.example.dueltower.engine.model.CardDefinition;
import com.example.dueltower.engine.model.CardType;
import com.example.dueltower.engine.model.PassiveDefinition;
import com.example.dueltower.screen.dto.GmLobbyScreenResponse;
import com.example.dueltower.session.dto.SessionStateDto;
import com.example.dueltower.session.runtime.SessionRuntime;
import com.example.dueltower.session.runtime.StateMapper;
import com.example.dueltower.session.service.SessionAccessDecision;
import com.example.dueltower.session.service.SessionAccessResolver;
import com.example.dueltower.session.service.SessionLifecycleService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class GmLobbyScreenService {

    private static final String GM_LOBBY_NOTICE =
            "GM lobby participant cards, loadout summaries, and start-combat availability are assembled on the server-side screen model.";

    private final SessionLifecycleService sessionLifecycleService;
    private final SessionAccessResolver sessionAccessResolver;
    private final ScreenResponseFactory screenResponseFactory;
    private final CharacterProfileService characterProfileService;
    private final CardService cardService;
    private final PassiveService passiveService;

    public GmLobbyScreenService(SessionLifecycleService sessionLifecycleService,
                                SessionAccessResolver sessionAccessResolver,
                                ScreenResponseFactory screenResponseFactory,
                                CharacterProfileService characterProfileService,
                                CardService cardService,
                                PassiveService passiveService) {
        this.sessionLifecycleService = sessionLifecycleService;
        this.sessionAccessResolver = sessionAccessResolver;
        this.screenResponseFactory = screenResponseFactory;
        this.characterProfileService = characterProfileService;
        this.cardService = cardService;
        this.passiveService = passiveService;
    }

    public GmLobbyScreenResponse getScreen(String code,
                                           String gmTokenHeader,
                                           String playerTokenHeader,
                                           Authentication authentication) {
        return sessionLifecycleService.withLockedSession(code, rt -> {
            SessionAccessDecision decision = sessionAccessResolver.requireSessionReadable(rt, gmTokenHeader, playerTokenHeader, authentication);
            return buildScreen(rt, decision);
        });
    }

    GmLobbyScreenResponse buildScreen(SessionRuntime rt,
                                      SessionAccessDecision decision) {
        SessionStateDto state = StateMapper.toDto(rt.code(), rt.state());
        List<CharacterProfileResponse> characters = characterProfileService.list();
        List<CardDefinition> exCards = cardService.list(CardType.EX);
        List<PassiveDefinition> passives = passiveService.list();

        return screenResponseFactory.gmLobby(
                ScreenRouteSpec.GM_LOBBY,
                state,
                rt,
                decision,
                characters,
                exCards,
                passives,
                gmLobbyNotices(decision)
        );
    }

    private List<String> gmLobbyNotices(SessionAccessDecision decision) {
        List<String> notices = new ArrayList<>();
        notices.add(GM_LOBBY_NOTICE);
        if (decision.source() == SessionAccessDecision.SessionAccessSource.AUTHENTICATED_GM) {
            notices.add("Current access is login fallback. START_COMBAT can restore GM access server-side, while other GM-only actions still require a valid X-GM-Token.");
        } else if (decision.source() != SessionAccessDecision.SessionAccessSource.GM_TOKEN) {
            notices.add("Current access can read the GM lobby, but GM-only actions remain disabled without a valid X-GM-Token.");
        }
        return List.copyOf(notices);
    }
}
