package com.example.dueltower.screen.dto;

import java.time.OffsetDateTime;
import java.util.List;

public class SessionScreenSkeletonResponse extends ScreenResponseBase {
    private final String sessionCode;
    private final long version;
    private final String routeTemplate;
    private final String policyGroup;
    private final String auth;
    private final boolean stub;

    public SessionScreenSkeletonResponse(String screenKey,
                                         OffsetDateTime generatedAt,
                                         List<String> uiNotices,
                                         List<ScreenActionDto> possibleActions,
                                         String sessionCode,
                                         long version,
                                         String routeTemplate,
                                         String policyGroup,
                                         String auth,
                                         boolean stub) {
        super(screenKey, generatedAt, uiNotices, possibleActions);
        this.sessionCode = sessionCode;
        this.version = version;
        this.routeTemplate = routeTemplate;
        this.policyGroup = policyGroup;
        this.auth = auth;
        this.stub = stub;
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

    public boolean isStub() {
        return stub;
    }
}
