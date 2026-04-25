package com.example.dueltower.character.service;

import com.example.dueltower.character.domain.CharacterProfile;
import com.example.dueltower.character.repository.CharacterProfileRepository;
import com.example.dueltower.content.card.model.OwnedCard;
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
@Deprecated
public class CharacterCurrentSkillDeckService {

    private final CharacterProfileRepository repository;

    public CharacterCurrentSkillDeckService(CharacterProfileRepository repository) {
        this.repository = repository;
    }

    /**
     * cardId 배열을 그대로 CharacterProfile.currentSkillDeck에 저장한다.
     * 저장된 PLAYER 덱 적용처럼 owned copy 구분이 없는 경로에서만 사용한다.
     */
    @Transactional
    public CharacterProfile replaceCurrentSkillDeckFromCardIds(CharacterProfile profile, List<String> cardIds) {
        CharacterProfile target = requirePersistedProfile(profile);
        List<String> currentSkillDeck = normalizeCardIds(cardIds);

        target.setCurrentSkillDeck(currentSkillDeck);
        return repository.save(target);
    }

    /**
     * ownedCardId 배열을 CharacterProfile.currentSkillDeck에 저장한다.
     * 세션/로드아웃처럼 특정 owned copy를 선택하는 경로에서만 사용한다.
     */
    @Transactional
    public CharacterProfile replaceCurrentSkillDeckFromOwnedCardIds(
            CharacterProfile profile,
            List<String> ownedCardIds,
            List<OwnedCard> ownedCards
    ) {
        CharacterProfile target = requirePersistedProfile(profile);
        List<String> currentSkillDeck = normalizeOwnedCardIds(ownedCardIds);
        validateOwnedCardIds(currentSkillDeck, ownedCards);

        target.setCurrentSkillDeck(currentSkillDeck);
        return repository.save(target);
    }

    @Transactional
    public void deleteCurrentSkillDeckMirror(long characterId) {
        // No-op: currentSkillDeck mirror Decks are no longer maintained.
    }

    @Transactional
    public CharacterProfile clearCurrentSkillDeck(CharacterProfile profile) {
        CharacterProfile target = requirePersistedProfile(profile);
        target.setCurrentSkillDeck(null);
        return repository.save(target);
    }

    private static CharacterProfile requirePersistedProfile(CharacterProfile profile) {
        if (profile == null || profile.getId() == null || profile.getId() <= 0) {
            throw new ResponseStatusException(BAD_REQUEST, "persisted character profile is required");
        }
        return profile;
    }

    private static List<String> normalizeCardIds(List<String> cardIds) {
        if (cardIds == null) {
            throw new ResponseStatusException(BAD_REQUEST, "currentSkillDeck cardIds is required");
        }

        List<String> normalized = new ArrayList<>(cardIds.size());
        for (String cardId : cardIds) {
            if (cardId == null || cardId.isBlank()) {
                throw new ResponseStatusException(BAD_REQUEST, "currentSkillDeck cardIds must not contain blank values");
            }
            normalized.add(cardId.trim());
        }
        return List.copyOf(normalized);
    }

    private static List<String> normalizeOwnedCardIds(List<String> ownedCardIds) {
        if (ownedCardIds == null) {
            throw new ResponseStatusException(BAD_REQUEST, "currentSkillDeck ownedCardIds is required");
        }

        List<String> normalized = new ArrayList<>(ownedCardIds.size());
        Set<String> seen = new LinkedHashSet<>();
        for (String ownedCardId : ownedCardIds) {
            if (ownedCardId == null || ownedCardId.isBlank()) {
                throw new ResponseStatusException(BAD_REQUEST, "currentSkillDeck ownedCardIds must not contain blank values");
            }
            String normalizedOwnedCardId = ownedCardId.trim();
            if (!seen.add(normalizedOwnedCardId)) {
                throw new ResponseStatusException(BAD_REQUEST, "currentSkillDeck ownedCardIds must not contain duplicate values: " + normalizedOwnedCardId);
            }
            normalized.add(normalizedOwnedCardId);
        }
        return List.copyOf(normalized);
    }

    private static void validateOwnedCardIds(List<String> ownedCardIds, List<OwnedCard> ownedCards) {
        if (ownedCards == null) {
            throw new ResponseStatusException(BAD_REQUEST, "ownedCards is required for ownedCardId currentSkillDeck writes");
        }

        Map<String, OwnedCard> ownedById = new LinkedHashMap<>();
        for (OwnedCard ownedCard : ownedCards) {
            if (ownedCard != null && ownedCard.ownedCardId() != null && !ownedCard.ownedCardId().isBlank()) {
                ownedById.put(ownedCard.ownedCardId().trim(), ownedCard);
            }
        }

        List<String> cardIds = new ArrayList<>(ownedCardIds.size());
        for (String ownedCardId : ownedCardIds) {
            OwnedCard ownedCard = ownedById.get(ownedCardId);
            if (ownedCard == null) {
                throw new ResponseStatusException(BAD_REQUEST, "owned card unavailable: " + ownedCardId);
            }
            if (ownedCard.cardId() == null || ownedCard.cardId().isBlank()) {
                throw new ResponseStatusException(BAD_REQUEST, "owned card has blank cardId: " + ownedCardId);
            }
        }
    }
}
