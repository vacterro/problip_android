package com.vacster.problip.settings

/**
 * Main-screen presentation mode of the blip counter. OFF hides the strip,
 * TOTAL keeps the legacy one-line total, STATS shows the four-period strip.
 * Recording and the 100K reward are unaffected by the mode.
 */
enum class BlipCounterMode {
    OFF,
    TOTAL,
    STATS,
}
