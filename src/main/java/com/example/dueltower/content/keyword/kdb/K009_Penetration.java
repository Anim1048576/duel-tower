package com.example.dueltower.content.keyword.kdb;

import com.example.dueltower.content.keyword.model.KeywordBlueprint;
import com.example.dueltower.engine.core.effect.keyword.DamageKeywordCtx;
import com.example.dueltower.engine.core.effect.keyword.KeywordRuntime;
import com.example.dueltower.engine.model.KeywordDefinition;
import org.springframework.stereotype.Component;

/**
 * [키워드 : 관통]
 * [보호]와 [방벽]을 무시하고 대미지를 줍니다.
 */
@Component
public class K009_Penetration implements KeywordBlueprint {

    public static final String ID = "관통";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public KeywordDefinition definition() {
        return new KeywordDefinition(
                ID,
                "관통",
                false,
                """
                        [보호]와 [방벽]을 무시하고 대미지를 줍니다.
                        """
        );
    }

    @Override
    public boolean ignoresShield(KeywordRuntime rt, DamageKeywordCtx c) {
        return rt.value() != 0;
    }

    @Override
    public boolean ignoresBarrier(KeywordRuntime rt, DamageKeywordCtx c) {
        return rt.value() != 0;
    }
}
