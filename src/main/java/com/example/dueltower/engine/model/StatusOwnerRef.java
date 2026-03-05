package com.example.dueltower.engine.model;

public sealed interface StatusOwnerRef
        permits StatusOwnerRef.Character, StatusOwnerRef.Faction, StatusOwnerRef.Card {

    record Character(TargetRef who) implements StatusOwnerRef {}
    record Faction(CombatState.FactionId id) implements StatusOwnerRef {}
    record Card(Ids.CardInstId id) implements StatusOwnerRef {}

    static StatusOwnerRef of(TargetRef who) {
        if (who instanceof TargetRef.Summon s) return new Card(new Ids.CardInstId(s.summonId().value()));
        return new Character(who);
    }
    static StatusOwnerRef of(CombatState.FactionId id) { return new Faction(id); }
    static StatusOwnerRef of(Ids.CardInstId id) { return new Card(id); }
}
