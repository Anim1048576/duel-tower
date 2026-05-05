package com.example.dueltower.screen.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Screen model for PlayerLobby.
 * The server owns participant summary, reference option curation,
 * and action availability so the frontend can stay in render + local draft mode.
 */
public class PlayerLobbyScreenResponse extends ScreenResponseBase {
    private final String sessionCode;
    private final long version;
    private final String routeTemplate;
    private final String policyGroup;
    private final String auth;
    private final List<PlayerLobbyParticipantSlotDto> participantSlots;
    private final PlayerLobbyMeDto me;
    private final PlayerLobbyDeckEditorStateDto deckEditor;
    private final PlayerLobbyReferencesDto references;

    public PlayerLobbyScreenResponse(String screenKey,
                                     OffsetDateTime generatedAt,
                                     List<String> uiNotices,
                                     List<ScreenActionDto> possibleActions,
                                     String sessionCode,
                                     long version,
                                     String routeTemplate,
                                     String policyGroup,
                                     String auth,
                                     List<PlayerLobbyParticipantSlotDto> participantSlots,
                                     PlayerLobbyMeDto me,
                                     PlayerLobbyDeckEditorStateDto deckEditor,
                                     PlayerLobbyReferencesDto references) {
        super(screenKey, generatedAt, uiNotices, possibleActions);
        this.sessionCode = sessionCode;
        this.version = version;
        this.routeTemplate = routeTemplate;
        this.policyGroup = policyGroup;
        this.auth = auth;
        this.participantSlots = participantSlots == null ? List.of() : List.copyOf(participantSlots);
        this.me = me;
        this.deckEditor = deckEditor;
        this.references = references;
    }

    public String getSessionCode() {
        return sessionCode;
    }

    public long getVersion() {
        return version;
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

    public List<PlayerLobbyParticipantSlotDto> getParticipantSlots() {
        return participantSlots;
    }

    public PlayerLobbyMeDto getMe() {
        return me;
    }

    public PlayerLobbyDeckEditorStateDto getDeckEditor() {
        return deckEditor;
    }

    public PlayerLobbyReferencesDto getReferences() {
        return references;
    }

}
