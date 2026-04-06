package com.example.dueltower.content.card.model.playspec;

import com.example.dueltower.engine.model.Target;

public record TargetSpec(
        Target target,
        boolean requiredSelection
) {
    public TargetSpec {
        target = target == null ? Target.NONE : target;
        if (target == Target.NONE && requiredSelection) {
            throw new IllegalArgumentException("Target.NONE cannot require selection");
        }
    }

    public static TargetSpec none() {
        return new TargetSpec(Target.NONE, false);
    }

    public static TargetSpec required(Target target) {
        if (target == null || target == Target.NONE) {
            throw new IllegalArgumentException("required target must not be NONE");
        }
        return new TargetSpec(target, true);
    }
}
