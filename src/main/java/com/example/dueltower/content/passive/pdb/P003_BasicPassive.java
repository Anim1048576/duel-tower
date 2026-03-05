package com.example.dueltower.content.passive.pdb;

import com.example.dueltower.content.passive.model.PassiveBlueprint;
import com.example.dueltower.engine.model.PassiveDefinition;
import org.springframework.stereotype.Component;

@Component
public class P003_BasicPassive implements PassiveBlueprint {
    public static final String ID = "P003";

    @Override public String id() { return ID; }

    @Override
    public PassiveDefinition definition() {
        return new PassiveDefinition(id(), "기본 패시브 III", 300, "reserved");
    }
}
