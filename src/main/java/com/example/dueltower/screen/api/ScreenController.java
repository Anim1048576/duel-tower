package com.example.dueltower.screen.api;

import com.example.dueltower.screen.dto.DeckEditorScreenResponse;
import com.example.dueltower.screen.dto.PresetEditorScreenResponse;
import com.example.dueltower.screen.service.ScreenQueryService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/screens")
public class ScreenController {

    private final ScreenQueryService screenQueryService;

    public ScreenController(ScreenQueryService screenQueryService) {
        this.screenQueryService = screenQueryService;
    }

    @GetMapping("/sessions/{code}/player-lobby")
    public Object playerLobby(@PathVariable String code,
                              @RequestHeader(value = "X-GM-Token", required = false) String gmTokenHeader,
                              @RequestHeader(value = "X-Player-Token", required = false) String playerTokenHeader,
                              Authentication authentication) {
        return screenQueryService.getPlayerLobby(code, gmTokenHeader, playerTokenHeader, authentication);
    }

    @GetMapping("/sessions/{code}/gm-lobby")
    public Object gmLobby(@PathVariable String code,
                          @RequestHeader(value = "X-GM-Token", required = false) String gmTokenHeader,
                          @RequestHeader(value = "X-Player-Token", required = false) String playerTokenHeader,
                          Authentication authentication) {
        return screenQueryService.getGmLobby(code, gmTokenHeader, playerTokenHeader, authentication);
    }

    @GetMapping("/sessions/{code}/combat")
    public Object combat(@PathVariable String code,
                         @RequestHeader(value = "X-GM-Token", required = false) String gmTokenHeader,
                         @RequestHeader(value = "X-Player-Token", required = false) String playerTokenHeader,
                         Authentication authentication) {
        return screenQueryService.getCombat(code, gmTokenHeader, playerTokenHeader, authentication);
    }

    @GetMapping("/decks/new/editor")
    public DeckEditorScreenResponse newDeckEditor(Authentication authentication) {
        return screenQueryService.getNewDeckEditor(authentication);
    }

    @GetMapping("/decks/{id}/editor")
    public DeckEditorScreenResponse deckEditor(@PathVariable long id,
                                               Authentication authentication) {
        return screenQueryService.getDeckEditor(id, authentication);
    }

    @GetMapping("/presets/new/editor")
    public PresetEditorScreenResponse newPresetEditor(Authentication authentication) {
        return screenQueryService.getNewPresetEditor(authentication);
    }

    @GetMapping("/presets/{id}/editor")
    public PresetEditorScreenResponse presetEditor(@PathVariable long id,
                                                   Authentication authentication) {
        return screenQueryService.getPresetEditor(id, authentication);
    }
}
