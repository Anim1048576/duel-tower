package com.example.dueltower.session.service;

import com.example.dueltower.engine.model.GameState;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
/**
 * Player self loadout facade.
 *
 * <p>deck/loadout/preset/forget 진입점을 모아 controller의 self-action 흐름을 단순화한다.</p>
 */
public class SessionLoadoutService {

    private final SessionService sessionService;

    public SessionLoadoutService(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    public GameState updateDeck(String code,
                                String actorPlayerIdRaw,
                                String targetPlayerIdRaw,
                                List<String> deckOwnedCardIdsRaw) {
        return sessionService.updateDeck(code, actorPlayerIdRaw, targetPlayerIdRaw, deckOwnedCardIdsRaw);
    }

    public GameState updateLoadout(String code,
                                   String actorPlayerIdRaw,
                                   String targetPlayerIdRaw,
                                   Long characterIdRaw,
                                   List<String> passiveIdsRaw,
                                   List<String> deckOwnedCardIdsRaw,
                                   String exCardIdRaw) {
        return sessionService.updateLoadout(code, actorPlayerIdRaw, targetPlayerIdRaw, characterIdRaw, passiveIdsRaw, deckOwnedCardIdsRaw, exCardIdRaw);
    }

    public GameState applyPresetToLoadout(String code,
                                          String actorPlayerIdRaw,
                                          String targetPlayerIdRaw,
                                          Long presetIdRaw) {
        return sessionService.applyPresetToLoadout(code, actorPlayerIdRaw, targetPlayerIdRaw, presetIdRaw);
    }

    public GameState forgetOwnedCard(String code,
                                     String actorPlayerIdRaw,
                                     String targetPlayerIdRaw,
                                     Integer ownedCardIndexRaw) {
        return sessionService.forgetOwnedCard(code, actorPlayerIdRaw, targetPlayerIdRaw, ownedCardIndexRaw);
    }
}
