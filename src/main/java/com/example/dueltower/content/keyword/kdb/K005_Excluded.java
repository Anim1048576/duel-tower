package com.example.dueltower.content.keyword.kdb;

import com.example.dueltower.content.keyword.KeywordBlueprint;
import com.example.dueltower.engine.core.effect.keyword.*;
import com.example.dueltower.engine.model.KeywordDefinition;
import com.example.dueltower.engine.model.Zone;
import org.springframework.stereotype.Component;

/**
 * [키워드 : 제외]
 * 이 [스킬 카드]는 사용했을 경우, [묘지]로 보내지지 않고 이번 전투에서 [제외]됩니다.
 * 구현 규칙:
 * - PLAY/DESTROY로 인해 GRAVE로 이동하려는 경우, EXCLUDED로 보낸다.
 * - 그 외(손패 제한 버리기 등)는 그대로 GRAVE.
 */
@Component
public class K005_Excluded implements KeywordBlueprint {

    public static final String ID = "제외";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public KeywordDefinition definition() {
        return new KeywordDefinition(
                ID,
                "제외",
                false,
                "이 [스킬 카드]는 사용했을 경우, [묘지]로 보내지지 않고 이번 전투에서 [제외]됩니다."
        );
    }

    @Override
    public Zone overrideMoveDestination(KeywordRuntime rt, MoveCtx c, Zone currentTo) {
        if (rt.value() == 0) return currentTo;
        if ((c.reason() == MoveReason.PLAY || c.reason() == MoveReason.DESTROY) && currentTo == Zone.GRAVE) {
            return Zone.EXCLUDED;
        }
        return currentTo;
    }

    @Override
    public boolean overrideExActivatable(KeywordRuntime rt, ExActivationCtx c, boolean current) {
        if (c.exCard() && c.reason() == ExActivationReason.USED_EX) return false;
        return current;
    }
}
