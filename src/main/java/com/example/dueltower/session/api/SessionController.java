package com.example.dueltower.session.api;

import com.example.dueltower.engine.command.*;
import com.example.dueltower.engine.core.EngineResult;
import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.Ids.CardInstId;
import com.example.dueltower.engine.model.Ids.PlayerId;
import com.example.dueltower.engine.model.TargetRef;
import com.example.dueltower.engine.model.TargetSelection;
import com.example.dueltower.session.service.SessionService;
import com.example.dueltower.session.dto.*;
import com.example.dueltower.session.runtime.SessionRuntime;
import com.example.dueltower.session.runtime.StateMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestController
@RequestMapping("/api/sessions")
@Slf4j
public class SessionController {

    private static final Set<String> PLAYER_AUTH_REQUIRED_TYPES = Set.of(
            "DRAW",
            "PLAY_CARD",
            "HAND_SWAP",
            "END_TURN",
            "USE_EX",
            "USE_SUMMON_ACTION",
            "DISCARD_TO_HAND_LIMIT",
            "RESOLVE_INITIATIVE_TIE"
    );

    private static final Set<String> GM_AUTH_REQUIRED_TYPES = Set.of(
            "ENEMY_PLAY_CARD",
            "ENEMY_USE_EX",
            "ENEMY_END_TURN"
    );

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
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

    @GetMapping("/{code}")
    public SessionStateDto state(@PathVariable String code) {
        return sessionService.withSessionLock(code, rt -> {
            log.debug("session state requested code={} version={}", code, rt.state().version());
            return StateMapper.toDto(rt.code(), rt.state());
        });
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
        sessionService.join(code, requestedPlayerId, requestedPassiveIds, req.presetDeckCardIds(), req.presetExCardId(), req.ownedCards());

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



    @PostMapping("/{code}/players/{playerId}/deck")
    public SessionStateDto updateDeck(@PathVariable String code,
                                      @PathVariable String playerId,
                                      @RequestHeader(value = "X-Player-Token", required = false) String playerTokenHeader,
                                      @RequestBody UpdateSessionDeckRequest req) {
        if (req == null || req.deckCardIds() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "deckCardIds is required");
        }

        String actorPlayerId = resolveActorPlayerId(code, playerTokenHeader);
        if (!playerId.equals(actorPlayerId)) {
            throw new ResponseStatusException(FORBIDDEN, "players may only edit their own deck");
        }
        sessionService.updateDeck(code, actorPlayerId, playerId, req.deckCardIds());
        return sessionService.withSessionLock(code, rt -> StateMapper.toDto(rt.code(), rt.state()));
    }
    @PostMapping("/{code}/command")
    public EngineResponseDto command(@PathVariable String code,
                                     @RequestHeader(value = "X-GM-Token", required = false) String gmTokenHeader,
                                     @RequestHeader(value = "X-Player-Token", required = false) String playerTokenHeader,
                                     @RequestBody CommandRequest req) {
        long startNs = System.nanoTime();

        if (req == null || req.type() == null || req.type().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "type is required");
        }
        if (req.expectedVersion() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "expectedVersion is required");
        }
        SessionRuntime rt = sessionService.get(code);

        String t = req.type().trim().toUpperCase(Locale.ROOT);
        if ("START_COMBAT".equals(t)) {
            requirePlayer(req.playerId());
            validateStartCombatAuthority(rt, gmTokenHeader);
        }

        if (PLAYER_AUTH_REQUIRED_TYPES.contains(t)) {
            requirePlayer(req.playerId());
            String actorPlayerId = resolveActorPlayerId(code, playerTokenHeader);
            if (!req.playerId().trim().equals(actorPlayerId)) {
                throw new ResponseStatusException(FORBIDDEN, "playerId mismatch");
            }
        }

        if (GM_AUTH_REQUIRED_TYPES.contains(t)) {
            validateStartCombatAuthority(rt, gmTokenHeader);
        }

        UUID commandId = parseOrNewUuid(req.commandId());

        log.debug("command received code={} type={} playerId={} expectedVersion={} commandId={} cardId={} summonId={} count={} discardIds={} targetPlayers={} targetEnemies={} targets={}",
                code,
                req.type(),
                (req.playerId() == null) ? null : req.playerId().trim(),
                req.expectedVersion(),
                commandId,
                (req.cardId() == null) ? null : req.cardId().trim(),
                (req.summonId() == null) ? null : req.summonId().trim(),
                req.count(),
                (req.discardIds() == null) ? 0 : req.discardIds().size(),
                (req.targetPlayerIds() == null) ? 0 : req.targetPlayerIds().size(),
                (req.targetEnemyIds() == null) ? 0 : req.targetEnemyIds().size(),
                (req.targets() == null) ? 0 : req.targets().size()
        );

        final EngineResult res = rt.withLock(() -> {
            GameCommand cmd = toCommand(req, commandId, req.expectedVersion());
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

    private String resolveActorPlayerId(String code, String playerTokenHeader) {
        String mappedPlayerId = sessionService.resolvePlayerIdByToken(code, playerTokenHeader);
        if (mappedPlayerId != null && !mappedPlayerId.isBlank()) {
            return mappedPlayerId;
        }

        throw new ResponseStatusException(UNAUTHORIZED, "player authorization required");
    }

    private static void validateStartCombatAuthority(SessionRuntime rt, String gmTokenHeader) {
        String token = (gmTokenHeader == null) ? "" : gmTokenHeader.trim();
        if (!token.isEmpty() && rt.gmToken().equals(token)) {
            return;
        }

        log.warn("START_COMBAT unauthorized: invalid GM token for code={}", rt.code());
        throw new ResponseStatusException(UNAUTHORIZED, "gm authorization required");
    }

    private static UUID parseOrNewUuid(String v) {
        if (v == null || v.isBlank()) return UUID.randomUUID();
        try {
            return UUID.fromString(v.trim());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(BAD_REQUEST, "invalid commandId uuid");
        }
    }

    private static PlayerId parsePlayerId(String playerId) {
        requirePlayer(playerId);
        return new PlayerId(playerId.trim());
    }

    private static Ids.EnemyId parseEnemyId(String enemyId) {
        if (enemyId == null || enemyId.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "enemyId is required");
        }
        return new Ids.EnemyId(enemyId.trim());
    }

    private static CardInstId parseCardInstId(String raw, String fieldName) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, fieldName + " is blank");
        }
        try {
            return new CardInstId(UUID.fromString(raw.trim()));
        } catch (Exception e) {
            throw new ResponseStatusException(BAD_REQUEST, "invalid " + fieldName + " uuid: " + raw);
        }
    }

    private static Ids.SummonInstId parseSummonInstId(String raw, String fieldName) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, fieldName + " is blank");
        }
        try {
            return new Ids.SummonInstId(UUID.fromString(raw.trim()));
        } catch (Exception e) {
            throw new ResponseStatusException(BAD_REQUEST, "invalid " + fieldName + " uuid: " + raw);
        }
    }

    private static CardInstId parseSingleCardInstId(List<String> raw, String fieldName) {
        List<String> list = (raw == null) ? List.of() : raw;
        if (list.size() != 1) {
            throw new ResponseStatusException(BAD_REQUEST, fieldName + " must have exactly 1 id");
        }
        return parseCardInstId(list.get(0), fieldName + "[0]");
    }

    private static List<CardInstId> parseCardInstIds(List<String> raw, String fieldName) {
        List<String> list = (raw == null) ? List.of() : raw;
        List<CardInstId> ids = new ArrayList<>(list.size());
        for (String s : list) {
            ids.add(parseCardInstId(s, fieldName));
        }
        return ids;
    }

    private static TargetSelection parseTargetSelection(CommandRequest req) {
        List<TargetRef> targets = new ArrayList<>();

        if (req.targets() != null) {
            for (TargetRefDto dto : req.targets()) {
                if (dto == null) continue;
                if (dto.playerId() != null && !dto.playerId().isBlank()) {
                    targets.add(TargetRef.ofPlayer(new PlayerId(dto.playerId().trim())));
                    continue;
                }
                if (dto.enemyId() != null && !dto.enemyId().isBlank()) {
                    targets.add(TargetRef.ofEnemy(new Ids.EnemyId(dto.enemyId().trim())));
                    continue;
                }
                if (dto.summonOwnerPlayerId() != null && !dto.summonOwnerPlayerId().isBlank()
                        && dto.summonInstanceId() != null && !dto.summonInstanceId().isBlank()) {
                    targets.add(TargetRef.ofSummon(
                            new PlayerId(dto.summonOwnerPlayerId().trim()),
                            parseSummonInstId(dto.summonInstanceId(), "targets.summonInstanceId")
                    ));
                }
            }
        }

        if (req.targetPlayerIds() != null) {
            for (String s : req.targetPlayerIds()) {
                if (s == null || s.isBlank()) continue;
                targets.add(TargetRef.ofPlayer(new PlayerId(s.trim())));
            }
        }
        if (req.targetEnemyIds() != null) {
            for (String s : req.targetEnemyIds()) {
                if (s == null || s.isBlank()) continue;
                targets.add(TargetRef.ofEnemy(new Ids.EnemyId(s.trim())));
            }
        }
        return targets.isEmpty() ? TargetSelection.empty() : new TargetSelection(List.copyOf(targets));
    }

    private static GameCommand toCommand(CommandRequest req, UUID commandId, long expectedVersion) {
        String type = req.type().trim().toUpperCase(Locale.ROOT);
        switch (type) {
            case "START_COMBAT" -> {
                PlayerId playerId = parsePlayerId(req.playerId());
                return new StartCombatCommand(commandId, expectedVersion, playerId);
            }
            case "DRAW" -> {
                // DRAW is a public product-rule command (validated by main-turn constraints in DrawCommand).
                PlayerId playerId = parsePlayerId(req.playerId());
                int count = (req.count() == null) ? 1 : req.count();
                return new DrawCommand(commandId, expectedVersion, playerId, count);
            }
            case "END_TURN" -> {
                PlayerId playerId = parsePlayerId(req.playerId());
                return new EndTurnCommand(commandId, expectedVersion, playerId);
            }
            case "HAND_SWAP" -> {
                PlayerId playerId = parsePlayerId(req.playerId());
                CardInstId id = parseSingleCardInstId(req.discardIds(), "discardIds");
                return new HandSwapCommand(commandId, expectedVersion, playerId, id);
            }
            case "PLAY_CARD" -> {
                PlayerId playerId = parsePlayerId(req.playerId());
                if (req.cardId() == null || req.cardId().isBlank()) {
                    throw new ResponseStatusException(BAD_REQUEST, "cardId is required");
                }

                CardInstId id = parseCardInstId(req.cardId(), "cardId");
                TargetSelection sel = parseTargetSelection(req);

                return new PlayCardCommand(commandId, expectedVersion, playerId, id, sel);
            }
            case "USE_EX" -> {
                PlayerId playerId = parsePlayerId(req.playerId());
                TargetSelection sel = parseTargetSelection(req);

                return new UseExCommand(commandId, expectedVersion, playerId, sel);
            }
            case "ENEMY_PLAY_CARD" -> {
                Ids.EnemyId enemyId = parseEnemyId(req.enemyId());
                if (req.cardId() == null || req.cardId().isBlank()) {
                    throw new ResponseStatusException(BAD_REQUEST, "cardId is required");
                }

                CardInstId id = parseCardInstId(req.cardId(), "cardId");
                TargetSelection sel = parseTargetSelection(req);

                return new EnemyPlayCardCommand(commandId, expectedVersion, enemyId, id, sel);
            }
            case "ENEMY_USE_EX" -> {
                Ids.EnemyId enemyId = parseEnemyId(req.enemyId());
                TargetSelection sel = parseTargetSelection(req);
                return new EnemyUseExCommand(commandId, expectedVersion, enemyId, sel);
            }
            case "ENEMY_END_TURN" -> {
                Ids.EnemyId enemyId = parseEnemyId(req.enemyId());
                return new EnemyEndTurnCommand(commandId, expectedVersion, enemyId);
            }
            case "USE_SUMMON_ACTION" -> {
                PlayerId playerId = parsePlayerId(req.playerId());
                if (req.summonId() == null || req.summonId().isBlank()) {
                    throw new ResponseStatusException(BAD_REQUEST, "summonId is required");
                }
                Ids.SummonInstId summonId = parseSummonInstId(req.summonId(), "summonId");
                TargetSelection sel = parseTargetSelection(req);
                return new UseSummonActionCommand(commandId, expectedVersion, playerId, summonId, sel);
            }
            case "DISCARD_TO_HAND_LIMIT" -> {
                PlayerId playerId = parsePlayerId(req.playerId());
                List<CardInstId> ids = parseCardInstIds(req.discardIds(), "discardIds");
                return new DiscardToHandLimitCommand(commandId, expectedVersion, playerId, ids);
            }
            case "RESOLVE_INITIATIVE_TIE" -> {
                PlayerId playerId = parsePlayerId(req.playerId());
                if (req.tieGroupIndex() == null) {
                    throw new ResponseStatusException(BAD_REQUEST, "tieGroupIndex is required");
                }
                if (req.orderedActorKeys() == null || req.orderedActorKeys().isEmpty()) {
                    throw new ResponseStatusException(BAD_REQUEST, "orderedActorKeys is required");
                }
                return new ResolveInitiativeTieCommand(
                        commandId,
                        expectedVersion,
                        playerId,
                        req.tieGroupIndex(),
                        req.orderedActorKeys()
                );
            }
            default -> throw new ResponseStatusException(BAD_REQUEST, "unknown command type: " + req.type());
        }
    }

    private static void requirePlayer(String playerId) {
        if (playerId == null || playerId.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "playerId is required");
        }
    }
}
