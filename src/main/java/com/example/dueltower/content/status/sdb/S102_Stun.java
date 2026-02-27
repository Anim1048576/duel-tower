package com.example.dueltower.content.status.sdb;

import com.example.dueltower.content.status.model.StatusBlueprint;
import com.example.dueltower.engine.core.effect.status.StatusRuntime;
import com.example.dueltower.engine.model.*;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * [해로운 상태 : 기절]
 * 자신은 [스킬 카드] 및 [EX 카드]를 낼 수 없다.
 * 턴을 종료하면 제거된다.
 */
@Component
public class S102_Stun implements StatusBlueprint {
    public static final String ID = "STUN";
    @Override public String id() { return ID; }

    @Override
    public StatusDefinition definition() {
        return new StatusDefinition(
                id(),
                "기절",
                StatusKind.DEBUFF,
                StatusScope.CHARACTER,
                999,
                false,
                """
                        자신은 [스킬 카드] 및 [EX 카드]를 낼 수 없다.
                        턴을 종료하면 제거된다.
                        """
        );
    }

    @Override
    public void validatePlayCard(StatusRuntime rt, TargetRef actor, CardInstance ci, CardDefinition def, List<String> errors) {
        if (rt.stacks(actor, id()) <= 0) return;
        if (def.type() == CardType.SKILL) {
            errors.add("stun: cannot play skill cards");
        }
    }

    @Override
    public void validateUseEx(StatusRuntime rt, TargetRef actor, CardInstance ci, CardDefinition def, List<String> errors) {
        if (rt.stacks(actor, id()) <= 0) return;
        if (def.type() == CardType.EX) {
            errors.add("stun: cannot use EX while stunned");
        }
    }

    @Override
    public void onTurnEnd(StatusRuntime rt, TargetRef owner, int stacks) {
        if (stacks <= 0) return;

        int next = 0;
        rt.stacksSet(owner, id(), next);
    }
}
