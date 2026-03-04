package com.example.dueltower.content.keyword.kdb;

import com.example.dueltower.content.keyword.model.KeywordBlueprint;
import com.example.dueltower.engine.core.effect.keyword.EnemyOneTargetCtx;
import com.example.dueltower.engine.core.effect.keyword.KeywordRuntime;
import com.example.dueltower.engine.model.KeywordDefinition;
import org.springframework.stereotype.Component;

/**
 * [키워드 : 명경]
 * [도발]을 무시하고 대상을 지정할 수 있습니다.
 */
@Component
public class K007_ClearMind implements KeywordBlueprint {

    public static final String ID = "명경";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public KeywordDefinition definition() {
        return new KeywordDefinition(
                ID,
                "명경",
                false,
                """
                        [도발]을 무시하고 대상을 지정할 수 있습니다.
                        """
        );
    }

    @Override
    public boolean ignoresTaunt(KeywordRuntime rt, EnemyOneTargetCtx c) {
        return rt.value() != 0;
    }
}
