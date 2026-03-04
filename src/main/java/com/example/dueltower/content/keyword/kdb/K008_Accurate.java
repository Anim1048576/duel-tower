package com.example.dueltower.content.keyword.kdb;

import com.example.dueltower.content.keyword.model.KeywordBlueprint;
import com.example.dueltower.engine.core.effect.keyword.DamageKeywordCtx;
import com.example.dueltower.engine.core.effect.keyword.KeywordRuntime;
import com.example.dueltower.engine.model.KeywordDefinition;
import org.springframework.stereotype.Component;

/**
 * [키워드 : 필중]
 * [회피]를 무시하고 대미지를 줍니다.
 */
@Component
public class K008_Accurate implements KeywordBlueprint {

    public static final String ID = "필중";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public KeywordDefinition definition() {
        return new KeywordDefinition(
                ID,
                "필중",
                false,
                """
                        [회피]를 무시하고 대미지를 줍니다.
                        """
        );
    }

    @Override
    public boolean ignoresEvasion(KeywordRuntime rt, DamageKeywordCtx c) {
        return rt.value() != 0;
    }
}
