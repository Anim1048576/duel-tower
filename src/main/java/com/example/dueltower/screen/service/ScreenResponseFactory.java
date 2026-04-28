package com.example.dueltower.screen.service;

import com.example.dueltower.character.domain.CharacterProfile;
import com.example.dueltower.character.repository.CharacterProfileRepository;
import com.example.dueltower.character.service.CharacterLoadoutService;
import com.example.dueltower.content.card.model.OwnedCard;
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
import com.example.dueltower.screen.dto.DisabledReasonDto;
import com.example.dueltower.screen.dto.GmLobbyParticipantCardDto;
import com.example.dueltower.screen.dto.GmLobbyScreenResponse;
import com.example.dueltower.screen.dto.GmLobbySelectableStartPlayerDto;
import com.example.dueltower.screen.dto.GmLobbyStartCombatDto;
import com.example.dueltower.screen.dto.GmLobbyTagDto;
import com.example.dueltower.screen.dto.PlayerLobbyDraftFlagsDto;
import com.example.dueltower.screen.dto.PlayerLobbyDeckEditorStateDto;
import com.example.dueltower.screen.dto.PlayerLobbyLoadoutDto;
import com.example.dueltower.screen.dto.PlayerLobbyMeDto;
import com.example.dueltower.screen.dto.PlayerLobbyMeSummaryDto;
import com.example.dueltower.screen.dto.PlayerLobbyOptionDto;
import com.example.dueltower.screen.dto.PlayerLobbyOwnedCardOptionDto;
import com.example.dueltower.screen.dto.PlayerLobbyParticipantSlotDto;
import com.example.dueltower.screen.dto.PlayerLobbyPresetItemDto;
import com.example.dueltower.screen.dto.PlayerLobbyPresetPreviewDto;
import com.example.dueltower.screen.dto.PlayerLobbyPresetsDto;
import com.example.dueltower.screen.dto.PlayerLobbyPreviewItemDto;
import com.example.dueltower.screen.dto.PlayerLobbyReferencesDto;
import com.example.dueltower.screen.dto.PlayerLobbyScreenResponse;
import com.example.dueltower.screen.dto.PlayerLobbyTagDto;
import com.example.dueltower.screen.dto.PresetEditorDerivedDto;
import com.example.dueltower.screen.dto.PresetEditorDraftDto;
import com.example.dueltower.screen.dto.PresetEditorResolvedDto;
import com.example.dueltower.screen.dto.PresetEditorResolvedItemDto;
import com.example.dueltower.screen.dto.PresetEditorResolvedTagDto;
import com.example.dueltower.screen.dto.PresetEditorScreenResponse;
import com.example.dueltower.screen.dto.ScreenActionAuth;
import com.example.dueltower.screen.dto.ScreenActionDto;
import com.example.dueltower.screen.dto.SessionScreenSkeletonResponse;
import com.example.dueltower.preset.dto.PresetResponse;
import com.example.dueltower.engine.model.CardDefinition;
import com.example.dueltower.engine.model.CardType;
import com.example.dueltower.engine.model.Ids.CardDefId;
import com.example.dueltower.engine.model.Ids.PlayerId;
import com.example.dueltower.engine.model.PassiveDefinition;
import com.example.dueltower.session.dto.OwnedCardDto;
import com.example.dueltower.session.dto.PlayerStateDto;
import com.example.dueltower.session.dto.SessionStateDto;
import com.example.dueltower.session.runtime.SessionRuntime;
import com.example.dueltower.session.service.PlayerLobbyDeckEditAnalysis;
import com.example.dueltower.session.service.SessionAccessDecision;
import com.example.dueltower.session.service.StartCombatAvailabilityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Objects;

@Component
public class ScreenResponseFactory {

    private static final DateTimeFormatter PRESET_EDITOR_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Seoul"));

    private final CharacterProfileRepository characterProfileRepository;
    private final CharacterLoadoutService characterLoadoutService;
    private final CardService cardService;
    private final PassiveService passiveService;
    private final StartCombatAvailabilityService startCombatAvailabilityService;

    @Autowired
    public ScreenResponseFactory(CharacterProfileRepository characterProfileRepository,
                                 CharacterLoadoutService characterLoadoutService,
                                 CardService cardService,
                                 PassiveService passiveService,
                                 StartCombatAvailabilityService startCombatAvailabilityService) {
        this.characterProfileRepository = characterProfileRepository;
        this.characterLoadoutService = Objects.requireNonNull(characterLoadoutService, "characterLoadoutService is required");
        this.cardService = cardService;
        this.passiveService = passiveService;
        this.startCombatAvailabilityService = startCombatAvailabilityService;
    }

    public ScreenResponseFactory(CharacterProfileRepository characterProfileRepository,
                                 CharacterLoadoutService characterLoadoutService,
                                 CardService cardService,
                                 PassiveService passiveService) {
        this(characterProfileRepository, characterLoadoutService, cardService, passiveService, null);
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

    public PlayerLobbyScreenResponse playerLobby(ScreenRouteSpec route,
                                                 SessionStateDto state,
                                                 SessionRuntime runtime,
                                                 String currentPlayerId,
                                                 PlayerStateDto me,
                                                 PlayerLobbyDeckEditAnalysis deckEditAnalysis,
                                                 List<com.example.dueltower.character.dto.CharacterProfileResponse> characters,
                                                 List<CardDefinition> exCards,
                                                 List<PassiveDefinition> passives,
                                                 List<PresetResponse> presets,
                                                 List<String> uiNotices) {
        PlayerLobbyLoadoutDto loadout = playerLobbyLoadout(runtime, me);
        PlayerLobbyPresetsDto presetSection = playerLobbyPresets(presets);
        return new PlayerLobbyScreenResponse(
                route.screenKey(),
                OffsetDateTime.now(),
                uiNotices,
                playerLobbyActions(state.sessionCode(), me, presetSection.selectedId(), loadout, deckEditAnalysis),
                state.sessionCode(),
                state.version(),
                route.routeTemplate(),
                route.readAuth().policyGroup(),
                route.readAuth().requiredAuth().wireValue(),
                participantSlots(state, currentPlayerId, runtime),
                new PlayerLobbyMeDto(
                        me.playerId(),
                        me.ready(),
                        loadout,
                        playerLobbySummary(me, loadout),
                        loadout,
                        playerLobbyDraftFlags(loadout)
                ),
                playerLobbyDeckEditor(me, deckEditAnalysis),
                playerLobbyReferences(characters, exCards, passives, me),
                presetSection
        );
    }

    public GmLobbyScreenResponse gmLobby(ScreenRouteSpec route,
                                         SessionStateDto state,
                                         SessionRuntime runtime,
                                         SessionAccessDecision accessDecision,
                                         List<com.example.dueltower.character.dto.CharacterProfileResponse> characters,
                                         List<CardDefinition> exCards,
                                         List<PassiveDefinition> passives,
                                         List<String> uiNotices) {
        List<PlayerStateDto> sortedPlayers = gmLobbySortedPlayers(state);
        GmLobbyStartCombatDto startCombat = gmLobbyStartCombat(state, runtime, sortedPlayers);
        return new GmLobbyScreenResponse(
                route.screenKey(),
                OffsetDateTime.now(),
                uiNotices,
                gmLobbyActions(state, sortedPlayers, startCombat, accessDecision),
                state.sessionCode(),
                state.version(),
                route.routeTemplate(),
                route.readAuth().policyGroup(),
                route.readAuth().requiredAuth().wireValue(),
                gmLobbyParticipantCards(state, runtime, sortedPlayers, characters, exCards, passives),
                startCombat
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

    public PlayerLobbyDeckEditorStateDto playerLobbyDeckEditorFromOwnedCards(List<OwnedCard> ownedCards,
                                                                             List<String> draftDeckOwnedCardIds,
                                                                             PlayerLobbyDeckEditAnalysis analysis) {
        List<OwnedCardRef> ownedCardRefs = ownedCards == null
                ? List.of()
                : ownedCards.stream()
                .map(ownedCard -> new OwnedCardRef(ownedCard.ownedCardId(), ownedCard.cardId()))
                .toList();
        return buildPlayerLobbyDeckEditor(ownedCardRefs, draftDeckOwnedCardIds, analysis);
    }

    private List<ScreenActionDto> playerLobbyActions(String sessionCode,
                                                     PlayerStateDto me,
                                                     Long selectedPresetId,
                                                     PlayerLobbyLoadoutDto loadout,
                                                     PlayerLobbyDeckEditAnalysis deckEditAnalysis) {
        List<ScreenActionDto> actions = new ArrayList<>();
        actions.add(toggleReadyAction(sessionCode, me, loadout, deckEditAnalysis));
        actions.add(leavePlayerLobbyAction(sessionCode));
        actions.add(saveLoadoutAction(sessionCode, me.playerId(), loadout, deckEditAnalysis));
        actions.add(applyPresetAction(sessionCode, me.playerId(), selectedPresetId));
        return List.copyOf(actions);
    }

    private List<ScreenActionDto> gmLobbyActions(SessionStateDto state,
                                                 List<PlayerStateDto> sortedPlayers,
                                                 GmLobbyStartCombatDto startCombat,
                                                 SessionAccessDecision accessDecision) {
        List<ScreenActionDto> actions = new ArrayList<>();
        boolean gmTokenAvailable = accessDecision.source() == SessionAccessDecision.SessionAccessSource.GM_TOKEN;
        actions.add(gmLobbyKickAction(state, sortedPlayers, gmTokenAvailable));
        actions.add(gmLobbyResetAction(state.sessionCode(), gmTokenAvailable));
        actions.add(gmLobbyStartCombatAction(state, startCombat, accessDecision));
        return List.copyOf(actions);
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

    private ScreenActionDto toggleReadyAction(String sessionCode,
                                              PlayerStateDto me,
                                              PlayerLobbyLoadoutDto loadout,
                                              PlayerLobbyDeckEditAnalysis deckEditAnalysis) {
        boolean nextReady = !me.ready();
        DisabledReasonDto disabledReason = nextReady ? playerLobbyLoadoutBlockedReason(loadout, deckEditAnalysis, "ready") : null;
        return ScreenActionDto.of(
                "playerLobby.toggleReady",
                nextReady ? "Mark ready" : "Mark not ready",
                "PUT",
                "/api/sessions/" + sessionCode + "/players/" + me.playerId() + "/ready",
                ScreenActionAuth.PLAYER_TOKEN,
                disabledReason == null,
                disabledReason,
                Map.of("ready", nextReady)
        );
    }

    private ScreenActionDto leavePlayerLobbyAction(String sessionCode) {
        return ScreenActionDto.of(
                "playerLobby.leave",
                "Leave session",
                "POST",
                "/api/sessions/" + sessionCode + "/leave",
                ScreenActionAuth.PLAYER_TOKEN,
                true,
                null,
                null
        );
    }

    private ScreenActionDto saveLoadoutAction(String sessionCode,
                                              String playerId,
                                              PlayerLobbyLoadoutDto loadout,
                                              PlayerLobbyDeckEditAnalysis deckEditAnalysis) {
        DisabledReasonDto disabledReason = playerLobbyLoadoutBlockedReason(loadout, deckEditAnalysis, "save");
        Map<String, Object> payloadTemplate = new LinkedHashMap<>();
        payloadTemplate.put("characterId", loadout.characterId());
        payloadTemplate.put("passiveIds", loadout.passiveIds());
        payloadTemplate.put("deckOwnedCardIds", loadout.deckOwnedCardIds());
        payloadTemplate.put("exCardId", safeTrim(loadout.exCardId()));
        return ScreenActionDto.of(
                "playerLobby.saveLoadout",
                "Save loadout",
                "POST",
                "/api/sessions/" + sessionCode + "/players/" + playerId + "/loadout",
                ScreenActionAuth.PLAYER_TOKEN,
                disabledReason == null,
                disabledReason,
                payloadTemplate
        );
    }

    private DisabledReasonDto playerLobbyLoadoutBlockedReason(PlayerLobbyLoadoutDto loadout,
                                                              PlayerLobbyDeckEditAnalysis deckEditAnalysis,
                                                              String actionName) {
        if (loadout == null || loadout.characterId() == null) {
            return new DisabledReasonDto(
                    "CHARACTER_REQUIRED",
                    "VALIDATION",
                    playerLobbyLoadoutBlockedMessage(actionName, "Choose a character first."),
                    "player lobby " + actionName + " requires a selected character",
                    null,
                    null,
                    null
            );
        }
        if (safeTrim(loadout.exCardId()).isBlank()) {
            return new DisabledReasonDto(
                    "EX_CARD_REQUIRED",
                    "VALIDATION",
                    playerLobbyLoadoutBlockedMessage(actionName, "Choose an EX card first."),
                    "player lobby " + actionName + " requires a selected EX card",
                    null,
                    null,
                    null
            );
        }
        if (deckEditAnalysis == null || !deckEditAnalysis.saveAllowed()) {
            List<String> reasonCodes = deckEditAnalysis == null
                    ? List.of()
                    : deckEditAnalysis.globalIssues().stream()
                    .map(issue -> issue.code().name())
                    .toList();
            return new DisabledReasonDto(
                    "DECK_EDIT_INVALID",
                    "VALIDATION",
                    playerLobbyLoadoutBlockedMessage(actionName, "Fix the deck validation issues first."),
                    "player lobby " + actionName + " blocked by deck edit analysis",
                    Map.of("reasonCodes", reasonCodes),
                    null,
                    null
            );
        }
        return null;
    }

    private String playerLobbyLoadoutBlockedMessage(String actionName, String reason) {
        if ("ready".equals(actionName)) {
            return reason + " You can mark ready after the loadout is valid.";
        }
        return reason + " You can save the loadout after it is valid.";
    }

    private ScreenActionDto applyPresetAction(String sessionCode,
                                              String playerId,
                                              Long selectedPresetId) {
        boolean enabled = selectedPresetId != null && selectedPresetId > 0;
        return ScreenActionDto.of(
                "playerLobby.applyPreset",
                "Apply preset",
                "POST",
                "/api/sessions/" + sessionCode + "/players/" + playerId + "/loadout/from-preset",
                ScreenActionAuth.PLAYER_TOKEN,
                enabled,
                enabled ? null : new DisabledReasonDto(
                        "PRESET_REQUIRED",
                        "VALIDATION",
                        "Choose a saved preset before applying it to the current session.",
                        "player lobby applyPreset requires at least one selectable preset",
                        null,
                        null,
                        null
                ),
                Map.of("presetId", selectedPresetId == null ? 0L : selectedPresetId)
        );
    }

    private ScreenActionDto gmLobbyKickAction(SessionStateDto state,
                                              List<PlayerStateDto> sortedPlayers,
                                              boolean gmActionAllowed) {
        PlayerStateDto selectedPlayer = sortedPlayers.isEmpty() ? null : sortedPlayers.get(0);
        DisabledReasonDto disabledReason = gmActionAllowed
                ? gmKickBlockedReason(state, selectedPlayer)
                : gmTokenRequiredReason("kick");
        boolean enabled = disabledReason == null;

        Map<String, Object> payloadTemplate = new LinkedHashMap<>();
        payloadTemplate.put("playerId", selectedPlayer == null ? "" : selectedPlayer.playerId());
        payloadTemplate.put("reason", "");

        return ScreenActionDto.of(
                "gmLobby.kick",
                "Kick player",
                "POST",
                "/api/sessions/" + state.sessionCode() + "/players/" + (selectedPlayer == null ? "<playerId>" : selectedPlayer.playerId()) + "/kick",
                ScreenActionAuth.GM_TOKEN,
                enabled,
                disabledReason,
                payloadTemplate
        );
    }

    private ScreenActionDto gmLobbyResetAction(String sessionCode,
                                               boolean gmActionAllowed) {
        DisabledReasonDto disabledReason = gmActionAllowed ? null : gmTokenRequiredReason("reset");
        Map<String, Object> payloadTemplate = new LinkedHashMap<>();
        payloadTemplate.put("keepPlayers", true);
        payloadTemplate.put("keepLoadouts", true);
        payloadTemplate.put("newSeed", null);
        return ScreenActionDto.of(
                "gmLobby.reset",
                "Reset session",
                "POST",
                "/api/sessions/" + sessionCode + "/reset",
                ScreenActionAuth.GM_TOKEN,
                disabledReason == null,
                disabledReason,
                payloadTemplate
        );
    }

    private ScreenActionDto gmLobbyStartCombatAction(SessionStateDto state,
                                                     GmLobbyStartCombatDto startCombat,
                                                     SessionAccessDecision accessDecision) {
        DisabledReasonDto disabledReason = null;
        ScreenActionAuth auth = switch (accessDecision.source()) {
            case GM_TOKEN -> ScreenActionAuth.GM_TOKEN;
            case AUTHENTICATED_GM -> ScreenActionAuth.LOGIN_COOKIE;
            default -> ScreenActionAuth.GM_TOKEN;
        };
        if (accessDecision.source() != SessionAccessDecision.SessionAccessSource.GM_TOKEN
                && accessDecision.source() != SessionAccessDecision.SessionAccessSource.AUTHENTICATED_GM) {
            disabledReason = new DisabledReasonDto(
                    "GM_ACCESS_REQUIRED",
                    "AUTH",
                    "GM access is required to start combat from the GM lobby.",
                    "current access source cannot restore GM access",
                    Map.of("source", accessDecision.source().name()),
                    403,
                    null
            );
        } else {
            disabledReason = startCombat.blockedReason();
        }
        Map<String, Object> payloadTemplate = new LinkedHashMap<>();
        payloadTemplate.put("expectedVersion", state.version());
        payloadTemplate.put("playerId", safeTrim(startCombat.recommendedStartPlayerId()));
        return ScreenActionDto.of(
                "gmLobby.startCombat",
                "Start combat",
                "POST",
                "/api/screens/sessions/" + state.sessionCode() + "/gm-lobby/start-combat",
                auth,
                disabledReason == null,
                disabledReason,
                payloadTemplate
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

    private List<PlayerLobbyParticipantSlotDto> participantSlots(SessionStateDto state,
                                                                 String currentPlayerId,
                                                                 SessionRuntime runtime) {
        List<PlayerStateDto> sortedPlayers = state.players().values().stream()
                .sorted(Comparator.comparing((PlayerStateDto player) -> !player.playerId().equals(currentPlayerId))
                        .thenComparing(PlayerStateDto::playerId))
                .toList();
        List<PlayerLobbyParticipantSlotDto> slots = new ArrayList<>();
        for (int index = 0; index < sortedPlayers.size(); index++) {
            PlayerStateDto player = sortedPlayers.get(index);
            slots.add(new PlayerLobbyParticipantSlotDto(
                    "P" + (index + 1),
                    participantName(player, currentPlayerId),
                    participantState(player, currentPlayerId),
                    participantTone(player, currentPlayerId),
                    playerLoadoutSummary(playerLobbyLoadout(runtime, player))
            ));
        }
        return List.copyOf(slots);
    }

    private PlayerLobbyMeSummaryDto playerLobbySummary(PlayerStateDto me, PlayerLobbyLoadoutDto loadout) {
        String readyLabel = me.ready() ? "Ready" : "Joined";
        String readyTone = me.ready() ? "success" : "accent";
        String loadoutSummary = playerLoadoutSummary(loadout);
        return new PlayerLobbyMeSummaryDto(
                readyLabel,
                readyTone,
                loadoutSummary,
                loadoutSummary,
                me.ready()
                        ? "You are marked ready in the current session."
                        : "You are joined in the current session and can still edit your loadout."
        );
    }

    private PlayerLobbyDraftFlagsDto playerLobbyDraftFlags(PlayerLobbyLoadoutDto draft) {
        boolean missingRequiredFields = draft.characterId() == null || safeTrim(draft.exCardId()).isBlank();
        return new PlayerLobbyDraftFlagsDto(
                false,
                draft.characterId() == null,
                missingRequiredFields
        );
    }

    private PlayerLobbyLoadoutDto playerLobbyLoadout(SessionRuntime runtime, PlayerStateDto player) {
        Long characterId = runtime.findCharacterIdByPlayerId(player.playerId());
        String exCardId = safeTrim(resolvePlayerExCardId(runtime, player));
        return new PlayerLobbyLoadoutDto(
                characterId,
                resolveCharacterLabel(characterId),
                List.copyOf(player.deckOwnedCardIds()),
                exCardId,
                resolveCardLabel(exCardId, "No EX card selected"),
                List.copyOf(player.passiveIds()),
                player.deckOwnedCardIds().size(),
                player.passiveIds().size()
        );
    }

    private PlayerLobbyDeckEditorStateDto playerLobbyDeckEditor(PlayerStateDto me,
                                                                PlayerLobbyDeckEditAnalysis analysis) {
        List<OwnedCardRef> ownedCardRefs = me == null || me.ownedCards() == null
                ? List.of()
                : me.ownedCards().stream()
                .map(ownedCard -> new OwnedCardRef(ownedCard.ownedCardId(), ownedCard.cardId()))
                .toList();
        List<String> draftDeckOwnedCardIds = me == null ? List.of() : me.deckOwnedCardIds();
        return buildPlayerLobbyDeckEditor(ownedCardRefs, draftDeckOwnedCardIds, analysis);
    }

    private PlayerLobbyDeckEditorStateDto buildPlayerLobbyDeckEditor(List<OwnedCardRef> ownedCards,
                                                                     List<String> draftDeckOwnedCardIds,
                                                                     PlayerLobbyDeckEditAnalysis analysis) {
        if (analysis == null) {
            return new PlayerLobbyDeckEditorStateDto(null, List.of(), List.of(), List.of(), List.of());
        }

        Set<String> draftOwnedCardIds = new LinkedHashSet<>(draftDeckOwnedCardIds == null ? List.of() : draftDeckOwnedCardIds);
        return new PlayerLobbyDeckEditorStateDto(
                new PlayerLobbyDeckEditorStateDto.DeckState(
                        analysis.deck().requiredDeckSize(),
                        analysis.deck().draftDeckSize(),
                        analysis.deck().changedCardCount(),
                        analysis.deck().saveAllowed()
                ),
                analysis.globalIssues().stream()
                        .map(issue -> issue.code().name())
                        .toList(),
                analysis.globalIssues().stream()
                        .map(issue -> new PlayerLobbyDeckEditorStateDto.Issue(
                                issue.level().name(),
                                issue.code().name(),
                                issue.details()
                        ))
                        .toList(),
                analysis.deckEntries().stream()
                        .map(entry -> new PlayerLobbyDeckEditorStateDto.DraftEntry(
                                entry.ownedCardId(),
                                entry.cardId(),
                                entry.inSavedDeck(),
                                entry.lockedInDeck(),
                                entry.removable(),
                                issueCodes(entry.blockedReasons())
                        ))
                        .toList(),
                analysis.cardPoolGroups().stream()
                        .map(group -> new PlayerLobbyDeckEditorStateDto.CardPoolGroup(
                                group.cardId(),
                                group.currentDeckCount(),
                                group.totalOwnedCount(),
                                group.availableOwnedCount(),
                                group.addable(),
                                issueCodes(group.blockedReasons()),
                                ownedCards.stream()
                                        .filter(ownedCard -> safeTrim(ownedCard.cardId()).equals(group.cardId()))
                                        .map(ownedCard -> playerLobbyDeckEditorOwnedCardState(ownedCard, group, draftOwnedCardIds))
                                        .toList()
                        ))
                        .toList()
        );
    }

    private PlayerLobbyDeckEditorStateDto.OwnedCardState playerLobbyDeckEditorOwnedCardState(
            OwnedCardRef ownedCard,
            PlayerLobbyDeckEditAnalysis.CardPoolGroupAnalysis group,
            Set<String> draftOwnedCardIds
    ) {
        boolean inDraftDeck = draftOwnedCardIds.contains(safeTrim(ownedCard.ownedCardId()));
        List<String> reasonCodes = inDraftDeck
                ? List.of(PlayerLobbyDeckEditAnalysis.IssueCode.ALREADY_IN_DECK.name())
                : issueCodes(group.blockedReasons());
        return new PlayerLobbyDeckEditorStateDto.OwnedCardState(
                ownedCard.ownedCardId(),
                ownedCard.cardId(),
                inDraftDeck,
                !inDraftDeck && group.addable(),
                reasonCodes
        );
    }

    private record OwnedCardRef(
            String ownedCardId,
            String cardId
    ) {}

    private List<String> issueCodes(List<PlayerLobbyDeckEditAnalysis.IssueCode> codes) {
        return codes.stream()
                .map(Enum::name)
                .toList();
    }

    private PlayerLobbyReferencesDto playerLobbyReferences(List<com.example.dueltower.character.dto.CharacterProfileResponse> characters,
                                                           List<CardDefinition> exCards,
                                                           List<PassiveDefinition> passives,
                                                           PlayerStateDto me) {
        return new PlayerLobbyReferencesDto(
                characterOptions(characters),
                exCardOptions(exCards),
                passiveOptions(passives),
                ownedCardOptions(me.ownedCards())
        );
    }

    private List<PlayerLobbyOptionDto> characterOptions(List<com.example.dueltower.character.dto.CharacterProfileResponse> characters) {
        return characters.stream()
                .map(character -> new PlayerLobbyOptionDto(
                        String.valueOf(character.id()),
                        character.name() + " #" + character.id(),
                        firstNonBlank(character.disposition(), character.oneLiner(), "Character reference"),
                        List.of(new PlayerLobbyTagDto("Character", "accent"))
                ))
                .toList();
    }

    private List<PlayerLobbyOptionDto> exCardOptions(List<CardDefinition> exCards) {
        return exCards.stream()
                .map(card -> new PlayerLobbyOptionDto(
                        card.id().value(),
                        presetCardLabel(card),
                        "EX card | Cost " + card.cost(),
                        List.of(new PlayerLobbyTagDto("EX", "accent"))
                ))
                .toList();
    }

    private List<PlayerLobbyOptionDto> passiveOptions(List<PassiveDefinition> passives) {
        return passives.stream()
                .map(passive -> new PlayerLobbyOptionDto(
                        passive.id(),
                        passive.name() + " (" + passive.id() + ")",
                        "Passive definition | Priority " + passive.priority(),
                        List.of(new PlayerLobbyTagDto("Passive", "success"))
                ))
                .toList();
    }

    private List<PlayerLobbyOwnedCardOptionDto> ownedCardOptions(List<OwnedCardDto> ownedCards) {
        return ownedCards.stream()
                .map(ownedCard -> {
                    Optional<CardDefinition> resolved = resolveCard(ownedCard.cardId());
                    List<PlayerLobbyTagDto> tags = new ArrayList<>();
                    resolved.ifPresent(card -> tags.add(new PlayerLobbyTagDto(card.type().name(), cardTypeTone(card.type()))));
                    if (Boolean.TRUE.equals(ownedCard.lockedInDeck())) {
                        tags.add(new PlayerLobbyTagDto("Locked", "warning"));
                    }
                    if (Boolean.FALSE.equals(ownedCard.forgettable())) {
                        tags.add(new PlayerLobbyTagDto("Forget blocked", "muted"));
                    }
                    return new PlayerLobbyOwnedCardOptionDto(
                            safeTrim(ownedCard.ownedCardId()),
                            safeTrim(ownedCard.cardId()),
                            resolved.map(card -> card.name() + " (" + safeTrim(ownedCard.ownedCardId()) + ")")
                                    .orElse(safeTrim(ownedCard.ownedCardId())),
                            resolved.map(card -> "Card " + card.id().value() + " | Cost " + card.cost())
                                    .orElse("Owned card reference"),
                            List.copyOf(tags)
                    );
                })
                .toList();
    }

    private PlayerLobbyPresetsDto playerLobbyPresets(List<PresetResponse> presets) {
        Long selectedId = presets.isEmpty() ? null : presets.get(0).id();
        PlayerLobbyPresetPreviewDto preview = presets.isEmpty() ? null : presetPreview(presets.get(0));
        return new PlayerLobbyPresetsDto(
                presets.stream()
                        .map(preset -> new PlayerLobbyPresetItemDto(
                                preset.id(),
                                preset.name() + " | Character #" + preset.characterId() + " | " + preset.deckCardIds().size() + " cards | " + preset.passiveIds().size() + " passives",
                                "EX " + safeTrim(preset.exCardId())
                        ))
                        .toList(),
                selectedId,
                preview
        );
    }

    private PlayerLobbyPresetPreviewDto presetPreview(PresetResponse preset) {
        return new PlayerLobbyPresetPreviewDto(
                preset.name(),
                "Deck " + preset.deckCardIds().size() + " cards | " + preset.passiveIds().size() + " passives",
                resolveCharacterLabel(preset.characterId()),
                resolveCardLabel(preset.exCardId(), "No EX card selected"),
                preset.deckCardIds().stream()
                        .map(this::presetPreviewDeckItem)
                        .toList(),
                preset.passiveIds().stream()
                        .map(this::presetPreviewPassiveItem)
                        .toList()
        );
    }

    private PlayerLobbyPreviewItemDto presetPreviewDeckItem(String cardId) {
        Optional<CardDefinition> resolved = resolveCard(cardId);
        return resolved
                .map(card -> new PlayerLobbyPreviewItemDto(
                        card.id().value(),
                        presetCardLabel(card),
                        card.type().name() + " | Cost " + card.cost(),
                        List.of(new PlayerLobbyTagDto(card.type().name(), cardTypeTone(card.type())))
                ))
                .orElseGet(() -> new PlayerLobbyPreviewItemDto(
                        safeTrim(cardId),
                        cardMissingLabel(cardId, "Unresolved card"),
                        "Card reference could not be restored",
                        List.of(new PlayerLobbyTagDto("Unresolved", "warning"))
                ));
    }

    private PlayerLobbyPreviewItemDto presetPreviewPassiveItem(String passiveId) {
        Optional<PassiveDefinition> resolved = resolvePassive(passiveId);
        return resolved
                .map(passive -> new PlayerLobbyPreviewItemDto(
                        passive.id(),
                        passive.name() + " (" + passive.id() + ")",
                        "Passive definition | Priority " + passive.priority(),
                        List.of(new PlayerLobbyTagDto("Passive", "success"))
                ))
                .orElseGet(() -> new PlayerLobbyPreviewItemDto(
                        safeTrim(passiveId),
                        safeTrim(passiveId),
                        "Passive reference could not be restored",
                        List.of(new PlayerLobbyTagDto("Unresolved", "warning"))
                ));
    }

    private List<PlayerStateDto> gmLobbySortedPlayers(SessionStateDto state) {
        return state.players().values().stream()
                .sorted(Comparator.comparing(PlayerStateDto::ready).reversed()
                        .thenComparing(PlayerStateDto::playerId))
                .toList();
    }

    private List<GmLobbyParticipantCardDto> gmLobbyParticipantCards(SessionStateDto state,
                                                                    SessionRuntime runtime,
                                                                    List<PlayerStateDto> sortedPlayers,
                                                                    List<com.example.dueltower.character.dto.CharacterProfileResponse> characters,
                                                                    List<CardDefinition> exCards,
                                                                    List<PassiveDefinition> passives) {
        List<GmLobbyParticipantCardDto> cards = new ArrayList<>();
        for (int index = 0; index < sortedPlayers.size(); index++) {
            PlayerStateDto player = sortedPlayers.get(index);
            String exCardId = safeTrim(resolvePlayerExCardId(runtime, player));
            cards.add(new GmLobbyParticipantCardDto(
                    "P" + (index + 1),
                    player.playerId(),
                    player.playerId(),
                    player.ready() ? "Ready" : "Not ready",
                    player.ready() ? "success" : "muted",
                    gmLobbyCharacterSummary(player, state, runtime, characters),
                    gmLobbyExSummary(exCardId, exCards),
                    gmLobbyPassiveSummary(player, passives),
                    gmLobbyDeckSummary(player),
                    gmLobbyDetailTags(player, exCardId, characters)
            ));
        }
        return List.copyOf(cards);
    }

    private GmLobbyStartCombatDto gmLobbyStartCombat(SessionStateDto state,
                                                     SessionRuntime runtime,
                                                     List<PlayerStateDto> sortedPlayers) {
        StartCombatAvailabilityService.StartCombatAvailability availability =
                startCombatAvailabilityService.analyze(runtime, null);
        String recommendedStartPlayerId = availability.recommendedStartPlayerId();
        DisabledReasonDto blockedReason = DisabledReasonDto.fromApiErrorResponse(availability.apiError());

        List<GmLobbySelectableStartPlayerDto> selectable = new ArrayList<>();
        for (int index = 0; index < sortedPlayers.size(); index++) {
            PlayerStateDto player = sortedPlayers.get(index);
            selectable.add(new GmLobbySelectableStartPlayerDto(
                    player.playerId(),
                    "P" + (index + 1),
                    player.playerId() + (player.ready() ? " | ready" : " | not ready"),
                    player.ready()
            ));
        }

        return new GmLobbyStartCombatDto(
                recommendedStartPlayerId,
                blockedReason,
                selectable
        );
    }

    private String gmLobbyCharacterSummary(PlayerStateDto player,
                                           SessionStateDto state,
                                           SessionRuntime runtime,
                                           List<com.example.dueltower.character.dto.CharacterProfileResponse> characters) {
        Long boundCharacterId = runtime.findCharacterIdByPlayerId(player.playerId());
        if (boundCharacterId != null) {
            String resolved = resolveCharacterLabel(boundCharacterId);
            if (!resolved.contains("(unresolved)")) {
                return resolved;
            }
        }

        String resolvedExCardId = safeTrim(resolvePlayerExCardId(runtime, player));
        Set<String> deckCardIds = new HashSet<>(gmLobbyResolvedDeckCardIds(player));
        com.example.dueltower.character.dto.CharacterProfileResponse bestMatch = null;
        int bestScore = 0;
        boolean bestExMatched = false;
        boolean ambiguous = false;

        for (com.example.dueltower.character.dto.CharacterProfileResponse character : characters) {
            String characterExCardId = normalizeCharacterExCardId(character.exCardId());
            boolean exMatched = !resolvedExCardId.isBlank() && resolvedExCardId.equals(characterExCardId);
            int deckOverlap = 0;
            List<String> previewCardIds = character.currentSkillDeckPreviewCardIds() == null
                    ? List.of()
                    : character.currentSkillDeckPreviewCardIds();
            for (String cardId : previewCardIds) {
                if (deckCardIds.contains(safeTrim(cardId))) {
                    deckOverlap++;
                }
            }
            int score = (exMatched ? 100 : 0) + deckOverlap;
            if (score <= 0) {
                continue;
            }
            if (bestMatch == null || score > bestScore) {
                bestMatch = character;
                bestScore = score;
                bestExMatched = exMatched;
                ambiguous = false;
                continue;
            }
            if (score == bestScore) {
                ambiguous = true;
            }
        }

        if (bestMatch == null) {
            return "Unavailable from current session data";
        }
        if (bestExMatched && !ambiguous) {
            return bestMatch.name() + " #" + bestMatch.id();
        }
        if (!ambiguous) {
            return "Likely " + bestMatch.name() + " #" + bestMatch.id();
        }
        return "Multiple character candidates";
    }

    private String gmLobbyExSummary(String exCardId,
                                    List<CardDefinition> exCards) {
        if (exCardId.isBlank()) {
            return "No EX configured";
        }
        return exCards.stream()
                .filter(card -> exCardId.equals(card.id().value()))
                .findFirst()
                .map(card -> card.name() + " (" + card.id().value() + ")")
                .orElse(exCardId);
    }

    private String gmLobbyPassiveSummary(PlayerStateDto player,
                                         List<PassiveDefinition> passives) {
        if (player.passiveIds().isEmpty()) {
            return "No passives equipped";
        }
        List<String> labels = player.passiveIds().stream()
                .map(passiveId -> resolvePassiveLabel(passiveId, passives))
                .filter(label -> !label.isBlank())
                .distinct()
                .limit(3)
                .toList();
        return player.passiveIds().size()
                + " equipped | "
                + formatPreviewList(labels, player.passiveIds().stream()
                .map(passiveId -> resolvePassiveLabel(passiveId, passives))
                .filter(label -> !label.isBlank())
                .distinct()
                .count());
    }

    private String gmLobbyDeckSummary(PlayerStateDto player) {
        List<String> deckCardIds = gmLobbyResolvedDeckCardIds(player);
        if (deckCardIds.isEmpty()) {
            return "No deck cards selected";
        }

        List<String> previewLabels = deckCardIds.stream()
                .map(cardId -> resolveCard(cardId)
                        .map(CardDefinition::name)
                        .orElse(cardId))
                .distinct()
                .limit(3)
                .toList();
        long uniqueCount = deckCardIds.stream().distinct().count();
        return deckCardIds.size()
                + " cards | "
                + uniqueCount
                + " unique | "
                + formatPreviewList(previewLabels, uniqueCount);
    }

    private List<GmLobbyTagDto> gmLobbyDetailTags(PlayerStateDto player,
                                                  String exCardId,
                                                  List<com.example.dueltower.character.dto.CharacterProfileResponse> characters) {
        List<GmLobbyTagDto> tags = new ArrayList<>();
        tags.add(new GmLobbyTagDto(
                player.deckOwnedCardIds().size() + " deck cards",
                player.deckOwnedCardIds().isEmpty() ? "muted" : "accent"
        ));
        tags.add(new GmLobbyTagDto(
                player.passiveIds().size() + " passives",
                player.passiveIds().isEmpty() ? "muted" : "success"
        ));
        tags.add(new GmLobbyTagDto(
                exCardId.isBlank() ? "No EX" : "EX selected",
                exCardId.isBlank() ? "muted" : "warning"
        ));
        String characterHint = gmLobbyCharacterHint(player, exCardId, characters);
        if (!characterHint.isBlank()) {
            tags.add(new GmLobbyTagDto(characterHint, "muted"));
        }
        return List.copyOf(tags);
    }

    private String gmLobbyCharacterHint(PlayerStateDto player,
                                        String exCardId,
                                        List<com.example.dueltower.character.dto.CharacterProfileResponse> characters) {
        if (exCardId.isBlank()) {
            return "";
        }
        long exMatchCount = characters.stream()
                .map(com.example.dueltower.character.dto.CharacterProfileResponse::exCardId)
                .map(this::normalizeCharacterExCardId)
                .filter(exCardId::equals)
                .count();
        if (exMatchCount == 1L) {
            return "Unique EX match";
        }
        if (exMatchCount > 1L) {
            return "Shared EX match";
        }
        if (!player.passiveIds().isEmpty()) {
            return "Passive-led estimate";
        }
        return "";
    }

    private DisabledReasonDto gmKickBlockedReason(SessionStateDto state,
                                                  PlayerStateDto selectedPlayer) {
        if (state.combat() != null && state.combat().phase() != null && !"END".equals(state.combat().phase())) {
            return new DisabledReasonDto(
                    "PLAYER_MANAGEMENT_UNAVAILABLE_DURING_COMBAT",
                    "RULE",
                    "Kick is unavailable while combat is active.",
                    "session combat is active",
                    Map.of("phase", state.combat().phase()),
                    null,
                    null
            );
        }
        if (selectedPlayer == null) {
            return new DisabledReasonDto(
                    "PLAYER_REQUIRED",
                    "RULE",
                    "Choose a participant before using kick.",
                    "gmLobby kick requires at least one joined player",
                    null,
                    null,
                    null
            );
        }
        return null;
    }

    private DisabledReasonDto gmTokenRequiredReason(String actionName) {
        return new DisabledReasonDto(
                "GM_TOKEN_REQUIRED",
                "AUTH",
                "Restore GM token access before using " + actionName + ".",
                "gmLobby action requires X-GM-Token",
                null,
                null,
                null
        );
    }

    private List<String> gmLobbyResolvedDeckCardIds(PlayerStateDto player) {
        return player.deckOwnedCardIds().stream()
                .map(ownedCardId -> player.ownedCards().stream()
                        .filter(entry -> ownedCardId.equals(entry.ownedCardId()))
                        .map(OwnedCardDto::cardId)
                        .findFirst()
                        .orElse(ownedCardId))
                .map(this::safeTrim)
                .filter(cardId -> !cardId.isBlank())
                .toList();
    }

    private String resolvePassiveLabel(String passiveId,
                                       List<PassiveDefinition> passives) {
        String normalizedPassiveId = safeTrim(passiveId);
        if (normalizedPassiveId.isBlank()) {
            return "";
        }
        return passives.stream()
                .filter(passive -> normalizedPassiveId.equals(passive.id()))
                .findFirst()
                .map(PassiveDefinition::name)
                .orElse(normalizedPassiveId);
    }

    private String normalizeCharacterExCardId(String rawExCard) {
        String normalized = safeTrim(rawExCard);
        if (normalized.isBlank()) {
            return "";
        }
        int idKey = normalized.indexOf("\"id\"");
        if (idKey >= 0) {
            int colon = normalized.indexOf(':', idKey);
            if (colon >= 0) {
                String suffix = normalized.substring(colon + 1).trim();
                suffix = suffix.replace("{", "").replace("}", "").replace("\"", "").trim();
                if (!suffix.isBlank()) {
                    String[] parts = suffix.split(",", 2);
                    return safeTrim(parts[0]);
                }
            }
        }
        return normalized;
    }

    private String formatPreviewList(List<String> preview,
                                     long totalCount) {
        List<String> filtered = preview.stream()
                .map(this::safeTrim)
                .filter(value -> !value.isBlank())
                .toList();
        if (filtered.isEmpty()) {
            return "";
        }
        long hiddenCount = Math.max(totalCount - filtered.size(), 0);
        return hiddenCount > 0
                ? String.join(", ", filtered) + " +" + hiddenCount + " more"
                : String.join(", ", filtered);
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

    private String participantName(PlayerStateDto player, String currentPlayerId) {
        return player.playerId().equals(currentPlayerId)
                ? player.playerId() + " (You)"
                : player.playerId();
    }

    private String participantState(PlayerStateDto player, String currentPlayerId) {
        if (player.playerId().equals(currentPlayerId)) {
            return player.ready() ? "You / Ready" : "You / Joined";
        }
        return player.ready() ? "Ready" : "Joined";
    }

    private String participantTone(PlayerStateDto player, String currentPlayerId) {
        if (player.playerId().equals(currentPlayerId)) {
            return player.ready() ? "success" : "accent";
        }
        return player.ready() ? "success" : "muted";
    }

    private String playerLoadoutSummary(PlayerLobbyLoadoutDto loadout) {
        String passiveSummary = loadout.passiveCount() > 0 ? loadout.passiveCount() + " passives" : "No passives";
        String exSummary = safeTrim(loadout.exCardId()).isBlank() ? "No EX card" : "EX " + safeTrim(loadout.exCardId());
        return "Deck " + loadout.deckCount() + " cards | " + passiveSummary + " | " + exSummary;
    }

    private String resolvePlayerExCardId(SessionRuntime runtime, PlayerStateDto player) {
        var runtimePlayer = runtime.state().player(new PlayerId(player.playerId()));
        if (runtimePlayer == null) {
            return safeTrim(player.exCard());
        }
        if (runtimePlayer.exCard() == null) {
            return safeTrim(player.exCard());
        }
        var exCard = runtime.state().card(runtimePlayer.exCard());
        return safeTrim(exCard == null ? player.exCard() : exCard.defId().value());
    }

    private String resolveCharacterLabel(Long characterId) {
        return resolveCharacter(characterId)
                .map(this::presetCharacterLabel)
                .orElse(characterMissingLabel(characterId));
    }

    private String resolveCardLabel(String rawCardId, String emptyLabel) {
        return resolveCard(rawCardId)
                .map(this::presetCardLabel)
                .orElse(cardMissingLabel(rawCardId, emptyLabel));
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
        int totalCards = characterLoadoutService.getCurrentSkillDeckPreviewCardIds(profile.getId()).size();
        if (totalCards > 0) {
            tags.add(new PresetEditorResolvedTagDto(totalCards + " applied cards", "muted"));
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

    private String firstNonBlank(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return fallback;
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}
