package com.example.dueltower.engine.command;

import com.example.dueltower.content.cardmodifier.cmdb.CardModifierIds;
import com.example.dueltower.engine.model.CardInstance;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.PlayerState;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;

final class JudgementEngine {
    static final int MAX_ABILITY = 20;
    private static final List<String> WEAKNESS_POOL = List.of(
            CardModifierIds.WEAKENED_COST_PLUS_ONE,
            CardModifierIds.WEAKENED_SELF_DAMAGE_10,
            CardModifierIds.WEAKENED_FINAL_HALF,
            CardModifierIds.WEAKENED_RANDOM_ENEMY_ONE,
            CardModifierIds.WEAKENED_DISCARD_ONE_SKILL
    );

    private final DiceRoller diceRoller;
    private final WeaknessPicker weaknessPicker;
    private final OwnedCardPicker ownedCardPicker;

    JudgementEngine() {
        this(new RandomDiceRoller(), new RandomWeaknessPicker(), new RandomOwnedCardPicker());
    }

    JudgementEngine(DiceRoller diceRoller, WeaknessPicker weaknessPicker, OwnedCardPicker ownedCardPicker) {
        this.diceRoller = Objects.requireNonNull(diceRoller, "diceRoller");
        this.weaknessPicker = Objects.requireNonNull(weaknessPicker, "weaknessPicker");
        this.ownedCardPicker = Objects.requireNonNull(ownedCardPicker, "ownedCardPicker");
    }

    static List<String> judgementAbilityChoices() {
        return List.of(Ability.BODY.id, Ability.SKILL.id, Ability.SENSE.id, Ability.WILL.id);
    }

    Result resolve(PlayerState player,
                   Ids.PlayerId playerId,
                   String abilityId,
                   long seed,
                   long version) {
        return resolve(player, null, playerId, abilityId, seed, version);
    }

    Result resolve(PlayerState player,
                   GameState state,
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
            return new Result(ability.id, ability.label, roll, abilityBefore, true, false, null, ability.id, abilityBefore, null, 0);
        }

        String ownedCardId = pickDeckOwnedCardId(player, seed, version, playerId, ability.id);
        String weakness = weaknessPicker.pick(WEAKNESS_POOL, seed, version, playerId, ability.id);
        applyWeaknessToOwnedCard(player, ownedCardId, weakness);
        int syncedInstanceCount = syncCardInstances(state, playerId, ownedCardId, weakness);
        int abilityAfter = Math.min(MAX_ABILITY, abilityBefore + 1);
        ability.write(player, abilityAfter);
        return new Result(
                ability.id,
                ability.label,
                roll,
                abilityBefore,
                false,
                true,
                weakness,
                ability.id,
                abilityAfter,
                ownedCardId,
                syncedInstanceCount
        );
    }

    interface DiceRoller {
        int rollD20(long seed, long version, Ids.PlayerId playerId, String abilityId);
    }

    interface WeaknessPicker {
        String pick(List<String> pool, long seed, long version, Ids.PlayerId playerId, String abilityId);
    }

    interface OwnedCardPicker {
        String pick(List<String> ownedCardIds, long seed, long version, Ids.PlayerId playerId, String abilityId);
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
            int increasedAbilityValue,
            String targetOwnedCardId,
            int syncedInstanceCount
    ) {}

    private String pickDeckOwnedCardId(PlayerState player,
                                       long seed,
                                       long version,
                                       Ids.PlayerId playerId,
                                       String abilityId) {
        List<String> pool = player.deckOwnedCardIds();
        if (pool == null || pool.isEmpty()) {
            throw new IllegalStateException("judgement weakness requires at least one deck owned card");
        }
        return ownedCardPicker.pick(pool, seed, version, playerId, abilityId);
    }

    private static void applyWeaknessToOwnedCard(PlayerState player, String ownedCardId, String weakness) {
        List<com.example.dueltower.content.card.model.OwnedCard> nextOwnedCards = new ArrayList<>(player.ownedCards().size());
        boolean updated = false;
        for (com.example.dueltower.content.card.model.OwnedCard ownedCard : player.ownedCards()) {
            if (ownedCard.ownedCardId().equals(ownedCardId)) {
                nextOwnedCards.add(ownedCard.withAddedModifier(weakness));
                updated = true;
            } else {
                nextOwnedCards.add(ownedCard);
            }
        }
        if (!updated) {
            throw new IllegalStateException("deck owned card not found in ownedCards: " + ownedCardId);
        }
        player.ownedCards(nextOwnedCards);
    }

    private static int syncCardInstances(GameState state,
                                         Ids.PlayerId playerId,
                                         String ownedCardId,
                                         String weakness) {
        if (state == null) {
            return 0;
        }
        int synced = 0;
        for (CardInstance instance : state.cardInstances().values()) {
            if (!playerId.equals(instance.ownerId())) {
                continue;
            }
            if (!ownedCardId.equals(instance.sourceOwnedCardId())) {
                continue;
            }
            List<com.example.dueltower.content.card.model.OwnedCardModifier> nextModifiers = new ArrayList<>(instance.modifiers());
            boolean alreadyExists = nextModifiers.stream().anyMatch(modifier -> weakness.equals(modifier.modifierId()));
            if (!alreadyExists) {
                nextModifiers.add(new com.example.dueltower.content.card.model.OwnedCardModifier(weakness, 1));
            }
            instance.replaceModifiers(nextModifiers);
            synced++;
        }
        return synced;
    }

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

    private static final class RandomOwnedCardPicker implements OwnedCardPicker {
        @Override
        public String pick(List<String> ownedCardIds, long seed, long version, Ids.PlayerId playerId, String abilityId) {
            if (ownedCardIds == null || ownedCardIds.isEmpty()) {
                throw new IllegalArgumentException("ownedCard pool is empty");
            }
            long playerHash = (playerId == null || playerId.value() == null) ? 0L : playerId.value().hashCode();
            long abilityHash = (abilityId == null) ? 0L : abilityId.hashCode();
            Random random = new Random((seed * 53L) ^ (version * 5003L) ^ (playerHash * 7L) ^ abilityHash);
            return ownedCardIds.get(random.nextInt(ownedCardIds.size()));
        }
    }
}
