package com.example.dueltower.content.card.cdb.player.nameless;

import com.example.dueltower.content.card.model.CardBlueprint;
import com.example.dueltower.content.card.model.playspec.CardPlaySpec;
import com.example.dueltower.content.keyword.kdb.K007_ClearMind;
import com.example.dueltower.content.meta.ContentOwnerIds;
import com.example.dueltower.content.status.sdb.S005_Taunt;
import com.example.dueltower.content.status.sdb.player.nameless.Nameless201_Entropy;
import com.example.dueltower.content.status.sdb.player.nameless.Nameless202_EventHorizon;
import com.example.dueltower.content.status.sdb.player.nameless.Nameless203_EventHorizonUsed;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.core.effect.EffectOps;
import com.example.dueltower.engine.core.effect.status.StatusRuntime;
import com.example.dueltower.engine.model.CardDefinition;
import com.example.dueltower.engine.model.CardType;
import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.StatusOwnerRef;
import com.example.dueltower.engine.model.Target;
import com.example.dueltower.engine.model.TargetRef;
import com.example.dueltower.engine.model.Zone;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class Nameless901_EX implements CardBlueprint {
    public static final String AUGMENT_1 = "AUGMENT_1";
    public static final String AUGMENT_2 = "AUGMENT_2";
    public static final String AUGMENT_3 = "AUGMENT_3";

    @Override public String id() { return "Nameless901_EX"; }

    @Override
    public String contentOwner() {
        return ContentOwnerIds.NAMELESS;
    }

    @Override
    public CardDefinition definition() {
        return new CardDefinition(
                new Ids.CardDefId(id()),
                "중력 특이점",
                CardType.EX,
                0,
                Map.of(K007_ClearMind.ID, 1),
                Zone.EX,
                false,
                """
                        [명경]
                        대상 1명을 지정하여 이하의 효과 중 1개를 선택해서 적용한다.
                        증강 1: 대상에게 [도발]이 없다면 [도발] 7을 부여하고, 있다면 [도발]을 해제한다.
                        증강 2: 대상에게 [엔트로피]가 없다면 [엔트로피] 4를 부여하고, 있다면 [엔트로피]를 해제한다.
                        증강 3: 대상이 자신일 경우에만 선택 가능하다. [사건의 지평선] 1을 부여한다. 세션 중 1회.
                        """
        );
    }

    @Override
    public CardPlaySpec playSpec() {
        return NamelessEffectSupport.anyOnePlaySpec();
    }

    @Override
    public List<String> validate(EffectContext ec) {
        List<String> errors = new ArrayList<>(NamelessEffectSupport.validateAnyOneTarget(ec));
        String choiceId = ec.choiceId();
        if (choiceId == null) {
            errors.add("choiceId is required");
            return errors;
        }
        if (!List.of(AUGMENT_1, AUGMENT_2, AUGMENT_3).contains(choiceId)) {
            errors.add("invalid choiceId: " + choiceId);
            return errors;
        }

        if (AUGMENT_3.equals(choiceId)) {
            TargetRef target = ec.selection().requireOne();
            if (!target.equals(ec.actorRef())) {
                errors.add("AUGMENT_3 requires self target");
            }
            StatusRuntime rt = new StatusRuntime(ec.state(), ec.ctx(), ec.out(), ec.sourceLabel());
            if (rt.stacks(ec.actorRef(), Nameless203_EventHorizonUsed.ID) > 0) {
                errors.add("AUGMENT_3 already used");
            }
        }
        return errors;
    }

    @Override
    public void resolve(EffectContext ec) {
        String choiceId = ec.choiceId();
        if (choiceId == null) throw new IllegalArgumentException("choiceId is required");
        switch (choiceId) {
            case AUGMENT_1 -> toggleStatus(ec, S005_Taunt.ID, 7);
            case AUGMENT_2 -> toggleStatus(ec, Nameless201_Entropy.ID, 4);
            case AUGMENT_3 -> resolveEventHorizon(ec);
            default -> throw new IllegalArgumentException("invalid choiceId: " + choiceId);
        }
    }

    private static void toggleStatus(EffectContext ec, String statusId, int amount) {
        TargetRef target = ec.selection().requireOne();
        StatusRuntime rt = new StatusRuntime(ec.state(), ec.ctx(), ec.out(), ec.sourceLabel());
        if (rt.stacks(target, statusId) > 0) {
            rt.stacksSet(StatusOwnerRef.of(target), statusId, 0);
            return;
        }
        new EffectOps(ec).addStatus(Target.ANY_ONE, statusId, amount);
    }

    private static void resolveEventHorizon(EffectContext ec) {
        TargetRef target = ec.selection().requireOne();
        if (!target.equals(ec.actorRef())) {
            throw new IllegalArgumentException("AUGMENT_3 requires self target");
        }
        StatusRuntime rt = new StatusRuntime(ec.state(), ec.ctx(), ec.out(), ec.sourceLabel());
        if (rt.stacks(ec.actorRef(), Nameless203_EventHorizonUsed.ID) > 0) {
            throw new IllegalArgumentException("AUGMENT_3 already used");
        }

        EffectOps ops = new EffectOps(ec);
        ops.addStatus(Target.ANY_ONE, Nameless202_EventHorizon.ID, 1);
        ops.addStatus(Target.ANY_ONE, Nameless203_EventHorizonUsed.ID, 1);
    }
}
