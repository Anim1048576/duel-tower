package com.example.dueltower.screen.service;

import com.example.dueltower.preset.dto.PresetResponse;
import com.example.dueltower.preset.service.PresetService;
import com.example.dueltower.screen.dto.PresetEditorDraftDto;
import com.example.dueltower.screen.dto.PresetEditorScreenResponse;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
/**
 * PresetEditor Screen API assembler.
 * The backend owns preview resolution and screen-shape assembly so the frontend
 * only keeps local input state and presentation helpers.
 */
public class PresetEditorScreenService {

    private static final List<String> PRESET_EDITOR_NOTICE = List.of(
            "Preset editor preview and reference resolution are assembled on the server-side screen model."
    );

    private final PresetService presetService;
    private final ScreenResponseFactory screenResponseFactory;

    public PresetEditorScreenService(PresetService presetService,
                                     ScreenResponseFactory screenResponseFactory) {
        this.presetService = presetService;
        this.screenResponseFactory = screenResponseFactory;
    }

    public PresetEditorScreenResponse getEditor(long presetId, Authentication authentication) {
        String ownerUsername = requireAuthenticatedUsername(authentication);
        PresetResponse preset = presetService.getMine(ownerUsername, presetId);
        PresetEditorDraftDto draft = screenResponseFactory.presetEditorDraft(preset);
        return screenResponseFactory.presetEditor(
                ScreenRouteSpec.PRESET_EDITOR,
                preset.id(),
                "edit",
                draft,
                screenResponseFactory.presetEditorResolved(draft),
                screenResponseFactory.presetEditorDerived("edit", preset, draft),
                PRESET_EDITOR_NOTICE
        );
    }

    public PresetEditorScreenResponse getNewEditor(Authentication authentication) {
        requireAuthenticatedUsername(authentication);
        PresetEditorDraftDto draft = screenResponseFactory.newPresetEditorDraft();
        return screenResponseFactory.presetEditor(
                ScreenRouteSpec.NEW_PRESET_EDITOR,
                null,
                "create",
                draft,
                screenResponseFactory.presetEditorResolved(draft),
                screenResponseFactory.presetEditorDerived("create", null, draft),
                PRESET_EDITOR_NOTICE
        );
    }

    private static String requireAuthenticatedUsername(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new ResponseStatusException(UNAUTHORIZED, "login required");
        }
        return authentication.getName();
    }
}
