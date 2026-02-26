package com.example.dueltower.engine.core.status;

import com.example.dueltower.engine.model.TargetRef;

public interface StatusEffect {
    String id();

    /** 상태가 '받는 피해'를 변형할 수 있는 훅 */
    default int onIncomingDamage(StatusRuntime rt, TargetRef target, int amount) {
        return amount;
    }

    /** 턴 종료 훅 */
    default void onTurnEnd(StatusRuntime rt, TargetRef owner, int stacks) {}
}
