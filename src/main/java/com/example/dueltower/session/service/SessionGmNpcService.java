package com.example.dueltower.session.service;

import com.example.dueltower.content.card.model.OwnedCard;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.Ids.CardDefId;
import com.example.dueltower.engine.model.Ids.PlayerId;
import com.example.dueltower.engine.model.PlayerControlType;
import com.example.dueltower.engine.model.PlayerState;
import com.example.dueltower.session.config.StarterLoadoutConfig;
import com.example.dueltower.session.dto.AddGmNpcRequest;
import com.example.dueltower.session.dto.AddGmNpcResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;

@Service
public class SessionGmNpcService {

    private static final String DEFAULT_NPC_PLAYER_ID = "gm-npc-1";

    private final SessionLifecycleService sessionLifecycleService;
    private final SessionLoadoutSupport sessionLoadoutSupport;
    private final StarterLoadoutConfig starterLoadoutConfig;

    public SessionGmNpcService(SessionLifecycleService sessionLifecycleService,
                               SessionLoadoutSupport sessionLoadoutSupport,
                               StarterLoadoutConfig starterLoadoutConfig) {
        this.sessionLifecycleService = sessionLifecycleService;
        this.sessionLoadoutSupport = sessionLoadoutSupport;
        this.starterLoadoutConfig = starterLoadoutConfig;
    }

    public AddGmNpcResponse addGmControlledNpc(String code,
                                               String controllerPlayerIdRaw,
                                               AddGmNpcRequest req) {
        String controllerPlayerId = normalizeRequired(controllerPlayerIdRaw, "controllerPlayerId");
        String npcPlayerId = DEFAULT_NPC_PLAYER_ID;
        Long characterId = req == null ? null : req.characterId();

        SessionLoadoutSupport.CharacterJoinTemplate characterTemplate = characterId == null
                ? null
                : sessionLoadoutSupport.loadCharacterJoinTemplate(characterId);

        return sessionLifecycleService.withLockedSession(code, rt -> {
            GameState state = rt.state();
            boolean alreadyHasGmNpc = state.players().values().stream()
                    .anyMatch(player -> player.controlType() == PlayerControlType.GM_CONTROLLED_NPC);
            if (alreadyHasGmNpc) {
                throw new ResponseStatusException(CONFLICT, "session already has a GM controlled NPC");
            }
            PlayerId npcId = new PlayerId(npcPlayerId);
            if (state.players().containsKey(npcId)) {
                throw new ResponseStatusException(CONFLICT, "NPC playerId already exists");
            }

            PlayerState npc = new PlayerState(npcId);
            npc.markGmControlledNpc(new PlayerId(controllerPlayerId));
            npc.ready(true);

            List<String> passiveIds = sessionLoadoutSupport.parsePassiveIds(
                    characterTemplate == null ? null : characterTemplate.passiveIds()
            );
            npc.passiveIds(passiveIds);

            if (characterId != null) {
                rt.bindCharacterId(npcId.value(), characterId);
            }

            List<OwnedCard> ownedCards = resolveOwnedCards(characterTemplate, req);
            npc.ownedCards(ownedCards);

            List<String> deckOwnedCardIds = resolveDeckOwnedCardIds(characterTemplate, req, ownedCards);
            boolean allowEmptyCharacterDeck = characterTemplate != null && deckOwnedCardIds.isEmpty();
            if (!allowEmptyCharacterDeck) {
                sessionLoadoutSupport.validateDeckBuild(deckOwnedCardIds, ownedCards, null);
            }

            state.players().put(npcId, npc);
            npc.deckOwnedCardIds(deckOwnedCardIds);
            sessionLoadoutSupport.loadDeck(state, npc, deckOwnedCardIds);
            String exCardId = resolveExCardId(characterTemplate, req);
            sessionLoadoutSupport.addCardToEx(state, npc, new CardDefId(sessionLoadoutSupport.normalizeExCardId(exCardId)));
            sessionLoadoutSupport.shuffleDeck(state, npc);
            return new AddGmNpcResponse(rt.code(), npcId.value());
        });
    }

    private List<OwnedCard> resolveOwnedCards(SessionLoadoutSupport.CharacterJoinTemplate characterTemplate,
                                             AddGmNpcRequest req) {
        if (characterTemplate != null) {
            return sessionLoadoutSupport.parseOwnedCards(characterTemplate.ownedCards());
        }
        return sessionLoadoutSupport.parseOwnedCards(req == null ? null : req.ownedCards());
    }

    private List<String> resolveDeckOwnedCardIds(SessionLoadoutSupport.CharacterJoinTemplate characterTemplate,
                                                AddGmNpcRequest req,
                                                List<OwnedCard> ownedCards) {
        if (characterTemplate != null) {
            return sessionLoadoutSupport.resolveJoinDeckOwnedCardIds(characterTemplate, null, ownedCards);
        }
        if (req != null && req.requestedDeckOwnedCardIds() != null) {
            return sessionLoadoutSupport.resolveJoinDeckOwnedCardIds(null, req.requestedDeckOwnedCardIds(), ownedCards);
        }
        return sessionLoadoutSupport.resolveCardIdsToOwnedCardIds(
                starterLoadoutConfig.defaultDeckCardIds(),
                ownedCards,
                "starter defaultDeckCardIds must not contain blank values"
        );
    }

    private String resolveExCardId(SessionLoadoutSupport.CharacterJoinTemplate characterTemplate,
                                   AddGmNpcRequest req) {
        if (characterTemplate != null) {
            return characterTemplate.exCardId();
        }
        if (req != null && req.exCardId() != null && !req.exCardId().isBlank()) {
            return req.exCardId().trim();
        }
        return starterLoadoutConfig.defaultExCardId();
    }

    private static String normalizeRequired(String raw, String fieldName) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, fieldName + " is required");
        }
        return raw.trim();
    }
}
