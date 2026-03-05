package com.example.dueltower.content.passive.pdb.player.tig;

import com.example.dueltower.content.passive.model.PassiveBlueprint;
import com.example.dueltower.engine.model.PassiveDefinition;
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
}
