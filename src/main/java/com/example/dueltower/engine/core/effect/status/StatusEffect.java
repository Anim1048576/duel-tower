package com.example.dueltower.engine.core.effect.status;

import com.example.dueltower.engine.model.*;

import java.util.List;

public interface StatusEffect {
    String id();

    /** 상태가 '받는 피해'를 변형할 수 있는 훅 */
    default int onIncomingDamage(StatusRuntime rt, StatusOwnerRef owner, TargetRef source, TargetRef target, int amount) {
        return amount;
    }

    /** 상태가 '주는 피해'를 변형할 수 있는 훅 */
    default int onOutgoingDamage(StatusRuntime rt, StatusOwnerRef owner, TargetRef source, TargetRef target, int amount) {
        return amount;
    }


/**
 * 상태가 '카드 코스트'를 변형할 수 있는 훅.
 * - PlayCardCommand / UseExCommand에서 사용한다.
 */
default int onCost(StatusRuntime rt, TargetRef actor, CardInstance ci, CardDefinition def, int currentCost) {
    return currentCost;
}

/** 카드 사용 가능 여부/사유를 검증하는 훅(스킬 카드) */
default void validatePlayCard(StatusRuntime rt, TargetRef actor, CardInstance ci, CardDefinition def, List<String> errors) {}

/** 카드 사용 후 훅(스킬 카드) */
default void onAfterPlayCard(StatusRuntime rt, TargetRef actor, CardInstance ci, CardDefinition def) {}

/** EX 사용 가능 여부/사유를 검증하는 훅 */
default void validateUseEx(StatusRuntime rt, TargetRef actor, CardInstance ci, CardDefinition def, List<String> errors) {}

/** EX 사용 후 훅 */
default void onAfterUseEx(StatusRuntime rt, TargetRef actor, CardInstance ci, CardDefinition def) {}

/**
 * (의도상) '적 1명(ENEMY_ONE)'을 대상으로 하는 경우, 타겟 선택을 검증하는 훅.
 * - 예: 도발
 * - validate 단계에서 호출되며, 상태를 변경하면 안 된다.
 */
default void validateEnemyOneTarget(
        StatusRuntime rt,
        TargetRef actor,
        Ids.CardInstId cardId,
        TargetRef chosenEnemy,
        List<TargetRef> enemyCandidates,
        List<String> errors
) {}

/**
 * (의도상) '적 1명(ENEMY_ONE)'을 대상으로 하는 경우, 실제 타겟을 재결정하는 훅.
 * - 예: 혼란(무작위 타겟), 도발(강제 타겟)
 * - handle 단계에서 호출되며, 필요 시 스택을 변경해도 된다.
 */
default TargetRef onResolveEnemyOneTarget(
        StatusRuntime rt,
        TargetRef actor,
        Ids.CardInstId cardId,
        TargetRef chosenEnemy,
        List<TargetRef> candidates
) {
    return chosenEnemy;
}

    /** 턴 종료 훅 */
    default void onTurnEnd(StatusRuntime rt, TargetRef owner, int stacks) {}

    /** 턴 시작 훅 */
    default void onTurnStart(StatusRuntime rt, TargetRef owner, int stacks) {}
}
