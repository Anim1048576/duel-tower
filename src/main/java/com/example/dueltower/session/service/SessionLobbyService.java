package com.example.dueltower.session.service;

import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.session.dto.OwnedCardDto;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
/**
 * Session lobby and participant facade.
 *
 * <p>join/leave/ready/kick/reset과 참가자 토큰 관련 진입점을 제공한다.</p>
 */
public class SessionLobbyService {

    private final SessionService sessionService;

    public SessionLobbyService(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    public GameState join(String code,
                          String playerIdRaw,
                          Long characterIdRaw,
                          List<String> passiveIdsRaw,
                          List<String> requestedPresetDeckOwnedCardIdsRaw,
                          String presetExCardIdRaw,
                          List<OwnedCardDto> ownedCardsRaw) {
        return sessionService.join(
                code,
                playerIdRaw,
                characterIdRaw,
                passiveIdsRaw,
                requestedPresetDeckOwnedCardIdsRaw,
                presetExCardIdRaw,
                ownedCardsRaw
        );
    }

    public String issuePlayerToken(String code, String playerIdRaw) {
        return sessionService.issuePlayerToken(code, playerIdRaw);
    }

    public String resolvePlayerIdByToken(String code, String playerTokenRaw) {
        return sessionService.resolvePlayerIdByToken(code, playerTokenRaw);
    }

    public GameState leaveSession(String code, String actorPlayerIdRaw) {
        return sessionService.leaveSession(code, actorPlayerIdRaw);
    }

    public GameState setPlayerReady(String code, String actorPlayerIdRaw, String targetPlayerIdRaw, boolean ready) {
        return sessionService.setPlayerReady(code, actorPlayerIdRaw, targetPlayerIdRaw, ready);
    }

    public GameState kickPlayer(String code, String targetPlayerIdRaw) {
        return sessionService.kickPlayer(code, targetPlayerIdRaw);
    }

    public GameState resetSession(String code, boolean keepPlayers, boolean keepLoadouts, Long newSeed) {
        return sessionService.resetSession(code, keepPlayers, keepLoadouts, newSeed);
    }
}
