package com.example.dueltower.session.api;

import com.example.dueltower.common.api.ApiErrorResolver;
import com.example.dueltower.engine.command.*;
import com.example.dueltower.engine.core.EngineResult;
import com.example.dueltower.content.equip.service.EquipService;
import com.example.dueltower.content.item.service.ItemService;
import com.example.dueltower.session.service.SessionAccessDecision;
import com.example.dueltower.session.service.SessionAccessResolver;
import com.example.dueltower.session.service.SessionService;
import com.example.dueltower.session.dto.*;
import com.example.dueltower.session.runtime.SessionRuntime;
import com.example.dueltower.session.runtime.StateMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.HttpStatus.NO_CONTENT;

@RestController
@RequestMapping("/api/sessions")
@Slf4j
public class SessionController {

    private final SessionService sessionService;
    private final SessionAccessResolver sessionAccessResolver;
    private final SessionCommandAuthorization sessionCommandAuthorization;

    public SessionController(SessionService sessionService,
                             SessionAccessResolver sessionAccessResolver,
                             SessionCommandAuthorization sessionCommandAuthorization,
                             ItemService itemService,
                             EquipService equipService) {
        this.sessionService = sessionService;
        this.sessionAccessResolver = sessionAccessResolver;
        this.sessionCommandAuthorization = sessionCommandAuthorization;
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
        SessionRuntime rt = sessionService.createSession(gmId);

        log.info("session created code={} gmId={} sessionId={} seed={}",
                rt.code(),
                rt.gmId(),
                rt.state().sessionId().value(),
                rt.state().seed()
        );

        SessionStateDto state = rt.withLock(() -> StateMapper.toDto(rt.code(), rt.state()));
        return new CreateSessionResponse(rt.code(), rt.gmId(), rt.gmToken(), state);
    }

    @GetMapping({"/{code}", "/{code}/state"})
    public SessionStateDto state(@PathVariable String code) {
        // PUBLIC_SESSION_STATE: 공개 상태 조회는 익명 접근을 유지한다.
        return sessionService.withSessionLock(code, rt -> {
            log.debug("session state requested code={} version={}", code, rt.state().version());
            return StateMapper.toDto(rt.code(), rt.state());
        });
    }

    @GetMapping("/{code}/recent-results")
    public RecentResultsResponse recentResults(@PathVariable String code,
                                               @RequestHeader(value = "X-GM-Token", required = false) String gmTokenHeader,
                                               @RequestHeader(value = "X-Player-Token", required = false) String playerTokenHeader,
                                               Authentication authentication) {
        SessionRuntime rt = sessionService.get(code);
        requireSessionReadableAndLog(rt, gmTokenHeader, playerTokenHeader, authentication, "GET /api/sessions/{code}/recent-results");
        return rt.withLock(() -> new RecentResultsResponse(
                rt.state().version(),
                rt.state().runState().resultPending(),
                StateMapper.toCurrentNodeDto(rt.state().runState()),
                StateMapper.toRecentResultDtos(rt.state().runState())
        ));
    }

    @GetMapping("/{code}/run")
    public RunStateDto run(@PathVariable String code,
                           @RequestHeader(value = "X-GM-Token", required = false) String gmTokenHeader,
                           @RequestHeader(value = "X-Player-Token", required = false) String playerTokenHeader,
                           Authentication authentication) {
        SessionRuntime rt = sessionService.get(code);
        requireSessionReadableAndLog(rt, gmTokenHeader, playerTokenHeader, authentication, "GET /api/sessions/{code}/run");
        return rt.withLock(() -> StateMapper.toRunDto(rt.state().runState()));
    }

    @GetMapping("/{code}/inventory")
    public SessionRunInventoryResponse inventory(@PathVariable String code,
                                                 @RequestHeader(value = "X-GM-Token", required = false) String gmTokenHeader,
                                                 @RequestHeader(value = "X-Player-Token", required = false) String playerTokenHeader,
                                                 Authentication authentication) {
        SessionRuntime rt = sessionService.get(code);
        requireSessionReadableAndLog(rt, gmTokenHeader, playerTokenHeader, authentication, "GET /api/sessions/{code}/inventory");
        return rt.withLock(() -> new SessionRunInventoryResponse(
                rt.state().version(),
                StateMapper.toInventoryDto(rt.state().runState())
        ));
    }

    @GetMapping("/{code}/results")
    public RecentResultsResponse results(@PathVariable String code,
                                         @RequestHeader(value = "X-GM-Token", required = false) String gmTokenHeader,
                                         @RequestHeader(value = "X-Player-Token", required = false) String playerTokenHeader,
                                         Authentication authentication) {
        SessionRuntime rt = sessionService.get(code);
        requireSessionReadableAndLog(rt, gmTokenHeader, playerTokenHeader, authentication, "GET /api/sessions/{code}/results");
        return rt.withLock(() -> new RecentResultsResponse(
                rt.state().version(),
                rt.state().runState().resultPending(),
                StateMapper.toCurrentNodeDto(rt.state().runState()),
                StateMapper.toRecentResultDtos(rt.state().runState())
        ));
    }

    @GetMapping("/{code}/choices")
    public SessionRunChoicesResponse choices(@PathVariable String code,
                                             @RequestHeader(value = "X-GM-Token", required = false) String gmTokenHeader,
                                             @RequestHeader(value = "X-Player-Token", required = false) String playerTokenHeader,
                                             Authentication authentication) {
        SessionRuntime rt = sessionService.get(code);
        requireSessionReadableAndLog(rt, gmTokenHeader, playerTokenHeader, authentication, "GET /api/sessions/{code}/choices");
        return rt.withLock(() -> new SessionRunChoicesResponse(
                rt.state().version(),
                rt.state().runState().resultPending(),
                StateMapper.toCurrentNodeDto(rt.state().runState()),
                StateMapper.toNodeChoiceDtos(rt.state().runState())
        ));
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
        sessionService.join(code, requestedPlayerId, req.characterId(), requestedPassiveIds, req.requestedPresetDeckOwnedCardIds(), req.presetExCardId(), req.ownedCards());

        SessionStateDto state = sessionService.withSessionLock(code, rt -> {
            log.info("session join code={} playerId={} requestedPassiveIds={} playersNow={}",
                    code,
                    requestedPlayerId,
                    requestedPassiveIds,
                    rt.state().players().size()
            );
            return StateMapper.toDto(rt.code(), rt.state());
        });

        String playerToken = sessionService.issuePlayerToken(code, requestedPlayerId);
        return new JoinSessionResponse(state, playerToken);
    }




    @PostMapping("/{code}/players/{playerId}/forget")
    public SessionStateDto forgetOwnedCard(@PathVariable String code,
                                           @PathVariable String playerId,
                                           @RequestHeader(value = "X-Player-Token", required = false) String playerTokenHeader,
                                           @RequestBody ForgetOwnedCardRequest req) {
        if (req == null || req.ownedCardIndex() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "ownedCardIndex is required");
        }

        SessionRuntime runtime = sessionService.get(code);
        String actorPlayerId = sessionAccessResolver.requirePlayerSelf(
                runtime,
                playerTokenHeader,
                playerId,
                "players may only forget their own cards"
        );

        sessionService.forgetOwnedCard(code, actorPlayerId, playerId, req.ownedCardIndex());
        return sessionService.withSessionLock(code, rt -> StateMapper.toDto(rt.code(), rt.state()));
    }

    @PostMapping("/{code}/players/{playerId}/deck")
    public SessionStateDto updateDeck(@PathVariable String code,
                                      @PathVariable String playerId,
                                      @RequestHeader(value = "X-Player-Token", required = false) String playerTokenHeader,
                                      @RequestBody UpdateSessionDeckRequest req) {
        if (req == null || req.requestedDeckOwnedCardIds() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "deckOwnedCardIds is required");
        }

        SessionRuntime runtime = sessionService.get(code);
        String actorPlayerId = sessionAccessResolver.requirePlayerSelf(
                runtime,
                playerTokenHeader,
                playerId,
                "players may only edit their own deck"
        );
        sessionService.updateDeck(code, actorPlayerId, playerId, req.requestedDeckOwnedCardIds());
        return sessionService.withSessionLock(code, rt -> StateMapper.toDto(rt.code(), rt.state()));
    }

    @PostMapping("/{code}/players/{playerId}/loadout")
    public SessionStateDto updateLoadout(@PathVariable String code,
                                         @PathVariable String playerId,
                                         @RequestHeader(value = "X-Player-Token", required = false) String playerTokenHeader,
                                         @RequestBody(required = false) UpdateSessionLoadoutRequest req) {
        if (req == null) {
            throw new ResponseStatusException(BAD_REQUEST, "request body is required");
        }
        SessionRuntime runtime = sessionService.get(code);
        String actorPlayerId = sessionAccessResolver.requirePlayerSelf(
                runtime,
                playerTokenHeader,
                playerId,
                "players may only edit their own loadout"
        );
        sessionService.updateLoadout(
                code,
                actorPlayerId,
                playerId,
                req.characterId(),
                req.passiveIds(),
                req.deckOwnedCardIds(),
                req.exCardId()
        );
        return sessionService.withSessionLock(code, rt -> StateMapper.toDto(rt.code(), rt.state()));
    }

    @PostMapping("/{code}/players/{playerId}/loadout/from-preset")
    public SessionStateDto applyPresetToLoadout(@PathVariable String code,
                                                @PathVariable String playerId,
                                                @RequestHeader(value = "X-Player-Token", required = false) String playerTokenHeader,
                                                @RequestBody(required = false) ApplyPresetToSessionRequest req) {
        if (req == null || req.presetId() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "presetId is required");
        }
        SessionRuntime runtime = sessionService.get(code);
        String actorPlayerId = sessionAccessResolver.requirePlayerSelf(
                runtime,
                playerTokenHeader,
                playerId,
                "players may only edit their own loadout"
        );
        sessionService.applyPresetToLoadout(code, actorPlayerId, playerId, req.presetId());
        return sessionService.withSessionLock(code, rt -> StateMapper.toDto(rt.code(), rt.state()));
    }

    @PutMapping("/{code}/players/{playerId}/ready")
    public SessionStateDto updateReady(@PathVariable String code,
                                       @PathVariable String playerId,
                                       @RequestHeader(value = "X-Player-Token", required = false) String playerTokenHeader,
                                       @RequestBody(required = false) UpdatePlayerReadyRequest req) {
        if (req == null || req.ready() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "ready is required");
        }
        SessionRuntime runtime = sessionService.get(code);
        String actorPlayerId = sessionAccessResolver.requirePlayerSelf(
                runtime,
                playerTokenHeader,
                playerId,
                "players may only update their own ready state"
        );
        sessionService.setPlayerReady(code, actorPlayerId, playerId, req.ready());
        return sessionService.withSessionLock(code, rt -> StateMapper.toDto(rt.code(), rt.state()));
    }

    @PostMapping("/{code}/leave")
    public SessionStateDto leave(@PathVariable String code,
                                 @RequestHeader(value = "X-Player-Token", required = false) String playerTokenHeader) {
        SessionRuntime runtime = sessionService.get(code);
        String actorPlayerId = sessionAccessResolver.requirePlayerToken(runtime, playerTokenHeader);
        sessionService.leaveSession(code, actorPlayerId);
        return sessionService.withSessionLock(code, rt -> StateMapper.toDto(rt.code(), rt.state()));
    }

    @PostMapping("/{code}/players/{playerId}/kick")
    public SessionStateDto kick(@PathVariable String code,
                                @PathVariable String playerId,
                                @RequestHeader(value = "X-GM-Token", required = false) String gmTokenHeader,
                                @RequestBody(required = false) KickPlayerRequest req) {
        SessionRuntime rt = sessionService.get(code);
        sessionAccessResolver.requireGm(rt, gmTokenHeader);
        if (req != null && req.reason() != null && !req.reason().isBlank()) {
            log.info("session kick code={} playerId={} reason={}", code, playerId, req.reason().trim());
        }
        sessionService.kickPlayer(rt.code(), playerId);
        return sessionService.withSessionLock(code, lockedRt -> StateMapper.toDto(lockedRt.code(), lockedRt.state()));
    }

    @PostMapping("/{code}/reset")
    public SessionStateDto reset(@PathVariable String code,
                                 @RequestHeader(value = "X-GM-Token", required = false) String gmTokenHeader,
                                 @RequestBody(required = false) ResetSessionRequest req) {
        SessionRuntime rt = sessionService.get(code);
        sessionAccessResolver.requireGm(rt, gmTokenHeader);
        ResetSessionRequest resetReq = (req == null) ? new ResetSessionRequest(null, null, null) : req;
        sessionService.resetSession(rt.code(), resetReq.keepPlayersOrDefault(), resetReq.keepLoadoutsOrDefault(), resetReq.newSeed());
        return sessionService.withSessionLock(code, lockedRt -> StateMapper.toDto(lockedRt.code(), lockedRt.state()));
    }

    @DeleteMapping("/{code}")
    @ResponseStatus(NO_CONTENT)
    public void delete(@PathVariable String code,
                       @RequestHeader(value = "X-GM-Token", required = false) String gmTokenHeader) {
        SessionRuntime rt = sessionService.get(code);
        sessionAccessResolver.requireGm(rt, gmTokenHeader);
        sessionService.deleteSession(code);
    }

    @PostMapping("/{code}/command")
    public EngineResponseDto command(@PathVariable String code,
                                     @RequestHeader(value = "X-GM-Token", required = false) String gmTokenHeader,
                                     @RequestHeader(value = "X-Player-Token", required = false) String playerTokenHeader,
                                     @RequestBody CommandRequest req) {
        long startNs = System.nanoTime();

        if (req == null || req.normalizedType().isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "type is required");
        }
        if (req.expectedVersion() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "expectedVersion is required");
        }
        SessionRuntime rt = sessionService.get(code);
        UUID commandId = parseOrNewUuid(req.commandId());
        SessionCommandType commandType = SessionCommandType.from(req.type());

        sessionCommandAuthorization.authorize(rt, commandType, req, gmTokenHeader, playerTokenHeader);

        log.debug("command received code={} type={} playerId={} expectedVersion={} commandId={} cardId={} summonId={} itemId={} count={} reason={} discardIds={} targetPlayers={} targetEnemies={} targets={}",
                code,
                commandType.requestType(),
                req.trimmedPlayerId(),
                req.expectedVersion(),
                commandId,
                req.trimmedCardId(),
                req.trimmedSummonId(),
                req.trimmedItemId(),
                req.count(),
                req.trimmedReason(),
                (req.discardIds() == null) ? 0 : req.discardIds().size(),
                (req.targetPlayerIds() == null) ? 0 : req.targetPlayerIds().size(),
                (req.targetEnemyIds() == null) ? 0 : req.targetEnemyIds().size(),
                (req.targets() == null) ? 0 : req.targets().size()
        );

        final EngineResult res = rt.withLock(() -> {
            GameCommand cmd = commandType.toCommand(req, commandId, req.expectedVersion());
            return rt.apply(cmd);
        });

        long tookMs = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
        if (res.accepted()) {
            log.debug("command accepted code={} type={} commandId={} events={} newVersion={} ({}ms)",
                    code, req.type(), commandId, res.events().size(), res.state().version(), tookMs);
        } else {
            log.warn("command rejected code={} type={} commandId={} errors={} version={} ({}ms)",
                    code, req.type(), commandId, res.errors(), res.state().version(), tookMs);
        }

        SessionStateDto state = rt.withLock(() -> StateMapper.toDto(rt.code(), res.state()));

        return new EngineResponseDto(
                res.accepted(),
                res.errors(),
                res.accepted() ? List.of() : List.of(ApiErrorResolver.commandRejection(res.errors())),
                StateMapper.toEventDtos(res.events()),
                state
        );
    }



    private static String requireAuthenticatedUsername(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new ResponseStatusException(UNAUTHORIZED, "authentication required");
        }
        return authentication.getName();
    }

    // SESSION_READABLE: read 허용 판단과 접근 로그를 같은 진입점에서 처리한다.
    private void requireSessionReadableAndLog(SessionRuntime rt,
                                              String gmTokenHeader,
                                              String playerTokenHeader,
                                              Authentication authentication,
                                              String endpoint) {
        SessionAccessDecision decision = sessionAccessResolver.requireSessionReadable(rt, gmTokenHeader, playerTokenHeader, authentication);
        log.info("session read granted code={} endpoint={} source={} tokenBased={} loginBased={} username={} playerId={}",
                decision.sessionCode(),
                endpoint,
                decision.source(),
                decision.tokenBased(),
                decision.loginBased(),
                decision.username(),
                decision.playerId()
        );
    }

    private static UUID parseOrNewUuid(String v) {
        if (v == null || v.isBlank()) return UUID.randomUUID();
        try {
            return UUID.fromString(v.trim());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(BAD_REQUEST, "invalid commandId uuid");
        }
    }
}
