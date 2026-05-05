package com.example.dueltower.screen.api;

import com.example.dueltower.screen.dto.CombatScreenResponse;
import com.example.dueltower.screen.dto.CombatScreenActionResponse;
import com.example.dueltower.screen.dto.DeckEditorScreenResponse;
import com.example.dueltower.screen.dto.GmLobbyScreenResponse;
import com.example.dueltower.screen.dto.GmLobbyStartCombatActionRequest;
import com.example.dueltower.screen.dto.GmLobbyStartCombatActionResponse;
import com.example.dueltower.screen.dto.PlayerLobbyScreenResponse;
import com.example.dueltower.session.dto.CommandRequest;
import com.example.dueltower.screen.service.ScreenQueryService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/screens")
public class ScreenController {

    private final ScreenQueryService screenQueryService;

    public ScreenController(ScreenQueryService screenQueryService) {
        this.screenQueryService = screenQueryService;
    }

    @GetMapping("/sessions/{code}/player-lobby")
    public PlayerLobbyScreenResponse playerLobby(@PathVariable String code,
                                                 @RequestHeader(value = "X-GM-Token", required = false) String gmTokenHeader,
                                                 @RequestHeader(value = "X-Player-Token", required = false) String playerTokenHeader,
                                                 Authentication authentication) {
        return screenQueryService.getPlayerLobby(code, gmTokenHeader, playerTokenHeader, authentication);
    }

    @GetMapping("/sessions/{code}/gm-lobby")
    public GmLobbyScreenResponse gmLobby(@PathVariable String code,
                                         @RequestHeader(value = "X-GM-Token", required = false) String gmTokenHeader,
                                         @RequestHeader(value = "X-Player-Token", required = false) String playerTokenHeader,
                                         Authentication authentication) {
        return screenQueryService.getGmLobby(code, gmTokenHeader, playerTokenHeader, authentication);
    }

    @PostMapping("/sessions/{code}/gm-lobby/start-combat")
    public GmLobbyStartCombatActionResponse startGmLobbyCombat(@PathVariable String code,
                                                               @RequestHeader(value = "X-GM-Token", required = false) String gmTokenHeader,
                                                               @RequestHeader(value = "X-Player-Token", required = false) String playerTokenHeader,
                                                               @RequestBody(required = false) GmLobbyStartCombatActionRequest request,
                                                               Authentication authentication) {
        return screenQueryService.startGmLobbyCombat(code, gmTokenHeader, playerTokenHeader, authentication, request);
    }

    @GetMapping("/sessions/{code}/combat")
    public CombatScreenResponse combat(@PathVariable String code,
                                       @RequestParam(required = false) Long afterVersion,
                                       @RequestParam(required = false) Integer eventLimit,
                                       @RequestHeader(value = "X-GM-Token", required = false) String gmTokenHeader,
                                       @RequestHeader(value = "X-Player-Token", required = false) String playerTokenHeader,
                                       Authentication authentication) {
        return screenQueryService.getCombat(code, afterVersion, eventLimit, gmTokenHeader, playerTokenHeader, authentication);
    }

    @PostMapping("/sessions/{code}/combat/actions/{actionId:.+}")
    public CombatScreenActionResponse executeCombatAction(@PathVariable String code,
                                                          @PathVariable String actionId,
                                                          @RequestHeader(value = "X-GM-Token", required = false) String gmTokenHeader,
                                                          @RequestHeader(value = "X-Player-Token", required = false) String playerTokenHeader,
                                                          @RequestBody(required = false) CommandRequest request,
                                                          Authentication authentication) {
        return screenQueryService.executeCombatAction(code, actionId, gmTokenHeader, playerTokenHeader, authentication, request);
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

}
