package com.example.dueltower.engine.core.enemy;

import com.example.dueltower.content.enemy.model.EnemyDefinition;
import com.example.dueltower.content.enemy.model.EnemyPassiveRef;
import com.example.dueltower.content.enemy.model.EnemyStatusRef;
import com.example.dueltower.engine.config.EncounterTableConfig;
import com.example.dueltower.engine.model.EnemyState;
import com.example.dueltower.engine.model.Ids;

import java.util.Objects;

public final class EnemyStateFactory {
    private EnemyStateFactory() {
    }

    public static EnemyState create(
            EncounterTableConfig.EnemyTemplate template,
            EnemyDefinition definition,
            int floorDelta
    ) {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(definition.stats(), "definition.stats");

        int resolvedFloorDelta = Math.max(0, floorDelta);
        int maxHp = Math.max(1, definition.stats().maxHp() + template.hpPerFloor() * resolvedFloorDelta);
        int attackPower = Math.max(0, definition.stats().attackPower() + template.attackPowerPerFloor() * resolvedFloorDelta);
        int healPower = Math.max(0, definition.stats().healPower() + template.healingPowerPerFloor() * resolvedFloorDelta);

        EnemyState enemy = new EnemyState(new Ids.EnemyId(template.instanceId()), maxHp);
        enemy.enemyDefId(definition.id());
        enemy.name(definition.name());
        enemy.maxAp(definition.stats().maxActionPoint());
        enemy.ap(definition.stats().maxActionPoint());
        enemy.attackPower(attackPower);
        enemy.healPower(healPower);
        enemy.passiveIds(definition.passives().stream()
                .map(EnemyPassiveRef::passiveId)
                .toList());
        for (EnemyStatusRef status : definition.startStatuses()) {
            enemy.statusAdd(status.statusId(), status.stacks());
        }
        return enemy;
    }
}
