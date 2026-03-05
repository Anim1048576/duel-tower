package com.example.dueltower.content.passive.pdb.player.tig;

import com.example.dueltower.content.passive.model.PassiveBlueprint;
import com.example.dueltower.content.status.sdb.player.tig.Tig201_Status;
import com.example.dueltower.engine.core.effect.passive.PassiveRuntime;
import com.example.dueltower.engine.model.*;
import org.springframework.stereotype.Component;

/**
 * 전투 진입 후 자신은 이하의 조건을 충족시 [극복]을 얻는다.
 * (같은 조건 충족으로는 얻을 수 없다.)
 * -자신의 Hp가 절반 이하일때.
 * -적을 쓰러뜨렸을 때.(처치관여)
 * -패가 0장이 되었을 때.
 * -Ex카드를 사용 했을 때.
 */
@Component
public class Tig001_Passive implements PassiveBlueprint {
    public static final String ID = "Tig001_Passive";

    private static final String KEY_HP_HALF = "TIG_PASSIVE_HP_HALF";
    private static final String KEY_KILL = "TIG_PASSIVE_KILL";
    private static final String KEY_HAND_ZERO = "TIG_PASSIVE_HAND_ZERO";
    private static final String KEY_USED_EX = "TIG_PASSIVE_USED_EX";

    @Override public String id() { return ID; }

    @Override
    public PassiveDefinition definition() {
        return new PassiveDefinition(
                id(),
                "험난한 영웅의 길",
                100,
                """
                        전투 진입 후 자신은 이하의 조건을 충족시 [극복]을 얻는다.
                        (같은 조건 충족으로는 얻을 수 없다.)
                        -자신의 Hp가 절반 이하일때.
                        -적을 쓰러뜨렸을 때.(처치관여)
                        -패가 0장이 되었을 때.
                        -Ex카드를 사용 했을 때.
                        """);
    }

    @Override
    public void onAfterPlayCard(PassiveRuntime rt, TargetRef actor, CardInstance ci, CardDefinition def) {
        checkAndApply(rt, actor, def.type() == CardType.EX);
    }

    @Override
    public int onOutgoingDamage(PassiveRuntime rt, TargetRef source, TargetRef target, int amount) {
        checkAndApply(rt, source, false);
        return amount;
    }

    @Override
    public int onIncomingDamage(PassiveRuntime rt, TargetRef source, TargetRef target, int amount) {
        checkAndApply(rt, target, false);
        return amount;
    }

    @Override
    public void onTurnStart(PassiveRuntime rt, TargetRef owner) {
        checkAndApply(rt, owner, false);
    }

    @Override
    public void onTurnEnd(PassiveRuntime rt, TargetRef owner) {
        checkAndApply(rt, owner, false);
    }

    private void checkAndApply(PassiveRuntime rt, TargetRef actor, boolean usedExNow) {
        if (!(actor instanceof TargetRef.Player p)) return;
        PlayerState ps = rt.player(p.id());

        grantOnce(rt, ps, KEY_HP_HALF, ps.hp() * 2 <= ps.maxHp());
        grantOnce(rt, ps, KEY_HAND_ZERO, ps.hand().isEmpty());
        grantOnce(rt, ps, KEY_USED_EX, usedExNow || ps.usedExThisTurn());

        boolean anyDefeated = rt.state().enemies().values().stream().anyMatch(e -> e.hp() <= 0);
        grantOnce(rt, ps, KEY_KILL, anyDefeated);
    }

    private void grantOnce(PassiveRuntime rt, PlayerState ps, String flagKey, boolean condition) {
        if (!condition) return;
        if (ps.status(flagKey) > 0) return;

        ps.statusSet(flagKey, 1);
        ps.statusAdd(Tig201_Status.ID, 1);
    }
}
