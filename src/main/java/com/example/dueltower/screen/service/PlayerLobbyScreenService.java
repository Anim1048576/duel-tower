package com.example.dueltower.screen.service;

import com.example.dueltower.character.dto.CharacterProfileResponse;
import com.example.dueltower.character.service.CharacterProfileService;
import com.example.dueltower.content.card.service.CardService;
import com.example.dueltower.content.passive.service.PassiveService;
import com.example.dueltower.engine.model.CardDefinition;
import com.example.dueltower.engine.model.CardType;
import com.example.dueltower.engine.model.PassiveDefinition;
import com.example.dueltower.preset.dto.PresetResponse;
import com.example.dueltower.preset.service.PresetService;
import com.example.dueltower.screen.dto.PlayerLobbyScreenResponse;
import com.example.dueltower.session.dto.PlayerStateDto;
import com.example.dueltower.session.dto.SessionStateDto;
import com.example.dueltower.session.runtime.SessionRuntime;
import com.example.dueltower.session.runtime.StateMapper;
import com.example.dueltower.session.service.SessionAccessDecision;
import com.example.dueltower.session.service.SessionAccessResolver;
import com.example.dueltower.session.service.SessionLifecycleService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.FORBIDDEN;

@Service
/**
 * PlayerLobby Screen API assembler.
 * The backend owns participant slot summary, reference option curation, preset preview resolution,
 * and action metadata so the frontend can focus on local draft input and rendering.
 */
public class PlayerLobbyScreenService {

    private static final List<String> PLAYER_LOBBY_NOTICE = List.of(
            "Player lobby participant summary, loadout references, and preset preview are assembled on the server-side screen model."
    );

    private final SessionLifecycleService sessionLifecycleService;
    private final SessionAccessResolver sessionAccessResolver;
    private final ScreenResponseFactory screenResponseFactory;
    private final CharacterProfileService characterProfileService;
    private final CardService cardService;
    private final PassiveService passiveService;
    private final PresetService presetService;

    public PlayerLobbyScreenService(SessionLifecycleService sessionLifecycleService,
                                    SessionAccessResolver sessionAccessResolver,
                                    ScreenResponseFactory screenResponseFactory,
                                    CharacterProfileService characterProfileService,
                                    CardService cardService,
                                    PassiveService passiveService,
                                    PresetService presetService) {
        this.sessionLifecycleService = sessionLifecycleService;
        this.sessionAccessResolver = sessionAccessResolver;
        this.screenResponseFactory = screenResponseFactory;
        this.characterProfileService = characterProfileService;
        this.cardService = cardService;
        this.passiveService = passiveService;
        this.presetService = presetService;
    }

    public PlayerLobbyScreenResponse getScreen(String code,
                                               String gmTokenHeader,
                                               String playerTokenHeader,
                                               Authentication authentication) {
        return sessionLifecycleService.withLockedSession(code, rt -> buildScreen(rt, gmTokenHeader, playerTokenHeader, authentication));
    }

    private PlayerLobbyScreenResponse buildScreen(SessionRuntime rt,
                                                  String gmTokenHeader,
                                                  String playerTokenHeader,
                                                  Authentication authentication) {
        SessionAccessDecision decision = sessionAccessResolver.requireSessionReadable(rt, gmTokenHeader, playerTokenHeader, authentication);
        String playerId = decision.playerId();
        if (playerId == null || playerId.isBlank()) {
            throw new ResponseStatusException(FORBIDDEN, "player lobby requires player access");
        }

        SessionStateDto state = StateMapper.toDto(rt.code(), rt.state());
        PlayerStateDto me = state.players().get(playerId);
        if (me == null) {
            throw new ResponseStatusException(FORBIDDEN, "player lobby access requires a joined player");
        }

        List<CharacterProfileResponse> characters = characterProfileService.list();
        List<CardDefinition> exCards = cardService.list(CardType.EX);
        List<PassiveDefinition> passives = passiveService.list();
        List<PresetResponse> presets = presetService.listMine(resolvePresetOwner(decision));

        return screenResponseFactory.playerLobby(
                ScreenRouteSpec.PLAYER_LOBBY,
                state,
                rt,
                playerId,
                me,
                characters,
                exCards,
                passives,
                presets,
                PLAYER_LOBBY_NOTICE
        );
    }

    private String resolvePresetOwner(SessionAccessDecision decision) {
        if (decision.username() != null && !decision.username().isBlank()) {
            return decision.username();
        }
        if (decision.playerId() != null && !decision.playerId().isBlank()) {
            return decision.playerId();
        }
        throw new ResponseStatusException(FORBIDDEN, "player preset access requires an owner id");
    }
}
