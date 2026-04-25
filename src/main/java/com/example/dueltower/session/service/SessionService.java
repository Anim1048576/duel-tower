package com.example.dueltower.session.service;

import com.example.dueltower.character.repository.CharacterProfileRepository;
import com.example.dueltower.config.GameRules;
import com.example.dueltower.config.RewardTableConfig;
import com.example.dueltower.content.card.model.OwnedCard;
import com.example.dueltower.content.card.model.OwnedCardModifier;
import com.example.dueltower.content.card.service.CardService;
import com.example.dueltower.content.cardmodifier.service.CardModifierService;
import com.example.dueltower.content.deck.service.DeckService;
import com.example.dueltower.content.equip.service.EquipService;
import com.example.dueltower.content.item.service.ItemService;
import com.example.dueltower.content.keyword.service.KeywordService;
import com.example.dueltower.content.passive.service.PassiveService;
import com.example.dueltower.content.status.service.StatusService;
import com.example.dueltower.engine.config.EncounterTables;
import com.example.dueltower.engine.config.RunConfigs;
import com.example.dueltower.session.config.StarterLoadoutConfig;
import com.example.dueltower.session.dto.OwnedCardDto;
import com.example.dueltower.session.dto.OwnedCardModifierDto;
import com.example.dueltower.session.runtime.SessionRuntime;
import com.example.dueltower.preset.service.PresetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * Compatibility surface for legacy tests that still reference {@code SessionService}.
 *
 * <p>Production session responsibilities live in dedicated services.
 * This class intentionally keeps only a tiny bridge plus parsing helpers used by
 * reflection-based compatibility tests.</p>
 */
@Service
@Deprecated(forRemoval = false)
public class SessionService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final SessionLoadoutSupport sessionLoadoutSupport;
    private final SessionLifecycleService sessionLifecycleService;
    private final StarterLoadoutConfig starterLoadoutConfig;

    @Autowired
    public SessionService(SessionLoadoutSupport sessionLoadoutSupport,
                          SessionLifecycleService sessionLifecycleService) {
        this.sessionLoadoutSupport = sessionLoadoutSupport;
        this.sessionLifecycleService = sessionLifecycleService;
        this.starterLoadoutConfig = null;
    }

    SessionService(CharacterProfileRepository characterProfileRepository,
                   CardService cardService,
                   DeckService deckService,
                   StatusService statusService,
                   KeywordService keywordService,
                   ItemService itemService,
                   EquipService equipService,
                   PassiveService passiveService,
                   CardModifierService cardModifierService,
                   PresetService presetService,
                   GameRules gameRules,
                   RewardTableConfig rewardTableConfig,
                   StarterLoadoutConfig starterLoadoutConfig,
                   RunConfigs runConfigs,
                   EncounterTables encounterTables,
                   Duration sessionTtl,
                   Duration cleanupInterval) {
        this.sessionLoadoutSupport = null;
        this.sessionLifecycleService = null;
        this.starterLoadoutConfig = starterLoadoutConfig;
    }

    public <T> T withSessionLock(String code, Function<SessionRuntime, T> reader) {
        if (sessionLifecycleService == null) {
            throw new IllegalStateException("SessionLifecycleService is not available");
        }
        return sessionLifecycleService.withLockedSession(code, reader);
    }

    // Legacy reflection entry points kept for parsing-focused tests.
    private List<OwnedCard> parseOwnedCards(List<OwnedCardDto> ownedCardsRaw) {
        if (ownedCardsRaw == null || ownedCardsRaw.isEmpty()) {
            return starterLoadoutConfig.defaultOwnedCards();
        }
        return SessionNormalizationSupport.normalizeOwnedCards(ownedCardsRaw);
    }

    private List<OwnedCardDto> parseOwnedCardsJson(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        try {
            List<JsonNode> nodes = JSON.readValue(raw, new TypeReference<>() {});
            List<OwnedCardDto> out = new ArrayList<>();
            for (int i = 0; i < nodes.size(); i++) {
                JsonNode node = nodes.get(i);
                if (node == null || node.isNull()) continue;
                if (node.isTextual()) {
                    String cardId = node.asText("").trim();
                    if (!cardId.isEmpty()) out.add(new OwnedCardDto(null, cardId, List.of(), false, false, false, true, null));
                    continue;
                }
                String cardId = node.path("cardId").asText("").trim();
                if (cardId.isEmpty()) {
                    throw invalidPersistedOwnedCards("entry[" + i + "] has missing cardId");
                }
                String ownedCardId = node.path("ownedCardId").asText("").trim();
                out.add(new OwnedCardDto(
                        ownedCardId.isEmpty() ? null : ownedCardId,
                        cardId,
                        parseOwnedCardModifierDtos(node.path("modifiers")),
                        node.path("strengthened").asBoolean(false),
                        node.path("weakened").asBoolean(false),
                        node.path("lockedInDeck").asBoolean(false),
                        true,
                        null
                ));
            }
            return List.copyOf(out);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw invalidPersistedOwnedCards("malformed JSON");
        }
    }

    private List<OwnedCardModifier> toOwnedCardModifiers(OwnedCardDto dto) {
        return SessionNormalizationSupport.normalizeOwnedCardModifiers(dto);
    }

    private static List<OwnedCardModifierDto> parseOwnedCardModifierDtos(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<OwnedCardModifierDto> out = new ArrayList<>();
        for (JsonNode modifierNode : node) {
            if (modifierNode == null || modifierNode.isNull()) {
                continue;
            }
            String modifierId = modifierNode.path("modifierId").asText("").trim();
            if (!modifierId.isEmpty()) {
                out.add(new OwnedCardModifierDto(modifierId, modifierNode.path("value").asInt(0)));
            }
        }
        return List.copyOf(out);
    }

    private static ResponseStatusException invalidPersistedOwnedCards(String detail) {
        return new ResponseStatusException(BAD_REQUEST, "invalid persisted ownedCards payload: " + detail);
    }
}
