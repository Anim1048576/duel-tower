package com.example.dueltower.screen.service;

import com.example.dueltower.screen.dto.CombatScreenResponse;
import com.example.dueltower.screen.dto.GmLobbyScreenResponse;
import com.example.dueltower.screen.dto.PlayerLobbyScreenResponse;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class SessionScreenService {

    private final PlayerLobbyScreenService playerLobbyScreenService;
    private final GmLobbyScreenService gmLobbyScreenService;
    private final CombatScreenService combatScreenService;

    public SessionScreenService(PlayerLobbyScreenService playerLobbyScreenService,
                                GmLobbyScreenService gmLobbyScreenService,
                                CombatScreenService combatScreenService) {
        this.playerLobbyScreenService = playerLobbyScreenService;
        this.gmLobbyScreenService = gmLobbyScreenService;
        this.combatScreenService = combatScreenService;
    }

    public PlayerLobbyScreenResponse getPlayerLobby(String code,
                                                    String gmTokenHeader,
                                                    String playerTokenHeader,
                                                    Authentication authentication) {
        return playerLobbyScreenService.getScreen(code, gmTokenHeader, playerTokenHeader, authentication);
    }

    public GmLobbyScreenResponse getGmLobby(String code,
                                            String gmTokenHeader,
                                            String playerTokenHeader,
                                            Authentication authentication) {
        return gmLobbyScreenService.getScreen(code, gmTokenHeader, playerTokenHeader, authentication);
    }

    public CombatScreenResponse getCombat(String code,
                                          Long afterVersion,
                                          Integer eventLimit,
                                          String gmTokenHeader,
                                          String playerTokenHeader,
                                          Authentication authentication) {
        return combatScreenService.getScreen(
                code,
                afterVersion,
                eventLimit,
                gmTokenHeader,
                playerTokenHeader,
                authentication
        );
    }
}
