package com.example.dueltower.content.deck.service;

import com.example.dueltower.content.card.service.CardService;
import com.example.dueltower.content.deck.domain.Deck;
import com.example.dueltower.content.deck.domain.DeckCard;
import com.example.dueltower.content.deck.domain.DeckType;
import com.example.dueltower.content.deck.dto.*;
import com.example.dueltower.content.deck.repository.DeckRepository;
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

        Map<String, Integer> cards = normalizeAndValidateCards(type, req == null ? null : req.cards());

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

        Map<String, Integer> cards = normalizeAndValidateCards(newType, req == null ? null : req.cards());

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

        // 요청 normalize + validate
        List<DeckCardSpec> specs = (req == null) ? null : req.cards();
        Map<String, Integer> toAdd = normalizeAndValidateAddSpecs(specs);

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
    public void upsertCharacterCurrentSkillDeck(long characterId, List<String> deckCardIds) {
        if (characterId <= 0) {
            throw new ResponseStatusException(BAD_REQUEST, "characterId must be positive");
        }

        String deckName = characterCurrentDeckName(characterId);
        Map<String, Integer> merged = normalizeAndMergeCardIds(deckCardIds);
        validateCardIdsExist(merged.keySet());
        deckLimitPolicy.validatePlayerDeckExact(merged);

        Deck deck = deckRepository.findFirstByTypeAndName(DeckType.PLAYER, deckName)
                .orElseGet(() -> Deck.create(deckName, DeckType.PLAYER));

        deck.changeType(DeckType.PLAYER);
        deck.rename(deckName);
        deck.syncCards(merged);
        deckRepository.save(deck);
    }

    @Transactional
    public void delete(long id) {
        if (!deckRepository.existsById(id)) {
            throw new ResponseStatusException(NOT_FOUND, "deck not found: " + id);
        }
        deckRepository.deleteById(id);
    }

    private String normalizeName(String raw, DeckType type) {
        String base = (raw == null) ? "" : raw.trim();
        if (!base.isBlank()) return base;
        return (type == DeckType.ENEMY) ? "enemy-deck" : "player-deck";
    }

    private Deck getDeckOrThrow(long id) {
        return deckRepository.findWithCardsById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "deck not found: " + id));
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
                throw new ResponseStatusException(BAD_REQUEST, "cardId is required");
            }
            String cardId = s.cardId().trim();
            int count = (s.count() == null) ? 1 : s.count();
            if (count <= 0) {
                throw new ResponseStatusException(BAD_REQUEST, "count must be >= 1: " + cardId);
            }
            merged.merge(cardId, count, Integer::sum);
        }
        return merged;
    }

    private Map<String, Integer> normalizeAndMergeCardIds(List<String> cardIds) {
        if (cardIds == null) {
            throw new ResponseStatusException(BAD_REQUEST, "deckCardIds is required");
        }

        Map<String, Integer> merged = new LinkedHashMap<>();
        for (String rawCardId : cardIds) {
            if (rawCardId == null || rawCardId.isBlank()) {
                throw new ResponseStatusException(BAD_REQUEST, "deckCardIds must not contain blank values");
            }
            String cardId = rawCardId.trim();
            merged.merge(cardId, 1, Integer::sum);
        }
        return merged;
    }

    private String characterCurrentDeckName(long characterId) {
        return "character:" + characterId + ":currentSkillDeck";
    }

    private void validateCardIdsExist(Set<String> cardIds) {
        var cardMap = cardService.asMap();
        for (String cardId : cardIds) {
            Ids.CardDefId id = new Ids.CardDefId(cardId);
            if (!cardMap.containsKey(id)) {
                throw new ResponseStatusException(BAD_REQUEST, "unknown cardId: " + cardId);
            }
        }
    }

    /**
     * cards 요청을 Map(cardId -> count)로 정규화 + 카드ID 존재 검증 + (플레이어 덱만) 제약 검증
     */
    private Map<String, Integer> normalizeAndValidateCards(DeckType type, List<DeckCardSpec> specs) {
        Map<String, Integer> merged = normalizeAndMergeSpecs(specs);

        // validate cardId exists in content
        validateCardIdsExist(merged.keySet());

        // 3) constraints (PLAYER only)
        if (type == DeckType.PLAYER) {
            deckLimitPolicy.validatePlayerDeckExact(merged);
        }

        return merged;
    }

    /**
     * add 전용: Map(cardId -> count)로 정규화 + 카드ID 존재 검증
     */
    private Map<String, Integer> normalizeAndValidateAddSpecs(List<DeckCardSpec> specs) {
        Map<String, Integer> merged = normalizeAndMergeSpecs(specs);
        validateCardIdsExist(merged.keySet());
        return merged;
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
