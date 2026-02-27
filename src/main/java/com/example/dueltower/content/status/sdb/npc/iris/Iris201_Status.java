package com.example.dueltower.content.status.sdb.npc.iris;

import com.example.dueltower.content.status.model.StatusBlueprint;
import com.example.dueltower.engine.model.*;
import org.springframework.stereotype.Component;

/**
 * [중립적 상태 : 축복]
 * 이 상태는 전투가 종료되어도 소멸하지 않는다.
 */
@Component
public class Iris201_Status implements StatusBlueprint {
    public static final String ID = "Iris201";
    @Override public String id() { return ID; }

    @Override
    public StatusDefinition definition() {
        return new StatusDefinition(
                ID,
                "축복",
                StatusKind.NEUTRAL,
                StatusScope.CHARACTER,
                999,
                true,
                """
                        이 상태는 전투가 종료되어도 소멸하지 않는다.
                        """
        );
    }
}
