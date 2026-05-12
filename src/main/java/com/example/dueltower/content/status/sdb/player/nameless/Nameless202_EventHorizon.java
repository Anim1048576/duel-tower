package com.example.dueltower.content.status.sdb.player.nameless;

import com.example.dueltower.content.meta.ContentOwnerIds;
import com.example.dueltower.content.status.model.StatusBlueprint;
import com.example.dueltower.engine.core.effect.status.StatusRuntime;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class Nameless202_EventHorizon implements StatusBlueprint {
    public static final String ID = "EVENT_HORIZON";
    public static final String CHOICE_TAKE_DAMAGE = "TAKE_DAMAGE";
    public static final String CHOICE_REMOVE_STATUS = "REMOVE_STATUS";
    public static final String DECISION_TYPE = "EVENT_HORIZON";
    public static final List<String> CHOICE_IDS = List.of(CHOICE_TAKE_DAMAGE, CHOICE_REMOVE_STATUS);

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String contentOwner() {
        return ContentOwnerIds.NAMELESS;
    }

    @Override
    public StatusDefinition definition() {
        return new StatusDefinition(
                id(),
                "사건의 지평선",
                StatusKind.BUFF,
                StatusScope.CHARACTER,
                Set.of(),
                0,
                false,
                """
                        상한치 1.
                        자신은 체력을 회복할 수 없으며, 이 상태 이외로 받는 모든 대미지를 무시한다.
                        자신은 턴을 종료할 때마다 자신의 최대 체력의 40% 만큼 대미지를 받는다. 또는, 이 상태를 해제한다.
                        또한, 자신은 자신의 턴에 [스킬 카드]를 1장밖에 사용할 수 없다.
                        """
        );
    }

    @Override
    public int onIncomingHeal(StatusRuntime rt, StatusOwnerRef owner, TargetRef source, TargetRef target, int amount) {
        if (amount > 0) {
            rt.log("[사건의 지평선] 회복을 차단했습니다.");
        }
        return 0;
    }

    @Override
    public int onIncomingDamage(
            StatusRuntime rt,
            StatusOwnerRef owner,
            TargetRef source,
            TargetRef target,
            int amount,
            String sourceStatusId
    ) {
        if (ID.equals(sourceStatusId)) return amount;
        if (amount > 0) {
            rt.log("[사건의 지평선] 피해를 무시했습니다.");
        }
        return 0;
    }

    @Override
    public void validatePlayCard(StatusRuntime rt, TargetRef actor, CardInstance ci, CardDefinition def, List<String> errors) {
        if (!(actor instanceof TargetRef.Player p)) return;
        if (rt.stacks(actor, id()) <= 0) return;
        if (def.type() != CardType.SKILL) return;

        PlayerState player = rt.state().player(p.id());
        if (player == null) return;
        if (player.cardsPlayedThisTurn() >= 1) {
            errors.add("[사건의 지평선] 상태에서는 자신의 턴에 스킬 카드를 1장만 사용할 수 있습니다.");
        }
    }

    @Override
    public void onTurnEnd(StatusRuntime rt, TargetRef owner, int stacks) {
        if (stacks <= 0) return;
        if (!(owner instanceof TargetRef.Player p)) return;

        PlayerState player = rt.state().player(p.id());
        if (player == null || player.pendingDecision() != null) return;

        String reason = "[사건의 지평선] 턴 종료 선택";
        player.pendingDecision(new PendingDecision.EventHorizonChoice(reason, p.id(), CHOICE_IDS));
        rt.out().add(new GameEvent.PendingDecisionSet(p.id().value(), DECISION_TYPE, reason));
    }
}
