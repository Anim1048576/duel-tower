package com.example.dueltower.session.service;

import com.example.dueltower.content.card.model.OwnedCard;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.Ids.CardDefId;
import com.example.dueltower.engine.model.Ids.CardInstId;
import com.example.dueltower.engine.model.Ids.PlayerId;
import com.example.dueltower.engine.model.NodeState;
import com.example.dueltower.engine.model.PlayerState;
import com.example.dueltower.session.config.StarterLoadoutConfig;
import com.example.dueltower.session.dto.OwnedCardDto;
import com.example.dueltower.session.runtime.SessionRuntime;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
/**
 * Session lobby and participant service.
 *
 * <p>join/leave/ready/kick/reset과 플레이어 토큰 처리의 실제 구현을 담당한다.</p>
 */
public class SessionLobbyService {

    private final SessionLifecycleService sessionLifecycleService;
    private final SessionLoadoutSupport sessionLoadoutSupport;
    private final StarterLoadoutConfig starterLoadoutConfig;

    public SessionLobbyService(SessionLifecycleService sessionLifecycleService,
                               SessionLoadoutSupport sessionLoadoutSupport,
                               StarterLoadoutConfig starterLoadoutConfig) {
        this.sessionLifecycleService = sessionLifecycleService;
        this.sessionLoadoutSupport = sessionLoadoutSupport;
        this.starterLoadoutConfig = starterLoadoutConfig;
    }

    public GameState join(String code,
                          String playerIdRaw,
                          Long characterIdRaw,
                          List<String> passiveIdsRaw,
                          List<String> requestedPresetDeckOwnedCardIdsRaw,
                          String presetExCardIdRaw,
                          List<OwnedCardDto> ownedCardsRaw) {
        if (playerIdRaw == null || playerIdRaw.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "playerId is required");
        }

        PlayerId pid = new PlayerId(playerIdRaw.trim());

        SessionLoadoutSupport.CharacterJoinTemplate characterTemplate = (characterIdRaw == null)
                ? null
                : sessionLoadoutSupport.loadCharacterJoinTemplate(characterIdRaw);

        List<String> passiveIds = sessionLoadoutSupport.parsePassiveIds(characterTemplate != null ? characterTemplate.passiveIds() : passiveIdsRaw);

        return sessionLifecycleService.withLockedSession(code, rt -> {
            GameState state = rt.state();

            if (state.players().containsKey(pid)) {
                List<String> existingPassiveIds = state.player(pid).passiveIds();
                if (!existingPassiveIds.equals(passiveIds)) {
                    throw new ResponseStatusException(
                            BAD_REQUEST,
                            "Passives are fixed at first join and cannot be changed later. Leave passiveIds empty or resend the same values."
                    );
                }
                return state;
            }

            PlayerState ps = new PlayerState(pid);
            ps.passiveIds(passiveIds);
            if (characterIdRaw != null) {
                rt.bindCharacterId(pid.value(), characterIdRaw);
            }

            List<OwnedCard> ownedCards = sessionLoadoutSupport.parseOwnedCards(characterTemplate != null ? characterTemplate.ownedCards() : ownedCardsRaw);
            ps.ownedCards(ownedCards);

            List<String> deckOwnedCardIds = sessionLoadoutSupport.resolveJoinDeckOwnedCardIds(
                    characterTemplate,
                    requestedPresetDeckOwnedCardIdsRaw,
                    ps.ownedCards()
            );
            boolean allowEmptyCharacterDeck = characterTemplate != null && deckOwnedCardIds.isEmpty();
            if (!allowEmptyCharacterDeck) {
                sessionLoadoutSupport.validateDeckBuild(deckOwnedCardIds, ps.ownedCards(), null);
            }

            state.players().put(pid, ps);
            ps.deckOwnedCardIds(deckOwnedCardIds);
            sessionLoadoutSupport.loadDeck(state, ps, deckOwnedCardIds);
            String exCardId = (characterTemplate != null) ? characterTemplate.exCardId() : presetExCardIdRaw;
            sessionLoadoutSupport.addCardToEx(state, ps, new CardDefId(sessionLoadoutSupport.normalizeExCardId(exCardId)));
            sessionLoadoutSupport.shuffleDeck(state, ps);
            return state;
        });
    }

    public String issuePlayerToken(String code, String playerIdRaw) {
        if (playerIdRaw == null || playerIdRaw.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "playerId is required");
        }
        String playerId = playerIdRaw.trim();
        return sessionLifecycleService.withLockedSession(code, rt -> {
            if (!rt.state().players().containsKey(new PlayerId(playerId))) {
                throw new ResponseStatusException(NOT_FOUND, "player not found");
            }
            return rt.issuePlayerToken(playerId);
        });
    }

    public String resolvePlayerIdByToken(String code, String playerTokenRaw) {
        if (playerTokenRaw == null || playerTokenRaw.isBlank()) {
            return null;
        }
        String token = playerTokenRaw.trim();
        return sessionLifecycleService.withLockedSession(code, rt -> rt.findPlayerIdByToken(token));
    }

    public GameState leaveSession(String code, String actorPlayerIdRaw) {
        if (actorPlayerIdRaw == null || actorPlayerIdRaw.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "actorPlayerId is required");
        }
        PlayerId actor = new PlayerId(actorPlayerIdRaw.trim());
        return sessionLifecycleService.withLockedSession(code, rt -> removePlayerFromSession(rt, actor, "player not found"));
    }

    public GameState setPlayerReady(String code, String actorPlayerIdRaw, String targetPlayerIdRaw, boolean ready) {
        if (actorPlayerIdRaw == null || actorPlayerIdRaw.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "actorPlayerId is required");
        }
        if (targetPlayerIdRaw == null || targetPlayerIdRaw.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "playerId is required");
        }

        PlayerId actor = new PlayerId(actorPlayerIdRaw.trim());
        PlayerId target = new PlayerId(targetPlayerIdRaw.trim());
        if (!actor.equals(target)) {
            throw new ResponseStatusException(FORBIDDEN, "players may only update their own ready state");
        }

        return sessionLifecycleService.withLockedSession(code, rt -> {
            PlayerState player = rt.state().players().get(target);
            if (player == null) {
                throw new ResponseStatusException(NOT_FOUND, "player not found");
            }
            player.ready(ready);
            return rt.state();
        });
    }

    public GameState kickPlayer(String code, String targetPlayerIdRaw) {
        if (targetPlayerIdRaw == null || targetPlayerIdRaw.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "playerId is required");
        }
        PlayerId target = new PlayerId(targetPlayerIdRaw.trim());
        return sessionLifecycleService.withLockedSession(code, rt -> removePlayerFromSession(rt, target, "player not found"));
    }

    public GameState resetSession(String code, boolean keepPlayers, boolean keepLoadouts, Long newSeed) {
        return sessionLifecycleService.withLockedSession(code, rt -> {
            GameState state = rt.state();
            long resetSeed = (newSeed == null) ? state.seed() : newSeed;
            resetSessionState(rt, state, keepPlayers, keepLoadouts, resetSeed);
            return state;
        });
    }

    private GameState removePlayerFromSession(SessionRuntime rt, PlayerId target, String notFoundMessage) {
        GameState state = rt.state();
        if (state.nodeState() == NodeState.COMBAT) {
            throw new ResponseStatusException(FORBIDDEN, "player management is unavailable during combat");
        }

        PlayerState ps = state.player(target);
        if (ps == null) {
            throw new ResponseStatusException(NOT_FOUND, notFoundMessage);
        }

        removePlayerRuntimeState(state, ps);
        state.players().remove(target);
        rt.removePlayerBindings(target.value());
        return state;
    }

    private void resetSessionState(SessionRuntime rt,
                                   GameState state,
                                   boolean keepPlayers,
                                   boolean keepLoadouts,
                                   long resetSeed) {
        Map<PlayerId, String> exCardByPlayerId = new LinkedHashMap<>();
        if (keepPlayers && keepLoadouts) {
            for (Map.Entry<PlayerId, PlayerState> entry : state.players().entrySet()) {
                exCardByPlayerId.put(entry.getKey(), sessionLoadoutSupport.resolveCurrentExCardId(state, entry.getValue()));
            }
        }

        state.enemies().clear();
        state.summons().clear();
        state.cardInstances().clear();
        state.combat(null);
        state.nodeState(NodeState.NON_COMBAT);

        if (!keepPlayers) {
            state.players().clear();
            rt.clearPlayerBindings();
            state.runState().initialize(resetSeed);
            return;
        }

        for (PlayerState ps : state.players().values()) {
            String exCardId = exCardByPlayerId.getOrDefault(ps.playerId(), starterLoadoutConfig.defaultExCardId());
            resetPlayerState(rt, state, ps, keepLoadouts, resetSeed, exCardId);
        }
        state.runState().initialize(resetSeed);
    }

    private void resetPlayerState(SessionRuntime rt,
                                  GameState state,
                                  PlayerState ps,
                                  boolean keepLoadouts,
                                  long resetSeed,
                                  String preservedExCardId) {
        ps.deck().clear();
        ps.hand().clear();
        ps.grave().clear();
        ps.field().clear();
        ps.excluded().clear();
        ps.activeSummons().clear();
        ps.summonByCard().clear();
        ps.statusValues().clear();
        ps.pendingDecision(null);
        ps.swappedThisTurn(false);
        ps.cardsPlayedThisTurn(0);
        ps.usedExThisTurn(false);
        ps.usedTenacityThisTurn(false);
        ps.tenacityDebtThisTurn(0);
        ps.exCooldownUntilRound(0);
        ps.exActivatable(true);
        ps.refillToMax();
        ps.exCard(null);

        if (!keepLoadouts) {
            List<OwnedCard> defaultOwnedCards = starterLoadoutConfig.defaultOwnedCards();
            ps.passiveIds(List.of());
            ps.ownedCards(defaultOwnedCards);
            List<String> defaultDeckOwnedCardIds = sessionLoadoutSupport.resolveCardIdsToOwnedCardIds(
                    starterLoadoutConfig.defaultPresetDeckCardIds(),
                    defaultOwnedCards,
                    "deckCardIds must not contain blank values"
            );
            ps.deckOwnedCardIds(defaultDeckOwnedCardIds);
            sessionLoadoutSupport.loadDeck(state, ps, defaultDeckOwnedCardIds);
            sessionLoadoutSupport.addCardToEx(state, ps, new CardDefId(starterLoadoutConfig.defaultExCardId()));
            rt.clearCharacterBinding(ps.playerId().value());
            sessionLoadoutSupport.shuffleDeck(state, ps, resetSeed);
            return;
        }

        List<String> deckOwnedCardIds = sessionLoadoutSupport.currentDeckOwnedCardIds(ps);
        if (deckOwnedCardIds != null && !deckOwnedCardIds.isEmpty()) {
            sessionLoadoutSupport.loadDeck(state, ps, deckOwnedCardIds);
            sessionLoadoutSupport.shuffleDeck(state, ps, resetSeed);
        }
        sessionLoadoutSupport.addCardToEx(state, ps, new CardDefId((preservedExCardId == null || preservedExCardId.isBlank())
                ? starterLoadoutConfig.defaultExCardId()
                : preservedExCardId.trim()));
    }

    private void removePlayerRuntimeState(GameState state, PlayerState ps) {
        Set<CardInstId> toDelete = new LinkedHashSet<>();
        toDelete.addAll(ps.deck());
        toDelete.addAll(ps.hand());
        toDelete.addAll(ps.grave());
        toDelete.addAll(ps.field());
        toDelete.addAll(ps.excluded());
        if (ps.exCard() != null) {
            toDelete.add(ps.exCard());
        }
        for (CardInstId id : toDelete) {
            state.cardInstances().remove(id);
        }

        List<Ids.SummonInstId> summonIds = new ArrayList<>(ps.activeSummons());
        for (Ids.SummonInstId summonId : summonIds) {
            state.summons().remove(summonId);
        }
    }
}
