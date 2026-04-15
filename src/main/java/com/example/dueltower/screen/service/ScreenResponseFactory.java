package com.example.dueltower.screen.service;

import com.example.dueltower.content.deck.domain.DeckType;
import com.example.dueltower.content.deck.dto.DeckValidationResponse;
import com.example.dueltower.content.deck.dto.DeckResponse;
import com.example.dueltower.screen.dto.DeckEditorDerivedDto;
import com.example.dueltower.screen.dto.DeckEditorDraftCardDto;
import com.example.dueltower.screen.dto.DeckEditorDraftDto;
import com.example.dueltower.screen.dto.DeckEditorScreenResponse;
import com.example.dueltower.screen.dto.DeckEditorValidationDto;
import com.example.dueltower.screen.dto.ScreenActionAuth;
import com.example.dueltower.screen.dto.ScreenActionDto;
import com.example.dueltower.screen.dto.SessionScreenSkeletonResponse;
import com.example.dueltower.session.dto.SessionStateDto;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ScreenResponseFactory {

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

    private Map<String, Object> deckUpsertPayload(DeckEditorDraftDto draft) {
        return Map.of(
                "name", draft.name(),
                "type", draft.type().name(),
                "cards", actionCardPayload(draft.cards())
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

    private String deckTypeLabel(DeckType type) {
        if (type == null) {
            return "N/A";
        }
        return switch (type) {
            case PLAYER -> "Player";
            case ENEMY -> "Enemy";
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
