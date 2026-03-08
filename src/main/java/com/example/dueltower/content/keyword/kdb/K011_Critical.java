package com.example.dueltower.content.keyword.kdb;

import com.example.dueltower.content.keyword.model.KeywordBlueprint;
import com.example.dueltower.engine.core.effect.keyword.DamageKeywordCtx;
import com.example.dueltower.engine.core.effect.keyword.KeywordRuntime;
import com.example.dueltower.engine.model.KeywordDefinition;
import org.springframework.stereotype.Component;

/**
 * [키워드 : 치명 n]
 * 해당 스킬이 n×5%p의 [치명타] 확률을 갖습니다.
 * [치명타] 발생시, 해당 대미지 체크로 발생하는 대미지 / 회복량이 1.5배가 됩니다.
 */
@Component
public class K011_Critical implements KeywordBlueprint {

    public static final String ID = "치명";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public KeywordDefinition definition() {
        return new KeywordDefinition(
                ID,
                "치명",
                true,
                """
                        해당 스킬이 n×5%p의 [치명타] 확률을 갖습니다.
                        [치명타] 발생시, 해당 대미지 체크로 발생하는 대미지 / 회복량이 1.5배가 됩니다.
                        """
        );
    }

    @Override
    public int criticalChancePercent(KeywordRuntime rt, DamageKeywordCtx c, String kind) {
        if (rt.value() <= 0) return 0;
        return Math.max(0, rt.value() * 5);
    }

    @Override
    public double criticalAmountMultiplier(KeywordRuntime rt, DamageKeywordCtx c, String kind) {
        if (rt.value() <= 0) return 1d;
        return 1.5d;
    }
}
