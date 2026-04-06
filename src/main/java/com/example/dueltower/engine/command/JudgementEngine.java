package com.example.dueltower.engine.command;

import com.example.dueltower.content.cardmodifier.cmdb.CardModifierIds;
import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.PlayerState;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;

final class JudgementEngine {
    static final int MAX_ABILITY = 20;
    static final String WEAKNESS_STATUS_PREFIX = "judgement.weakness.";
    private static final List<String> WEAKNESS_POOL = List.of(
            CardModifierIds.WEAKENED_COST_PLUS_ONE,
            CardModifierIds.WEAKENED_SELF_DAMAGE_10,
            CardModifierIds.WEAKENED_FINAL_HALF,
            CardModifierIds.WEAKENED_RANDOM_ENEMY_ONE,
            CardModifierIds.WEAKENED_DISCARD_ONE_SKILL
    );

    private final DiceRoller diceRoller;
    private final WeaknessPicker weaknessPicker;

    JudgementEngine() {
        this(new RandomDiceRoller(), new RandomWeaknessPicker());
    }

    JudgementEngine(DiceRoller diceRoller, WeaknessPicker weaknessPicker) {
        this.diceRoller = Objects.requireNonNull(diceRoller, "diceRoller");
        this.weaknessPicker = Objects.requireNonNull(weaknessPicker, "weaknessPicker");
    }

    static List<String> judgementAbilityChoices() {
        return List.of(Ability.BODY.id, Ability.SKILL.id, Ability.SENSE.id, Ability.WILL.id);
    }

    Result resolve(PlayerState player,
                   Ids.PlayerId playerId,
                   String abilityId,
                   long seed,
                   long version) {
        Ability ability = Ability.fromId(abilityId);
        int abilityBefore = ability.read(player);
        if (abilityBefore >= MAX_ABILITY) {
            throw new IllegalArgumentException("ability already maxed: " + ability.id);
        }

        int roll = diceRoller.rollD20(seed, version, playerId, ability.id);
        boolean success = roll <= abilityBefore;
        if (success) {
            return new Result(ability.id, ability.label, roll, abilityBefore, true, false, null, ability.id, abilityBefore);
        }

        String weakness = weaknessPicker.pick(WEAKNESS_POOL, seed, version, playerId, ability.id);
        player.statusAdd(WEAKNESS_STATUS_PREFIX + weakness, 1);
        int abilityAfter = Math.min(MAX_ABILITY, abilityBefore + 1);
        ability.write(player, abilityAfter);
        return new Result(ability.id, ability.label, roll, abilityBefore, false, true, weakness, ability.id, abilityAfter);
    }

    interface DiceRoller {
        int rollD20(long seed, long version, Ids.PlayerId playerId, String abilityId);
    }

    interface WeaknessPicker {
        String pick(List<String> pool, long seed, long version, Ids.PlayerId playerId, String abilityId);
    }

    record Result(
            String usedAbility,
            String usedAbilityLabel,
            int roll,
            int abilityBefore,
            boolean success,
            boolean memoryAccepted,
            String grantedWeakness,
            String increasedAbility,
            int increasedAbilityValue
    ) {}

    private enum Ability {
        BODY("BODY", "신체") {
            @Override
            int read(PlayerState player) { return player.body(); }
            @Override
            void write(PlayerState player, int value) { player.body(value); }
        },
        SKILL("SKILL", "기술") {
            @Override
            int read(PlayerState player) { return player.skill(); }
            @Override
            void write(PlayerState player, int value) { player.skill(value); }
        },
        SENSE("SENSE", "감각") {
            @Override
            int read(PlayerState player) { return player.sense(); }
            @Override
            void write(PlayerState player, int value) { player.sense(value); }
        },
        WILL("WILL", "의지") {
            @Override
            int read(PlayerState player) { return player.will(); }
            @Override
            void write(PlayerState player, int value) { player.will(value); }
        };

        private final String id;
        private final String label;

        Ability(String id, String label) {
            this.id = id;
            this.label = label;
        }

        abstract int read(PlayerState player);
        abstract void write(PlayerState player, int value);

        static Ability fromId(String raw) {
            if (raw == null || raw.isBlank()) {
                throw new IllegalArgumentException("ability is required");
            }
            String normalized = raw.trim().toUpperCase(Locale.ROOT);
            for (Ability ability : values()) {
                if (ability.id.equals(normalized)) {
                    return ability;
                }
            }
            throw new IllegalArgumentException("invalid judgement choice");
        }
    }

    private static final class RandomDiceRoller implements DiceRoller {
        @Override
        public int rollD20(long seed, long version, Ids.PlayerId playerId, String abilityId) {
            long playerHash = (playerId == null || playerId.value() == null) ? 0L : playerId.value().hashCode();
            long abilityHash = (abilityId == null) ? 0L : abilityId.hashCode();
            Random random = new Random(seed ^ (version * 1109L) ^ (playerHash * 31L) ^ abilityHash);
            return random.nextInt(20) + 1;
        }
    }

    private static final class RandomWeaknessPicker implements WeaknessPicker {
        @Override
        public String pick(List<String> pool, long seed, long version, Ids.PlayerId playerId, String abilityId) {
            if (pool == null || pool.isEmpty()) {
                throw new IllegalArgumentException("weakness pool is empty");
            }
            long playerHash = (playerId == null || playerId.value() == null) ? 0L : playerId.value().hashCode();
            long abilityHash = (abilityId == null) ? 0L : abilityId.hashCode();
            Random random = new Random((seed * 37L) ^ (version * 2029L) ^ playerHash ^ (abilityHash * 13L));
            return pool.get(random.nextInt(pool.size()));
        }
    }
}
