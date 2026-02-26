package com.example.dueltower.engine.core.effect.keyword;

public enum DiscardReason {
    HAND_SWAP,        // voluntary discard (e.g. HandSwap)
    HAND_LIMIT,       // forced by hand limit decision
    EFFECT            // discard caused by an effect (future)
}
