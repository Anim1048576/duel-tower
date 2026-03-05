package com.example.dueltower.content.status.sdb.player.tig;

import com.example.dueltower.content.status.model.StatusBlueprint;
import com.example.dueltower.engine.model.StatusDefinition;
import com.example.dueltower.engine.model.StatusKind;
import com.example.dueltower.engine.model.StatusScope;
import org.springframework.stereotype.Component;

/** [중립적 상태 : 극복] */
@Component
public class Tig201_Status implements StatusBlueprint {
    public static final String ID = "Tig201_Status";
    @Override public String id() { return ID; }

    @Override
    public StatusDefinition definition() {
        return new StatusDefinition(
                ID,
                "극복",
                StatusKind.NEUTRAL,
                StatusScope.CHARACTER,
                java.util.Set.of(),
                999,
                true,
                """
                        자신은 이하의 조건을 충족시 [극복]을 얻는다.
                        (같은 조건 충족으로는 얻을 수 없다.)
                        -자신의 Hp가 절반 이하일때.
                        -적을 쓰러뜨렸을 때.(처치관여)
                        -패가 0장이 되었을 때.
                        -Ex카드를 사용 했을 때.
                        """
        );
    }
}
