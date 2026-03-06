package com.example.dueltower.character.service;

import com.example.dueltower.character.domain.CharacterProfile;
import org.springframework.stereotype.Component;

@Component
public class CharacterCombatStatCalculator {

    public CombatStats calculate(CharacterProfile profile) {
        int maxHp = calculateMaxHp(profile.getPhysical(), profile.getTechnique());
        int maxAp = calculateMaxAp(profile.getWillpower());
        int attackPower = calculateAttackPower(profile.getPhysical(), profile.getTechnique(), profile.getSense());
        int healPower = calculateHealPower(profile.getSense(), profile.getTechnique());
        return new CombatStats(maxHp, maxAp, attackPower, healPower);
    }

    static int calculateMaxHp(int physical, int technique) {
        double s = physical * 5.0 + technique * 3.0 + 3.0;
        int scaled = roundDownWithSoftCap(s, 40.0);
        return Math.max(scaled, 20);
    }

    static int calculateMaxAp(int willpower) {
        return (int) Math.floor(3.0 + willpower / 6.0);
    }

    static int calculateAttackPower(int physical, int technique, int sense) {
        double s = physical + technique + sense / 2.0;
        return roundDownWithSoftCap(s, 10.0);
    }

    static int calculateHealPower(int sense, int technique) {
        double s = sense * 2.0 + technique / 2.0;
        return roundDownWithSoftCap(s, 10.0);
    }

    private static int roundDownWithSoftCap(double score, double threshold) {
        double scaled = Math.min(score, threshold) + Math.max(score - threshold, 0) / 2.0;
        return (int) Math.floor(scaled);
    }

    public record CombatStats(int maxHp, int maxAp, int attackPower, int healPower) {}
}
