package com.example.dueltower.screen.service;

import com.example.dueltower.content.deck.dto.DeckCardSpec;
import com.example.dueltower.content.deck.dto.DeckResponse;
import com.example.dueltower.content.deck.service.DeckService;
import com.example.dueltower.screen.dto.DeckEditorDraftDto;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class DeckEditorScreenService {

    private static final List<String> DECK_EDITOR_NOTICE = List.of(
            "Deck editor actions currently reuse the existing /api/content/decks endpoints."
    );

    private final DeckService deckService;
    private final ScreenResponseFactory screenResponseFactory;

    public DeckEditorScreenService(DeckService deckService,
                                   ScreenResponseFactory screenResponseFactory) {
        this.deckService = deckService;
        this.screenResponseFactory = screenResponseFactory;
    }

    public Object getEditor(long deckId, Authentication authentication) {
        requireAuthenticatedUsername(authentication);
        DeckResponse deck = deckService.get(deckId);
        DeckEditorDraftDto draft = screenResponseFactory.deckEditorDraft(deck);
        return screenResponseFactory.deckEditor(
                ScreenRouteSpec.DECK_EDITOR,
                deck.id(),
                "edit",
                draft,
                screenResponseFactory.deckEditorDerived("edit", deck, draft),
                screenResponseFactory.deckEditorValidation(
                        deckService.validateDraft(draft.type(), toDeckCardSpecs(draft)),
                        draft
                ),
                DECK_EDITOR_NOTICE
        );
    }

    public Object getNewEditor(Authentication authentication) {
        requireAuthenticatedUsername(authentication);
        DeckEditorDraftDto draft = screenResponseFactory.newDeckEditorDraft();
        return screenResponseFactory.deckEditor(
                ScreenRouteSpec.NEW_DECK_EDITOR,
                null,
                "create",
                draft,
                screenResponseFactory.deckEditorDerived("create", null, draft),
                screenResponseFactory.deckEditorValidation(
                        deckService.validateDraft(draft.type(), toDeckCardSpecs(draft)),
                        draft
                ),
                DECK_EDITOR_NOTICE
        );
    }

    private List<DeckCardSpec> toDeckCardSpecs(DeckEditorDraftDto draft) {
        return draft.cards().stream()
                .map(card -> new DeckCardSpec(card.cardId(), card.count()))
                .toList();
    }

    private static String requireAuthenticatedUsername(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new ResponseStatusException(UNAUTHORIZED, "login required");
        }
        return authentication.getName();
    }
}
