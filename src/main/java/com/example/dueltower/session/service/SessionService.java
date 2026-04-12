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
import com.example.dueltower.session.runtime.SessionRuntime;
import com.example.dueltower.preset.service.PresetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;

/**
 * Legacy compatibility bridge for former {@code SessionService} consumers.
 *
 * <p>Production session responsibilities now live in dedicated services:
 * lifecycle, lobby, loadout, query, and command. This class intentionally keeps
 * only a tiny public surface for compatibility tests and temporary callers.</p>
 */
@Service
@Deprecated(forRemoval = false)
public class SessionService {

    private final SessionLoadoutSupport sessionLoadoutSupport;
    private final SessionLifecycleService sessionLifecycleService;

    @Autowired
    public SessionService(SessionLoadoutSupport sessionLoadoutSupport,
                          SessionLifecycleService sessionLifecycleService) {
        this.sessionLoadoutSupport = sessionLoadoutSupport;
        this.sessionLifecycleService = sessionLifecycleService;
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
        this(
                new SessionLoadoutSupport(
                        characterProfileRepository,
                        cardService,
                        deckService,
                        passiveService,
                        gameRules,
                        starterLoadoutConfig
                ),
                null
        );
    }

    public <T> T withSessionLock(String code, Function<SessionRuntime, T> reader) {
        if (sessionLifecycleService == null) {
            throw new IllegalStateException("SessionLifecycleService is not available");
        }
        return sessionLifecycleService.withSessionLock(code, reader);
    }

    // Legacy reflection entry points kept for parsing-focused tests.
    private List<OwnedCard> parseOwnedCards(List<OwnedCardDto> ownedCardsRaw) {
        return sessionLoadoutSupport.parseOwnedCards(ownedCardsRaw);
    }

    private List<OwnedCardDto> parseOwnedCardsJson(String raw) {
        return sessionLoadoutSupport.parseOwnedCardsJson(raw);
    }

    private List<OwnedCardModifier> toOwnedCardModifiers(OwnedCardDto dto) {
        return sessionLoadoutSupport.toOwnedCardModifiers(dto);
    }
}
