package com.example.dueltower.screen.service;

import com.example.dueltower.screen.dto.DeckEditorScreenResponse;
import com.example.dueltower.screen.dto.PresetEditorScreenResponse;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class ScreenQueryService {

    private final SessionScreenService sessionScreenService;
    private final DeckEditorScreenService deckEditorScreenService;
    private final PresetEditorScreenService presetEditorScreenService;

    public ScreenQueryService(SessionScreenService sessionScreenService,
                              DeckEditorScreenService deckEditorScreenService,
                              PresetEditorScreenService presetEditorScreenService) {
        this.sessionScreenService = sessionScreenService;
        this.deckEditorScreenService = deckEditorScreenService;
        this.presetEditorScreenService = presetEditorScreenService;
    }

    public Object getPlayerLobby(String code,
                                 String gmTokenHeader,
                                 String playerTokenHeader,
                                 Authentication authentication) {
        return sessionScreenService.getPlayerLobby(code, gmTokenHeader, playerTokenHeader, authentication);
    }

    public Object getGmLobby(String code,
                             String gmTokenHeader,
                             String playerTokenHeader,
                             Authentication authentication) {
        return sessionScreenService.getGmLobby(code, gmTokenHeader, playerTokenHeader, authentication);
    }

    public Object getCombat(String code,
                            String gmTokenHeader,
                            String playerTokenHeader,
                            Authentication authentication) {
        return sessionScreenService.getCombat(code, gmTokenHeader, playerTokenHeader, authentication);
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
