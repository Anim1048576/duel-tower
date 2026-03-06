package com.example.dueltower.content.status.sdb;

import com.example.dueltower.content.status.model.StatusBlueprint;
import com.example.dueltower.engine.core.effect.status.StatusRuntime;
import com.example.dueltower.engine.model.*;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * [해로운 상태 : 봉인]
 * 자신은 [EX 카드]를 낼 수 없다.
 * 턴을 종료하면 제거된다.
 */
@Component
public class S108_Seal implements StatusBlueprint {
    public static final String ID = "SEAL";
    @Override public String id() { return ID; }

    @Override
    public StatusDefinition definition() {
        return new StatusDefinition(
                id(),
                "봉인",
                StatusKind.DEBUFF,
                StatusScope.CHARACTER,
                java.util.Set.of(),
                35,
                false,
                """
                        자신은 [EX 카드]를 낼 수 없다.
                        턴을 종료하면 제거된다.
                        """
        );
    }

    @Override
    public void validateUseEx(StatusRuntime rt, TargetRef actor, CardInstance ci, CardDefinition def, List<String> errors) {
        if (rt.stacks(actor, id()) <= 0) return;
        if (def.type() == CardType.EX) {
            errors.add("seal: cannot use EX while sealed");
        }
    }

    @Override
    public void onTurnEnd(StatusRuntime rt, TargetRef owner, int stacks) {
        if (stacks <= 0) return;
        rt.stacksSet(owner, id(), 0);
    }
}
