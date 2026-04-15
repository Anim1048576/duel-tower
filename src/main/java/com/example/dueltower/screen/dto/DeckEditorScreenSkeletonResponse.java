package com.example.dueltower.screen.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Legacy scaffold DTO kept temporarily for workspace cleanup compatibility.
 * New DeckEditor responses should use {@link DeckEditorScreenResponse}.
 */
@Deprecated(forRemoval = true)
public class DeckEditorScreenSkeletonResponse extends ScreenResponseBase {
    private final Long deckId;
    private final String mode;
    private final String deckName;
    private final String routeTemplate;
    private final String policyGroup;
    private final String auth;
    private final boolean stub;

    public DeckEditorScreenSkeletonResponse(String screenKey,
                                            OffsetDateTime generatedAt,
                                            List<String> uiNotices,
                                            List<ScreenActionDto> possibleActions,
                                            Long deckId,
                                            String mode,
                                            String deckName,
                                            String routeTemplate,
                                            String policyGroup,
                                            String auth,
                                            boolean stub) {
        super(screenKey, generatedAt, uiNotices, possibleActions);
        this.deckId = deckId;
        this.mode = mode;
        this.deckName = deckName;
        this.routeTemplate = routeTemplate;
        this.policyGroup = policyGroup;
        this.auth = auth;
        this.stub = stub;
    }

    public Long getDeckId() {
        return deckId;
    }

    public String getMode() {
        return mode;
    }

    public String getDeckName() {
        return deckName;
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

    public boolean isStub() {
        return stub;
    }
}
