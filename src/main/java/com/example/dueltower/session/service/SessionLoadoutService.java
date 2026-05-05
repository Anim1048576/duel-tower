package com.example.dueltower.session.service;

import com.example.dueltower.common.api.ApiErrorException;
import com.example.dueltower.common.api.ApiErrorResolver;
import com.example.dueltower.content.card.model.OwnedCard;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.Ids.CardDefId;
import com.example.dueltower.engine.model.Ids.PlayerId;
import com.example.dueltower.engine.model.PlayerState;
import com.example.dueltower.session.runtime.SessionRuntime;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
/**
 * Player self loadout service.
 *
 * <p>deck/loadout/forget self-action 구현과 관련 검증을 담당한다.</p>
 */
public class SessionLoadoutService {

    private final SessionLifecycleService sessionLifecycleService;
    private final SessionLoadoutSupport sessionLoadoutSupport;

    public SessionLoadoutService(SessionLifecycleService sessionLifecycleService,
                                 SessionLoadoutSupport sessionLoadoutSupport) {
        this.sessionLifecycleService = sessionLifecycleService;
        this.sessionLoadoutSupport = sessionLoadoutSupport;
    }

    public GameState updateDeck(String code,
                                String actorPlayerIdRaw,
                                String targetPlayerIdRaw,
                                List<String> deckOwnedCardIdsRaw) {
        if (targetPlayerIdRaw == null || targetPlayerIdRaw.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "playerId is required");
        }
        if (actorPlayerIdRaw == null || actorPlayerIdRaw.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "actorPlayerId is required");
        }

        PlayerId actor = new PlayerId(actorPlayerIdRaw.trim());
        PlayerId target = new PlayerId(targetPlayerIdRaw.trim());
        return sessionLifecycleService.withLockedSession(code, rt -> {
            GameState state = rt.state();
            if (!actor.equals(target)) {
                throw new ResponseStatusException(FORBIDDEN, "players may only edit their own deck");
            }

            PlayerState ps = state.player(target);
            if (ps == null) {
                throw new ResponseStatusException(NOT_FOUND, "player not found");
            }

            sessionLoadoutSupport.validateDeckEditableState(state.nodeState(), ps);

            List<String> deckOwnedCardIds = sessionLoadoutSupport.resolveRequestedDeckOwnedCardIds(deckOwnedCardIdsRaw, ps.ownedCards());
            sessionLoadoutSupport.validateDeckBuild(deckOwnedCardIds, ps.ownedCards(), sessionLoadoutSupport.currentDeckOwnedCardIds(ps));
            ps.deckOwnedCardIds(deckOwnedCardIds);
            sessionLoadoutSupport.loadDeck(state, ps, deckOwnedCardIds);
            sessionLoadoutSupport.shuffleDeck(state, ps);
            sessionLoadoutSupport.persistCharacterDeck(rt, target, deckOwnedCardIds, ps.ownedCards());
            return state;
        });
    }

    public GameState updateLoadout(String code,
                                   String actorPlayerIdRaw,
                                   String targetPlayerIdRaw,
                                   Long characterIdRaw,
                                   List<String> passiveIdsRaw,
                                   List<String> deckOwnedCardIdsRaw,
                                   String exCardIdRaw) {
        return applyLoadoutToPlayer(
                code,
                actorPlayerIdRaw,
                targetPlayerIdRaw,
                LoadoutApplySpec.direct(characterIdRaw, passiveIdsRaw, deckOwnedCardIdsRaw, exCardIdRaw)
        );
    }

    public LoadoutPreview previewLoadout(String code,
                                         String actorPlayerIdRaw,
                                         String targetPlayerIdRaw,
                                         Long characterIdRaw,
                                         List<String> passiveIdsRaw,
                                         List<String> deckOwnedCardIdsRaw,
                                         String exCardIdRaw) {
        String actorPlayerId = requirePlayerText(actorPlayerIdRaw, "actorPlayerId is required");
        String targetPlayerId = requirePlayerText(targetPlayerIdRaw, "playerId is required");

        PlayerId actor = new PlayerId(actorPlayerId);
        PlayerId target = new PlayerId(targetPlayerId);
        return sessionLifecycleService.withLockedSession(code, rt -> {
            GameState state = rt.state();
            if (!actor.equals(target)) {
                throw new ResponseStatusException(FORBIDDEN, "players may only edit their own loadout");
            }

            PlayerState ps = state.player(target);
            if (ps == null) {
                throw new ResponseStatusException(NOT_FOUND, "player not found");
            }

            sessionLoadoutSupport.validateDeckEditableState(state.nodeState(), ps);

            SessionLoadoutSupport.CharacterJoinTemplate characterTemplate = (characterIdRaw == null)
                    ? null
                    : sessionLoadoutSupport.loadCharacterJoinTemplate(characterIdRaw);
            List<OwnedCard> effectiveOwnedCards = sessionLoadoutSupport.resolveLoadoutOwnedCards(ps, characterTemplate);
            List<String> effectivePassiveIds = sessionLoadoutSupport.resolveLoadoutPassiveIds(ps, characterTemplate, passiveIdsRaw);
            List<String> effectiveDeckOwnedCardIds = sessionLoadoutSupport.resolveLoadoutDeckOwnedCardIds(
                    ps,
                    effectiveOwnedCards,
                    characterTemplate,
                    deckOwnedCardIdsRaw
            );
            String effectiveExCardId = sessionLoadoutSupport.resolveLoadoutExCardId(state, ps, characterTemplate, exCardIdRaw);
            sessionLoadoutSupport.validateExCardId(effectiveExCardId);

            Long effectiveCharacterId = characterIdRaw != null
                    ? characterIdRaw
                    : rt.findCharacterIdByPlayerId(target.value());
            List<String> savedDeckOwnedCardIds = (characterTemplate == null)
                    ? sessionLoadoutSupport.currentDeckOwnedCardIds(ps)
                    : List.of();
            PlayerLobbyDeckEditAnalysis deckEditAnalysis = sessionLoadoutSupport.analyzePlayerLobbyDeckEdit(
                    effectiveOwnedCards,
                    savedDeckOwnedCardIds,
                    effectiveDeckOwnedCardIds
            );

            return new LoadoutPreview(
                    effectiveCharacterId,
                    effectivePassiveIds,
                    effectiveDeckOwnedCardIds,
                    effectiveExCardId,
                    effectiveOwnedCards,
                    deckEditAnalysis
            );
        });
    }

    public GameState forgetOwnedCard(String code,
                                     String actorPlayerIdRaw,
                                     String targetPlayerIdRaw,
                                     Integer ownedCardIndexRaw) {
        if (targetPlayerIdRaw == null || targetPlayerIdRaw.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "playerId is required");
        }
        if (actorPlayerIdRaw == null || actorPlayerIdRaw.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "actorPlayerId is required");
        }
        if (ownedCardIndexRaw == null) {
            throw new ResponseStatusException(BAD_REQUEST, "ownedCardIndex is required");
        }

        PlayerId actor = new PlayerId(actorPlayerIdRaw.trim());
        PlayerId target = new PlayerId(targetPlayerIdRaw.trim());
        return sessionLifecycleService.withLockedSession(code, rt -> {
            GameState state = rt.state();
            if (!actor.equals(target)) {
                throw new ResponseStatusException(FORBIDDEN, "players may only forget their own cards");
            }

            PlayerState ps = state.player(target);
            if (ps == null) {
                throw new ResponseStatusException(NOT_FOUND, "player not found");
            }

            List<OwnedCard> ownedCards = new ArrayList<>(ps.ownedCards());
            if (ownedCardIndexRaw < 0 || ownedCardIndexRaw >= ownedCards.size()) {
                throw new ApiErrorException(
                        BAD_REQUEST,
                        "INVALID_OWNED_CARD_INDEX",
                        "VALIDATION",
                        "?좏깮??移대뱶 ?꾩튂瑜??ㅼ떆 ?뺤씤??二쇱꽭??",
                        "ownedCardIndex out of range: " + ownedCardIndexRaw + " (size " + ownedCards.size() + ")",
                        ApiErrorResolver.details("ownedCardIndex", ownedCardIndexRaw, "ownedCardCount", ownedCards.size())
                );
            }

            Map<String, Integer> ownedCounts = sessionLoadoutSupport.cardCountsFromOwned(ownedCards);
            List<String> currentDeckOwnedCardIds = sessionLoadoutSupport.currentDeckOwnedCardIds(ps);
            Map<String, Integer> deckCounts = sessionLoadoutSupport.cardCounts(
                    sessionLoadoutSupport.currentDeckCardIdsFromOwnedCardIds(currentDeckOwnedCardIds, ownedCards)
            );

            if (ps.forgettingRequired() && !OwnedCardForgetPolicy.hasForgettableCardWithDeckMembership(
                    ownedCards,
                    ownedCounts,
                    deckCounts,
                    new LinkedHashSet<>(currentDeckOwnedCardIds)
            )) {
                throw new ApiErrorException(
                        BAD_REQUEST,
                        "FORGET_REQUIRED",
                        "RULE",
                        "먼저 잊을 수 있는 카드를 정리해야 다음 단계로 진행할 수 있습니다.",
                        "cannot resolve forgetting required: no forgettable cards (all are strengthened/weakened/locked or required by current deck)",
                        ApiErrorResolver.details("playerId", target.value(), "ownedCardCount", ownedCards.size())
                );
            }

            OwnedCard selectedCard = ownedCards.get(ownedCardIndexRaw);
            boolean inCurrentDeck = currentDeckOwnedCardIds.contains(selectedCard.ownedCardId());
            OwnedCardForgetPolicy.ForgetCheck forgetCheck = OwnedCardForgetPolicy.evaluateWithDeckMembership(
                    selectedCard,
                    ownedCounts,
                    deckCounts,
                    inCurrentDeck
            );
            if (!forgetCheck.forgettable()) {
                throw new ApiErrorException(
                        BAD_REQUEST,
                        "CARD_NOT_FORGETTABLE",
                        "RULE",
                        "선택한 카드는 지금 잊을 수 없습니다.",
                        "cannot forget owned card at index " + ownedCardIndexRaw + ": " + forgetCheck.reason(),
                        ApiErrorResolver.details("ownedCardIndex", ownedCardIndexRaw, "reason", forgetCheck.reason())
                );
            }

            ownedCards.remove((int) ownedCardIndexRaw);
            ps.ownedCards(ownedCards);
            return state;
        });
    }

    private GameState applyLoadoutToPlayer(String code,
                                           String actorPlayerIdRaw,
                                           String targetPlayerIdRaw,
                                           LoadoutApplySpec spec) {
        String actorPlayerId = requirePlayerText(actorPlayerIdRaw, "actorPlayerId is required");
        String targetPlayerId = requirePlayerText(targetPlayerIdRaw, "playerId is required");

        PlayerId actor = new PlayerId(actorPlayerId);
        PlayerId target = new PlayerId(targetPlayerId);
        return sessionLifecycleService.withLockedSession(code, rt -> {
            GameState state = rt.state();
            if (!actor.equals(target)) {
                throw new ResponseStatusException(FORBIDDEN, "players may only edit their own loadout");
            }

            PlayerState ps = state.player(target);
            if (ps == null) {
                throw new ResponseStatusException(NOT_FOUND, "player not found");
            }

            sessionLoadoutSupport.validateDeckEditableState(state.nodeState(), ps);
            SessionLoadoutSupport.CharacterJoinTemplate characterTemplate = (spec.characterId() == null)
                    ? null
                    : sessionLoadoutSupport.loadCharacterJoinTemplate(spec.characterId());

            applyLoadout(
                    rt,
                    state,
                    ps,
                    characterTemplate,
                    spec.characterId(),
                    spec.passiveIds(),
                    spec.deckCardIds(),
                    spec.exCardId()
            );
            sessionLoadoutSupport.persistCharacterDeck(rt, target, ps.deckOwnedCardIds(), ps.ownedCards());
            return state;
        });
    }

    private void applyLoadout(SessionRuntime rt,
                              GameState state,
                              PlayerState ps,
                              SessionLoadoutSupport.CharacterJoinTemplate characterTemplate,
                              Long characterIdRaw,
                              List<String> passiveIdsRaw,
                              List<String> deckOwnedCardIdsRaw,
                              String exCardIdRaw) {
        List<String> previousDeckOwnedCardIds = sessionLoadoutSupport.currentDeckOwnedCardIds(ps);
        List<OwnedCard> effectiveOwnedCards = sessionLoadoutSupport.resolveLoadoutOwnedCards(ps, characterTemplate);
        List<String> effectivePassiveIds = sessionLoadoutSupport.resolveLoadoutPassiveIds(ps, characterTemplate, passiveIdsRaw);
        List<String> effectiveDeckOwnedCardIds = sessionLoadoutSupport.resolveLoadoutDeckOwnedCardIds(ps, effectiveOwnedCards, characterTemplate, deckOwnedCardIdsRaw);
        String effectiveExCardId = sessionLoadoutSupport.resolveLoadoutExCardId(state, ps, characterTemplate, exCardIdRaw);

        List<String> currentDeckForValidation = (characterTemplate == null) ? previousDeckOwnedCardIds : null;
        boolean allowEmptyCharacterDeck = characterTemplate != null && effectiveDeckOwnedCardIds.isEmpty();
        if (!allowEmptyCharacterDeck) {
            sessionLoadoutSupport.validateDeckBuild(effectiveDeckOwnedCardIds, effectiveOwnedCards, currentDeckForValidation);
        }
        sessionLoadoutSupport.validateExCardId(effectiveExCardId);

        if (characterTemplate != null && characterIdRaw != null) {
            rt.bindCharacterId(ps.playerId().value(), characterIdRaw);
        }

        ps.ownedCards(effectiveOwnedCards);
        ps.passiveIds(effectivePassiveIds);
        ps.deckOwnedCardIds(effectiveDeckOwnedCardIds);
        sessionLoadoutSupport.loadDeck(state, ps, effectiveDeckOwnedCardIds);
        sessionLoadoutSupport.addCardToEx(state, ps, new CardDefId(sessionLoadoutSupport.normalizeExCardId(effectiveExCardId)));
        sessionLoadoutSupport.shuffleDeck(state, ps);
    }

    private String requirePlayerText(String raw, String message) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, message);
        }
        return raw.trim();
    }

    private record LoadoutApplySpec(
            Long characterId,
            List<String> passiveIds,
            List<String> deckCardIds,
            String exCardId
    ) {
        private static LoadoutApplySpec direct(Long characterId,
                                               List<String> passiveIds,
                                               List<String> deckCardIds,
                                               String exCardId) {
            return new LoadoutApplySpec(characterId, passiveIds, deckCardIds, exCardId);
        }
    }

    public record LoadoutPreview(
            Long characterId,
            List<String> passiveIds,
            List<String> deckOwnedCardIds,
            String exCardId,
            List<OwnedCard> ownedCards,
            PlayerLobbyDeckEditAnalysis deckEditAnalysis
    ) {}
}
