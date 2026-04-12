package com.example.dueltower.session.api;

import com.example.dueltower.content.equip.service.EquipService;
import com.example.dueltower.content.item.service.ItemService;
import com.example.dueltower.session.dto.ApplyPresetToSessionRequest;
import com.example.dueltower.session.dto.CommandRequest;
import com.example.dueltower.session.dto.CreateSessionRequest;
import com.example.dueltower.session.dto.CreateSessionResponse;
import com.example.dueltower.session.dto.EngineResponseDto;
import com.example.dueltower.session.dto.ForgetOwnedCardRequest;
import com.example.dueltower.session.dto.JoinSessionRequest;
import com.example.dueltower.session.dto.JoinSessionResponse;
import com.example.dueltower.session.dto.KickPlayerRequest;
import com.example.dueltower.session.dto.RecentResultsResponse;
import com.example.dueltower.session.dto.ResetSessionRequest;
import com.example.dueltower.session.dto.RestoreGmAccessResponse;
import com.example.dueltower.session.dto.RunStateDto;
import com.example.dueltower.session.dto.SessionRunChoicesResponse;
import com.example.dueltower.session.dto.SessionRunInventoryResponse;
import com.example.dueltower.session.dto.SessionStateDto;
import com.example.dueltower.session.dto.UpdatePlayerReadyRequest;
import com.example.dueltower.session.dto.UpdateSessionDeckRequest;
import com.example.dueltower.session.dto.UpdateSessionLoadoutRequest;
import com.example.dueltower.session.runtime.SessionRuntime;
import com.example.dueltower.session.runtime.StateMapper;
import com.example.dueltower.session.service.SessionAccessResolver;
import com.example.dueltower.session.service.SessionCommandService;
import com.example.dueltower.session.service.SessionLifecycleService;
import com.example.dueltower.session.service.SessionLoadoutService;
import com.example.dueltower.session.service.SessionLobbyService;
import com.example.dueltower.session.service.SessionQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestController
@RequestMapping("/api/sessions")
@Slf4j
public class SessionController {

    private final SessionLifecycleService sessionLifecycleService;
    private final SessionLoadoutService sessionLoadoutService;
    private final SessionLobbyService sessionLobbyService;
    private final SessionQueryService sessionQueryService;
    private final SessionCommandService sessionCommandService;
    private final SessionAccessResolver sessionAccessResolver;

    public SessionController(SessionLifecycleService sessionLifecycleService,
                             SessionLoadoutService sessionLoadoutService,
                             SessionLobbyService sessionLobbyService,
                             SessionQueryService sessionQueryService,
                             SessionCommandService sessionCommandService,
                             SessionAccessResolver sessionAccessResolver,
                             ItemService itemService,
                             EquipService equipService) {
        this.sessionLifecycleService = sessionLifecycleService;
        this.sessionLoadoutService = sessionLoadoutService;
        this.sessionLobbyService = sessionLobbyService;
        this.sessionQueryService = sessionQueryService;
        this.sessionCommandService = sessionCommandService;
        this.sessionAccessResolver = sessionAccessResolver;
        StateMapper.configureContentServices(itemService, equipService);
    }

    @PostMapping
    public CreateSessionResponse create(@RequestBody(required = false) CreateSessionRequest req,
                                        Authentication authentication) {
        String loginUsername = requireAuthenticatedUsername(authentication);
        String gmId = (req == null || req.gmId() == null || req.gmId().isBlank()) ? loginUsername : req.gmId().trim();
        if (!gmId.equals(loginUsername)) {
            throw new ResponseStatusException(FORBIDDEN, "gmId must match the authenticated user");
        }
        SessionRuntime rt = sessionLifecycleService.createSession(gmId);

        log.info("session created code={} gmId={} sessionId={} seed={}",
                rt.code(),
                rt.gmId(),
                rt.state().sessionId().value(),
                rt.state().seed()
        );

        SessionStateDto state = sessionLifecycleService.withLockedSession(rt.code(), lockedRt -> StateMapper.toDto(lockedRt.code(), lockedRt.state()));
        return new CreateSessionResponse(rt.code(), rt.gmId(), rt.gmToken(), state);
    }

    @GetMapping({"/{code}", "/{code}/state"})
    public SessionStateDto state(@PathVariable String code) {
        return sessionQueryService.getPublicState(code);
    }

    @GetMapping("/{code}/recent-results")
    public RecentResultsResponse recentResults(@PathVariable String code,
                                               @RequestHeader(value = "X-GM-Token", required = false) String gmTokenHeader,
                                               @RequestHeader(value = "X-Player-Token", required = false) String playerTokenHeader,
                                               Authentication authentication) {
        return sessionQueryService.getRecentResults(code, gmTokenHeader, playerTokenHeader, authentication);
    }

    @GetMapping("/{code}/run")
    public RunStateDto run(@PathVariable String code,
                           @RequestHeader(value = "X-GM-Token", required = false) String gmTokenHeader,
                           @RequestHeader(value = "X-Player-Token", required = false) String playerTokenHeader,
                           Authentication authentication) {
        return sessionQueryService.getRun(code, gmTokenHeader, playerTokenHeader, authentication);
    }

    @GetMapping("/{code}/inventory")
    public SessionRunInventoryResponse inventory(@PathVariable String code,
                                                 @RequestHeader(value = "X-GM-Token", required = false) String gmTokenHeader,
                                                 @RequestHeader(value = "X-Player-Token", required = false) String playerTokenHeader,
                                                 Authentication authentication) {
        return sessionQueryService.getInventory(code, gmTokenHeader, playerTokenHeader, authentication);
    }

    @GetMapping("/{code}/results")
    public RecentResultsResponse results(@PathVariable String code,
                                         @RequestHeader(value = "X-GM-Token", required = false) String gmTokenHeader,
                                         @RequestHeader(value = "X-Player-Token", required = false) String playerTokenHeader,
                                         Authentication authentication) {
        return sessionQueryService.getResults(code, gmTokenHeader, playerTokenHeader, authentication);
    }

    @GetMapping("/{code}/choices")
    public SessionRunChoicesResponse choices(@PathVariable String code,
                                             @RequestHeader(value = "X-GM-Token", required = false) String gmTokenHeader,
                                             @RequestHeader(value = "X-Player-Token", required = false) String playerTokenHeader,
                                             Authentication authentication) {
        return sessionQueryService.getChoices(code, gmTokenHeader, playerTokenHeader, authentication);
    }

    @PostMapping("/{code}/join")
    public JoinSessionResponse join(@PathVariable String code,
                                    @RequestBody(required = false) JoinSessionRequest req,
                                    Authentication authentication) {
        String loginUsername = requireAuthenticatedUsername(authentication);
        if (req == null) {
            throw new ResponseStatusException(BAD_REQUEST, "request body is required");
        }
        String requestedPlayerId = (req.playerId() == null || req.playerId().isBlank()) ? loginUsername : req.playerId().trim();
        if (!requestedPlayerId.equals(loginUsername)) {
            throw new ResponseStatusException(FORBIDDEN, "playerId must match the authenticated user");
        }
        List<String> requestedPassiveIds = (req.passiveIds() == null) ? List.of() : req.passiveIds();
        sessionLobbyService.join(code, requestedPlayerId, req.characterId(), requestedPassiveIds, req.requestedPresetDeckOwnedCardIds(), req.presetExCardId(), req.ownedCards());

        SessionStateDto state = sessionLifecycleService.withLockedSession(code, rt -> {
            log.info("session join code={} playerId={} requestedPassiveIds={} playersNow={}",
                    code,
                    requestedPlayerId,
                    requestedPassiveIds,
                    rt.state().players().size()
            );
            return StateMapper.toDto(rt.code(), rt.state());
        });

        String playerToken = sessionLobbyService.issuePlayerToken(code, requestedPlayerId);
        return new JoinSessionResponse(state, playerToken);
    }

    @PostMapping("/{code}/gm-access/restore")
    public RestoreGmAccessResponse restoreGmAccess(@PathVariable String code,
                                                   Authentication authentication) {
        String username = requireAuthenticatedUsername(authentication);
        SessionRuntime rt = sessionLifecycleService.get(code);
        if (!rt.gmId().equals(username)) {
            throw new ResponseStatusException(FORBIDDEN, "gm access restore forbidden");
        }

        SessionStateDto state = sessionLifecycleService.withLockedSession(
                code,
                lockedRt -> StateMapper.toDto(lockedRt.code(), lockedRt.state())
        );
        return new RestoreGmAccessResponse(rt.code(), rt.gmToken(), state);
    }

    @PostMapping("/{code}/players/{playerId}/forget")
    public SessionStateDto forgetOwnedCard(@PathVariable String code,
                                           @PathVariable String playerId,
                                           @RequestHeader(value = "X-Player-Token", required = false) String playerTokenHeader,
                                           @RequestBody ForgetOwnedCardRequest req) {
        if (req == null || req.ownedCardIndex() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "ownedCardIndex is required");
        }

        SessionRuntime runtime = sessionLifecycleService.get(code);
        String actorPlayerId = sessionAccessResolver.requirePlayerSelf(
                runtime,
                playerTokenHeader,
                playerId,
                "players may only forget their own cards"
        );

        sessionLoadoutService.forgetOwnedCard(code, actorPlayerId, playerId, req.ownedCardIndex());
        return sessionLifecycleService.withLockedSession(code, rt -> StateMapper.toDto(rt.code(), rt.state()));
    }

    @PostMapping("/{code}/players/{playerId}/deck")
    public SessionStateDto updateDeck(@PathVariable String code,
                                      @PathVariable String playerId,
                                      @RequestHeader(value = "X-Player-Token", required = false) String playerTokenHeader,
                                      @RequestBody UpdateSessionDeckRequest req) {
        if (req == null || req.requestedDeckOwnedCardIds() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "deckOwnedCardIds is required");
        }

        SessionRuntime runtime = sessionLifecycleService.get(code);
        String actorPlayerId = sessionAccessResolver.requirePlayerSelf(
                runtime,
                playerTokenHeader,
                playerId,
                "players may only edit their own deck"
        );
        sessionLoadoutService.updateDeck(code, actorPlayerId, playerId, req.requestedDeckOwnedCardIds());
        return sessionLifecycleService.withLockedSession(code, rt -> StateMapper.toDto(rt.code(), rt.state()));
    }

    @PostMapping("/{code}/players/{playerId}/loadout")
    public SessionStateDto updateLoadout(@PathVariable String code,
                                         @PathVariable String playerId,
                                         @RequestHeader(value = "X-Player-Token", required = false) String playerTokenHeader,
                                         @RequestBody(required = false) UpdateSessionLoadoutRequest req) {
        if (req == null) {
            throw new ResponseStatusException(BAD_REQUEST, "request body is required");
        }
        SessionRuntime runtime = sessionLifecycleService.get(code);
        String actorPlayerId = sessionAccessResolver.requirePlayerSelf(
                runtime,
                playerTokenHeader,
                playerId,
                "players may only edit their own loadout"
        );
        sessionLoadoutService.updateLoadout(
                code,
                actorPlayerId,
                playerId,
                req.characterId(),
                req.passiveIds(),
                req.deckOwnedCardIds(),
                req.exCardId()
        );
        return sessionLifecycleService.withLockedSession(code, rt -> StateMapper.toDto(rt.code(), rt.state()));
    }

    @PostMapping("/{code}/players/{playerId}/loadout/from-preset")
    public SessionStateDto applyPresetToLoadout(@PathVariable String code,
                                                @PathVariable String playerId,
                                                @RequestHeader(value = "X-Player-Token", required = false) String playerTokenHeader,
                                                @RequestBody(required = false) ApplyPresetToSessionRequest req) {
        if (req == null || req.presetId() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "presetId is required");
        }
        SessionRuntime runtime = sessionLifecycleService.get(code);
        String actorPlayerId = sessionAccessResolver.requirePlayerSelf(
                runtime,
                playerTokenHeader,
                playerId,
                "players may only edit their own loadout"
        );
        sessionLoadoutService.applyPresetToLoadout(code, actorPlayerId, playerId, req.presetId());
        return sessionLifecycleService.withLockedSession(code, rt -> StateMapper.toDto(rt.code(), rt.state()));
    }

    @PutMapping("/{code}/players/{playerId}/ready")
    public SessionStateDto updateReady(@PathVariable String code,
                                       @PathVariable String playerId,
                                       @RequestHeader(value = "X-Player-Token", required = false) String playerTokenHeader,
                                       @RequestBody(required = false) UpdatePlayerReadyRequest req) {
        if (req == null || req.ready() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "ready is required");
        }
        SessionRuntime runtime = sessionLifecycleService.get(code);
        String actorPlayerId = sessionAccessResolver.requirePlayerSelf(
                runtime,
                playerTokenHeader,
                playerId,
                "players may only update their own ready state"
        );
        sessionLobbyService.setPlayerReady(code, actorPlayerId, playerId, req.ready());
        return sessionLifecycleService.withLockedSession(code, rt -> StateMapper.toDto(rt.code(), rt.state()));
    }

    @PostMapping("/{code}/leave")
    public SessionStateDto leave(@PathVariable String code,
                                 @RequestHeader(value = "X-Player-Token", required = false) String playerTokenHeader) {
        SessionRuntime runtime = sessionLifecycleService.get(code);
        String actorPlayerId = sessionAccessResolver.requirePlayerToken(runtime, playerTokenHeader);
        sessionLobbyService.leaveSession(code, actorPlayerId);
        return sessionLifecycleService.withLockedSession(code, rt -> StateMapper.toDto(rt.code(), rt.state()));
    }

    @PostMapping("/{code}/players/{playerId}/kick")
    public SessionStateDto kick(@PathVariable String code,
                                @PathVariable String playerId,
                                @RequestHeader(value = "X-GM-Token", required = false) String gmTokenHeader,
                                @RequestBody(required = false) KickPlayerRequest req) {
        SessionRuntime rt = sessionLifecycleService.get(code);
        sessionAccessResolver.requireGm(rt, gmTokenHeader);
        if (req != null && req.reason() != null && !req.reason().isBlank()) {
            log.info("session kick code={} playerId={} reason={}", code, playerId, req.reason().trim());
        }
        sessionLobbyService.kickPlayer(rt.code(), playerId);
        return sessionLifecycleService.withLockedSession(code, lockedRt -> StateMapper.toDto(lockedRt.code(), lockedRt.state()));
    }

    @PostMapping("/{code}/reset")
    public SessionStateDto reset(@PathVariable String code,
                                 @RequestHeader(value = "X-GM-Token", required = false) String gmTokenHeader,
                                 @RequestBody(required = false) ResetSessionRequest req) {
        SessionRuntime rt = sessionLifecycleService.get(code);
        sessionAccessResolver.requireGm(rt, gmTokenHeader);
        ResetSessionRequest resetReq = (req == null) ? new ResetSessionRequest(null, null, null) : req;
        sessionLobbyService.resetSession(rt.code(), resetReq.keepPlayersOrDefault(), resetReq.keepLoadoutsOrDefault(), resetReq.newSeed());
        return sessionLifecycleService.withLockedSession(code, lockedRt -> StateMapper.toDto(lockedRt.code(), lockedRt.state()));
    }

    @DeleteMapping("/{code}")
    @ResponseStatus(NO_CONTENT)
    public void delete(@PathVariable String code,
                       @RequestHeader(value = "X-GM-Token", required = false) String gmTokenHeader) {
        SessionRuntime rt = sessionLifecycleService.get(code);
        sessionAccessResolver.requireGm(rt, gmTokenHeader);
        sessionLifecycleService.deleteSession(code);
    }

    // COMMAND WRITE FLOW:
    // controller는 HTTP 경계만 담당하고, 실제 command 실행은 SessionCommandService가 맡는다.
    @PostMapping("/{code}/command")
    public EngineResponseDto command(@PathVariable String code,
                                     @RequestHeader(value = "X-GM-Token", required = false) String gmTokenHeader,
                                     @RequestHeader(value = "X-Player-Token", required = false) String playerTokenHeader,
                                     @RequestBody CommandRequest req) {
        return sessionCommandService.handleCommand(code, gmTokenHeader, playerTokenHeader, req);
    }

    private static String requireAuthenticatedUsername(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new ResponseStatusException(UNAUTHORIZED, "authentication required");
        }
        return authentication.getName();
    }
}
