package com.example.dueltower.engine.model;

/**
 * Engine-level semantic tags for statuses.
 * Engine rules should depend on meaning, not on a specific content ID.
 */
public enum StatusTag {
    EVASION,   // 회피 계열: 필중으로 무시 가능
    SHIELD,    // 보호 계열: 관통으로 무시 가능
    BARRIER,   // 방벽 계열: 관통으로 무시 가능
    TAUNT,     // 도발 계열: ENEMY_ONE 타겟팅 강제
    CONFUSION  // 혼란 계열: 도발 무시(및 타겟 룰 변경)
}
