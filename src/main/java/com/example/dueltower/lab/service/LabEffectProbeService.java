package com.example.dueltower.lab.service;

import com.example.dueltower.config.GameRules;
import com.example.dueltower.config.RewardTableConfig;
import com.example.dueltower.content.card.service.CardService;
import com.example.dueltower.content.cardmodifier.service.CardModifierService;
import com.example.dueltower.content.equip.service.EquipService;
import com.example.dueltower.content.enemy.service.EnemyService;
import com.example.dueltower.content.item.service.ItemService;
import com.example.dueltower.content.keyword.service.KeywordService;
import com.example.dueltower.content.passive.service.PassiveService;
import com.example.dueltower.content.status.service.StatusService;
import com.example.dueltower.engine.config.EncounterTables;
import com.example.dueltower.engine.config.RunConfigs;
import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.core.effect.card.CardEffect;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.CardDefinition;
import com.example.dueltower.engine.model.CardInstance;
import com.example.dueltower.engine.model.CardType;
import com.example.dueltower.engine.model.EnemyState;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.Ids.CardDefId;
import com.example.dueltower.engine.model.Ids.CardInstId;
import com.example.dueltower.engine.model.Ids.EnemyId;
import com.example.dueltower.engine.model.Ids.PlayerId;
import com.example.dueltower.engine.model.Ids.SessionId;
import com.example.dueltower.engine.model.Ids.SummonInstId;
import com.example.dueltower.engine.model.PlayerState;
import com.example.dueltower.engine.model.StatusDefinition;
import com.example.dueltower.engine.model.SummonState;
import com.example.dueltower.engine.model.TargetRef;
import com.example.dueltower.engine.model.TargetSelection;
import com.example.dueltower.engine.model.Zone;
import com.example.dueltower.lab.dto.LabEffectProbeRequest;
import com.example.dueltower.lab.dto.LabEffectProbeResponse;
import com.example.dueltower.lab.dto.LabProbeActorDto;
import com.example.dueltower.lab.dto.LabProbeCardOptionDto;
import com.example.dueltower.lab.dto.LabProbeChangesDto;
import com.example.dueltower.lab.dto.LabProbeEventDto;
import com.example.dueltower.lab.dto.LabProbeSelectionDto;
import com.example.dueltower.lab.dto.LabProbeSnapshotDto;
import com.example.dueltower.lab.dto.LabProbeTargetDto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class LabEffectProbeService {

    private static final PlayerId ACTOR_ID = new PlayerId("lab_actor");
    private static final String DEFAULT_ENEMY_ID = "dummy_enemy";
    private static final List<String> NOTES = List.of(
            "Lab Probe does not check AP cost, hand ownership, zone movement, deck state, or turn flow.",
            "Attack/heal power is injected through a probe stat source.",
            "Lab Probe runs CardEffect.validate and, when valid, CardEffect.resolve directly."
    );

    private final CardService cardService;
    private final StatusService statusService;
    private final KeywordService keywordService;
    private final PassiveService passiveService;
    private final CardModifierService cardModifierService;
    private final ItemService itemService;
    private final EquipService equipService;
    private final EnemyService enemyService;
    private final GameRules gameRules;
    private final RewardTableConfig rewardTableConfig;
    private final RunConfigs runConfigs;
    private final EncounterTables encounterTables;

    public LabEffectProbeService(
            CardService cardService,
            StatusService statusService,
            KeywordService keywordService,
            PassiveService passiveService,
            CardModifierService cardModifierService,
            ItemService itemService,
            EquipService equipService,
            EnemyService enemyService,
            GameRules gameRules,
            RewardTableConfig rewardTableConfig,
            RunConfigs runConfigs,
            EncounterTables encounterTables
    ) {
        this.cardService = cardService;
        this.statusService = statusService;
        this.keywordService = keywordService;
        this.passiveService = passiveService;
        this.cardModifierService = cardModifierService;
        this.itemService = itemService;
        this.equipService = equipService;
        this.enemyService = enemyService;
        this.gameRules = gameRules;
        this.rewardTableConfig = rewardTableConfig;
        this.runConfigs = runConfigs;
        this.encounterTables = encounterTables;
    }

    public List<LabProbeCardOptionDto> cards() {
        Map<CardDefId, CardEffect> effects = cardService.effectsMap();
        return cardService.list(CardType.SKILL).stream()
                .filter(card -> effects.containsKey(card.id()))
                .map(this::toCardOption)
                .toList();
    }

    public LabEffectProbeResponse probe(LabEffectProbeRequest request) {
        if (request == null) {
            throw badRequest("request body is required");
        }

        CardDefId cardDefId = new CardDefId(requireText(request.cardId(), "cardId is required"));
        CardDefinition card = requireCard(cardDefId);
        CardEffect effect = requireEffect(cardDefId);

        ProbeInput input = normalizeInput(request);
        EngineContext engineContext = engineContext();
        GameState state = new GameState(
                new SessionId(UUID.randomUUID()),
                request.seed() == null ? ThreadLocalRandom.current().nextLong() : request.seed(),
                runConfigs.runConfig()
        );

        PlayerState actor = createPlayer(ACTOR_ID, input.actor());
        state.players().put(ACTOR_ID, actor);

        TargetRef targetRef = null;
        if (input.target() != null) {
            targetRef = createTarget(state, input.target());
        }

        CardInstId sourceCardId = new CardInstId(UUID.randomUUID());
        state.cardInstances().put(sourceCardId, new CardInstance(sourceCardId, cardDefId, ACTOR_ID, Zone.HAND));

        SummonInstId statSourceSummonId = new SummonInstId(UUID.randomUUID());
        state.summons().put(statSourceSummonId, new SummonState(
                statSourceSummonId,
                ACTOR_ID,
                sourceCardId,
                1,
                1,
                input.actor().attackPower(),
                input.actor().healPower(),
                0,
                false
        ));

        TargetSelection selection = toSelection(input.selection(), targetRef);
        List<GameEvent> events = new ArrayList<>();
        EffectContext effectContext = new EffectContext(
                state,
                engineContext,
                ACTOR_ID,
                sourceCardId,
                selection,
                toCardInstIds(input.selection().discardIds(), "discardIds"),
                toCardInstIds(input.selection().selectedIds(), "selectedIds"),
                events,
                statSourceSummonId,
                null,
                input.selection().choiceId()
        );

        List<TargetRef> snapshotTargets = snapshotTargets(targetRef);
        LabProbeSnapshotDto before = snapshot(state, snapshotTargets);
        List<String> validationErrors = validate(effect, effectContext);
        boolean valid = validationErrors.isEmpty();
        boolean resolved = false;

        if (valid && !input.validateOnly()) {
            resolve(effect, effectContext);
            resolved = true;
        }

        LabProbeSnapshotDto after = snapshot(state, snapshotTargets);
        return new LabEffectProbeResponse(
                card.id().value(),
                card.name(),
                valid,
                validationErrors,
                resolved,
                before,
                after,
                changes(before, after),
                events.stream().map(this::toEvent).toList(),
                NOTES
        );
    }

    private LabProbeCardOptionDto toCardOption(CardDefinition card) {
        List<String> tags = new ArrayList<>();
        tags.add(card.type().name());
        card.keywords().keySet().stream().sorted().forEach(tags::add);
        return new LabProbeCardOptionDto(
                card.id().value(),
                card.name(),
                card.type().name(),
                card.cost(),
                card.description(),
                List.copyOf(tags)
        );
    }

    private CardDefinition requireCard(CardDefId cardDefId) {
        CardDefinition definition = cardService.asMap().get(cardDefId);
        if (definition == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "card not found: " + cardDefId.value());
        }
        return definition;
    }

    private CardEffect requireEffect(CardDefId cardDefId) {
        CardEffect effect = cardService.effectsMap().get(cardDefId);
        if (effect == null || requireCard(cardDefId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "card not found: " + cardDefId.value());
        }
        return effect;
    }

    private ProbeInput normalizeInput(LabEffectProbeRequest request) {
        LabProbeActorDto actor = normalizeActor(request.actor());
        LabProbeSelectionDto selection = normalizeSelection(request.selection());
        LabProbeTargetDto target = normalizeTarget(request.target(), false);

        if (selection.targets() != null && selection.targets().size() > 1) {
            throw badRequest("selection.targets supports at most 1 target in Lab Probe MVP");
        }
        if (target != null && selection.targets() != null && !selection.targets().isEmpty()) {
            LabProbeTargetDto selected = normalizeTarget(selection.targets().get(0), true);
            if (!sameTarget(target, selected)) {
                throw badRequest("selection target must match target in Lab Probe MVP");
            }
        }

        return new ProbeInput(
                actor,
                target,
                selection,
                Boolean.TRUE.equals(request.validateOnly())
        );
    }

    private LabProbeActorDto normalizeActor(LabProbeActorDto actor) {
        if (actor == null) {
            throw badRequest("actor is required");
        }
        int attackPower = requireMin(actor.attackPower(), 0, "actor.attackPower");
        int healPower = requireMin(actor.healPower(), 0, "actor.healPower");
        int maxHp = requireMin(actor.maxHp(), 1, "actor.maxHp");
        int hp = requireMin(actor.hp(), 1, "actor.hp");
        if (hp > maxHp) {
            throw badRequest("actor.hp must be less than or equal to actor.maxHp");
        }
        return new LabProbeActorDto(attackPower, healPower, hp, maxHp, normalizeStatuses(actor.statuses()));
    }

    private LabProbeTargetDto normalizeTarget(LabProbeTargetDto target, boolean refOnly) {
        if (target == null) {
            return null;
        }
        String kind = normalizeKind(target.kind());
        String id = target.id() == null || target.id().isBlank()
                ? defaultTargetId(kind)
                : target.id().trim();
        if (refOnly) {
            return new LabProbeTargetDto(kind, id, null, null, Map.of());
        }
        int maxHp = requireMin(target.maxHp(), 1, "target.maxHp");
        int hp = requireMin(target.hp(), 1, "target.hp");
        if (hp > maxHp) {
            throw badRequest("target.hp must be less than or equal to target.maxHp");
        }
        return new LabProbeTargetDto(kind, id, hp, maxHp, normalizeStatuses(target.statuses()));
    }

    private LabProbeSelectionDto normalizeSelection(LabProbeSelectionDto selection) {
        if (selection == null) {
            return new LabProbeSelectionDto(null, List.of(), List.of(), null);
        }
        List<LabProbeTargetDto> targets = selection.targets() == null
                ? null
                : selection.targets().stream().map(target -> normalizeTarget(target, true)).toList();
        return new LabProbeSelectionDto(
                targets,
                normalizeStringList(selection.discardIds()),
                normalizeStringList(selection.selectedIds()),
                selection.choiceId() == null || selection.choiceId().isBlank() ? null : selection.choiceId().trim()
        );
    }

    private PlayerState createPlayer(PlayerId playerId, LabProbeActorDto source) {
        PlayerState player = new PlayerState(playerId);
        player.overrideVitals(source.hp(), source.maxHp());
        applyStatuses(player.statusValues(), source.statuses());
        return player;
    }

    private PlayerState createTargetPlayer(PlayerId playerId, LabProbeTargetDto source) {
        PlayerState player = new PlayerState(playerId);
        player.overrideVitals(source.hp(), source.maxHp());
        applyStatuses(player.statusValues(), source.statuses());
        return player;
    }

    private TargetRef createTarget(GameState state, LabProbeTargetDto target) {
        return switch (target.kind()) {
            case "ENEMY" -> {
                EnemyId enemyId = new EnemyId(target.id());
                EnemyState enemy = new EnemyState(enemyId, target.maxHp());
                enemy.hp(target.hp());
                enemy.name(target.id());
                applyStatuses(enemy.statusValues(), target.statuses());
                state.enemies().put(enemyId, enemy);
                yield TargetRef.ofEnemy(enemyId);
            }
            case "PLAYER" -> {
                PlayerId playerId = new PlayerId(target.id());
                if (!ACTOR_ID.equals(playerId)) {
                    state.players().put(playerId, createTargetPlayer(playerId, target));
                }
                yield TargetRef.ofPlayer(playerId);
            }
            default -> throw badRequest("unsupported target kind: " + target.kind());
        };
    }

    private TargetSelection toSelection(LabProbeSelectionDto selection, TargetRef defaultTarget) {
        if (selection.targets() == null) {
            return defaultTarget == null ? TargetSelection.empty() : new TargetSelection(List.of(defaultTarget));
        }
        List<TargetRef> targets = selection.targets().stream().map(this::toTargetRef).toList();
        return new TargetSelection(targets);
    }

    private TargetRef toTargetRef(LabProbeTargetDto target) {
        return switch (target.kind()) {
            case "ENEMY" -> TargetRef.ofEnemy(new EnemyId(target.id()));
            case "PLAYER" -> TargetRef.ofPlayer(new PlayerId(target.id()));
            default -> throw badRequest("unsupported target kind: " + target.kind());
        };
    }

    private List<String> validate(CardEffect effect, EffectContext effectContext) {
        try {
            return List.copyOf(effect.validate(effectContext));
        } catch (IllegalArgumentException ex) {
            throw badRequest(ex.getMessage());
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
        }
    }

    private void resolve(CardEffect effect, EffectContext effectContext) {
        try {
            effect.resolve(effectContext);
        } catch (IllegalArgumentException ex) {
            throw badRequest(ex.getMessage());
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
        }
    }

    private LabProbeSnapshotDto snapshot(GameState state, List<TargetRef> targets) {
        PlayerState actor = state.player(ACTOR_ID);
        return new LabProbeSnapshotDto(
                new LabProbeSnapshotDto.Actor(
                        ACTOR_ID.value(),
                        actor.hp(),
                        actor.maxHp(),
                        actor.ap(),
                        copyStatuses(actor.statusValues())
                ),
                targets.stream()
                        .map(target -> snapshotTarget(state, target))
                        .toList()
        );
    }

    private LabProbeSnapshotDto.Target snapshotTarget(GameState state, TargetRef target) {
        if (target instanceof TargetRef.Enemy enemyRef) {
            EnemyState enemy = state.enemy(enemyRef.id());
            if (enemy == null) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "missing enemy: " + enemyRef.id().value());
            }
            return new LabProbeSnapshotDto.Target(
                    "ENEMY",
                    enemy.enemyId().value(),
                    enemy.hp(),
                    enemy.maxHp(),
                    enemy.ap(),
                    copyStatuses(enemy.statusValues())
            );
        }
        if (target instanceof TargetRef.Player playerRef) {
            PlayerState player = state.player(playerRef.id());
            if (player == null) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "missing player: " + playerRef.id().value());
            }
            return new LabProbeSnapshotDto.Target(
                    "PLAYER",
                    player.playerId().value(),
                    player.hp(),
                    player.maxHp(),
                    player.ap(),
                    copyStatuses(player.statusValues())
            );
        }
        throw badRequest("unsupported target kind: SUMMON");
    }

    private LabProbeChangesDto changes(LabProbeSnapshotDto before, LabProbeSnapshotDto after) {
        List<LabProbeChangesDto.TargetChanges> targetChanges = new ArrayList<>();
        for (int i = 0; i < before.targets().size(); i++) {
            LabProbeSnapshotDto.Target beforeTarget = before.targets().get(i);
            LabProbeSnapshotDto.Target afterTarget = after.targets().get(i);
            targetChanges.add(new LabProbeChangesDto.TargetChanges(
                    afterTarget.kind(),
                    afterTarget.id(),
                    afterTarget.hp() - beforeTarget.hp(),
                    statusChanges(beforeTarget.statuses(), afterTarget.statuses()),
                    addedStatuses(beforeTarget.statuses(), afterTarget.statuses()),
                    removedStatuses(beforeTarget.statuses(), afterTarget.statuses()),
                    changedStatuses(beforeTarget.statuses(), afterTarget.statuses())
            ));
        }
        return new LabProbeChangesDto(
                new LabProbeChangesDto.EntityChanges(
                        after.actor().hp() - before.actor().hp(),
                        statusChanges(before.actor().statuses(), after.actor().statuses()),
                        addedStatuses(before.actor().statuses(), after.actor().statuses()),
                        removedStatuses(before.actor().statuses(), after.actor().statuses()),
                        changedStatuses(before.actor().statuses(), after.actor().statuses())
                ),
                List.copyOf(targetChanges)
        );
    }

    private List<LabProbeChangesDto.StatusChange> statusChanges(Map<String, Integer> before, Map<String, Integer> after) {
        LinkedHashSet<String> keys = statusKeys(before, after);
        List<LabProbeChangesDto.StatusChange> changes = new ArrayList<>();
        for (String key : keys) {
            int beforeValue = before.getOrDefault(key, 0);
            int afterValue = after.getOrDefault(key, 0);
            if (beforeValue != afterValue) {
                changes.add(new LabProbeChangesDto.StatusChange(key, beforeValue, afterValue));
            }
        }
        return List.copyOf(changes);
    }

    private List<String> addedStatuses(Map<String, Integer> before, Map<String, Integer> after) {
        return statusKeys(before, after).stream()
                .filter(key -> before.getOrDefault(key, 0) <= 0 && after.getOrDefault(key, 0) > 0)
                .toList();
    }

    private List<String> removedStatuses(Map<String, Integer> before, Map<String, Integer> after) {
        return statusKeys(before, after).stream()
                .filter(key -> before.getOrDefault(key, 0) > 0 && after.getOrDefault(key, 0) <= 0)
                .toList();
    }

    private List<String> changedStatuses(Map<String, Integer> before, Map<String, Integer> after) {
        return statusKeys(before, after).stream()
                .filter(key -> before.getOrDefault(key, 0) > 0 && after.getOrDefault(key, 0) > 0)
                .filter(key -> !before.get(key).equals(after.get(key)))
                .toList();
    }

    private LinkedHashSet<String> statusKeys(Map<String, Integer> before, Map<String, Integer> after) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        before.keySet().stream().sorted().forEach(keys::add);
        after.keySet().stream().sorted().forEach(keys::add);
        return keys;
    }

    private LabProbeEventDto toEvent(GameEvent event) {
        if (event instanceof GameEvent.LogAppended log) {
            return new LabProbeEventDto("LogAppended", log.line(), Map.of());
        }
        if (event instanceof GameEvent.CombatLogAppended log) {
            return new LabProbeEventDto("CombatLogAppended", log.message(), log.data());
        }
        if (event instanceof GameEvent.CardsMoved moved) {
            return new LabProbeEventDto("CardsMoved", moved.playerId() + " " + moved.from() + " -> " + moved.to(), Map.of(
                    "playerId", moved.playerId(),
                    "from", moved.from(),
                    "to", moved.to(),
                    "count", moved.count()
            ));
        }
        if (event instanceof GameEvent.PendingDecisionSet pending) {
            return new LabProbeEventDto("PendingDecisionSet", pending.reason(), Map.of(
                    "playerId", pending.playerId(),
                    "decisionType", pending.type()
            ));
        }
        return new LabProbeEventDto(event.getClass().getSimpleName(), event.toString(), Map.of());
    }

    private EngineContext engineContext() {
        return new EngineContext(
                cardService.asMap(),
                cardService.effectsMap(),
                statusService.defsMap(),
                statusService.effectsMap(),
                keywordService.defsMap(),
                keywordService.effectsMap(),
                passiveService.defsMap(),
                passiveService.effectsMap(),
                cardModifierService.defsMap(),
                cardModifierService.effectsMap(),
                itemService.defsMap(),
                itemService.effectsMap(),
                equipService.defsMap(),
                gameRules,
                rewardTableConfig,
                encounterTables.encounterTableConfig(),
                runConfigs.runConfig(),
                enemyService.defsMap()
        );
    }

    private List<TargetRef> snapshotTargets(TargetRef targetRef) {
        return targetRef == null ? List.of() : List.of(targetRef);
    }

    private void applyStatuses(Map<String, Integer> target, Map<String, Integer> statuses) {
        statuses.forEach((key, value) -> {
            if (value != null && value > 0) {
                target.put(key, value);
            }
        });
    }

    private Map<String, Integer> normalizeStatuses(Map<String, Integer> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> normalized = new LinkedHashMap<>();
        statuses.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    if (entry.getKey() == null || entry.getKey().isBlank()) {
                        return;
                    }
                    Integer value = entry.getValue();
                    if (value == null || value <= 0) {
                        return;
                    }
                    normalized.put(normalizeStatusId(entry.getKey()), value);
                });
        return Map.copyOf(normalized);
    }

    private String normalizeStatusId(String raw) {
        String value = raw.trim();
        Map<String, String> aliases = Map.of(
                "보호", "SHIELD",
                "고통", "PAIN",
                "축복", "Iris201_Status"
        );
        if (aliases.containsKey(value)) {
            return aliases.get(value);
        }
        if (statusService.defsMap().containsKey(value)) {
            return value;
        }
        String upper = value.toUpperCase();
        if (statusService.defsMap().containsKey(upper)) {
            return upper;
        }
        return statusService.defsMap().values().stream()
                .filter(def -> value.equals(def.name()))
                .map(StatusDefinition::id)
                .findFirst()
                .orElse(value);
    }

    private Map<String, Integer> copyStatuses(Map<String, Integer> statuses) {
        return statuses.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
                .sorted(Map.Entry.comparingByKey())
                .collect(LinkedHashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), LinkedHashMap::putAll);
    }

    private List<CardInstId> toCardInstIds(List<String> rawIds, String fieldName) {
        if (rawIds == null || rawIds.isEmpty()) {
            return List.of();
        }
        return rawIds.stream()
                .map(raw -> toCardInstId(raw, fieldName))
                .toList();
    }

    private CardInstId toCardInstId(String raw, String fieldName) {
        String value = requireText(raw, fieldName + " must not contain blank values");
        try {
            return new CardInstId(UUID.fromString(value));
        } catch (IllegalArgumentException ignored) {
            return new CardInstId(UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)));
        }
    }

    private List<String> normalizeStringList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
    }

    private String normalizeKind(String kind) {
        String normalized = requireText(kind, "target.kind is required").toUpperCase();
        if (!normalized.equals("ENEMY") && !normalized.equals("PLAYER")) {
            throw badRequest("unsupported target kind: " + normalized);
        }
        return normalized;
    }

    private String defaultTargetId(String kind) {
        return "PLAYER".equals(kind) ? ACTOR_ID.value() : DEFAULT_ENEMY_ID;
    }

    private boolean sameTarget(LabProbeTargetDto left, LabProbeTargetDto right) {
        return left != null
                && right != null
                && left.kind().equals(right.kind())
                && left.id().equals(right.id());
    }

    private int requireMin(Integer value, int min, String fieldName) {
        if (value == null) {
            throw badRequest(fieldName + " is required");
        }
        if (value < min) {
            throw badRequest(fieldName + " must be >= " + min);
        }
        return value;
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw badRequest(message);
        }
        return value.trim();
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private record ProbeInput(
            LabProbeActorDto actor,
            LabProbeTargetDto target,
            LabProbeSelectionDto selection,
            boolean validateOnly
    ) {
    }
}
