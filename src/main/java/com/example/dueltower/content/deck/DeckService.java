package com.example.dueltower.content.deck;

import com.example.dueltower.content.card.CardService;
import com.example.dueltower.content.deck.dto.*;
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

    public DeckService(DeckRepository deckRepository, CardService cardService) {
        this.deckRepository = deckRepository;
        this.cardService = cardService;
    }

    @Transactional
    public DeckResponse create(CreateDeckRequest req) {
        DeckType type = (req == null || req.type() == null) ? DeckType.PLAYER : req.type();
        String name = normalizeName(req == null ? null : req.name(), type);

        List<DeckCard> cards = normalizeAndValidateCards(type, req == null ? null : req.cards());

        Deck deck = Deck.builder()
                .name(name)
                .type(type)
                .build();
        deck.replaceCards(cards);

        Deck saved = deckRepository.save(deck);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<DeckResponse> list() {
        return deckRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public DeckResponse get(long id) {
        Deck deck = deckRepository.findWithCardsById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "deck not found: " + id));
        return toResponse(deck);
    }

    @Transactional
    public DeckResponse update(long id, UpdateDeckRequest req) {
        Deck deck = deckRepository.findWithCardsById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "deck not found: " + id));

        DeckType newType = (req == null || req.type() == null) ? deck.getType() : req.type();
        String newName = normalizeName(req == null ? null : req.name(), newType);

        List<DeckCard> cards = normalizeAndValidateCards(newType, req == null ? null : req.cards());

        deck.setType(newType);
        deck.setName(newName);
        deck.replaceCards(cards);

        return toResponse(deck);
    }

    /**
     * 덱에 카드를 누적 추가한다.
     * - PLAYER 덱은 총합 12장을 초과할 수 없고, 동일 카드는 3장을 초과할 수 없다.
     * - ENEMY 덱은 제약 없음
     */
    @Transactional
    public DeckResponse addCards(long id, AddDeckCardsRequest req) {
        Deck deck = deckRepository.findWithCardsById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "deck not found: " + id));

        // 현재 덱 상태 -> Map(cardId -> count)
        Map<String, Integer> current = new LinkedHashMap<>();
        if (deck.getCards() != null) {
            for (DeckCard c : deck.getCards()) {
                if (c == null) continue;
                if (c.getCardId() == null || c.getCardId().isBlank()) continue;
                current.merge(c.getCardId().trim(), c.getCount(), Integer::sum);
            }
        }

        // 요청 normalize + validate
        List<DeckCardSpec> specs = (req == null) ? null : req.cards();
        Map<String, Integer> toAdd = normalizeAndValidateAddSpecs(specs);

        // apply
        for (var e : toAdd.entrySet()) {
            current.merge(e.getKey(), e.getValue(), Integer::sum);
        }

        // constraints (PLAYER only, partial)
        if (deck.getType() == DeckType.PLAYER) {
            int total = current.values().stream().mapToInt(Integer::intValue).sum();
            if (total > 12) {
                throw new ResponseStatusException(BAD_REQUEST, "player deck cannot exceed 12 cards (got " + total + ")");
            }
            for (var e : current.entrySet()) {
                if (e.getValue() > 3) {
                    throw new ResponseStatusException(BAD_REQUEST, "player deck: max 3 copies per card (" + e.getKey() + "=" + e.getValue() + ")");
                }
            }
        }

        // to entities & persist
        List<DeckCard> newCards = new ArrayList<>();
        for (var e : current.entrySet()) {
            newCards.add(DeckCard.builder()
                    .cardId(e.getKey())
                    .count(e.getValue())
                    .build());
        }
        deck.replaceCards(newCards);
        return toResponse(deck);
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

    /**
     * cards 요청을 Map(cardId -> count)로 정규화 + 카드ID 존재 검증 + (플레이어 덱만) 제약 검증
     */
    private List<DeckCard> normalizeAndValidateCards(DeckType type, List<DeckCardSpec> specs) {
        if (specs == null) specs = List.of();

        // 1) normalize & merge duplicates
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

        // 2) validate cardId exists in content
        var cardMap = cardService.asMap();
        for (String cardId : merged.keySet()) {
            Ids.CardDefId id = new Ids.CardDefId(cardId);
            if (!cardMap.containsKey(id)) {
                throw new ResponseStatusException(BAD_REQUEST, "unknown cardId: " + cardId);
            }
        }

        // 3) constraints (PLAYER only)
        if (type == DeckType.PLAYER) {
            int total = merged.values().stream().mapToInt(Integer::intValue).sum();
            if (total != 12) {
                throw new ResponseStatusException(BAD_REQUEST, "player deck must have exactly 12 cards (got " + total + ")");
            }
            for (var e : merged.entrySet()) {
                if (e.getValue() > 3) {
                    throw new ResponseStatusException(BAD_REQUEST, "player deck: max 3 copies per card (" + e.getKey() + "=" + e.getValue() + ")");
                }
            }
        }

        // 4) to entities
        List<DeckCard> cards = new ArrayList<>();
        for (var e : merged.entrySet()) {
            cards.add(DeckCard.builder()
                    .cardId(e.getKey())
                    .count(e.getValue())
                    .build());
        }
        return cards;
    }

    /**
     * add 전용: Map(cardId -> count)로 정규화 + 카드ID 존재 검증
     */
    private Map<String, Integer> normalizeAndValidateAddSpecs(List<DeckCardSpec> specs) {
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

        var cardMap = cardService.asMap();
        for (String cardId : merged.keySet()) {
            Ids.CardDefId id = new Ids.CardDefId(cardId);
            if (!cardMap.containsKey(id)) {
                throw new ResponseStatusException(BAD_REQUEST, "unknown cardId: " + cardId);
            }
        }
        return merged;
    }

    private DeckResponse toResponse(Deck deck) {
        int total = 0;
        List<DeckCardDto> cards = new ArrayList<>();
        if (deck.getCards() != null) {
            for (DeckCard c : deck.getCards()) {
                total += c.getCount();
                cards.add(new DeckCardDto(c.getCardId(), c.getCount()));
            }
        }
        return new DeckResponse(deck.getId(), deck.getName(), deck.getType(), total, List.copyOf(cards));
    }
}
