package com.example.dueltower.content.status.sdb;

import com.example.dueltower.content.status.model.StatusBlueprint;
import com.example.dueltower.engine.core.effect.status.StatusRuntime;
import com.example.dueltower.engine.model.*;
import org.springframework.stereotype.Component;

/**
 * [해로운 상태 : 고통]
 * 턴 종료 시, 해당 수치만큼 대미지를 받고, 이 수치를 절반으로 감소한다.
 */
@Component
public class S101_Pain implements StatusBlueprint {
    public static final String ID = "PAIN";
    @Override public String id() { return ID; }

    @Override
    public StatusDefinition definition() {
        return new StatusDefinition(
                id(),
                "고통",
                StatusKind.DEBUFF,
                StatusScope.CHARACTER,
                50,
                false,
                """
                        턴 종료 시, 해당 수치만큼 대미지를 받고, 이 수치를 절반으로 감소한다.
                        """
        );
    }

    @Override
    public void onTurnEnd(StatusRuntime rt, TargetRef owner, int stacks) {
        if (stacks <= 0) return;

        // 턴 종료 피해 (보호 등 onIncomingDamage 훅의 영향을 받음)
        rt.damage(owner, stacks);

        // 절반 감소 (내림)
        int next = stacks / 2;
        rt.stacksSet(owner, id(), next);
    }
}
