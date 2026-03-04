package com.example.dueltower.content.keyword.kdb;

import com.example.dueltower.content.keyword.model.KeywordBlueprint;
import com.example.dueltower.engine.core.effect.keyword.ApDebtCtx;
import com.example.dueltower.engine.core.effect.keyword.KeywordRuntime;
import com.example.dueltower.engine.model.KeywordDefinition;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * [키워드 : 집념]
 * 이 [스킬 카드]를 내기 위한 [행동력]이 모자라더라도 사용할 수 있습니다.
 * 모자란 [코스트]만큼 턴 종료시에 회복하는 [행동력]이 차감됩니다. (최대 0까지)
 * 이 [키워드]를 가진 [스킬 카드]는 1턴에 1장만 낼 수 있습니다.
 */
@Component
public class K010_Tenacity implements KeywordBlueprint {

    public static final String ID = "집념";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public KeywordDefinition definition() {
        return new KeywordDefinition(
                ID,
                "집념",
                false,
                """
                        이 [스킬 카드]를 내기 위한 [행동력]이 모자라더라도 사용할 수 있습니다.
                        모자란 [코스트]만큼 턴 종료시에 회복하는 [행동력]이 차감됩니다. (최대 0까지)
                        이 [키워드]를 가진 [스킬 카드]는 1턴에 1장만 낼 수 있습니다.
                        """
        );
    }

    @Override
    public void validateApDebtPayment(KeywordRuntime rt, ApDebtCtx c, int cost, int have, List<String> errors) {
        if (rt.value() == 0) return;

        // 턴당 1장 제한
        if (c.owner() != null && c.owner().usedTenacityThisTurn()) {
            errors.add("집념은 턴당 1장만 사용할 수 있습니다.");
        }
    }

    @Override
    public boolean allowsApDebtPayment(KeywordRuntime rt, ApDebtCtx c, int cost, int have) {
        if (rt.value() == 0) return false;

        // 턴당 1장 제한
        if (c.owner() != null && c.owner().usedTenacityThisTurn()) return false;

        // AP가 충분해도 '사용'은 가능(턴당 1장 제한 때문)
        // 실제 부족분 처리(debt)는 apDebtAmount에서 계산.
        return true;
    }

    @Override
    public int apDebtAmount(KeywordRuntime rt, ApDebtCtx c, int cost, int have) {
        if (rt.value() == 0) return 0;
        if (c.owner() != null && c.owner().usedTenacityThisTurn()) return 0;
        return Math.max(0, cost - have);
    }
}
