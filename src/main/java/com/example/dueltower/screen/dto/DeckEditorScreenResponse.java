package com.example.dueltower.screen.dto;

import java.time.OffsetDateTime;
import java.util.List;

public class DeckEditorScreenResponse extends ScreenResponseBase {
    private final Long deckId;
    private final String mode;
    private final String routeTemplate;
    private final String policyGroup;
    private final String auth;
    private final DeckEditorDraftDto draft;
    private final DeckEditorDerivedDto derived;
    private final DeckEditorValidationDto validation;

    public DeckEditorScreenResponse(String screenKey,
                                    OffsetDateTime generatedAt,
                                    List<String> uiNotices,
                                    List<ScreenActionDto> possibleActions,
                                    Long deckId,
                                    String mode,
                                    String routeTemplate,
                                    String policyGroup,
                                    String auth,
                                    DeckEditorDraftDto draft,
                                    DeckEditorDerivedDto derived,
                                    DeckEditorValidationDto validation) {
        super(screenKey, generatedAt, uiNotices, possibleActions);
        this.deckId = deckId;
        this.mode = mode;
        this.routeTemplate = routeTemplate;
        this.policyGroup = policyGroup;
        this.auth = auth;
        this.draft = draft;
        this.derived = derived;
        this.validation = validation;
    }

    public Long getDeckId() {
        return deckId;
    }

    public String getMode() {
        return mode;
    }

    public String getRouteTemplate() {
        return routeTemplate;
    }

    public String getPolicyGroup() {
        return policyGroup;
    }

    public String getAuth() {
        return auth;
    }

    public DeckEditorDraftDto getDraft() {
        return draft;
    }

    public DeckEditorDerivedDto getDerived() {
        return derived;
    }

    public DeckEditorValidationDto getValidation() {
        return validation;
    }
}
