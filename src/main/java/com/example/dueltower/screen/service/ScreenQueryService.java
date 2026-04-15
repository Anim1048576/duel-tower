package com.example.dueltower.screen.service;

import com.example.dueltower.screen.dto.DeckEditorScreenResponse;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class ScreenQueryService {

    private final SessionScreenService sessionScreenService;
    private final DeckEditorScreenService deckEditorScreenService;

    public ScreenQueryService(SessionScreenService sessionScreenService,
                              DeckEditorScreenService deckEditorScreenService) {
        this.sessionScreenService = sessionScreenService;
        this.deckEditorScreenService = deckEditorScreenService;
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
}
