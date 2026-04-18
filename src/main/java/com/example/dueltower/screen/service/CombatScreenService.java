package com.example.dueltower.screen.service;

import com.example.dueltower.content.card.dto.CardDetailResponse;
import com.example.dueltower.content.card.service.CardService;
import com.example.dueltower.engine.model.CardDefinition;
import com.example.dueltower.engine.model.CardType;
import com.example.dueltower.engine.model.Ids.CardDefId;
import com.example.dueltower.screen.dto.CombatScreenResponse;
import com.example.dueltower.screen.dto.DisabledReasonDto;
import com.example.dueltower.screen.dto.ScreenActionAuth;
import com.example.dueltower.screen.dto.ScreenActionDto;
import com.example.dueltower.session.dto.CombatStateDto;
import com.example.dueltower.session.dto.PlayerStateDto;
import com.example.dueltower.session.dto.SessionStateDto;
import com.example.dueltower.session.runtime.SessionRuntime;
import com.example.dueltower.session.runtime.StateMapper;
import com.example.dueltower.session.service.SessionAccessDecision;
import com.example.dueltower.session.service.SessionAccessResolver;
import com.example.dueltower.session.service.SessionLifecycleService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class CombatScreenService {

    private static final int DEFAULT_EVENT_LIMIT = 12;
    private static final int MAX_EVENT_LIMIT = 50;

    private final SessionLifecycleService sessionLifecycleService;
    private final SessionAccessResolver sessionAccessResolver;
    private final CardService cardService;

    public CombatScreenService(SessionLifecycleService sessionLifecycleService,
                               SessionAccessResolver sessionAccessResolver,
                               CardService cardService) {
        this.sessionLifecycleService = sessionLifecycleService;
        this.sessionAccessResolver = sessionAccessResolver;
        this.cardService = cardService;
    }

    public CombatScreenResponse getScreen(String code,
                                          Long afterVersion,
                                          Integer eventLimit,
                                          String gmTokenHeader,
                                          String playerTokenHeader,
                                          Authentication authentication) {
        long normalizedAfterVersion = normalizeAfterVersion(afterVersion);
        int normalizedEventLimit = normalizeEventLimit(eventLimit);

        return sessionLifecycleService.withLockedSession(code, rt -> {
            SessionAccessDecision decision = sessionAccessResolver.requireSessionReadable(
                    rt,
                    gmTokenHeader,
                    playerTokenHeader,
                    authentication
            );
            SessionStateDto state = StateMapper.toDto(rt.code(), rt.state());
            Map<CardDefId, CardDefinition> cardDefinitions = cardService.asMap();

            CombatScreenResponse.ActorSummary currentActor = actorSummary(
                    state.combat() == null ? null : state.combat().currentTurnPlayer()
            );
            CombatScreenResponse.Access access = access(state, decision);
            String visiblePlayerId = visiblePlayerId(state, decision);
            CombatScreenResponse.Actors actors = actors(state, cardDefinitions);
            CombatScreenResponse.Zones zones = zones(state, visiblePlayerId, cardDefinitions);
            CombatScreenResponse.Sidebar sidebar = sidebar(rt, normalizedEventLimit);

            return new CombatScreenResponse(
                    ScreenRouteSpec.COMBAT.screenKey(),
                    OffsetDateTime.now(ZoneId.of("Asia/Seoul")),
                    notices(decision, visiblePlayerId),
                    possibleActions(state, decision),
                    state.sessionCode(),
                    state.version(),
                    state.version() > normalizedAfterVersion,
                    status(state, currentActor),
                    access,
                    actors,
                    zones,
                    sidebar
            );
        });
    }

    private CombatScreenResponse.Status status(SessionStateDto state,
                                               CombatScreenResponse.ActorSummary currentActor) {
        int tieGroupCount = state.combat() == null
                ? 0
                : (int) state.combat().initiativeTieGroups().stream().filter(group -> group.size() > 1).count();
        String battlefieldSummary = state.players().size()
                + " players | "
                + (state.combat() == null ? 0 : state.combat().enemies().size())
                + " enemies | "
                + (state.combat() == null ? 0 : state.combat().summons().size())
                + " summons";
        String runSummary = state.run() != null && state.run().currentNode() != null
                ? state.run().currentNode().name() + " | " + state.run().currentNode().typeLabel()
                : state.run() != null && state.run().resultPending()
                ? "A run result is pending resolution."
                : "Run node unavailable";

        return new CombatScreenResponse.Status(
                state.combat() == null ? null : state.combat().round(),
                state.combat() == null ? null : state.combat().phase(),
                currentActor,
                turnOrderSummary(state),
                battlefieldSummary,
                runSummary,
                tieGroupCount > 0 ? tieGroupCount + " tie groups" : "No tie groups"
        );
    }

    private CombatScreenResponse.Access access(SessionStateDto state,
                                               SessionAccessDecision decision) {
        String role = role(decision);
        String runtimePlayerId = decision.playerId();
        String currentActorPlayerId = currentActorPlayerId(state);
        PlayerStateDto runtimePlayer = runtimePlayerId == null ? null : state.players().get(runtimePlayerId);
        boolean hasPlayerToken = decision.source() == SessionAccessDecision.SessionAccessSource.PLAYER_TOKEN;
        boolean hasGmToken = decision.source() == SessionAccessDecision.SessionAccessSource.GM_TOKEN;
        boolean canIssuePlayerCommand = hasPlayerToken
                && runtimePlayerId != null
                && currentActorPlayerId != null
                && runtimePlayerId.equals(currentActorPlayerId);
        boolean hasPendingDecision = runtimePlayer != null && runtimePlayer.pendingDecision() != null;
        boolean exAvailable = runtimePlayer != null && runtimePlayer.exCard() != null && !runtimePlayer.exOnCooldown();

        return new CombatScreenResponse.Access(
                role,
                runtimePlayerId,
                state.version(),
                new CombatScreenResponse.GuardSummary(
                        canIssuePlayerCommand,
                        hasPlayerToken && hasPendingDecision,
                        hasPlayerToken && runtimePlayerId != null,
                        "gm".equals(role) && hasGmToken,
                        exAvailable,
                        hasPendingDecision,
                        runtimePlayerId != null && runtimePlayerId.equals(currentActorPlayerId),
                        state.combat() != null
                )
        );
    }

    private CombatScreenResponse.Actors actors(SessionStateDto state,
                                               Map<CardDefId, CardDefinition> cardDefinitions) {
        List<CombatScreenResponse.PlayerView> players = state.players().values().stream()
                .sorted(Comparator.comparing(PlayerStateDto::playerId))
                .map(player -> playerView(player, state, cardDefinitions))
                .toList();
        List<CombatScreenResponse.EnemyView> enemies = state.combat() == null
                ? List.of()
                : state.combat().enemies().stream().map(this::enemyView).toList();
        List<CombatScreenResponse.SummonView> summons = state.combat() == null
                ? List.of()
                : state.combat().summons().stream().map(this::summonView).toList();
        return new CombatScreenResponse.Actors(players, enemies, summons);
    }

    private CombatScreenResponse.PlayerView playerView(PlayerStateDto player,
                                                       SessionStateDto state,
                                                       Map<CardDefId, CardDefinition> cardDefinitions) {
        String pendingLabel = player.pendingDecision() == null ? "None" : player.pendingDecision().type();
        List<CombatScreenResponse.CardView> handCards = cardViews(player.hand(), state, cardDefinitions);
        List<CombatScreenResponse.CardView> fieldCards = cardViews(player.field(), state, cardDefinitions);
        List<CombatScreenResponse.CardView> graveCards = cardViews(player.grave(), state, cardDefinitions);
        List<CombatScreenResponse.CardView> excludedCards = cardViews(player.excluded(), state, cardDefinitions);

        return new CombatScreenResponse.PlayerView(
                player.playerId(),
                player.ready(),
                player.pendingDecision() != null ? player.pendingDecision().type() : (player.ready() ? "Ready" : "Joined"),
                player.pendingDecision() != null ? "warning" : (player.ready() ? "success" : "accent"),
                List.of(
                        new CombatScreenResponse.Metric("Hand", handCards.size(), "Limit " + player.handLimit()),
                        new CombatScreenResponse.Metric("Field", fieldCards.size(), "Limit " + player.fieldLimit()),
                        new CombatScreenResponse.Metric("Deck", player.deck().size(), "Cards remaining"),
                        new CombatScreenResponse.Metric(
                                "Owned",
                                player.ownedCardCount() + "/" + player.maxOwnedCardCount(),
                                "Owned pool"
                        )
                ),
                List.of(
                        "EX " + nullSafe(player.exCard(), "None") + " | Cooldown " + yesNo(player.exOnCooldown()) + " | Passives " + player.passiveIds().size(),
                        "Pending " + pendingLabel + " | Ready " + yesNo(player.ready()) + " | Cards played " + player.cardsPlayedThisTurn(),
                        "Grave " + graveCards.size() + " | Excluded " + excludedCards.size() + " | Forgetting required " + yesNo(player.forgettingRequired())
                ),
                List.of(
                        new CombatScreenResponse.Tag(
                                player.exCard() == null ? "No EX" : (player.exOnCooldown() ? "EX cooldown" : "EX ready"),
                                player.exCard() == null ? "muted" : (player.exOnCooldown() ? "muted" : "warning")
                        ),
                        new CombatScreenResponse.Tag(
                                player.pendingDecision() == null ? "No pending decision" : player.pendingDecision().type(),
                                player.pendingDecision() == null ? "muted" : "warning"
                        ),
                        new CombatScreenResponse.Tag(graveCards.size() + " grave", graveCards.isEmpty() ? "muted" : "accent"),
                        new CombatScreenResponse.Tag(excludedCards.size() + " excluded", excludedCards.isEmpty() ? "muted" : "accent")
                ),
                List.copyOf(player.passiveIds()),
                handCards,
                fieldCards,
                graveCards,
                excludedCards,
                cardView(player.exCard(), state, cardDefinitions)
        );
    }

    private CombatScreenResponse.EnemyView enemyView(CombatStateDto.EnemyCombatDto enemy) {
        List<String> statusEntries = enemy.statuses().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .toList();
        return new CombatScreenResponse.EnemyView(
                enemy.enemyId(),
                enemy.exActivatable() ? "EX ready" : (enemy.exOnCooldown() ? "Cooldown" : "Active"),
                enemy.exActivatable() ? "warning" : (enemy.exOnCooldown() ? "muted" : "accent"),
                List.of(
                        new CombatScreenResponse.Metric("HP", enemy.hp() + "/" + enemy.maxHp(), "Current / max"),
                        new CombatScreenResponse.Metric("AP", enemy.ap(), "Current AP"),
                        new CombatScreenResponse.Metric("ATK", enemy.attackPower(), "Attack power"),
                        new CombatScreenResponse.Metric("HEAL", enemy.healPower(), "Heal power")
                ),
                List.of(
                        "EX " + nullSafe(enemy.exCardId(), "None"),
                        "Statuses " + (statusEntries.isEmpty() ? "None" : statusEntries.size()),
                        "Enemy id " + enemy.enemyId()
                ),
                statusEntries
        );
    }

    private CombatScreenResponse.SummonView summonView(CombatStateDto.SummonDto summon) {
        return new CombatScreenResponse.SummonView(
                summon.summonId(),
                summon.owner(),
                summon.actionAvailable() ? "Action ready" : "Tapped",
                summon.actionAvailable() ? "success" : "muted",
                List.of(
                        new CombatScreenResponse.Metric("HP", summon.hp(), "Current HP"),
                        new CombatScreenResponse.Metric("ATK", summon.atk(), "Attack power"),
                        new CombatScreenResponse.Metric("HEAL", summon.heal(), "Heal power")
                ),
                List.of(
                        "Owner " + summon.owner(),
                        "Action available " + yesNo(summon.actionAvailable())
                )
        );
    }

    private CombatScreenResponse.Zones zones(SessionStateDto state,
                                             String visiblePlayerId,
                                             Map<CardDefId, CardDefinition> cardDefinitions) {
        PlayerStateDto player = visiblePlayerId == null ? null : state.players().get(visiblePlayerId);
        if (player == null) {
            return new CombatScreenResponse.Zones(visiblePlayerId, List.of(), List.of(), List.of(), List.of(), null);
        }
        return new CombatScreenResponse.Zones(
                visiblePlayerId,
                cardViews(player.hand(), state, cardDefinitions),
                cardViews(player.field(), state, cardDefinitions),
                cardViews(player.grave(), state, cardDefinitions),
                cardViews(player.excluded(), state, cardDefinitions),
                cardView(player.exCard(), state, cardDefinitions)
        );
    }

    private CombatScreenResponse.Sidebar sidebar(SessionRuntime rt,
                                                 int limit) {
        List<SessionRuntime.StoredEvent> history = rt.eventHistorySnapshot();
        List<CombatScreenResponse.FeedEntry> events = new ArrayList<>();
        List<CombatScreenResponse.FeedEntry> logs = new ArrayList<>();

        for (int i = history.size() - 1; i >= 0 && (events.size() < limit || logs.size() < limit); i--) {
            SessionRuntime.StoredEvent stored = history.get(i);
            var eventDto = StateMapper.toEventDto(stored.event());

            if (events.size() < limit) {
                events.add(new CombatScreenResponse.FeedEntry(
                        eventDto.type(),
                        List.of(
                                "Version " + stored.version() + " | Cursor " + stored.cursor(),
                                formatInstant(stored.occurredAt())
                        )
                ));
            }

            if ("LOG_APPENDED".equals(eventDto.type()) && logs.size() < limit) {
                Object line = eventDto.payload().get("line");
                logs.add(new CombatScreenResponse.FeedEntry(
                        "LOG_APPENDED",
                        List.of(
                                line == null ? "" : line.toString(),
                                "Version " + stored.version() + " | " + formatInstant(stored.occurredAt())
                        )
                ));
            }
        }

        List<CombatScreenResponse.RecentResultEntry> recentResults = rt.state().runState().recentResults().stream()
                .limit(limit)
                .map(result -> new CombatScreenResponse.RecentResultEntry(
                        result.title(),
                        result.summary(),
                        result.type() + " | " + nullSafe(result.at(), "Time unavailable")
                ))
                .toList();

        return new CombatScreenResponse.Sidebar(List.copyOf(events), List.copyOf(logs), recentResults);
    }

    private List<String> notices(SessionAccessDecision decision,
                                 String visiblePlayerId) {
        List<String> notices = new ArrayList<>();
        switch (decision.source()) {
            case PLAYER_TOKEN, AUTHENTICATED_PLAYER -> notices.add(
                    "Player access restored for " + decision.playerId() + ". Visible zones follow that player when available."
            );
            case GM_TOKEN -> notices.add("GM access restored for this combat route.");
            case AUTHENTICATED_GM -> notices.add("GM login fallback can read combat, but GM-only commands still require X-GM-Token.");
        }
        if (visiblePlayerId == null || visiblePlayerId.isBlank()) {
            notices.add("Visible player zones are unavailable because no joined player is present.");
        }
        return List.copyOf(notices);
    }

    private List<ScreenActionDto> possibleActions(SessionStateDto state,
                                                  SessionAccessDecision decision) {
        String runtimePlayerId = decision.playerId();
        String currentActorPlayerId = currentActorPlayerId(state);
        PlayerStateDto runtimePlayer = runtimePlayerId == null ? null : state.players().get(runtimePlayerId);
        boolean hasPlayerToken = decision.source() == SessionAccessDecision.SessionAccessSource.PLAYER_TOKEN;
        boolean canIssuePlayerCommand = hasPlayerToken
                && runtimePlayerId != null
                && currentActorPlayerId != null
                && runtimePlayerId.equals(currentActorPlayerId);
        boolean canClearRecentResults = hasPlayerToken && runtimePlayerId != null;
        boolean hasPendingDecision = runtimePlayer != null && runtimePlayer.pendingDecision() != null;
        boolean exAvailable = runtimePlayer != null && runtimePlayer.exCard() != null && !runtimePlayer.exOnCooldown();

        List<ScreenActionDto> actions = new ArrayList<>();
        actions.add(playerCommandAction(
                state.sessionCode(),
                "combat.draw",
                "Draw",
                canIssuePlayerCommand,
                playerCommandDisabledReason(runtimePlayerId, canIssuePlayerCommand),
                Map.of(
                        "type", "DRAW",
                        "expectedVersion", state.version(),
                        "playerId", runtimePlayerId == null ? "" : runtimePlayerId,
                        "count", 1
                )
        ));
        actions.add(playerCommandAction(
                state.sessionCode(),
                "combat.endTurn",
                "End turn",
                canIssuePlayerCommand,
                playerCommandDisabledReason(runtimePlayerId, canIssuePlayerCommand),
                Map.of(
                        "type", "END_TURN",
                        "expectedVersion", state.version(),
                        "playerId", runtimePlayerId == null ? "" : runtimePlayerId
                )
        ));
        actions.add(playerCommandAction(
                state.sessionCode(),
                "combat.clearRecentResults",
                "Clear recent results",
                canClearRecentResults,
                canClearRecentResults ? null : playerTokenRequiredReason("clear recent results"),
                Map.of(
                        "type", "CLEAR_RECENT_RESULTS",
                        "expectedVersion", state.version(),
                        "playerId", runtimePlayerId == null ? "" : runtimePlayerId
                )
        ));
        actions.add(playerCommandAction(
                state.sessionCode(),
                "combat.playCard",
                "Play selected card",
                canIssuePlayerCommand,
                playerCommandDisabledReason(runtimePlayerId, canIssuePlayerCommand),
                new LinkedHashMap<>(Map.of(
                        "type", "PLAY_CARD",
                        "expectedVersion", state.version(),
                        "playerId", runtimePlayerId == null ? "" : runtimePlayerId,
                        "cardId", "",
                        "discardIds", List.of(),
                        "selectedIds", List.of(),
                        "targets", List.of()
                ))
        ));
        actions.add(playerCommandAction(
                state.sessionCode(),
                "combat.useEx",
                "Use EX",
                canIssuePlayerCommand && exAvailable,
                runtimePlayerId == null
                        ? playerTokenRequiredReason("use EX")
                        : canIssuePlayerCommand
                        ? (exAvailable ? null : exUnavailableReason())
                        : playerTurnRequiredReason(),
                new LinkedHashMap<>(Map.of(
                        "type", "USE_EX",
                        "expectedVersion", state.version(),
                        "playerId", runtimePlayerId == null ? "" : runtimePlayerId,
                        "targets", List.of()
                ))
        ));

        if (hasPendingDecision) {
            actions.add(resolvePendingAction(state, runtimePlayerId, runtimePlayer));
        }

        return List.copyOf(actions);
    }

    private ScreenActionDto resolvePendingAction(SessionStateDto state,
                                                 String runtimePlayerId,
                                                 PlayerStateDto runtimePlayer) {
        String pendingType = runtimePlayer.pendingDecision().type();
        String commandType = switch (pendingType) {
            case "DISCARD_TO_HAND_LIMIT" -> "DISCARD_TO_HAND_LIMIT";
            case "SEARCH_PICK" -> "SEARCH_PICK";
            case "INITIATIVE_TIE_ORDER" -> "RESOLVE_INITIATIVE_TIE";
            case "JUDGEMENT" -> "RESOLVE_JUDGEMENT";
            default -> null;
        };
        boolean enabled = commandType != null;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("expectedVersion", state.version());
        payload.put("playerId", runtimePlayerId == null ? "" : runtimePlayerId);
        payload.put("type", commandType == null ? pendingType : commandType);
        payload.put("discardIds", List.of());
        payload.put("selectedIds", List.of());
        payload.put("orderedActorKeys", List.of());
        payload.put("choiceId", "");
        if (runtimePlayer.pendingDecision().groupIndex() != null) {
            payload.put("tieGroupIndex", runtimePlayer.pendingDecision().groupIndex());
        }

        return playerCommandAction(
                state.sessionCode(),
                "combat.resolvePending",
                "Resolve pending decision",
                enabled,
                enabled ? null : unsupportedPendingReason(pendingType),
                payload
        );
    }

    private ScreenActionDto playerCommandAction(String sessionCode,
                                                String id,
                                                String label,
                                                boolean enabled,
                                                DisabledReasonDto disabledReason,
                                                Map<String, Object> payloadTemplate) {
        return ScreenActionDto.of(
                id,
                label,
                "POST",
                "/api/sessions/" + sessionCode + "/command",
                ScreenActionAuth.PLAYER_TOKEN,
                enabled,
                disabledReason,
                payloadTemplate
        );
    }

    private DisabledReasonDto playerCommandDisabledReason(String runtimePlayerId,
                                                          boolean canIssuePlayerCommand) {
        if (canIssuePlayerCommand) {
            return null;
        }
        if (runtimePlayerId == null || runtimePlayerId.isBlank()) {
            return playerTokenRequiredReason("use this action");
        }
        return playerTurnRequiredReason();
    }

    private String visiblePlayerId(SessionStateDto state,
                                   SessionAccessDecision decision) {
        if (decision.playerId() != null && state.players().containsKey(decision.playerId())) {
            return decision.playerId();
        }
        return state.players().keySet().stream().findFirst().orElse(null);
    }

    private List<CombatScreenResponse.CardView> cardViews(List<String> instanceIds,
                                                          SessionStateDto state,
                                                          Map<CardDefId, CardDefinition> cardDefinitions) {
        return instanceIds.stream().map(instanceId -> cardView(instanceId, state, cardDefinitions)).toList();
    }

    private CombatScreenResponse.CardView cardView(String instanceId,
                                                   SessionStateDto state,
                                                   Map<CardDefId, CardDefinition> cardDefinitions) {
        if (instanceId == null || instanceId.isBlank()) {
            return null;
        }

        var instance = state.cards().get(instanceId);
        if (instance == null) {
            return new CombatScreenResponse.CardView(
                    instanceId,
                    null,
                    instanceId,
                    "Unresolved card instance",
                    true,
                    List.of(new CombatScreenResponse.Tag("Unresolved", "warning")),
                    "Instance " + instanceId
            );
        }

        CardDefinition definition = null;
        if (instance.defId() != null && !instance.defId().isBlank()) {
            definition = cardDefinitions.get(new CardDefId(instance.defId()));
        }

        if (definition == null) {
            return new CombatScreenResponse.CardView(
                    instanceId,
                    instance.defId(),
                    nullSafe(instance.defId(), instanceId),
                    "Unresolved card definition",
                    true,
                    List.of(new CombatScreenResponse.Tag("Unresolved", "warning")),
                    "Instance " + instanceId
            );
        }

        CardDetailResponse detail = cardService.get(definition.id().value());
        return new CombatScreenResponse.CardView(
                instanceId,
                definition.id().value(),
                definition.name(),
                cardTypeLabel(definition.type()),
                false,
                cardTags(definition),
                "Cost " + definition.cost()
                        + " | "
                        + nullSafe(detail.resolveTo() == null ? null : detail.resolveTo().name(), "Resolve N/A")
                        + " | Instance " + instanceId
        );
    }

    private List<CombatScreenResponse.Tag> cardTags(CardDefinition definition) {
        List<CombatScreenResponse.Tag> tags = new ArrayList<>();
        tags.add(new CombatScreenResponse.Tag(cardTypeLabel(definition.type()), cardTypeTone(definition.type())));
        if (definition.token()) {
            tags.add(new CombatScreenResponse.Tag("Token", "muted"));
        }
        definition.keywords().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .limit(4)
                .forEach(entry -> tags.add(new CombatScreenResponse.Tag(
                        entry.getValue() == null || entry.getValue() == 0
                                ? entry.getKey()
                                : entry.getKey() + " " + entry.getValue(),
                        "muted"
                )));
        return List.copyOf(tags);
    }

    private CombatScreenResponse.ActorSummary actorSummary(String rawActor) {
        String normalized = rawActor == null ? "" : rawActor.trim();
        if (normalized.isBlank()) {
            return new CombatScreenResponse.ActorSummary(
                    null,
                    "none",
                    null,
                    "No active turn",
                    "Combat turn owner is not available in the current state.",
                    "muted"
            );
        }
        if (normalized.startsWith("P:")) {
            String playerId = normalized.substring(2).trim();
            return new CombatScreenResponse.ActorSummary(
                    normalized,
                    "player",
                    playerId.isBlank() ? null : playerId,
                    playerId.isBlank() ? normalized : playerId,
                    playerId.isBlank()
                            ? "A player turn is active, but the actor id is incomplete."
                            : playerId + " is the current acting player.",
                    "success"
            );
        }
        if (normalized.startsWith("E:")) {
            String enemyId = normalized.substring(2).trim();
            return new CombatScreenResponse.ActorSummary(
                    normalized,
                    "enemy",
                    enemyId.isBlank() ? null : enemyId,
                    enemyId.isBlank() ? normalized : enemyId,
                    enemyId.isBlank()
                            ? "An enemy turn is active, but the actor id is incomplete."
                            : enemyId + " is the current acting enemy.",
                    "warning"
            );
        }
        return new CombatScreenResponse.ActorSummary(
                normalized,
                "unknown",
                normalized,
                normalized,
                "Current actor format is not recognized, so the raw value is shown.",
                "accent"
        );
    }

    private String currentActorPlayerId(SessionStateDto state) {
        if (state.combat() == null || state.combat().currentTurnPlayer() == null) {
            return null;
        }
        String currentActor = state.combat().currentTurnPlayer().trim();
        if (currentActor.startsWith("P:")) {
            String playerId = currentActor.substring(2).trim();
            return playerId.isBlank() ? null : playerId;
        }
        return null;
    }

    private String turnOrderSummary(SessionStateDto state) {
        if (state.combat() == null || state.combat().turnOrder().isEmpty()) {
            return "Turn order is not available yet.";
        }
        List<String> preview = state.combat().turnOrder().stream()
                .limit(6)
                .map(this::actorSummary)
                .map(CombatScreenResponse.ActorSummary::label)
                .toList();
        long hiddenCount = state.combat().turnOrder().size() - preview.size();
        String summary = String.join(" -> ", preview);
        return hiddenCount > 0 ? summary + " +" + hiddenCount + " more" : summary;
    }

    private String role(SessionAccessDecision decision) {
        return switch (decision.source()) {
            case PLAYER_TOKEN, AUTHENTICATED_PLAYER -> "player";
            case GM_TOKEN, AUTHENTICATED_GM -> "gm";
        };
    }

    private DisabledReasonDto playerTokenRequiredReason(String actionName) {
        return new DisabledReasonDto(
                "PLAYER_TOKEN_REQUIRED",
                "AUTH",
                "Player token access is required to " + actionName + ".",
                "combat action requires X-Player-Token",
                null,
                null,
                null
        );
    }

    private DisabledReasonDto playerTurnRequiredReason() {
        return new DisabledReasonDto(
                "PLAYER_TURN_REQUIRED",
                "RULE",
                "The runtime player must own the current turn before issuing this action.",
                "runtime player is not the current actor",
                null,
                null,
                null
        );
    }

    private DisabledReasonDto exUnavailableReason() {
        return new DisabledReasonDto(
                "EX_UNAVAILABLE",
                "RULE",
                "EX is not available for the runtime player.",
                "runtime player ex card missing or on cooldown",
                null,
                null,
                null
        );
    }

    private DisabledReasonDto unsupportedPendingReason(String pendingType) {
        return new DisabledReasonDto(
                "PENDING_DECISION_UNSUPPORTED",
                "RULE",
                "This pending decision type is not yet wired to a screen action.",
                "unsupported pending decision type: " + pendingType,
                Map.of("pendingType", pendingType),
                null,
                null
        );
    }

    private static String cardTypeLabel(CardType type) {
        if (type == null) {
            return "Card";
        }
        return switch (type) {
            case EX -> "EX";
            case SKILL -> "Skill";
            case TOKEN -> "Token";
        };
    }

    private static String cardTypeTone(CardType type) {
        if (type == null) {
            return "muted";
        }
        return switch (type) {
            case EX -> "accent";
            case SKILL -> "success";
            case TOKEN -> "muted";
        };
    }

    private static String formatInstant(java.time.Instant instant) {
        return instant == null ? "Timestamp unavailable" : instant.toString();
    }

    private static String nullSafe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String yesNo(boolean value) {
        return value ? "Yes" : "No";
    }

    private static long normalizeAfterVersion(Long afterVersion) {
        long minVersion = afterVersion == null ? 0L : afterVersion;
        if (minVersion < 0L) {
            throw new ResponseStatusException(BAD_REQUEST, "afterVersion must be >= 0");
        }
        return minVersion;
    }

    private static int normalizeEventLimit(Integer eventLimit) {
        int normalized = eventLimit == null ? DEFAULT_EVENT_LIMIT : eventLimit;
        if (normalized <= 0) {
            throw new ResponseStatusException(BAD_REQUEST, "eventLimit must be > 0");
        }
        return Math.min(normalized, MAX_EVENT_LIMIT);
    }
}
