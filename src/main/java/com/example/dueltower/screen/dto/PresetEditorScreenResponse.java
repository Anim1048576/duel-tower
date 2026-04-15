package com.example.dueltower.screen.dto;

import java.time.OffsetDateTime;
import java.util.List;

public class PresetEditorScreenResponse extends ScreenResponseBase {
    private final Long presetId;
    private final String mode;
    private final String routeTemplate;
    private final String policyGroup;
    private final String auth;
    private final PresetEditorDraftDto draft;
    private final PresetEditorResolvedDto resolved;
    private final PresetEditorDerivedDto derived;

    public PresetEditorScreenResponse(String screenKey,
                                      OffsetDateTime generatedAt,
                                      List<String> uiNotices,
                                      List<ScreenActionDto> possibleActions,
                                      Long presetId,
                                      String mode,
                                      String routeTemplate,
                                      String policyGroup,
                                      String auth,
                                      PresetEditorDraftDto draft,
                                      PresetEditorResolvedDto resolved,
                                      PresetEditorDerivedDto derived) {
        super(screenKey, generatedAt, uiNotices, possibleActions);
        this.presetId = presetId;
        this.mode = mode;
        this.routeTemplate = routeTemplate;
        this.policyGroup = policyGroup;
        this.auth = auth;
        this.draft = draft;
        this.resolved = resolved;
        this.derived = derived;
    }

    public Long getPresetId() {
        return presetId;
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

    public PresetEditorDraftDto getDraft() {
        return draft;
    }

    public PresetEditorResolvedDto getResolved() {
        return resolved;
    }

    public PresetEditorDerivedDto getDerived() {
        return derived;
    }
}
