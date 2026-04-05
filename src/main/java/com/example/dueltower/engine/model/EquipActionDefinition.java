package com.example.dueltower.engine.model;

public record EquipActionDefinition(
        String actionId,
        String name,
        String summary,
        String description,
        Target target,
        int apCost,
        boolean requiresLoadedAmmo,
        int loadedAmmoCost,
        int fixedDamage
) {
}
