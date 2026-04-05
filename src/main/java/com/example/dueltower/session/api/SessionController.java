package com.example.dueltower.session.api;

import com.example.dueltower.common.api.ApiErrorResolver;
import com.example.dueltower.engine.command.*;
import com.example.dueltower.engine.core.EngineResult;
import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.Ids.CardInstId;
import com.example.dueltower.engine.model.Ids.PlayerId;
import com.example.dueltower.engine.model.TargetRef;
import com.example.dueltower.engine.model.TargetSelection;
import com.example.dueltower.content.equip.service.EquipService;
import com.example.dueltower.content.item.service.ItemService;
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
import java.util.Set;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.HttpStatus.NO_CONTENT;

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
            "USE_EQUIP_ACTION",
            "RELOAD_EQUIPMENT",
            "USE_ITEM",
            "BUY_SHOP_ITEM",
            "EQUIP_EQUIPMENT",
            "UNEQUIP_EQUIPMENT",
            "OPEN_CHEST",
            "RESOLVE_JUDGEMENT",
            "SURRENDER_COMBAT",
            "SELL_INVENTORY_ITEM",
            "RETREAT_COMBAT",
            "DISCARD_TO_HAND_LIMIT",
            "RESOLVE_INITIATIVE_TIE",
            "SEARCH_PICK",
            "RESOLVE_SEARCH_PICK",
            "SELECT_NODE_CHOICE",
            "CLEAR_RECENT_RESULTS"
    );

    private static final Set<String> GM_AUTH_REQUIRED_TYPES = Set.of(
            "ENEMY_PLAY_CARD",
            "ENEMY_USE_EX",
            "ENEMY_END_TURN"
    );

    private final SessionService sessionService;

    public SessionController(SessionService sessionService, ItemService itemService, EquipService equipService) {
        this.sessionService = sessionService;
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
        validateSessionReadAccess(rt, gmTokenHeader, playerTokenHeader, authentication);
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
        validateSessionReadAccess(rt, gmTokenHeader, playerTokenHeader, authentication);
        return rt.withLock(() -> StateMapper.toRunDto(rt.state().runState()));
    }

    @GetMapping("/{code}/inventory")
    public SessionRunInventoryResponse inventory(@PathVariable String code,
                                                 @RequestHeader(value = "X-GM-Token", required = false) String gmTokenHeader,
                                                 @RequestHeader(value = "X-Player-Token", required = false) String playerTokenHeader,
                                                 Authentication authentication) {
        SessionRuntime rt = sessionService.get(code);
        validateSessionReadAccess(rt, gmTokenHeader, playerTokenHeader, authentication);
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
        validateSessionReadAccess(rt, gmTokenHeader, playerTokenHeader, authentication);
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
        validateSessionReadAccess(rt, gmTokenHeader, playerTokenHeader, authentication);
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

        String actorPlayerId = resolveActorPlayerId(code, playerTokenHeader);
        if (!playerId.equals(actorPlayerId)) {
            throw new ResponseStatusException(FORBIDDEN, "players may only forget their own cards");
        }

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

        String actorPlayerId = resolveActorPlayerId(code, playerTokenHeader);
        if (!playerId.equals(actorPlayerId)) {
            throw new ResponseStatusException(FORBIDDEN, "players may only edit their own deck");
        }
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
        String actorPlayerId = resolveActorPlayerId(code, playerTokenHeader);
        if (!playerId.equals(actorPlayerId)) {
            throw new ResponseStatusException(FORBIDDEN, "players may only edit their own loadout");
        }
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
        String actorPlayerId = resolveActorPlayerId(code, playerTokenHeader);
        if (!playerId.equals(actorPlayerId)) {
            throw new ResponseStatusException(FORBIDDEN, "players may only edit their own loadout");
        }
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
        String actorPlayerId = resolveActorPlayerId(code, playerTokenHeader);
        if (!playerId.equals(actorPlayerId)) {
            throw new ResponseStatusException(FORBIDDEN, "players may only update their own ready state");
        }
        sessionService.setPlayerReady(code, actorPlayerId, playerId, req.ready());
        return sessionService.withSessionLock(code, rt -> StateMapper.toDto(rt.code(), rt.state()));
    }

    @PostMapping("/{code}/leave")
    public SessionStateDto leave(@PathVariable String code,
                                 @RequestHeader(value = "X-Player-Token", required = false) String playerTokenHeader) {
        String actorPlayerId = resolveActorPlayerId(code, playerTokenHeader);
        sessionService.leaveSession(code, actorPlayerId);
        return sessionService.withSessionLock(code, rt -> StateMapper.toDto(rt.code(), rt.state()));
    }

    @PostMapping("/{code}/players/{playerId}/kick")
    public SessionStateDto kick(@PathVariable String code,
                                @PathVariable String playerId,
                                @RequestHeader(value = "X-GM-Token", required = false) String gmTokenHeader,
                                @RequestBody(required = false) KickPlayerRequest req) {
        SessionRuntime rt = requireGmSession(code, gmTokenHeader);
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
        SessionRuntime rt = requireGmSession(code, gmTokenHeader);
        ResetSessionRequest resetReq = (req == null) ? new ResetSessionRequest(null, null, null) : req;
        sessionService.resetSession(rt.code(), resetReq.keepPlayersOrDefault(), resetReq.keepLoadoutsOrDefault(), resetReq.newSeed());
        return sessionService.withSessionLock(code, lockedRt -> StateMapper.toDto(lockedRt.code(), lockedRt.state()));
    }

    @DeleteMapping("/{code}")
    @ResponseStatus(NO_CONTENT)
    public void delete(@PathVariable String code,
                       @RequestHeader(value = "X-GM-Token", required = false) String gmTokenHeader) {
        requireGmSession(code, gmTokenHeader);
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

        String t = req.normalizedType();
        if ("START_COMBAT".equals(t)) {
            requirePlayer(req.trimmedPlayerId());
            validateStartCombatAuthority(rt, gmTokenHeader);
        }

        validatePlayerAuthorityIfRequired(code, playerTokenHeader, req, t);

        if (GM_AUTH_REQUIRED_TYPES.contains(t)) {
            validateStartCombatAuthority(rt, gmTokenHeader);
        }

        UUID commandId = parseOrNewUuid(req.commandId());

        log.debug("command received code={} type={} playerId={} expectedVersion={} commandId={} cardId={} summonId={} itemId={} count={} reason={} discardIds={} targetPlayers={} targetEnemies={} targets={}",
                code,
                t,
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

    private void validateSessionReadAccess(SessionRuntime rt,
                                           String gmTokenHeader,
                                           String playerTokenHeader,
                                           Authentication authentication) {
        String gmToken = (gmTokenHeader == null) ? "" : gmTokenHeader.trim();
        if (!gmToken.isEmpty() && rt.gmToken().equals(gmToken)) {
            return;
        }

        String actorPlayerId = rt.withLock(() -> {
            String token = (playerTokenHeader == null) ? "" : playerTokenHeader.trim();
            if (token.isEmpty()) {
                return null;
            }
            return rt.findPlayerIdByToken(token);
        });
        if (actorPlayerId != null) {
            return;
        }

        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getPrincipal())) {
            String username = authentication.getName();
            if (rt.gmId().equals(username)) {
                return;
            }
            if (rt.state().players().containsKey(new PlayerId(username))) {
                return;
            }
        }

        throw new ResponseStatusException(UNAUTHORIZED, "participant or gm authorization required");
    }

    private SessionRuntime requireGmSession(String code, String gmTokenHeader) {
        SessionRuntime rt = sessionService.get(code);
        validateStartCombatAuthority(rt, gmTokenHeader);
        return rt;
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
        String type = req.normalizedType();
        switch (type) {
            case "START_COMBAT" -> {
                PlayerId playerId = commandPlayerId(req);
                return new StartCombatCommand(commandId, expectedVersion, playerId);
            }
            case "DRAW" -> {
                // DRAW is a public product-rule command (validated by main-turn constraints in DrawCommand).
                PlayerId playerId = commandPlayerId(req);
                int count = req.countOrDefault(1);
                return new DrawCommand(commandId, expectedVersion, playerId, count);
            }
            case "END_TURN" -> {
                PlayerId playerId = commandPlayerId(req);
                return new EndTurnCommand(commandId, expectedVersion, playerId);
            }
            case "HAND_SWAP" -> {
                PlayerId playerId = commandPlayerId(req);
                CardInstId id = parseSingleCardInstId(req.discardIds(), "discardIds");
                return new HandSwapCommand(commandId, expectedVersion, playerId, id);
            }
            case "PLAY_CARD" -> {
                PlayerId playerId = commandPlayerId(req);
                CardInstId id = parseCardInstId(requireText(req.trimmedCardId(), "cardId"), "cardId");
                TargetSelection sel = parseTargetSelection(req);

                return new PlayCardCommand(commandId, expectedVersion, playerId, id, sel);
            }
            case "USE_EX" -> {
                PlayerId playerId = commandPlayerId(req);
                TargetSelection sel = parseTargetSelection(req);

                return new UseExCommand(commandId, expectedVersion, playerId, sel);
            }
            case "ENEMY_PLAY_CARD" -> {
                Ids.EnemyId enemyId = parseEnemyId(req.enemyId());
                CardInstId id = parseCardInstId(requireText(req.trimmedCardId(), "cardId"), "cardId");
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
                PlayerId playerId = commandPlayerId(req);
                Ids.SummonInstId summonId = parseSummonInstId(requireText(req.trimmedSummonId(), "summonId"), "summonId");
                TargetSelection sel = parseTargetSelection(req);
                return new UseSummonActionCommand(commandId, expectedVersion, playerId, summonId, sel);
            }
            case "USE_EQUIP_ACTION" -> {
                PlayerId playerId = commandPlayerId(req);
                String inventoryEquipId = requireText(req.trimmedInventoryEquipId(), "inventoryEquipId");
                TargetSelection sel = parseTargetSelection(req);
                return new UseEquipActionCommand(commandId, expectedVersion, playerId, inventoryEquipId, sel);
            }
            case "RELOAD_EQUIPMENT" -> {
                PlayerId playerId = commandPlayerId(req);
                String inventoryEquipId = requireText(req.trimmedInventoryEquipId(), "inventoryEquipId");
                return new ReloadEquipmentCommand(commandId, expectedVersion, playerId, inventoryEquipId);
            }
            case "USE_ITEM" -> {
                PlayerId playerId = commandPlayerId(req);
                String itemId = requireText(req.trimmedItemId(), "itemId");
                int count = countOrDefault(req, 1);
                TargetSelection sel = parseTargetSelection(req);
                return new UseItemCommand(commandId, expectedVersion, playerId, itemId, count, sel);
            }
            case "BUY_SHOP_ITEM" -> {
                PlayerId playerId = commandPlayerId(req);
                String offerId = requireText(req.trimmedOfferId(), "offerId");
                int count = countOrDefault(req, 1);
                return new BuyShopItemCommand(commandId, expectedVersion, playerId, offerId, count);
            }
            case "EQUIP_EQUIPMENT" -> {
                PlayerId playerId = commandPlayerId(req);
                String inventoryEquipId = requireText(req.trimmedInventoryEquipId(), "inventoryEquipId");
                return new EquipEquipmentCommand(commandId, expectedVersion, playerId, inventoryEquipId);
            }
            case "UNEQUIP_EQUIPMENT" -> {
                PlayerId playerId = commandPlayerId(req);
                String inventoryEquipId = requireText(req.trimmedInventoryEquipId(), "inventoryEquipId");
                return new UnequipEquipmentCommand(commandId, expectedVersion, playerId, inventoryEquipId);
            }
            case "OPEN_CHEST" -> {
                PlayerId playerId = commandPlayerId(req);
                int count = countOrDefault(req, 1);
                return new OpenChestCommand(commandId, expectedVersion, playerId, count);
            }
            case "RESOLVE_JUDGEMENT" -> {
                PlayerId playerId = commandPlayerId(req);
                String choiceId = requireText(req.trimmedChoiceId(), "choiceId");
                return new ResolveJudgementCommand(commandId, expectedVersion, playerId, choiceId);
            }
            case "SURRENDER_COMBAT" -> {
                PlayerId playerId = commandPlayerId(req);
                String reason = req.trimmedReason();
                return new SurrenderCombatCommand(commandId, expectedVersion, playerId, reason);
            }
            case "SELL_INVENTORY_ITEM" -> {
                PlayerId playerId = commandPlayerId(req);
                int count = countOrDefault(req, 1);
                return new SellInventoryItemCommand(commandId, expectedVersion, playerId, req.trimmedItemId(), req.trimmedInventoryEquipId(), count);
            }
            case "RETREAT_COMBAT" -> {
                PlayerId playerId = commandPlayerId(req);
                String reason = req.trimmedReason();
                return new RetreatCombatCommand(commandId, expectedVersion, playerId, reason);
            }
            case "DISCARD_TO_HAND_LIMIT" -> {
                PlayerId playerId = commandPlayerId(req);
                List<CardInstId> ids = parseCardInstIds(req.discardIds(), "discardIds");
                return new DiscardToHandLimitCommand(commandId, expectedVersion, playerId, ids);
            }
            case "RESOLVE_INITIATIVE_TIE" -> {
                PlayerId playerId = commandPlayerId(req);
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
            case "SEARCH_PICK", "RESOLVE_SEARCH_PICK" -> {
                PlayerId playerId = commandPlayerId(req);
                List<CardInstId> ids = parseCardInstIds(req.selectedIds(), "selectedIds");
                return new ResolveSearchPickCommand(commandId, expectedVersion, playerId, ids);
            }
            case "SELECT_NODE_CHOICE" -> {
                PlayerId playerId = commandPlayerId(req);
                return new SelectNodeChoiceCommand(commandId, expectedVersion, playerId, requireText(req.trimmedChoiceId(), "choiceId"));
            }
            case "CLEAR_RECENT_RESULTS" -> {
                PlayerId playerId = commandPlayerId(req);
                return new ClearRecentResultsCommand(commandId, expectedVersion, playerId);
            }
            // 다음 라운드 run-loop 확장 포인트:
            // - CLAIM_RECENT_RESULT: resultId/resultIndex
            default -> throw new ResponseStatusException(BAD_REQUEST, "unknown command type: " + req.type());
        }
    }

    private static PlayerId commandPlayerId(CommandRequest req) {
        return parsePlayerId(requireText(req.trimmedPlayerId(), "playerId"));
    }

    private static int countOrDefault(CommandRequest req, int fallback) {
        return req.countOrDefault(fallback);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, fieldName + " is required");
        }
        return value.trim();
    }

    private void validatePlayerAuthorityIfRequired(String code, String playerTokenHeader, CommandRequest req, String type) {
        if (!PLAYER_AUTH_REQUIRED_TYPES.contains(type)) {
            return;
        }
        String requestPlayerId = requireText(req.trimmedPlayerId(), "playerId");
        String actorPlayerId = resolveActorPlayerId(code, playerTokenHeader);
        if (!requestPlayerId.equals(actorPlayerId)) {
            throw new ResponseStatusException(FORBIDDEN, "playerId mismatch");
        }
    }

    private static void requirePlayer(String playerId) {
        requireText(playerId, "playerId");
    }
}
