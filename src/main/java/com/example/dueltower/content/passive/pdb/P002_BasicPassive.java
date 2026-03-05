package com.example.dueltower.content.passive.pdb;

import com.example.dueltower.content.passive.model.PassiveBlueprint;
import com.example.dueltower.engine.model.PassiveDefinition;
import org.springframework.stereotype.Component;

@Component
public class P002_BasicPassive implements PassiveBlueprint {
    public static final String ID = "P002";

    @Override public String id() { return ID; }

    @Override
    public PassiveDefinition definition() {
        return new PassiveDefinition(id(), "기본 패시브 II", 200, "reserved");
    }
}
