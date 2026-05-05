package com.example.dueltower.content.deck.service;

import com.example.dueltower.content.card.service.CardService;
import com.example.dueltower.content.deck.domain.Deck;
import com.example.dueltower.content.deck.domain.DeckCard;
import com.example.dueltower.content.deck.domain.DeckType;
import com.example.dueltower.content.deck.dto.*;
import com.example.dueltower.content.deck.repository.DeckRepository;
import com.example.dueltower.engine.model.CardType;
import com.example.dueltower.engine.model.Ids;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class DeckService {

    private final DeckRepository deckRepository;
    private final CardService cardService;
    private final DeckLimitPolicy deckLimitPolicy;

    public DeckService(DeckRepository deckRepository, CardService cardService, DeckLimitPolicy deckLimitPolicy) {
        this.deckRepository = deckRepository;
        this.cardService = cardService;
        this.deckLimitPolicy = deckLimitPolicy;
    }

    @Transactional
    public DeckResponse create(CreateDeckRequest req) {
        DeckType type = (req == null || req.type() == null) ? DeckType.PLAYER : req.type();
        String name = normalizeName(req == null ? null : req.name(), type);

        Map<String, Integer> cards = normalizeAndValidateSpecs(req == null ? null : req.cards(), false);
        applyPlayerDeckRulesOrThrow(type, cards, true);

        Deck deck = Deck.create(name, type);
        deck.syncCards(cards);

        Deck saved = deckRepository.save(deck);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<DeckResponse> list() {
        return deckRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public DeckResponse get(long id) {
        Deck deck = getDeckOrThrow(id);
        return toResponse(deck);
    }

    @Transactional
    public DeckResponse update(long id, UpdateDeckRequest req) {
        Deck deck = getDeckOrThrow(id);

        DeckType newType = (req == null || req.type() == null) ? deck.getType() : req.type();
        String newName = normalizeName(req == null ? null : req.name(), newType);

        Map<String, Integer> cards = normalizeAndValidateSpecs(req == null ? null : req.cards(), false);
        applyPlayerDeckRulesOrThrow(newType, cards, true);

        deck.changeType(newType);
        deck.rename(newName);
        deck.syncCards(cards);

        return toResponse(deck);
    }

    /**
     * 덱에 카드를 누적 추가한다.
     * - PLAYER 덱은 총합 상한과 카드별 매수 제한(카드별 오버라이드 가능)을 검증한다.
     * - ENEMY 덱은 제약 없음
     */
    @Transactional
    public DeckResponse addCards(long id, AddDeckCardsRequest req) {
        Deck deck = getDeckOrThrow(id);
        Map<String, Integer> toAdd = normalizeAndValidateSpecs(req == null ? null : req.cards(), false);

        // add on current aggregate state
        for (var e : toAdd.entrySet()) {
            deck.addCardCopies(e.getKey(), e.getValue());
        }

        // constraints (PLAYER only, partial)
        if (deck.getType() == DeckType.PLAYER) {
            deckLimitPolicy.validatePlayerDeckUpTo(toCountMap(deck));
        }

        return toResponse(deck);
    }

    @Transactional
    public DeckResponse replaceCards(long id, ReplaceDeckCardsRequest req) {
        Deck deck = getDeckOrThrow(id);
        Map<String, Integer> cards = normalizeAndValidateSpecs(requiredCards(req), true);
        applyPlayerDeckRulesOrThrow(deck.getType(), cards, true);
        deck.syncCards(cards);
        return toResponse(deck);
    }

    @Transactional
    public DeckResponse removeCards(long id, RemoveDeckCardsRequest req) {
        Deck deck = getDeckOrThrow(id);
        Map<String, Integer> toRemove = normalizeAndValidateSpecs(requiredCards(req), true);
        Map<String, Integer> current = toCountMap(deck);

        for (var e : toRemove.entrySet()) {
            Integer existing = current.get(e.getKey());
            if (existing == null || existing <= 0) {
                throw badRequest("card not found in deck: " + e.getKey());
            }
            if (existing < e.getValue()) {
                throw badRequest("remove count exceeds current count: " + e.getKey());
            }
            int remain = existing - e.getValue();
            if (remain == 0) {
                current.remove(e.getKey());
            } else {
                current.put(e.getKey(), remain);
            }
        }

        deck.syncCards(current);
        return toResponse(deck);
    }

    @Transactional(readOnly = true)
    public DeckValidationResponse validateDeck(long id, ReplaceDeckCardsRequest req) {
        return validateDeck(id, DeckValidationRequest.fromReplaceCardsRequest(req));
    }

    @Transactional(readOnly = true)
    public DeckValidationResponse validateDeck(long id, DeckValidationRequest req) {
        Deck deck = getDeckOrThrow(id);
        DeckType effectiveType = (req != null && req.type() != null) ? req.type() : deck.getType();
        Map<String, Integer> cards = (req != null && req.cards() != null)
                ? normalizeAndMergeSpecs(req.cards())
                : toCountMap(deck);

        List<DeckValidationIssue> issues = collectDeckValidationIssues(effectiveType, cards, true);
        int totalCards = cards.values().stream().mapToInt(Integer::intValue).sum();
        return new DeckValidationResponse(issues.isEmpty(), List.copyOf(issues), totalCards);
    }

    @Transactional(readOnly = true)
    public DeckValidationResponse validateDraft(DeckType type, List<DeckCardSpec> cards) {
        DeckType effectiveType = (type == null) ? DeckType.PLAYER : type;
        Map<String, Integer> mergedCards = normalizeAndMergeSpecs(cards);
        List<DeckValidationIssue> issues = collectDeckValidationIssues(effectiveType, mergedCards, true);
        int totalCards = mergedCards.values().stream().mapToInt(Integer::intValue).sum();
        return new DeckValidationResponse(issues.isEmpty(), List.copyOf(issues), totalCards);
    }

    @Transactional(readOnly = true)
    public List<String> expandPlayerDeckCardIds(long deckId) {
        Deck deck = getDeckOrThrow(deckId);
        if (deck.getType() != DeckType.PLAYER) {
            throw badRequest("only PLAYER deck can be applied to currentSkillDeck");
        }

        Map<String, Integer> cards = toCountMap(deck);
        applyPlayerDeckRulesOrThrow(DeckType.PLAYER, cards, true);

        List<String> expanded = new ArrayList<>();
        for (DeckCard card : deck.getCards()) {
            for (int i = 0; i < card.getCount(); i++) {
                expanded.add(card.getCardId());
            }
        }
        return List.copyOf(expanded);
    }

    /**
     * @deprecated Use {@link #expandPlayerDeckCardIds(long)}. DeckService no longer owns character current loadouts.
     */
    @Deprecated
    @Transactional(readOnly = true)
    public List<String> expandPlayerDeckCardIdsForCurrentSkillDeck(long deckId) {
        return expandPlayerDeckCardIds(deckId);
    }

    @Transactional
    public void delete(long id) {
        Deck deck = getDeckOrThrow(id);
        deckRepository.delete(deck);
    }

    private String normalizeName(String raw, DeckType type) {
        String base = (raw == null) ? "" : raw.trim();
        if (!base.isBlank()) return base;
        return (type == DeckType.ENEMY) ? "enemy-deck" : "player-deck";
    }

    private Deck getDeckOrThrow(long id) {
        return deckRepository.findWithCardsById(id)
                .orElseThrow(() -> notFound("deck not found: " + id));
    }

    /**
     * 요청 스펙을 Map(cardId -> count)로 정규화 + 중복 합산.
     */
    private Map<String, Integer> normalizeAndMergeSpecs(List<DeckCardSpec> specs) {
        if (specs == null) specs = List.of();

        Map<String, Integer> merged = new LinkedHashMap<>();
        for (DeckCardSpec s : specs) {
            if (s == null) continue;
            if (s.cardId() == null || s.cardId().isBlank()) {
                throw badRequest("cardId is required");
            }
            String cardId = s.cardId().trim();
            int count = (s.count() == null) ? 1 : s.count();
            if (count <= 0) {
                throw badRequest("count must be >= 1: " + cardId);
            }
            merged.merge(cardId, count, Integer::sum);
        }
        return merged;
    }

    private void validateCardIdsExist(Set<String> cardIds) {
        var cardMap = cardService.asMap();
        for (String cardId : cardIds) {
            Ids.CardDefId id = new Ids.CardDefId(cardId);
            if (!cardMap.containsKey(id)) {
                throw badRequest("unknown cardId: " + cardId);
            }
        }
    }

    private List<DeckValidationIssue> validateExRules(Map<String, Integer> merged) {
        List<DeckValidationIssue> issues = new ArrayList<>();
        var cardMap = cardService.asMap();
        int exCount = 0;
        for (var e : merged.entrySet()) {
            var def = cardMap.get(new Ids.CardDefId(e.getKey()));
            if (def == null) {
                continue;
            }
            if (def.type() == CardType.EX) {
                exCount += e.getValue();
            }
            if (def.type() == CardType.TOKEN) {
                issues.add(issue("TOKEN_NOT_ALLOWED", "TOKEN card is not allowed in deck: " + e.getKey(), "cards"));
            }
        }
        if (exCount > 0) {
            issues.add(issue("EX_NOT_ALLOWED", "EX card is not allowed in deck", "cards"));
        }
        return issues;
    }

    /**
     * add/replace/remove(validate candidate)에서 공통 재사용할 카드 입력 정규화.
     * 다음 단계(remove endpoint, session validation 재사용)의 공통 진입점으로 사용한다.
     */
    private Map<String, Integer> normalizeAndValidateSpecs(List<DeckCardSpec> specs, boolean requireCards) {
        if (requireCards && specs == null) {
            throw badRequest("cards is required");
        }
        Map<String, Integer> merged = normalizeAndMergeSpecs(specs);
        validateCardIdsExist(merged.keySet());
        return merged;
    }

    /**
     * PLAYER 덱 규칙을 모아 검증한다.
     * - validate endpoint: collectDeckValidationIssues 호출
     * - mutation(create/update/replace/upsert): applyPlayerDeckRulesOrThrow 호출
     */
    private List<DeckValidationIssue> collectDeckValidationIssues(DeckType type, Map<String, Integer> cards, boolean requireExactTotal) {
        List<DeckValidationIssue> issues = new ArrayList<>();

        try {
            validateCardIdsExist(cards.keySet());
        } catch (ResponseStatusException e) {
            issues.add(issue("UNKNOWN_CARD_ID", safeReason(e), "cards"));
        }

        if (type != DeckType.PLAYER) {
            return issues;
        }

        issues.addAll(validateExRules(cards));

        try {
            if (requireExactTotal) {
                deckLimitPolicy.validatePlayerDeckExact(cards);
            } else {
                deckLimitPolicy.validatePlayerDeckUpTo(cards);
            }
        } catch (ResponseStatusException e) {
            String reason = safeReason(e);
            String code = reason.contains("copies per card")
                    ? "COPY_LIMIT_EXCEEDED"
                    : "TOTAL_CARDS_INVALID";
            issues.add(issue(code, reason, "cards"));
        }

        return issues;
    }

    private void applyPlayerDeckRulesOrThrow(DeckType type, Map<String, Integer> cards, boolean requireExactTotal) {
        List<DeckValidationIssue> issues = collectDeckValidationIssues(type, cards, requireExactTotal);
        if (!issues.isEmpty()) {
            throw badRequest(issues.get(0).message());
        }
    }

    private List<DeckCardSpec> requiredCards(ReplaceDeckCardsRequest req) {
        if (req == null || req.cards() == null) {
            throw badRequest("cards is required");
        }
        return req.cards();
    }

    private List<DeckCardSpec> requiredCards(RemoveDeckCardsRequest req) {
        if (req == null || req.cards() == null) {
            throw badRequest("cards is required");
        }
        return req.cards();
    }

    private DeckValidationIssue issue(String code, String message, String field) {
        return new DeckValidationIssue(code, message, field);
    }

    private String safeReason(ResponseStatusException e) {
        return (e.getReason() == null || e.getReason().isBlank()) ? "invalid deck request" : e.getReason();
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(BAD_REQUEST, message);
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(NOT_FOUND, message);
    }

    private Map<String, Integer> toCountMap(Deck deck) {
        Map<String, Integer> current = new LinkedHashMap<>();
        for (DeckCard c : deck.getCards()) {
            current.merge(c.getCardId(), c.getCount(), Integer::sum);
        }
        return current;
    }

    private DeckResponse toResponse(Deck deck) {
        int total = 0;
        List<DeckCardDto> cards = new ArrayList<>();
        for (DeckCard c : deck.getCards()) {
            total += c.getCount();
            cards.add(new DeckCardDto(c.getCardId(), c.getCount()));
        }
        return new DeckResponse(deck.getId(), deck.getName(), deck.getType(), total, List.copyOf(cards));
    }
}
