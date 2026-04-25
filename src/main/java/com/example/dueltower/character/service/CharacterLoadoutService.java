package com.example.dueltower.character.service;

import com.example.dueltower.character.domain.CharacterCurrentSkillDeckEntry;
import com.example.dueltower.character.domain.CharacterExLoadout;
import com.example.dueltower.character.repository.CharacterCurrentSkillDeckEntryRepository;
import com.example.dueltower.character.repository.CharacterExLoadoutRepository;
import com.example.dueltower.content.card.model.OwnedCard;
import com.example.dueltower.content.deck.service.DeckService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class CharacterLoadoutService {

    private final CharacterCurrentSkillDeckEntryRepository currentSkillDeckEntryRepository;
    private final CharacterExLoadoutRepository exLoadoutRepository;
    private final CharacterCardCollectionService cardCollectionService;
    private final DeckService deckService;

    public CharacterLoadoutService(CharacterCurrentSkillDeckEntryRepository currentSkillDeckEntryRepository,
                                   CharacterExLoadoutRepository exLoadoutRepository,
                                   CharacterCardCollectionService cardCollectionService,
                                   DeckService deckService) {
        this.currentSkillDeckEntryRepository = currentSkillDeckEntryRepository;
        this.exLoadoutRepository = exLoadoutRepository;
        this.cardCollectionService = cardCollectionService;
        this.deckService = deckService;
    }

    @Transactional
    public void replaceCurrentSkillDeckFromOwnedCardIds(Long characterId, List<String> ownedCardIds) {
        requireCharacterId(characterId);
        List<String> normalized = normalizeOwnedCardIds(ownedCardIds);
        validateOwnedCardsAvailable(characterId, normalized);

        currentSkillDeckEntryRepository.deleteByCharacterId(characterId);
        currentSkillDeckEntryRepository.flush();

        List<CharacterCurrentSkillDeckEntry> entries = new ArrayList<>(normalized.size());
        for (int i = 0; i < normalized.size(); i++) {
            entries.add(CharacterCurrentSkillDeckEntry.builder()
                    .characterId(characterId)
                    .ownedCardId(normalized.get(i))
                    .position(i)
                    .build());
        }
        currentSkillDeckEntryRepository.saveAll(entries);
    }

    @Transactional
    public void clearCurrentSkillDeck(Long characterId) {
        requireCharacterId(characterId);
        currentSkillDeckEntryRepository.deleteByCharacterId(characterId);
    }

    @Transactional(readOnly = true)
    public List<String> getCurrentSkillDeckOwnedCardIds(Long characterId) {
        requireCharacterId(characterId);
        return currentSkillDeckEntryRepository.findByCharacterIdOrderByPositionAsc(characterId).stream()
                .map(CharacterCurrentSkillDeckEntry::getOwnedCardId)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> getCurrentSkillDeckPreviewCardIds(Long characterId) {
        Map<String, OwnedCard> ownedById = cardCollectionService.ownedCardMap(characterId);
        List<String> preview = new ArrayList<>();
        for (String ownedCardId : getCurrentSkillDeckOwnedCardIds(characterId)) {
            OwnedCard ownedCard = ownedById.get(ownedCardId);
            if (ownedCard == null) {
                continue;
            }
            preview.add(ownedCard.cardId());
        }
        return List.copyOf(preview);
    }

    @Transactional
    public void replaceExCard(Long characterId, String exCardId) {
        requireCharacterId(characterId);
        String normalized = requireText(exCardId, "exCardId is required");

        CharacterExLoadout loadout = exLoadoutRepository.findById(characterId)
                .orElseGet(() -> CharacterExLoadout.builder()
                        .characterId(characterId)
                        .exCardId(normalized)
                        .build());
        loadout.setExCardId(normalized);
        exLoadoutRepository.save(loadout);
    }

    @Transactional
    public void clearExCard(Long characterId) {
        requireCharacterId(characterId);
        exLoadoutRepository.deleteByCharacterId(characterId);
    }

    @Transactional
    public void deleteLoadout(Long characterId) {
        clearCurrentSkillDeck(characterId);
        clearExCard(characterId);
    }

    @Transactional(readOnly = true)
    public String getExCardId(Long characterId) {
        requireCharacterId(characterId);
        return exLoadoutRepository.findById(characterId)
                .map(CharacterExLoadout::getExCardId)
                .orElse(null);
    }

    @Transactional
    public void applyDeckTemplate(Long characterId, Long deckId) {
        requireCharacterId(characterId);
        if (deckId == null || deckId <= 0) {
            throw new ResponseStatusException(BAD_REQUEST, "deckId must be a positive number");
        }

        List<String> deckCardIds = deckService.expandPlayerDeckCardIdsForCurrentSkillDeck(deckId);
        List<OwnedCard> ownedCards = cardCollectionService.toRuntimeOwnedCards(characterId);
        replaceCurrentSkillDeckFromOwnedCardIds(characterId, resolveCardIdsToOwnedCardIds(deckCardIds, ownedCards));
    }

    private void validateOwnedCardsAvailable(Long characterId, List<String> ownedCardIds) {
        for (String ownedCardId : ownedCardIds) {
            if (!cardCollectionService.hasOwnedCard(characterId, ownedCardId)) {
                throw new ResponseStatusException(BAD_REQUEST, "owned card unavailable: " + ownedCardId);
            }
        }
    }

    private static List<String> resolveCardIdsToOwnedCardIds(List<String> cardIds, List<OwnedCard> ownedCards) {
        Map<String, List<OwnedCard>> ownedByCardId = new LinkedHashMap<>();
        for (OwnedCard ownedCard : ownedCards) {
            ownedByCardId.computeIfAbsent(ownedCard.cardId(), ignored -> new ArrayList<>()).add(ownedCard);
        }

        Map<String, Integer> nextIndexByCardId = new LinkedHashMap<>();
        List<String> resolved = new ArrayList<>(cardIds.size());
        for (String cardIdRaw : cardIds) {
            String cardId = requireText(cardIdRaw, "deck cardIds must not contain blank values");
            List<OwnedCard> candidates = ownedByCardId.getOrDefault(cardId, List.of());
            int nextIndex = nextIndexByCardId.getOrDefault(cardId, 0);
            if (nextIndex >= candidates.size()) {
                throw new ResponseStatusException(BAD_REQUEST, "owned card unavailable: " + cardId);
            }
            resolved.add(candidates.get(nextIndex).ownedCardId());
            nextIndexByCardId.put(cardId, nextIndex + 1);
        }
        return List.copyOf(resolved);
    }

    private static List<String> normalizeOwnedCardIds(List<String> ownedCardIds) {
        if (ownedCardIds == null) {
            throw new ResponseStatusException(BAD_REQUEST, "currentSkillDeck ownedCardIds is required");
        }

        List<String> normalized = new ArrayList<>(ownedCardIds.size());
        Set<String> seen = new LinkedHashSet<>();
        for (String raw : ownedCardIds) {
            String ownedCardId = requireText(raw, "currentSkillDeck ownedCardIds must not contain blank values");
            if (!seen.add(ownedCardId)) {
                throw new ResponseStatusException(BAD_REQUEST, "currentSkillDeck ownedCardIds must not contain duplicate values: " + ownedCardId);
            }
            normalized.add(ownedCardId);
        }
        return List.copyOf(normalized);
    }

    private static String requireText(String raw, String message) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, message);
        }
        return raw.trim();
    }

    private static void requireCharacterId(Long characterId) {
        if (characterId == null || characterId <= 0) {
            throw new ResponseStatusException(BAD_REQUEST, "characterId must be a positive number");
        }
    }
}
