package com.example.dueltower.screen.service;

import com.example.dueltower.screen.dto.CombatScreenResponse;
import com.example.dueltower.screen.dto.CombatScreenActionResponse;
import com.example.dueltower.screen.dto.DeckEditorScreenResponse;
import com.example.dueltower.screen.dto.GmLobbyScreenResponse;
import com.example.dueltower.screen.dto.GmLobbyStartCombatActionRequest;
import com.example.dueltower.screen.dto.GmLobbyStartCombatActionResponse;
import com.example.dueltower.screen.dto.PlayerLobbyScreenResponse;
import com.example.dueltower.screen.dto.PresetEditorScreenResponse;
import com.example.dueltower.session.dto.CommandRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class ScreenQueryService {

    private final SessionScreenService sessionScreenService;
    private final DeckEditorScreenService deckEditorScreenService;
    private final PresetEditorScreenService presetEditorScreenService;
    private final GmLobbyStartCombatActionService gmLobbyStartCombatActionService;

    public ScreenQueryService(SessionScreenService sessionScreenService,
                              DeckEditorScreenService deckEditorScreenService,
                              PresetEditorScreenService presetEditorScreenService,
                              GmLobbyStartCombatActionService gmLobbyStartCombatActionService) {
        this.sessionScreenService = sessionScreenService;
        this.deckEditorScreenService = deckEditorScreenService;
        this.presetEditorScreenService = presetEditorScreenService;
        this.gmLobbyStartCombatActionService = gmLobbyStartCombatActionService;
    }

    public PlayerLobbyScreenResponse getPlayerLobby(String code,
                                                    String gmTokenHeader,
                                                    String playerTokenHeader,
                                                    Authentication authentication) {
        return sessionScreenService.getPlayerLobby(code, gmTokenHeader, playerTokenHeader, authentication);
    }

    public GmLobbyScreenResponse getGmLobby(String code,
                                            String gmTokenHeader,
                                            String playerTokenHeader,
                                            Authentication authentication) {
        return sessionScreenService.getGmLobby(code, gmTokenHeader, playerTokenHeader, authentication);
    }

    public CombatScreenResponse getCombat(String code,
                                          Long afterVersion,
                                          Integer eventLimit,
                                          String gmTokenHeader,
                                          String playerTokenHeader,
                                          Authentication authentication) {
        return sessionScreenService.getCombat(code, afterVersion, eventLimit, gmTokenHeader, playerTokenHeader, authentication);
    }

    public CombatScreenActionResponse executeCombatAction(String code,
                                                          String actionId,
                                                          String gmTokenHeader,
                                                          String playerTokenHeader,
                                                          Authentication authentication,
                                                          CommandRequest request) {
        return sessionScreenService.executeCombatAction(code, actionId, gmTokenHeader, playerTokenHeader, authentication, request);
    }

    public GmLobbyStartCombatActionResponse startGmLobbyCombat(String code,
                                                               String gmTokenHeader,
                                                               String playerTokenHeader,
                                                               Authentication authentication,
                                                               GmLobbyStartCombatActionRequest request) {
        return gmLobbyStartCombatActionService.startCombat(code, gmTokenHeader, playerTokenHeader, authentication, request);
    }

    public DeckEditorScreenResponse getDeckEditor(long deckId, Authentication authentication) {
        return deckEditorScreenService.getEditor(deckId, authentication);
    }

    public DeckEditorScreenResponse getNewDeckEditor(Authentication authentication) {
        return deckEditorScreenService.getNewEditor(authentication);
    }

    public PresetEditorScreenResponse getPresetEditor(long presetId, Authentication authentication) {
        return presetEditorScreenService.getEditor(presetId, authentication);
    }

    public PresetEditorScreenResponse getNewPresetEditor(Authentication authentication) {
        return presetEditorScreenService.getNewEditor(authentication);
    }
}
