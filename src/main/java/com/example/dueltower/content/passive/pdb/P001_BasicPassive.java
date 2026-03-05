package com.example.dueltower.content.passive.pdb;

import com.example.dueltower.content.passive.model.PassiveBlueprint;
import com.example.dueltower.engine.model.PassiveDefinition;
import org.springframework.stereotype.Component;

@Component
public class P001_BasicPassive implements PassiveBlueprint {
    public static final String ID = "P001";

    @Override public String id() { return ID; }

    @Override
    public PassiveDefinition definition() {
        return new PassiveDefinition(id(), "기본 패시브 I", 100, "reserved");
    }
}
