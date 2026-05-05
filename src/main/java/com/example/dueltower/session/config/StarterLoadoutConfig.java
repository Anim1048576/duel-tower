package com.example.dueltower.session.config;

import com.example.dueltower.config.GameRules;
import com.example.dueltower.content.card.model.OwnedCard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class StarterLoadoutConfig {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final String defaultExCardId;
    private final List<OwnedCard> defaultOwnedCards;
    private final List<String> defaultDeckCardIds;

    @Autowired
    public StarterLoadoutConfig(GameRules gameRules,
                                @Value("${duel.balance.starter-loadout:classpath:balance/starter-loadout.json}") Resource starterLoadoutResource) {
        this(load(starterLoadoutResource), gameRules);
    }

    StarterLoadoutConfig(StarterLoadoutRaw raw, GameRules gameRules) {
        if (raw == null) {
            throw new IllegalStateException("starter loadout config is missing");
        }

        String normalizedExCardId = normalizeRequired(raw.defaultExCardId(), "defaultExCardId");

        List<String> normalizedOwnedCardIds = normalizeRequiredList(raw.defaultOwnedCardIds(), "defaultOwnedCardIds");
        if (normalizedOwnedCardIds.size() > gameRules.maxOwnedCards()) {
            throw new IllegalStateException("starter defaultOwnedCardIds size must be <= " + gameRules.maxOwnedCards());
        }

        List<String> normalizedDeckCardIds = normalizeRequiredList(raw.defaultDeckCardIds(), "defaultDeckCardIds");
        if (normalizedDeckCardIds.size() != gameRules.deckSize()) {
            throw new IllegalStateException("starter defaultDeckCardIds size must be exactly " + gameRules.deckSize());
        }

        validateDeckCardCoverage(normalizedDeckCardIds, normalizedOwnedCardIds);

        List<OwnedCard> ownedCards = new ArrayList<>(normalizedOwnedCardIds.size());
        for (String cardId : normalizedOwnedCardIds) {
            ownedCards.add(OwnedCard.fromLegacy(cardId, false, false, false));
        }

        this.defaultExCardId = normalizedExCardId;
        this.defaultOwnedCards = List.copyOf(ownedCards);
        this.defaultDeckCardIds = List.copyOf(normalizedDeckCardIds);
    }

    public static StarterLoadoutConfig defaults(GameRules gameRules) {
        return new StarterLoadoutConfig(new StarterLoadoutRaw(
                "EX901",
                List.of(
                        "C001", "C001", "C001", "C001", "C001",
                        "C002", "C002", "C002", "C002", "C002",
                        "C003", "C003", "C003", "C003", "C003",
                        "C004", "C004", "C004", "C004", "C004"
                ),
                List.of(
                        "C001", "C001", "C001",
                        "C002", "C002", "C002",
                        "C003", "C003", "C003",
                        "C004", "C004", "C004"
                )
        ), gameRules);
    }

    public String defaultExCardId() {
        return defaultExCardId;
    }

    public List<OwnedCard> defaultOwnedCards() {
        return defaultOwnedCards;
    }

    public List<String> defaultDeckCardIds() {
        return defaultDeckCardIds;
    }

    static StarterLoadoutRaw load(Resource resource) {
        if (resource == null) {
            throw new IllegalStateException("starter loadout resource is missing");
        }
        try (InputStream in = resource.getInputStream()) {
            return JSON.readValue(in, StarterLoadoutRaw.class);
        } catch (IOException e) {
            throw new IllegalStateException("failed to load starter loadout config from " + resource.getDescription(), e);
        }
    }

    private static String normalizeRequired(String raw, String fieldName) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("starter " + fieldName + " must not be blank");
        }
        return raw.trim();
    }

    private static List<String> normalizeRequiredList(List<String> rawList, String fieldName) {
        if (rawList == null || rawList.isEmpty()) {
            throw new IllegalStateException("starter " + fieldName + " must not be empty");
        }
        List<String> out = new ArrayList<>(rawList.size());
        for (String raw : rawList) {
            out.add(normalizeRequired(raw, fieldName + "[]"));
        }
        return out;
    }

    private static void validateDeckCardCoverage(List<String> deckCardIds, List<String> ownedCardIds) {
        Map<String, Integer> ownedCountByCardId = new LinkedHashMap<>();
        for (String ownedCardId : ownedCardIds) {
            ownedCountByCardId.merge(ownedCardId, 1, Integer::sum);
        }

        Map<String, Integer> deckCountByCardId = new LinkedHashMap<>();
        for (String deckCardId : deckCardIds) {
            deckCountByCardId.merge(deckCardId, 1, Integer::sum);
        }

        for (Map.Entry<String, Integer> entry : deckCountByCardId.entrySet()) {
            int ownedCount = ownedCountByCardId.getOrDefault(entry.getKey(), 0);
            if (ownedCount < entry.getValue()) {
                throw new IllegalStateException("starter defaultDeckCardIds contains " + entry.getKey()
                        + " x" + entry.getValue() + " but defaultOwnedCardIds has only x" + ownedCount);
            }
        }
    }

    record StarterLoadoutRaw(
            String defaultExCardId,
            List<String> defaultOwnedCardIds,
            List<String> defaultDeckCardIds
    ) {}
}
