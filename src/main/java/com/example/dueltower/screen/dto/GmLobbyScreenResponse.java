package com.example.dueltower.screen.dto;

import java.time.OffsetDateTime;
import java.util.List;

public class GmLobbyScreenResponse extends ScreenResponseBase {
    private final String sessionCode;
    private final long version;
    private final String routeTemplate;
    private final String policyGroup;
    private final String auth;
    private final List<GmLobbyParticipantCardDto> participantCards;
    private final GmLobbyStartCombatDto startCombat;

    public GmLobbyScreenResponse(String screenKey,
                                 OffsetDateTime generatedAt,
                                 List<String> uiNotices,
                                 List<ScreenActionDto> possibleActions,
                                 String sessionCode,
                                 long version,
                                 String routeTemplate,
                                 String policyGroup,
                                 String auth,
                                 List<GmLobbyParticipantCardDto> participantCards,
                                 GmLobbyStartCombatDto startCombat) {
        super(screenKey, generatedAt, uiNotices, possibleActions);
        this.sessionCode = sessionCode;
        this.version = version;
        this.routeTemplate = routeTemplate;
        this.policyGroup = policyGroup;
        this.auth = auth;
        this.participantCards = participantCards == null ? List.of() : List.copyOf(participantCards);
        this.startCombat = startCombat;
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

    public List<GmLobbyParticipantCardDto> getParticipantCards() {
        return participantCards;
    }

    public GmLobbyStartCombatDto getStartCombat() {
        return startCombat;
    }
}
