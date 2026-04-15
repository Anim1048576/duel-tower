package com.example.dueltower.screen.service;

import com.example.dueltower.character.domain.CharacterProfile;
import com.example.dueltower.character.repository.CharacterProfileRepository;
import com.example.dueltower.content.deck.domain.DeckType;
import com.example.dueltower.content.deck.dto.DeckValidationResponse;
import com.example.dueltower.content.deck.dto.DeckResponse;
import com.example.dueltower.content.card.service.CardService;
import com.example.dueltower.content.passive.service.PassiveService;
import com.example.dueltower.screen.dto.DeckEditorDerivedDto;
import com.example.dueltower.screen.dto.DeckEditorDraftCardDto;
import com.example.dueltower.screen.dto.DeckEditorDraftDto;
import com.example.dueltower.screen.dto.DeckEditorScreenResponse;
import com.example.dueltower.screen.dto.DeckEditorValidationDto;
import com.example.dueltower.screen.dto.PresetEditorDerivedDto;
import com.example.dueltower.screen.dto.PresetEditorDraftDto;
import com.example.dueltower.screen.dto.PresetEditorResolvedDto;
import com.example.dueltower.screen.dto.PresetEditorResolvedItemDto;
import com.example.dueltower.screen.dto.PresetEditorResolvedTagDto;
import com.example.dueltower.screen.dto.PresetEditorScreenResponse;
import com.example.dueltower.screen.dto.ScreenActionAuth;
import com.example.dueltower.screen.dto.ScreenActionDto;
import com.example.dueltower.screen.dto.SessionScreenSkeletonResponse;
import com.example.dueltower.session.dto.SessionStateDto;
import com.example.dueltower.preset.dto.PresetResponse;
import com.example.dueltower.engine.model.CardDefinition;
import com.example.dueltower.engine.model.CardType;
import com.example.dueltower.engine.model.Ids.CardDefId;
import com.example.dueltower.engine.model.PassiveDefinition;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class ScreenResponseFactory {

    private static final DateTimeFormatter PRESET_EDITOR_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Seoul"));

    private final CharacterProfileRepository characterProfileRepository;
    private final CardService cardService;
    private final PassiveService passiveService;

    public ScreenResponseFactory(CharacterProfileRepository characterProfileRepository,
                                 CardService cardService,
                                 PassiveService passiveService) {
        this.characterProfileRepository = characterProfileRepository;
        this.cardService = cardService;
        this.passiveService = passiveService;
    }

    public SessionScreenSkeletonResponse sessionSkeleton(ScreenRouteSpec route,
                                                         SessionStateDto state,
                                                         List<String> uiNotices) {
        return new SessionScreenSkeletonResponse(
                route.screenKey(),
                OffsetDateTime.now(),
                uiNotices,
                List.of(),
                state.sessionCode(),
                state.version(),
                route.routeTemplate(),
                route.readAuth().policyGroup(),
                route.readAuth().requiredAuth().wireValue(),
                true
        );
    }

    public DeckEditorScreenResponse deckEditor(ScreenRouteSpec route,
                                               Long deckId,
                                               String mode,
                                               DeckEditorDraftDto draft,
                                               DeckEditorDerivedDto derived,
                                               DeckEditorValidationDto validation,
                                               List<String> uiNotices) {
        return new DeckEditorScreenResponse(
                route.screenKey(),
                OffsetDateTime.now(),
                uiNotices,
                deckEditorActions(deckId, mode, draft),
                deckId,
                mode,
                route.routeTemplate(),
                route.readAuth().policyGroup(),
                route.readAuth().requiredAuth().wireValue(),
                draft,
                derived,
                validation
        );
    }

    public PresetEditorScreenResponse presetEditor(ScreenRouteSpec route,
                                                   Long presetId,
                                                   String mode,
                                                   PresetEditorDraftDto draft,
                                                   PresetEditorResolvedDto resolved,
                                                   PresetEditorDerivedDto derived,
                                                   List<String> uiNotices) {
        return new PresetEditorScreenResponse(
                route.screenKey(),
                OffsetDateTime.now(),
                uiNotices,
                presetEditorActions(presetId, mode, draft),
                presetId,
                mode,
                route.routeTemplate(),
                route.readAuth().policyGroup(),
                route.readAuth().requiredAuth().wireValue(),
                draft,
                resolved,
                derived
        );
    }

    public DeckEditorDraftDto deckEditorDraft(DeckResponse deck) {
        return new DeckEditorDraftDto(
                deck.name(),
                deck.type(),
                draftCards(deck.cards())
        );
    }

    public DeckEditorDraftDto newDeckEditorDraft() {
        return new DeckEditorDraftDto(
                "",
                DeckType.PLAYER,
                List.of()
        );
    }

    public PresetEditorDraftDto presetEditorDraft(PresetResponse preset) {
        return new PresetEditorDraftDto(
                preset.name(),
                preset.characterId(),
                List.copyOf(preset.deckCardIds()),
                preset.exCardId(),
                List.copyOf(preset.passiveIds())
        );
    }

    public PresetEditorDraftDto newPresetEditorDraft() {
        return new PresetEditorDraftDto(
                "",
                null,
                List.of(),
                "",
                List.of()
        );
    }

    public DeckEditorDerivedDto deckEditorDerived(String mode,
                                                  DeckResponse sourceDeck,
                                                  DeckEditorDraftDto draft) {
        return new DeckEditorDerivedDto(
                deriveTitle(mode, sourceDeck, draft),
                deckTypeLabel(draft.type()),
                totalCards(draft.cards()),
                isDraftDirty(sourceDeck, draft)
        );
    }

    public PresetEditorResolvedDto presetEditorResolved(PresetEditorDraftDto draft) {
        Optional<CharacterProfile> character = resolveCharacter(draft.characterId());
        Optional<CardDefinition> exCard = resolveCard(draft.exCardId());

        return new PresetEditorResolvedDto(
                character.map(this::presetCharacterLabel).orElse(characterMissingLabel(draft.characterId())),
                character.map(this::presetCharacterSubtitle).orElse(characterMissingSubtitle(draft.characterId())),
                character.map(this::presetCharacterTags).orElse(List.of(new PresetEditorResolvedTagDto(
                        draft.characterId() == null ? "Empty" : "Unresolved",
                        draft.characterId() == null ? "muted" : "warning"
                ))),
                exCard.map(this::presetCardLabel).orElse(cardMissingLabel(draft.exCardId(), "No EX card selected")),
                exCard.map(this::presetExSubtitle).orElse(cardMissingSubtitle(draft.exCardId(), "Select an EX card for the preset draft")),
                exCard.map(card -> List.of(
                        new PresetEditorResolvedTagDto("EX", "accent"),
                        new PresetEditorResolvedTagDto("Resolved", "success")
                )).orElse(List.of(new PresetEditorResolvedTagDto(
                        safeTrim(draft.exCardId()).isBlank() ? "Empty" : "Unresolved",
                        safeTrim(draft.exCardId()).isBlank() ? "muted" : "warning"
                ))),
                presetDeckItems(draft.deckCardIds()),
                presetPassiveItems(draft.passiveIds())
        );
    }

    public PresetEditorDerivedDto presetEditorDerived(String mode,
                                                      PresetResponse sourcePreset,
                                                      PresetEditorDraftDto draft) {
        return new PresetEditorDerivedDto(
                isPresetDraftDirty(sourcePreset, draft),
                formatPresetTimestampLabel(sourcePreset == null ? null : sourcePreset.createdAt(), "Available after create"),
                formatPresetTimestampLabel(sourcePreset == null ? null : sourcePreset.updatedAt(), "Available after create")
        );
    }

    public DeckEditorValidationDto deckEditorValidation(DeckValidationResponse validationResponse,
                                                        DeckEditorDraftDto draft) {
        String validatedSignature = draftSignature(draft);
        return new DeckEditorValidationDto(
                validationResponse.valid(),
                validationResponse.normalizedTotalCards(),
                validationResponse.issues(),
                validatedSignature,
                OffsetDateTime.now()
        );
    }

    private List<ScreenActionDto> deckEditorActions(Long deckId,
                                                    String mode,
                                                    DeckEditorDraftDto draft) {
        if ("create".equals(mode)) {
            return List.of(createDeckAction(draft));
        }

        List<ScreenActionDto> actions = new ArrayList<>();
        actions.add(validateDeckAction(deckId, draft));
        actions.add(saveDeckAction(deckId, draft));
        actions.add(deleteDeckAction(deckId));
        return List.copyOf(actions);
    }

    private List<ScreenActionDto> presetEditorActions(Long presetId,
                                                      String mode,
                                                      PresetEditorDraftDto draft) {
        if ("create".equals(mode)) {
            return List.of(createPresetAction(draft));
        }

        List<ScreenActionDto> actions = new ArrayList<>();
        actions.add(savePresetAction(presetId, draft));
        actions.add(clonePresetAction(presetId));
        actions.add(deletePresetAction(presetId));
        return List.copyOf(actions);
    }

    private ScreenActionDto validateDeckAction(Long deckId, DeckEditorDraftDto draft) {
        return ScreenActionDto.of(
                "deckEditor.validate",
                "Validate deck",
                "POST",
                "/api/content/decks/" + deckId + "/validate",
                ScreenActionAuth.LOGIN_COOKIE,
                true,
                null,
                Map.of(
                        "type", draft.type().name(),
                        "cards", actionCardPayload(draft.cards())
                )
        );
    }

    private ScreenActionDto saveDeckAction(Long deckId, DeckEditorDraftDto draft) {
        return ScreenActionDto.of(
                "deckEditor.save",
                "Save deck",
                "PUT",
                "/api/content/decks/" + deckId,
                ScreenActionAuth.LOGIN_COOKIE,
                true,
                null,
                deckUpsertPayload(draft)
        );
    }

    private ScreenActionDto createDeckAction(DeckEditorDraftDto draft) {
        return ScreenActionDto.of(
                "deckEditor.create",
                "Create deck",
                "POST",
                "/api/content/decks",
                ScreenActionAuth.LOGIN_COOKIE,
                true,
                null,
                deckUpsertPayload(draft)
        );
    }

    private ScreenActionDto deleteDeckAction(Long deckId) {
        return ScreenActionDto.of(
                "deckEditor.delete",
                "Delete deck",
                "DELETE",
                "/api/content/decks/" + deckId,
                ScreenActionAuth.LOGIN_COOKIE,
                true,
                null,
                null
        );
    }

    private ScreenActionDto savePresetAction(Long presetId, PresetEditorDraftDto draft) {
        return ScreenActionDto.of(
                "presetEditor.save",
                "Save preset",
                "PUT",
                "/api/me/presets/" + presetId,
                ScreenActionAuth.LOGIN_COOKIE,
                true,
                null,
                presetUpsertPayload(draft)
        );
    }

    private ScreenActionDto createPresetAction(PresetEditorDraftDto draft) {
        return ScreenActionDto.of(
                "presetEditor.create",
                "Create preset",
                "POST",
                "/api/me/presets",
                ScreenActionAuth.LOGIN_COOKIE,
                true,
                null,
                presetUpsertPayload(draft)
        );
    }

    private ScreenActionDto clonePresetAction(Long presetId) {
        return ScreenActionDto.of(
                "presetEditor.clone",
                "Clone preset",
                "POST",
                "/api/me/presets/" + presetId + "/clone",
                ScreenActionAuth.LOGIN_COOKIE,
                true,
                null,
                null
        );
    }

    private ScreenActionDto deletePresetAction(Long presetId) {
        return ScreenActionDto.of(
                "presetEditor.delete",
                "Delete preset",
                "DELETE",
                "/api/me/presets/" + presetId,
                ScreenActionAuth.LOGIN_COOKIE,
                true,
                null,
                null
        );
    }

    private Map<String, Object> deckUpsertPayload(DeckEditorDraftDto draft) {
        return Map.of(
                "name", draft.name(),
                "type", draft.type().name(),
                "cards", actionCardPayload(draft.cards())
        );
    }

    private Map<String, Object> presetUpsertPayload(PresetEditorDraftDto draft) {
        return Map.of(
                "name", safeTrim(draft.name()),
                "characterId", draft.characterId() == null ? 0L : draft.characterId(),
                "deckCardIds", List.copyOf(draft.deckCardIds()),
                "exCardId", safeTrim(draft.exCardId()),
                "passiveIds", List.copyOf(draft.passiveIds())
        );
    }

    private List<Map<String, Object>> actionCardPayload(List<DeckEditorDraftCardDto> cards) {
        return cards.stream()
                .map(card -> Map.<String, Object>of(
                        "cardId", card.cardId(),
                        "count", card.count()
                ))
                .toList();
    }

    private List<DeckEditorDraftCardDto> draftCards(List<com.example.dueltower.content.deck.dto.DeckCardDto> cards) {
        List<DeckEditorDraftCardDto> draftCards = new ArrayList<>();
        for (int index = 0; index < cards.size(); index++) {
            var card = cards.get(index);
            int position = index + 1;
            draftCards.add(new DeckEditorDraftCardDto(
                    "deck-card-" + position,
                    card.cardId(),
                    card.count(),
                    position
            ));
        }
        return List.copyOf(draftCards);
    }

    private String deriveTitle(String mode, DeckResponse sourceDeck, DeckEditorDraftDto draft) {
        String trimmedName = draft.name() == null ? "" : draft.name().trim();
        if (!trimmedName.isBlank()) {
            return trimmedName;
        }
        if ("create".equals(mode)) {
            return "New deck";
        }
        if (sourceDeck != null && sourceDeck.name() != null && !sourceDeck.name().isBlank()) {
            return sourceDeck.name();
        }
        return "Untitled deck";
    }

    private Optional<CharacterProfile> resolveCharacter(Long characterId) {
        if (characterId == null || characterId <= 0) {
            return Optional.empty();
        }
        return characterProfileRepository.findById(characterId);
    }

    private Optional<CardDefinition> resolveCard(String rawCardId) {
        String normalized = safeTrim(rawCardId);
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(cardService.asMap().get(new CardDefId(normalized)));
    }

    private Optional<PassiveDefinition> resolvePassive(String rawPassiveId) {
        String normalized = safeTrim(rawPassiveId);
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(passiveService.defsMap().get(normalized));
    }

    private List<PresetEditorResolvedItemDto> presetDeckItems(List<String> deckCardIds) {
        List<PresetEditorResolvedItemDto> items = new ArrayList<>();
        for (int index = 0; index < deckCardIds.size(); index++) {
            int position = index + 1;
            String cardId = deckCardIds.get(index);
            Optional<CardDefinition> resolved = resolveCard(cardId);
            items.add(resolved
                    .map(card -> new PresetEditorResolvedItemDto(
                            "deck-" + position,
                            presetCardLabel(card),
                            card.type().name(),
                            "Entry " + position + " | Cost " + card.cost(),
                            List.of(
                                    new PresetEditorResolvedTagDto(card.type().name(), cardTypeTone(card.type())),
                                    new PresetEditorResolvedTagDto("Resolved", "success")
                            )
                    ))
                    .orElseGet(() -> new PresetEditorResolvedItemDto(
                            "deck-" + position,
                            safeTrim(cardId),
                            "Deck card id",
                            "Entry " + position + " | Unresolved",
                            List.of(new PresetEditorResolvedTagDto("Unresolved", "warning"))
                    )));
        }
        return List.copyOf(items);
    }

    private List<PresetEditorResolvedItemDto> presetPassiveItems(List<String> passiveIds) {
        List<PresetEditorResolvedItemDto> items = new ArrayList<>();
        for (int index = 0; index < passiveIds.size(); index++) {
            int position = index + 1;
            String passiveId = passiveIds.get(index);
            Optional<PassiveDefinition> resolved = resolvePassive(passiveId);
            items.add(resolved
                    .map(passive -> new PresetEditorResolvedItemDto(
                            "passive-" + position,
                            passive.name() + " (" + passive.id() + ")",
                            "Passive definition",
                            "Entry " + position + " | Priority " + passive.priority(),
                            List.of(
                                    new PresetEditorResolvedTagDto("Passive", "success"),
                                    new PresetEditorResolvedTagDto("Resolved", "success")
                            )
                    ))
                    .orElseGet(() -> new PresetEditorResolvedItemDto(
                            "passive-" + position,
                            safeTrim(passiveId),
                            "Passive id",
                            "Entry " + position + " | Unresolved",
                            List.of(new PresetEditorResolvedTagDto("Unresolved", "warning"))
                    )));
        }
        return List.copyOf(items);
    }

    private String presetCharacterLabel(CharacterProfile profile) {
        return profile.getName() + " #" + profile.getId();
    }

    private String presetCharacterSubtitle(CharacterProfile profile) {
        if (profile.getDisposition() != null && !profile.getDisposition().isBlank()) {
            return profile.getDisposition();
        }
        if (profile.getOneLiner() != null && !profile.getOneLiner().isBlank()) {
            return profile.getOneLiner();
        }
        return "Character reference";
    }

    private List<PresetEditorResolvedTagDto> presetCharacterTags(CharacterProfile profile) {
        List<PresetEditorResolvedTagDto> tags = new ArrayList<>();
        tags.add(new PresetEditorResolvedTagDto("Character", "accent"));
        tags.add(new PresetEditorResolvedTagDto("Resolved", "success"));
        if (profile.getCurrentSkillDeck() != null && !profile.getCurrentSkillDeck().isEmpty()) {
            tags.add(new PresetEditorResolvedTagDto(profile.getCurrentSkillDeck().size() + " linked cards", "muted"));
        }
        return List.copyOf(tags);
    }

    private String characterMissingLabel(Long characterId) {
        if (characterId == null) {
            return "No character selected";
        }
        return "Character #" + characterId + " (unresolved)";
    }

    private String characterMissingSubtitle(Long characterId) {
        if (characterId == null) {
            return "Choose a character for the preset draft";
        }
        return "The referenced character could not be restored";
    }

    private String presetCardLabel(CardDefinition card) {
        return card.name() + " (" + card.id().value() + ")";
    }

    private String presetExSubtitle(CardDefinition card) {
        return "EX card | Cost " + card.cost();
    }

    private String cardMissingLabel(String rawCardId, String emptyLabel) {
        String normalized = safeTrim(rawCardId);
        if (normalized.isBlank()) {
            return emptyLabel;
        }
        return normalized + " (unresolved)";
    }

    private String cardMissingSubtitle(String rawCardId, String emptySubtitle) {
        return safeTrim(rawCardId).isBlank() ? emptySubtitle : "The referenced card could not be restored";
    }

    private String formatPresetTimestampLabel(java.sql.Timestamp timestamp, String fallback) {
        if (timestamp == null) {
            return fallback;
        }
        return PRESET_EDITOR_TIMESTAMP_FORMATTER.format(timestamp.toInstant());
    }

    private boolean isPresetDraftDirty(PresetResponse sourcePreset, PresetEditorDraftDto draft) {
        if (sourcePreset == null) {
            return false;
        }
        if (!safeTrim(sourcePreset.name()).equals(safeTrim(draft.name()))) {
            return true;
        }
        if (!sourcePreset.characterId().equals(draft.characterId())) {
            return true;
        }
        if (!safeTrim(sourcePreset.exCardId()).equals(safeTrim(draft.exCardId()))) {
            return true;
        }
        if (!sourcePreset.deckCardIds().equals(draft.deckCardIds())) {
            return true;
        }
        return !sourcePreset.passiveIds().equals(draft.passiveIds());
    }

    private String deckTypeLabel(DeckType type) {
        if (type == null) {
            return "N/A";
        }
        return switch (type) {
            case PLAYER -> "Player";
            case ENEMY -> "Enemy";
        };
    }

    private String cardTypeTone(CardType type) {
        if (type == null) {
            return "muted";
        }
        return switch (type) {
            case EX -> "accent";
            case SKILL, TOKEN -> "muted";
        };
    }

    private int totalCards(List<DeckEditorDraftCardDto> cards) {
        return cards.stream().mapToInt(DeckEditorDraftCardDto::count).sum();
    }

    private boolean isDraftDirty(DeckResponse sourceDeck, DeckEditorDraftDto draft) {
        if (sourceDeck == null) {
            return false;
        }
        if (!safeTrim(sourceDeck.name()).equals(safeTrim(draft.name()))) {
            return true;
        }
        if (sourceDeck.type() != draft.type()) {
            return true;
        }
        if (sourceDeck.cards().size() != draft.cards().size()) {
            return true;
        }
        for (int index = 0; index < sourceDeck.cards().size(); index++) {
            var sourceCard = sourceDeck.cards().get(index);
            var draftCard = draft.cards().get(index);
            if (!safeTrim(sourceCard.cardId()).equals(safeTrim(draftCard.cardId()))) {
                return true;
            }
            if (sourceCard.count() != draftCard.count()) {
                return true;
            }
        }
        return false;
    }

    private String draftSignature(DeckEditorDraftDto draft) {
        String typeToken = draft.type() == null ? "" : draft.type().name();
        String cardsToken = draft.cards().stream()
                .map(card -> safeTrim(card.cardId()) + ":" + card.count())
                .reduce((left, right) -> left + "|" + right)
                .orElse("");
        return "type=" + typeToken + ";cards=" + cardsToken;
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}
