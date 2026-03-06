package com.example.dueltower.character.service;

import com.example.dueltower.character.domain.CharacterProfile;
import org.springframework.stereotype.Component;

@Component
public class CharacterCombatStatCalculator {

    public CombatStats calculate(CharacterProfile profile) {
        int hp = profile.getPhysical() * 10 + profile.getWillpower() * 5;
        int actionPower = profile.getTechnique() * 2 + profile.getSense();
        int attackPower = profile.getPhysical() * 2 + profile.getTechnique();
        int healingPower = profile.getWillpower() * 2 + profile.getSense();
        return new CombatStats(hp, actionPower, attackPower, healingPower);
    }

    public record CombatStats(int hp, int actionPower, int attackPower, int healingPower) {}
}
