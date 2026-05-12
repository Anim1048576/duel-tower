package com.example.dueltower.engine.model;

import java.util.Objects;
import java.util.UUID;

public sealed interface PendingDecision permits PendingDecision.DiscardToHandLimit, PendingDecision.SearchPick, PendingDecision.InitiativeTieOrder, PendingDecision.JudgementChoice, PendingDecision.LastWordsChoice, PendingDecision.ReactionCard, PendingDecision.EventHorizonChoice {
    record DiscardToHandLimit(String reason, int limit) implements PendingDecision {
        public DiscardToHandLimit {
            Objects.requireNonNull(reason);
        }
    }

    record SearchPick(
            String reason,
            java.util.List<Ids.CardInstId> candidateIds,
            int pickCount,
            Zone destination,
            boolean shuffleAfterPick,
            UUID correlationId
    ) implements PendingDecision {
        public SearchPick {
            Objects.requireNonNull(reason);
            Objects.requireNonNull(candidateIds);
            candidateIds = java.util.List.copyOf(candidateIds);
            Objects.requireNonNull(destination);
            if (pickCount <= 0 || pickCount > candidateIds.size()) {
                throw new IllegalArgumentException("pickCount must be between 1 and candidateIds.size()");
            }
            if (candidateIds.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("candidateIds must not contain null");
            }
            if (candidateIds.size() != new java.util.LinkedHashSet<>(candidateIds).size()) {
                throw new IllegalArgumentException("candidateIds must be unique");
            }
        }
    }

    record InitiativeTieOrder(String reason, int groupIndex, java.util.List<String> actorKeys) implements PendingDecision {
        public InitiativeTieOrder {
            Objects.requireNonNull(reason);
            Objects.requireNonNull(actorKeys);
        }
    }

    record JudgementChoice(
            String reason,
            java.util.List<String> choiceIds,
            String usedAbility,
            Integer roll,
            Integer abilityBefore,
            Boolean initialSuccess,
            Boolean memoryAcceptAllowed,
            Boolean naturalTwenty
    ) implements PendingDecision {
        public JudgementChoice(String reason, java.util.List<String> choiceIds) {
            this(reason, choiceIds, null, null, null, null, null, null);
        }

        public JudgementChoice {
            Objects.requireNonNull(reason);
            Objects.requireNonNull(choiceIds);
            choiceIds = java.util.List.copyOf(choiceIds);
            if (choiceIds.isEmpty()) {
                throw new IllegalArgumentException("choiceIds must not be empty");
            }
            if (choiceIds.stream().anyMatch(id -> id == null || id.isBlank())) {
                throw new IllegalArgumentException("choiceIds must not contain blank");
            }
        }
    }

    record LastWordsChoice(
            String reason,
            java.util.List<Ids.CardInstId> candidateIds,
            boolean skippable,
            UUID correlationId
    ) implements PendingDecision {
        public LastWordsChoice {
            Objects.requireNonNull(reason);
            Objects.requireNonNull(candidateIds);
            candidateIds = java.util.List.copyOf(candidateIds);
            if (candidateIds.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("candidateIds must not contain null");
            }
            if (candidateIds.size() != new java.util.LinkedHashSet<>(candidateIds).size()) {
                throw new IllegalArgumentException("candidateIds must be unique");
            }
            if (candidateIds.isEmpty()) {
                throw new IllegalArgumentException("candidateIds must not be empty");
            }
            Objects.requireNonNull(correlationId);
        }
    }

    record ReactionCard(
            String reason,
            java.util.List<Ids.CardInstId> candidateIds,
            boolean skippable,
            ReactionContext context
    ) implements PendingDecision {
        public ReactionCard {
            Objects.requireNonNull(reason);
            Objects.requireNonNull(candidateIds);
            candidateIds = java.util.List.copyOf(candidateIds);
            if (candidateIds.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("candidateIds must not contain null");
            }
            if (candidateIds.size() != new java.util.LinkedHashSet<>(candidateIds).size()) {
                throw new IllegalArgumentException("candidateIds must be unique");
            }
            if (candidateIds.isEmpty()) {
                throw new IllegalArgumentException("candidateIds must not be empty");
            }
            Objects.requireNonNull(context);
        }
    }

    record EventHorizonChoice(
            String reason,
            Ids.PlayerId playerId,
            java.util.List<String> choiceIds
    ) implements PendingDecision {
        public EventHorizonChoice {
            Objects.requireNonNull(reason);
            Objects.requireNonNull(playerId);
            Objects.requireNonNull(choiceIds);
            choiceIds = java.util.List.copyOf(choiceIds);
            if (choiceIds.isEmpty()) {
                throw new IllegalArgumentException("choiceIds must not be empty");
            }
            if (choiceIds.stream().anyMatch(id -> id == null || id.isBlank())) {
                throw new IllegalArgumentException("choiceIds must not contain blank");
            }
            if (choiceIds.size() != new java.util.LinkedHashSet<>(choiceIds).size()) {
                throw new IllegalArgumentException("choiceIds must be unique");
            }
        }
    }

    record ReactionContext(
            UUID reactionId,
            Ids.PlayerId ownerPlayerId,
            ReactionTrigger trigger,
            TargetRef source,
            TargetRef subject,
            int damageAmount,
            String sourceAction
    ) {
        public ReactionContext {
            Objects.requireNonNull(reactionId);
            Objects.requireNonNull(ownerPlayerId);
            Objects.requireNonNull(trigger);
            Objects.requireNonNull(source);
            Objects.requireNonNull(subject);
            Objects.requireNonNull(sourceAction);
            if (damageAmount < 0) {
                throw new IllegalArgumentException("damageAmount must not be negative");
            }
        }
    }

}
