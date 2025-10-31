package me.mrostrich.survivalgames.state;

/**
 * Canonical match state used across the refactored Survival Games plugin.
 * KEEP STABLE: other classes depend on these exact enum names.
 */
public enum MatchState {
    WAITING,    // Pre-game lobby, players can prepare
    GRACE,      // Obviously Grace Period dumbass
    FIGHT,    // Main fight phase (after grace)
    FINAL_FIGHT,// Accelerated border / final confrontation
    ENDED       // Match finished
}