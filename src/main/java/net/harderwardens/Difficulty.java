package net.harderwardens;

/**
 * The six difficulty levels for Harder Wardens.
 * Configurable via config/harder_wardens.json.
 */
public enum Difficulty {
    /** Vanilla Warden HP, vanilla damage, improved loot */
    EASY,

    /** Vanilla Warden HP, 1.5x damage, standard loot (default) */
    NORMAL,

    /** 750 HP, 2x damage, good loot */
    HARD,

    /** 1000 HP, 2.5x damage, great loot */
    NIGHTMARE,

    /** 1500 HP, 3x damage, epic loot */
    INSANE,

    /** Fully customisable via config */
    CUSTOM
}
