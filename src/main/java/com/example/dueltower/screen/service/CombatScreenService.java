package com.example.dueltower.screen.service;

import com.example.dueltower.content.card.dto.CardDetailResponse;
import com.example.dueltower.content.card.model.playspec.CardPlaySpec;
import com.example.dueltower.content.card.model.playspec.ChoiceRequirement;
import com.example.dueltower.content.card.model.playspec.DiscardFromHandRequirement;
import com.example.dueltower.content.card.model.playspec.ExtraPlayRequirement;
import com.example.dueltower.content.card.model.playspec.BoardObjectFilter;
import com.example.dueltower.content.card.model.playspec.BoardObjectKind;
import com.example.dueltower.content.card.model.playspec.BoardObjectRelation;
import com.example.dueltower.content.card.model.playspec.SelectBoardObjectsRequirement;
import com.example.dueltower.content.card.model.playspec.SelectFieldCardsRequirement;
import com.example.dueltower.content.card.service.CardService;
import com.example.dueltower.engine.model.CardDefinition;
import com.example.dueltower.engine.model.CardType;
import com.example.dueltower.engine.model.Ids.CardDefId;
import com.example.dueltower.session.dto.PendingDecisionDto;
import com.example.dueltower.screen.dto.CombatScreenResponse;
import com.example.dueltower.screen.dto.DisabledReasonDto;
import com.example.dueltower.screen.dto.ScreenActionAuth;
import com.example.dueltower.screen.dto.ScreenActionDto;
import com.example.dueltower.session.dto.CombatStateDto;
import com.example.dueltower.session.dto.ControllableActorDto;
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
import java.util.Objects;
import java.util.stream.IntStream;

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
                    possibleActions(state, decision, cardDefinitions),
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
        String commandActorPlayerId = commandActorPlayerId(state, runtimePlayerId, currentActorPlayerId);
        PlayerStateDto runtimePlayer = commandActorPlayerId == null ? null : state.players().get(commandActorPlayerId);
        boolean hasPlayerToken = decision.source() == SessionAccessDecision.SessionAccessSource.PLAYER_TOKEN;
        boolean hasGmToken = decision.source() == SessionAccessDecision.SessionAccessSource.GM_TOKEN;
        boolean canIssuePlayerCommand = hasPlayerToken
                && commandActorPlayerId != null
                && currentActorPlayerId != null
                && commandActorPlayerId.equals(currentActorPlayerId);
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
                        commandActorPlayerId != null && commandActorPlayerId.equals(currentActorPlayerId),
                        state.combat() != null
                ),
                controllableActors(state, runtimePlayerId)
        );
    }

    private List<ControllableActorDto> controllableActors(SessionStateDto state, String runtimePlayerId) {
        if (runtimePlayerId == null || runtimePlayerId.isBlank()) {
            return List.of();
        }
        return state.players().values().stream()
                .filter(player -> runtimePlayerId.equals(player.playerId())
                        || ("GM_CONTROLLED_NPC".equals(player.controlType())
                        && runtimePlayerId.equals(player.controllerPlayerId())))
                .map(player -> new ControllableActorDto(player.playerId(), player.playerId(), player.controlType()))
                .toList();
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
                        eventDisplayTitle(eventDto.type()),
                        List.of(
                                "Version " + stored.version() + " | Cursor " + stored.cursor(),
                                formatInstant(stored.occurredAt())
                        ),
                        eventDto.type(),
                        "DEBUG",
                        eventDisplayTitle(eventDto.type()),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        eventDetails(eventDto.payload()),
                        eventDto.payload(),
                        stored.version(),
                        stored.cursor(),
                        formatInstant(stored.occurredAt()),
                        eventDto.payload()
                ));
            }

            if ("COMBAT_LOG_APPENDED".equals(eventDto.type()) && logs.size() < limit) {
                Map<String, Object> payload = eventDto.payload();
                String message = localizedCombatLogMessage(payload);
                List<String> details = localizedCombatLogDetails(payload);
                logs.add(new CombatScreenResponse.FeedEntry(
                        combatLogTitle(payload),
                        details.isEmpty() ? List.of(message) : prepend(message, details),
                        stringValue(payload.get("type"), "combat.log"),
                        stringValue(payload.get("visibility"), "PLAYER"),
                        message,
                        stringValue(payload.get("actorId"), null),
                        stringValue(payload.get("actorName"), null),
                        stringValue(payload.get("targetId"), null),
                        stringValue(payload.get("targetName"), null),
                        stringValue(payload.get("cardDefId"), null),
                        stringValue(payload.get("cardName"), null),
                        details,
                        payload.get("data"),
                        stored.version(),
                        stored.cursor(),
                        formatInstant(stored.occurredAt()),
                        payload
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

    private String eventDisplayTitle(String type) {
        return switch (type == null ? "" : type) {
            case "COMBAT_LOG_APPENDED" -> "Combat log updated";
            case "LOG_APPENDED" -> "Legacy log appended";
            case "CARDS_MOVED" -> "Cards moved";
            case "TURN_ADVANCED" -> "Turn advanced";
            case "DECK_SHUFFLED" -> "Deck shuffled";
            case "DECK_REFILLED" -> "Deck refilled";
            case "PENDING_DECISION_SET" -> "Pending decision opened";
            case "PENDING_DECISION_CLEARED" -> "Pending decision cleared";
            default -> nullSafe(type, "Unknown event");
        };
    }

    private String combatLogTitle(Map<String, Object> payload) {
        String type = stringValue(payload.get("type"), "combat.log");
        return switch (type) {
            case "combat.start" -> "전투 시작";
            case "combat.initiative" -> "행동 순서 판정";
            case "combat.draw" -> "드로우";
            case "combat.playCard" -> "카드 사용";
            case "combat.damage" -> "피해";
            case "combat.heal" -> "회복";
            case "combat.status" -> "상태 변화";
            case "combat.cardMove" -> "카드 이동";
            case "combat.lastWordsSkipped" -> "유언 생략";
            case "combat.encounter" -> "인카운터";
            default -> "전투 로그";
        };
    }

    private String localizedCombatLogMessage(Map<String, Object> payload) {
        String type = stringValue(payload.get("type"), "combat.log");
        Map<String, Object> data = objectMap(payload.get("data"));
        return switch (type) {
            case "combat.start" -> "전투가 시작되었다.";
            case "combat.initiative" -> "행동 순서 판정: " + stringList(data.get("summaries")).stream()
                    .findFirst()
                    .orElse(stringValue(payload.get("message"), "행동 순서 판정이 완료되었다."));
            case "combat.draw" -> stringValue(data.get("actorId"), stringValue(payload.get("actorId"), "플레이어"))
                    + "이 카드 " + stringValue(data.get("count"), "0") + "장을 드로우했다.";
            case "combat.playCard" -> stringValue(payload.get("actorName"), stringValue(payload.get("actorId"), "플레이어"))
                    + "이 [" + cardLogName(payload, data) + "]을 사용했다.";
            case "combat.damage" -> stringValue(data.get("target"), stringValue(payload.get("targetName"), "대상"))
                    + "이 " + stringValue(data.get("amount"), "0") + " 피해를 받았다. HP: "
                    + stringValue(data.get("hpBefore"), "?") + " -> " + stringValue(data.get("hpAfter"), "?");
            case "combat.heal" -> stringValue(data.get("target"), stringValue(payload.get("targetName"), "대상"))
                    + "이 " + stringValue(data.get("amount"), "0") + " 회복했다. HP: "
                    + stringValue(data.get("hpBefore"), "?") + " -> " + stringValue(data.get("hpAfter"), "?");
            case "combat.status" -> stringValue(data.get("owner"), stringValue(payload.get("targetName"), "대상"))
                    + "에게 [" + stringValue(data.get("statusName"), stringValue(data.get("statusId"), "상태")) + "] "
                    + statusChangeLabel(data) + ". " + stringValue(data.get("before"), "?") + " -> "
                    + stringValue(data.get("after"), "?");
            case "combat.cardMove" -> "[" + cardLogName(payload, data) + "] 이동: "
                    + zoneLabel(stringValue(data.get("from"), "")) + " -> " + zoneLabel(stringValue(data.get("to"), ""));
            case "combat.lastWordsSkipped" -> "[유언] 처리를 생략했다.";
            default -> stringValue(payload.get("message"), "전투 로그");
        };
    }

    private List<String> localizedCombatLogDetails(Map<String, Object> payload) {
        String type = stringValue(payload.get("type"), "combat.log");
        Map<String, Object> data = objectMap(payload.get("data"));
        return switch (type) {
            case "combat.start" -> combatStartDetails(data);
            case "combat.initiative" -> initiativeDetails(data);
            case "combat.draw" -> List.of("사유: " + reasonLabel(stringValue(data.get("reason"), "")));
            case "combat.playCard" -> playCardDetails(payload, data);
            case "combat.damage" -> List.of(
                    "피해: " + stringValue(data.get("amount"), "0"),
                    "HP: " + stringValue(data.get("hpBefore"), "?") + " -> " + stringValue(data.get("hpAfter"), "?"),
                    "출처: " + stringValue(data.get("source"), "알 수 없음")
            );
            case "combat.heal" -> List.of(
                    "회복: " + stringValue(data.get("amount"), "0"),
                    "HP: " + stringValue(data.get("hpBefore"), "?") + " -> " + stringValue(data.get("hpAfter"), "?"),
                    "출처: " + stringValue(data.get("source"), "알 수 없음")
            );
            case "combat.status" -> List.of(
                    "상태: " + stringValue(data.get("statusName"), stringValue(data.get("statusId"), "상태")),
                    "스택: " + stringValue(data.get("before"), "?") + " -> " + stringValue(data.get("after"), "?"),
                    "출처: " + stringValue(data.get("source"), "알 수 없음")
            );
            case "combat.cardMove" -> List.of(
                    "카드: " + cardLogName(payload, data),
                    "카드 ID: " + stringValue(data.get("cardDefId"), stringValue(payload.get("cardDefId"), "알 수 없음")),
                    "인스턴스: " + stringValue(data.get("cardInstanceId"), "알 수 없음"),
                    "소유자: " + stringValue(data.get("ownerId"), stringValue(payload.get("actorId"), "알 수 없음")),
                    "이동: " + zoneLabel(stringValue(data.get("from"), "")) + " -> " + zoneLabel(stringValue(data.get("to"), "")),
                    "사유: " + reasonLabel(stringValue(data.get("reason"), ""))
            );
            case "combat.lastWordsSkipped" -> lastWordsSkippedDetails(data);
            default -> stringList(payload.get("details"));
        };
    }

    private List<String> combatStartDetails(Map<String, Object> data) {
        List<String> details = new ArrayList<>();
        List<String> order = stringList(data.get("order"));
        if (!order.isEmpty()) {
            details.add("행동 순서: " + order.stream().map(this::actorLabel).reduce((a, b) -> a + " -> " + b).orElse(""));
        }
        String encounterId = stringValue(data.get("encounterId"), null);
        if (encounterId != null) {
            details.add("인카운터: " + encounterId);
        }
        List<String> enemies = stringList(data.get("enemies"));
        if (!enemies.isEmpty()) {
            details.add("적 배치: " + String.join(", ", enemies));
        }
        return List.copyOf(details);
    }

    private List<String> initiativeDetails(Map<String, Object> data) {
        List<String> summaries = stringList(data.get("summaries"));
        if (!summaries.isEmpty()) {
            return List.of("판정 결과: " + String.join(", ", summaries));
        }
        Map<String, Object> rolls = objectMap(data.get("rolls"));
        if (rolls.isEmpty()) {
            return List.of();
        }
        return List.of("판정 결과: " + rolls.entrySet().stream()
                .map(entry -> actorLabel(entry.getKey()) + " " + entry.getValue())
                .reduce((a, b) -> a + ", " + b)
                .orElse(""));
    }

    private List<String> playCardDetails(Map<String, Object> payload, Map<String, Object> data) {
        List<String> details = new ArrayList<>();
        List<String> targets = stringList(data.get("targets")).stream().map(this::actorLabel).toList();
        details.add("대상: " + (targets.isEmpty() ? "없음" : String.join(", ", targets)));
        details.add("비용: 행동력 " + stringValue(data.get("cost"), "0")
                + ("0".equals(stringValue(data.get("apDebt"), "0")) ? "" : " (부채 " + stringValue(data.get("apDebt"), "0") + ")"));
        details.add("카드 이동: " + zoneLabel(stringValue(data.get("from"), ""))
                + " -> " + zoneLabel(stringValue(data.get("to"), "")));
        details.add("카드 ID: " + stringValue(data.get("cardDefId"), stringValue(payload.get("cardDefId"), "알 수 없음")));
        details.add("인스턴스: " + stringValue(data.get("cardInstanceId"), "알 수 없음"));
        return List.copyOf(details);
    }

    private List<String> lastWordsSkippedDetails(Map<String, Object> data) {
        String reason = stringValue(data.get("reason"), "");
        List<String> details = new ArrayList<>();
        details.add("사유: " + switch (reason) {
            case "NO_CANDIDATES" -> "발동 가능한 유언 효과가 없습니다.";
            case "NO_PAYABLE_CANDIDATES" -> "지불 가능한 유언 효과가 없습니다.";
            case "PENDING_DECISION_EXISTS" -> "먼저 처리해야 할 선택지가 있습니다.";
            default -> stringValue(data.get("reasonLabel"), "유언 처리를 진행할 수 없습니다.");
        });
        List<String> checkedZones = stringList(data.get("checkedZones"));
        if (!checkedZones.isEmpty()) {
            details.add("검사 영역: " + checkedZones.stream().map(this::zoneLabel).reduce((a, b) -> a + ", " + b).orElse(""));
        }
        String candidateCount = stringValue(data.get("candidateCount"), null);
        if (candidateCount != null) {
            details.add("후보 수: " + candidateCount);
        }
        return List.copyOf(details);
    }

    private String cardLogName(Map<String, Object> payload, Map<String, Object> data) {
        String cardName = stringValue(data.get("cardName"), stringValue(payload.get("cardName"), null));
        if (cardName != null) {
            return cardName;
        }
        String cardDefId = stringValue(data.get("cardDefId"), stringValue(payload.get("cardDefId"), null));
        return cardDefId == null ? "알 수 없는 카드" : cardDefId;
    }

    private String statusChangeLabel(Map<String, Object> data) {
        int before = intValue(data.get("before"));
        int after = intValue(data.get("after"));
        if (after <= 0) {
            return "제거";
        }
        if (before <= 0) {
            return after + " 부여";
        }
        return "변경";
    }

    private String actorLabel(String value) {
        if (value == null || value.isBlank()) {
            return "알 수 없음";
        }
        if (value.startsWith("P:") || value.startsWith("E:")) {
            return value.substring(2);
        }
        if (value.startsWith("PLAYER:")) {
            return value.substring("PLAYER:".length());
        }
        if (value.startsWith("ENEMY:")) {
            return value.substring("ENEMY:".length());
        }
        return value;
    }

    private String zoneLabel(String value) {
        if (value == null || value.isBlank()) {
            return "알 수 없음";
        }
        return switch (value) {
            case "HAND" -> "패";
            case "DECK" -> "덱";
            case "GRAVE", "GRAVEYARD" -> "묘지";
            case "FIELD" -> "필드";
            case "EXCLUDED", "BANISHED" -> "제외";
            case "EX" -> "EX";
            case "PLAYER_FIELD" -> "플레이어 필드";
            case "PLAYER_GRAVEYARD" -> "플레이어 묘지";
            default -> value;
        };
    }

    private String reasonLabel(String value) {
        if (value == null || value.isBlank()) {
            return "알 수 없음";
        }
        return switch (value) {
            case "PLAY", "PLAY_CARD" -> "카드 사용";
            case "COMBAT_START" -> "전투 시작";
            case "TURN_START" -> "턴 시작";
            case "TURN_END" -> "턴 종료";
            case "DAMAGE" -> "피해";
            case "HEAL" -> "회복";
            case "DRAW" -> "드로우";
            case "DISCARD" -> "버림";
            case "OTHER" -> "기타";
            default -> value;
        };
    }

    private Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                out.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return out;
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private List<String> eventDetails(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return List.of();
        }
        return payload.entrySet().stream()
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .toList();
    }

    private List<String> prepend(String first, List<String> rest) {
        List<String> out = new ArrayList<>();
        out.add(first);
        out.addAll(rest);
        return List.copyOf(out);
    }

    private String stringValue(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String normalized = value.toString();
        return normalized.isBlank() ? fallback : normalized;
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
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
                                                  SessionAccessDecision decision,
                                                  Map<CardDefId, CardDefinition> cardDefinitions) {
        String runtimePlayerId = decision.playerId();
        String currentActorPlayerId = currentActorPlayerId(state);
        String commandActorPlayerId = commandActorPlayerId(state, runtimePlayerId, currentActorPlayerId);
        PlayerStateDto runtimePlayer = commandActorPlayerId == null ? null : state.players().get(commandActorPlayerId);
        boolean hasPlayerToken = decision.source() == SessionAccessDecision.SessionAccessSource.PLAYER_TOKEN;
        boolean canIssuePlayerCommand = hasPlayerToken
                && commandActorPlayerId != null
                && currentActorPlayerId != null
                && commandActorPlayerId.equals(currentActorPlayerId);
        boolean canClearRecentResults = hasPlayerToken && runtimePlayerId != null;
        boolean hasPendingDecision = runtimePlayer != null && runtimePlayer.pendingDecision() != null;
        boolean exAvailable = runtimePlayer != null && runtimePlayer.exCard() != null && !runtimePlayer.exOnCooldown();
        List<Map<String, Object>> playCardSourceOptions = runtimePlayer == null
                ? List.of()
                : runtimePlayer.hand().stream()
                .map(instanceId -> playCardSourceOption(instanceId, state))
                .toList();
        Map<String, Object> playCardMetadata = new LinkedHashMap<>();
        playCardMetadata.put("kind", "playCard");
        playCardMetadata.put("note", "Server-calculated command support and requirement views for each playable hand card.");
        playCardMetadata.put("localSelection", Map.of("requiresSelectedCard", true, "sourceType", "handCard"));
        playCardMetadata.put("sourceOptions", playCardSourceOptions);

        Map<String, Object> useExMetadata = new LinkedHashMap<>();
        useExMetadata.put("kind", "useEx");
        useExMetadata.put("note", "Server-calculated EX command requirement view.");
        useExMetadata.put("sourceCard", runtimePlayer == null ? null : cardView(runtimePlayer.exCard(), state, cardDefinitions));
        RequirementMetadata useExRequirement = runtimePlayer == null ? RequirementMetadata.empty(null)
                : requirementMetadataForInstance(runtimePlayer.exCard(), state);
        useExMetadata.put("requirementView", useExRequirement.view());
        useExMetadata.put("supported", useExRequirement.supported());
        useExMetadata.put("unsupportedReason", useExRequirement.unsupportedReason());

        List<ScreenActionDto> actions = new ArrayList<>();
        actions.add(playerCommandAction(
                state.sessionCode(),
                "combat.draw",
                "Draw",
                canIssuePlayerCommand,
                playerCommandDisabledReason(runtimePlayerId, canIssuePlayerCommand),
                Map.of(
                        "kind", "simple",
                        "note", canIssuePlayerCommand
                                ? "Draw is available for the runtime player on the current turn."
                                : "Requires player-token turn ownership."
                ),
                Map.of(
                        "type", "DRAW",
                        "expectedVersion", state.version(),
                        "playerId", commandActorPlayerId == null ? "" : commandActorPlayerId,
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
                        "kind", "simple",
                        "note", canIssuePlayerCommand
                                ? "End turn is available for the runtime player on the current turn."
                                : "Requires player-token turn ownership."
                ),
                Map.of(
                        "type", "END_TURN",
                        "expectedVersion", state.version(),
                        "playerId", commandActorPlayerId == null ? "" : commandActorPlayerId
                )
        ));
        actions.add(playerCommandAction(
                state.sessionCode(),
                "combat.clearRecentResults",
                "Clear recent results",
                canClearRecentResults,
                canClearRecentResults ? null : playerTokenRequiredReason("clear recent results"),
                Map.of(
                        "kind", "utility",
                        "note", canClearRecentResults
                                ? "Player-side utility command that clears the recent result stack."
                                : "Requires X-Player-Token for the current runtime player."
                ),
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
                playCardMetadata,
                new LinkedHashMap<>(Map.of(
                        "type", "PLAY_CARD",
                        "expectedVersion", state.version(),
                        "playerId", commandActorPlayerId == null ? "" : commandActorPlayerId,
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
                canIssuePlayerCommand && exAvailable && useExRequirement.supported(),
                runtimePlayerId == null
                        ? playerTokenRequiredReason("use EX")
                        : canIssuePlayerCommand
                        ? (exAvailable
                        ? (useExRequirement.supported() ? null : unsupportedRequirementReason(useExRequirement.unsupportedReason()))
                        : exUnavailableReason())
                        : playerTurnRequiredReason(),
                useExMetadata,
                new LinkedHashMap<>(Map.of(
                        "type", "USE_EX",
                        "expectedVersion", state.version(),
                        "playerId", commandActorPlayerId == null ? "" : commandActorPlayerId,
                        "targets", List.of()
                ))
        ));

        actions.add(resolvePendingAction(state, commandActorPlayerId, runtimePlayer, hasPlayerToken, hasPendingDecision, cardDefinitions));

        return List.copyOf(actions);
    }

    private String commandActorPlayerId(SessionStateDto state, String runtimePlayerId, String currentActorPlayerId) {
        if (runtimePlayerId == null || runtimePlayerId.isBlank()) {
            return null;
        }
        if (currentActorPlayerId != null && canControl(state, runtimePlayerId, currentActorPlayerId)) {
            return currentActorPlayerId;
        }
        return runtimePlayerId;
    }

    private boolean canControl(SessionStateDto state, String requesterPlayerId, String actorPlayerId) {
        if (requesterPlayerId == null || actorPlayerId == null) {
            return false;
        }
        if (requesterPlayerId.equals(actorPlayerId)) {
            return state.players().containsKey(actorPlayerId);
        }
        PlayerStateDto actor = state.players().get(actorPlayerId);
        return actor != null
                && "GM_CONTROLLED_NPC".equals(actor.controlType())
                && requesterPlayerId.equals(actor.controllerPlayerId());
    }

    private ScreenActionDto resolvePendingAction(SessionStateDto state,
                                                 String runtimePlayerId,
                                                 PlayerStateDto runtimePlayer,
                                                 boolean hasPlayerToken,
                                                 boolean hasPendingDecision,
                                                 Map<CardDefId, CardDefinition> cardDefinitions) {
        PendingDecisionDto pendingDecision = runtimePlayer == null ? null : runtimePlayer.pendingDecision();
        String pendingType = pendingDecision == null ? null : pendingDecision.type();
        String commandType;
        if (pendingType == null || pendingType.isBlank()) {
            commandType = null;
        } else {
            commandType = switch (pendingType) {
                case "DISCARD_TO_HAND_LIMIT" -> "DISCARD_TO_HAND_LIMIT";
                case "SEARCH_PICK" -> "SEARCH_PICK";
                case "LAST_WORDS" -> "LAST_WORDS";
                case "INITIATIVE_TIE_ORDER" -> "RESOLVE_INITIATIVE_TIE";
                default -> null;
            };
        }
        boolean supported = commandType != null;
        boolean enabled = hasPlayerToken && hasPendingDecision && supported;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("expectedVersion", state.version());
        payload.put("playerId", runtimePlayerId == null ? "" : runtimePlayerId);
        payload.put("type", commandType == null ? pendingType : commandType);
        payload.put("discardIds", List.of());
        payload.put("selectedIds", List.of());
        payload.put("orderedActorKeys", List.of());
        payload.put("choiceId", "");
        if (pendingDecision != null && pendingDecision.groupIndex() != null) {
            payload.put("tieGroupIndex", pendingDecision.groupIndex());
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("kind", "pendingDecision");
        metadata.put("note", "Server-calculated pending decision support and input schema.");
        metadata.put("supported", supported);
        metadata.put("unsupportedReason", pendingUnsupportedReason(pendingType));
        metadata.put("pendingDecisionType", pendingType);
        metadata.put("schema", pendingDecisionSchema(pendingDecision, state, cardDefinitions));
        metadata.put("blocked", !enabled);

        return playerCommandAction(
                state.sessionCode(),
                "combat.resolvePending",
                "Resolve pending decision",
                enabled,
                !hasPlayerToken
                        ? playerTokenRequiredReason("resolve the pending decision")
                        : !hasPendingDecision
                        ? pendingDecisionRequiredReason()
                        : supported
                        ? null
                        : unsupportedPendingReason(pendingType),
                metadata,
                payload
        );
    }

    private ScreenActionDto playerCommandAction(String sessionCode,
                                                String id,
                                                String label,
                                                boolean enabled,
                                                DisabledReasonDto disabledReason,
                                                Map<String, Object> metadata,
                                                Map<String, Object> payloadTemplate) {
        return ScreenActionDto.of(
                id,
                label,
                "POST",
                "/api/screens/sessions/" + sessionCode + "/combat/actions/" + id,
                ScreenActionAuth.PLAYER_TOKEN,
                enabled,
                disabledReason,
                payloadTemplate,
                metadata
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

    private Map<String, Object> playCardSourceOption(String instanceId,
                                                     SessionStateDto state) {
        CombatScreenResponse.CardView sourceCard = cardView(instanceId, state, cardService.asMap());
        RequirementMetadata requirement = requirementMetadataForInstance(instanceId, state);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("instanceId", instanceId);
        metadata.put("title", sourceCard == null ? instanceId : sourceCard.title());
        metadata.put("sourceCard", sourceCard);
        metadata.put("requirementView", requirement.view());
        metadata.put("supported", requirement.supported());
        metadata.put("unsupportedReason", requirement.unsupportedReason());
        return metadata;
    }

    private RequirementMetadata requirementMetadataForInstance(String instanceId,
                                                               SessionStateDto state) {
        if (instanceId == null || instanceId.isBlank()) {
            return RequirementMetadata.empty(null);
        }
        var instance = state.cards().get(instanceId);
        if (instance == null || instance.defId() == null || instance.defId().isBlank()) {
            return RequirementMetadata.empty("The selected source card could not be resolved.");
        }
        CardPlaySpec playSpec = cardService.playSpec(new CardDefId(instance.defId()));
        CombatScreenResponse.CardView sourceCard = cardView(instanceId, state, cardService.asMap());
        String sourceLabel = sourceCard == null ? instanceId : sourceCard.title();
        return requirementMetadata(playSpec, sourceLabel, state, instanceId, ownerPlayerIdForSource(instanceId, state));
    }

    private RequirementMetadata requirementMetadata(CardPlaySpec playSpec,
                                                    String sourceLabel,
                                                    SessionStateDto state,
                                                    String sourceInstanceId,
                                                    String sourceOwnerPlayerId) {
        Map<String, Object> view = requirementView(playSpec, sourceLabel, state, sourceInstanceId, sourceOwnerPlayerId);
        ChoiceRequirement choiceRequirement = firstRequirement(playSpec, ChoiceRequirement.class);
        String unsupportedReason = choiceRequirement == null
                ? null
                : sourceLabel + " has a choice-based follow-up that is not supported in this combat step yet.";
        return new RequirementMetadata(view, unsupportedReason == null, unsupportedReason);
    }

    private Map<String, Object> requirementView(CardPlaySpec playSpec,
                                                String sourceLabel,
                                                SessionStateDto state,
                                                String sourceInstanceId,
                                                String sourceOwnerPlayerId) {
        DiscardFromHandRequirement discardRequirement = firstRequirement(playSpec, DiscardFromHandRequirement.class);
        SelectBoardObjectsRequirement boardObjectRequirement = firstRequirement(playSpec, SelectBoardObjectsRequirement.class);
        SelectFieldCardsRequirement selectedIdsRequirement = firstRequirement(playSpec, SelectFieldCardsRequirement.class);
        ChoiceRequirement choiceRequirement = firstRequirement(playSpec, ChoiceRequirement.class);
        String boardObjectSummary = describeBoardObjectRequirement(boardObjectRequirement);
        Map<String, Object> boardObjectRequirementView = boardObjectRequirementView(boardObjectRequirement);
        Map<String, Object> boardObjectSelectionHints = boardObjectSelectionHints(
                boardObjectRequirement,
                state,
                sourceInstanceId,
                sourceOwnerPlayerId
        );
        boolean fieldSelectionHandledByBoardObjects = boardObjectRequirement != null && isFieldCardOnly(boardObjectRequirement);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("sourceLabel", sourceLabel);
        view.put("targetSummary", boardObjectRequirement != null && !fieldSelectionHandledByBoardObjects
                ? boardObjectSummary
                : describeTargetRequirement(playSpec));
        view.put("discardSummary", discardRequirement == null
                ? "No extra hand discard required"
                : "Select " + discardRequirement.count() + " hand discard" + (discardRequirement.count() > 1 ? "s" : "")
                + (discardRequirement.excludeSourceCard() ? " excluding the source card" : ""));
        view.put("selectedIdsSummary", fieldSelectionHandledByBoardObjects
                ? boardObjectSummary
                : selectedIdsRequirement == null
                ? "No extra field selection required"
                : "Select " + selectedIdsRequirement.minSelections() + "-" + selectedIdsRequirement.maxSelections() + " field card ids");
        view.put("choiceSummary", choiceRequirement == null
                ? "No explicit choice requirement"
                : choiceRequirement.label() + " (" + choiceRequirement.options().stream().map(option -> option.label()).toList() + ")");
        view.put("boardObjectSummary", boardObjectRequirement == null ? "No board-object selection requirement" : boardObjectSummary);
        view.put("targetRule", Map.of(
                "target", playSpec.target().target().name(),
                "requiredSelection", playSpec.target().requiredSelection()
        ));
        view.put("discardRequirement", discardRequirement == null ? null : Map.of(
                "count", discardRequirement.count(),
                "excludeSourceCard", discardRequirement.excludeSourceCard(),
                "filter", discardRequirement.filter().name()
        ));
        view.put("selectedIdsRequirement", selectedIdsRequirement == null || fieldSelectionHandledByBoardObjects ? null : Map.of(
                "minSelections", selectedIdsRequirement.minSelections(),
                "maxSelections", selectedIdsRequirement.maxSelections(),
                "scope", selectedIdsRequirement.scope().name(),
                "filter", selectedIdsRequirement.filter().name(),
                "excludeSourceCard", selectedIdsRequirement.excludeSourceCard()
        ));
        view.put("boardObjectRequirement", boardObjectRequirementView);
        view.put("boardObjectSelectionHints", boardObjectSelectionHints);
        view.put("pendingChoiceSchema", choiceRequirement == null ? null : Map.of(
                "id", choiceRequirement.id(),
                "label", choiceRequirement.label(),
                "minSelections", choiceRequirement.minSelections(),
                "maxSelections", choiceRequirement.maxSelections(),
                "options", choiceRequirement.options().stream()
                        .map(option -> Map.of(
                                "id", option.id(),
                                "label", option.label(),
                                "description", option.description() == null ? "" : option.description()
                        ))
                        .toList()
        ));
        view.put("unsupportedReason", choiceRequirement == null
                ? null
                : sourceLabel + " has a choice-based follow-up that is not supported in this combat step yet.");
        return view;
    }

    private String describeBoardObjectRequirement(SelectBoardObjectsRequirement requirement) {
        if (requirement == null) {
            return "No board-object selection requirement";
        }

        String countSummary = describeSelectionRange(requirement.minSelections(), requirement.maxSelections());
        String filterPrefix = requirement.filter() == BoardObjectFilter.INSTALLED_ONLY ? "installed " : "";
        String objectLabel = describeBoardObjectKinds(requirement.kinds(), requirement.relation());
        String excludeSource = requirement.excludeSourceCard() ? " excluding the source card" : "";
        return countSummary + " " + filterPrefix + objectLabel + excludeSource;
    }

    private String describeTargetRequirement(CardPlaySpec playSpec) {
        if (playSpec == null || !playSpec.target().requiredSelection() || playSpec.target().target() == com.example.dueltower.engine.model.Target.NONE) {
            return "No manual target required";
        }

        return switch (playSpec.target().target()) {
            case ENEMY_ONE -> "Select exactly one enemy or summon target";
            case ALLY_ONE -> "Select exactly one ally player or summon target";
            case ANY_ONE -> "Select exactly one target";
            case SELF -> "Self-targeted automatically";
            case ENEMY_ALL, ENEMY_SIDE -> "Enemy-side target is resolved automatically";
            case ALLY_ALL, ALLY_SIDE -> "Ally-side target is resolved automatically";
            default -> "Target rule: " + playSpec.target().target().name();
        };
    }

    private Map<String, Object> boardObjectRequirementView(SelectBoardObjectsRequirement requirement) {
        if (requirement == null) {
            return null;
        }
        return Map.of(
                "minSelections", requirement.minSelections(),
                "maxSelections", requirement.maxSelections(),
                "kinds", requirement.kinds().stream().map(BoardObjectKind::name).toList(),
                "relation", requirement.relation().name(),
                "filter", requirement.filter().name(),
                "excludeSourceCard", requirement.excludeSourceCard()
        );
    }

    private Map<String, Object> boardObjectSelectionHints(SelectBoardObjectsRequirement requirement,
                                                          SessionStateDto state,
                                                          String sourceInstanceId,
                                                          String sourceOwnerPlayerId) {
        if (requirement == null || state == null) {
            return null;
        }

        int candidateCount = countBoardObjectCandidates(requirement, state, sourceInstanceId, sourceOwnerPlayerId);
        int maxAllowed = Math.min(requirement.maxSelections(), candidateCount);
        List<Integer> allowedCounts = maxAllowed < requirement.minSelections()
                ? List.of()
                : IntStream.rangeClosed(requirement.minSelections(), maxAllowed).boxed().toList();

        Map<String, Object> hints = new LinkedHashMap<>();
        hints.put("candidateCount", candidateCount);
        hints.put("allowedCounts", allowedCounts);
        hints.put("skipCountChoice", allowedCounts.size() <= 1);
        return hints;
    }

    private int countBoardObjectCandidates(SelectBoardObjectsRequirement requirement,
                                           SessionStateDto state,
                                           String sourceInstanceId,
                                           String sourceOwnerPlayerId) {
        int total = 0;
        if (requirement.kinds().contains(BoardObjectKind.CHARACTER)) {
            total += countCharacterCandidates(requirement.relation(), state, sourceOwnerPlayerId);
        }
        if (requirement.kinds().contains(BoardObjectKind.SUMMON)) {
            total += countSummonCandidates(requirement.relation(), state, sourceOwnerPlayerId);
        }
        if (requirement.kinds().contains(BoardObjectKind.FIELD_CARD)) {
            total += countFieldCardCandidates(requirement.relation(), state, sourceOwnerPlayerId);
        }
        if (requirement.excludeSourceCard() && sourceInstanceId != null && requirement.kinds().contains(BoardObjectKind.FIELD_CARD)) {
            boolean sourceOnField = state.players().values().stream().anyMatch(player -> player.field().contains(sourceInstanceId));
            if (sourceOnField) {
                total = Math.max(0, total - 1);
            }
        }
        return total;
    }

    private int countCharacterCandidates(BoardObjectRelation relation,
                                         SessionStateDto state,
                                         String sourceOwnerPlayerId) {
        int playerCount = state.players() == null ? 0 : state.players().size();
        int enemyCount = state.combat() == null || state.combat().enemies() == null ? 0 : state.combat().enemies().size();
        return switch (relation) {
            case ALLY -> sourceOwnerPlayerId == null || !state.players().containsKey(sourceOwnerPlayerId) ? 0 : playerCount;
            case HOSTILE -> enemyCount;
            case ANY -> playerCount + enemyCount;
        };
    }

    private int countSummonCandidates(BoardObjectRelation relation,
                                      SessionStateDto state,
                                      String sourceOwnerPlayerId) {
        if (state.combat() == null || state.combat().summons() == null) {
            return 0;
        }
        return switch (relation) {
            case ALLY -> (int) state.combat().summons().stream()
                    .filter(summon -> Objects.equals(summon.owner(), sourceOwnerPlayerId))
                    .count();
            case HOSTILE -> (int) state.combat().summons().stream()
                    .filter(summon -> !Objects.equals(summon.owner(), sourceOwnerPlayerId))
                    .count();
            case ANY -> state.combat().summons().size();
        };
    }

    private int countFieldCardCandidates(BoardObjectRelation relation,
                                         SessionStateDto state,
                                         String sourceOwnerPlayerId) {
        if (state.players() == null) {
            return 0;
        }
        return switch (relation) {
            case ALLY -> sourceOwnerPlayerId == null || !state.players().containsKey(sourceOwnerPlayerId)
                    ? 0
                    : state.players().get(sourceOwnerPlayerId).field().size();
            case HOSTILE -> state.players().values().stream()
                    .filter(player -> !Objects.equals(player.playerId(), sourceOwnerPlayerId))
                    .mapToInt(player -> player.field().size())
                    .sum();
            case ANY -> state.players().values().stream().mapToInt(player -> player.field().size()).sum();
        };
    }

    private String ownerPlayerIdForSource(String sourceInstanceId,
                                          SessionStateDto state) {
        if (sourceInstanceId == null || sourceInstanceId.isBlank() || state.players() == null) {
            return null;
        }
        return state.players().values().stream()
                .filter(player -> player.hand().contains(sourceInstanceId)
                        || player.field().contains(sourceInstanceId)
                        || player.grave().contains(sourceInstanceId)
                        || player.excluded().contains(sourceInstanceId)
                        || Objects.equals(player.exCard(), sourceInstanceId))
                .map(PlayerStateDto::playerId)
                .findFirst()
                .orElse(null);
    }

    private boolean isFieldCardOnly(SelectBoardObjectsRequirement requirement) {
        return requirement != null
                && requirement.kinds().size() == 1
                && requirement.kinds().contains(BoardObjectKind.FIELD_CARD);
    }

    private String describeSelectionRange(int minSelections,
                                          int maxSelections) {
        if (minSelections == maxSelections) {
            return minSelections == 1
                    ? "Select exactly one"
                    : "Select exactly " + minSelections;
        }
        if (minSelections == 0) {
            return "Select up to " + maxSelections;
        }
        return "Select " + minSelections + "-" + maxSelections;
    }

    private String describeBoardObjectKinds(List<BoardObjectKind> kinds,
                                            BoardObjectRelation relation) {
        List<String> labels = kinds.stream()
                .map(kind -> describeBoardObjectKind(kind, relation))
                .toList();
        if (labels.size() == 1) {
            return labels.get(0);
        }
        if (labels.size() == 2) {
            return labels.get(0) + " or " + labels.get(1);
        }
        return String.join(", ", labels.subList(0, labels.size() - 1)) + ", or " + labels.get(labels.size() - 1);
    }

    private String describeBoardObjectKind(BoardObjectKind kind,
                                           BoardObjectRelation relation) {
        return switch (kind) {
            case CHARACTER -> switch (relation) {
                case ALLY -> "ally character";
                case HOSTILE -> "hostile character";
                case ANY -> "character";
            };
            case SUMMON -> switch (relation) {
                case ALLY -> "ally summon";
                case HOSTILE -> "hostile summon";
                case ANY -> "summon";
            };
            case FIELD_CARD -> switch (relation) {
                case ALLY -> "ally field card";
                case HOSTILE -> "hostile field card";
                case ANY -> "field card";
            };
        };
    }

    private <T extends ExtraPlayRequirement> T firstRequirement(CardPlaySpec playSpec,
                                                                Class<T> requirementType) {
        if (playSpec == null || playSpec.extraRequirements() == null) {
            return null;
        }
        return playSpec.extraRequirements().stream()
                .filter(requirementType::isInstance)
                .map(requirementType::cast)
                .findFirst()
                .orElse(null);
    }

    private Map<String, Object> pendingDecisionSchema(PendingDecisionDto pendingDecision,
                                                      SessionStateDto state,
                                                      Map<CardDefId, CardDefinition> cardDefinitions) {
        if (pendingDecision == null || pendingDecision.type() == null || pendingDecision.type().isBlank()) {
            return null;
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", pendingDecision.type());
        schema.put("reason", pendingDecision.reason());
        switch (pendingDecision.type()) {
            case "DISCARD_TO_HAND_LIMIT" -> {
                schema.put("discardCount", pendingDecision.limit());
                schema.put("selectedIdsField", "discardIds");
            }
            case "SEARCH_PICK" -> {
                schema.put("pickCount", pendingDecision.pickCount());
                schema.put("candidateIds", pendingDecision.candidateIds());
                schema.put("destination", pendingDecision.destination());
                schema.put("shuffleAfterPick", pendingDecision.shuffleAfterPick());
                schema.put("selectedIdsField", "selectedIds");
            }
            case "LAST_WORDS" -> {
                schema.put("pickCount", 1);
                schema.put("candidateIds", pendingDecision.candidateIds());
                schema.put("candidateCards", cardViews(pendingDecision.candidateIds(), state, cardDefinitions));
                schema.put("canSkip", pendingDecision.canSkip());
                schema.put("selectedIdsField", "selectedIds");
            }
            case "INITIATIVE_TIE_ORDER" -> {
                schema.put("groupIndex", pendingDecision.groupIndex());
                schema.put("actorKeys", pendingDecision.actorKeys());
                schema.put("selectedIdsField", "orderedActorKeys");
            }
            default -> {
                schema.put("candidateIds", pendingDecision.candidateIds());
                schema.put("actorKeys", pendingDecision.actorKeys());
            }
        }
        return schema;
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

    private DisabledReasonDto unsupportedRequirementReason(String reason) {
        return new DisabledReasonDto(
                "ACTION_REQUIREMENT_UNSUPPORTED",
                "RULE",
                nullSafe(reason, "This action requirement is not supported in this combat step."),
                reason,
                null,
                null,
                null
        );
    }

    private DisabledReasonDto pendingDecisionRequiredReason() {
        return new DisabledReasonDto(
                "PENDING_DECISION_REQUIRED",
                "RULE",
                "A pending decision is required before this action becomes available.",
                "runtime player has no pending decision",
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

    private String pendingUnsupportedReason(String pendingType) {
        if (pendingType == null || pendingType.isBlank()) {
            return "Pending decision type is missing.";
        }
        return switch (pendingType) {
            case "DISCARD_TO_HAND_LIMIT", "SEARCH_PICK", "LAST_WORDS", "INITIATIVE_TIE_ORDER" -> null;
            default -> pendingType + " is not supported in this combat step yet.";
        };
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

    private record RequirementMetadata(
            Map<String, Object> view,
            boolean supported,
            String unsupportedReason
    ) {
        private static RequirementMetadata empty(String unsupportedReason) {
            return new RequirementMetadata(null, unsupportedReason == null, unsupportedReason);
        }
    }
}
