package dev.servereer.morphedtrials;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** One dungeon's live attempt — a party of players moving through its levels together. Mobs, clear
 *  state, and level progress are shared; only the "have I personally stepped onto the pad yet" edge
 *  tracking is per player (people walk in at slightly different times). */
final class DungeonSession {

    enum State { LOBBY, COUNTDOWN, ACTIVE }

    final String dungeonName;
    final Set<UUID> participants = new LinkedHashSet<>();
    State state = State.LOBBY;
    int levelIndex = 1;
    int countdownSecondsLeft;
    final Set<UUID> spawnedMobs = new HashSet<>();
    boolean mobsSpawnedOnce;
    boolean countdownStarted;   // guards startCountdown() itself — set the instant it BEGINS, not when
                                 // mobs actually spawn, so a second trigger (e.g. the move-listener
                                 // fallback firing moments after the morph-completion callback already
                                 // started one) can't open a duplicate boss bar / skip the count.
    boolean cleared;
    final Map<UUID, Boolean> wasInStart = new HashMap<>();
    final Map<UUID, Boolean> wasInEnd = new HashMap<>();
    final Set<UUID> onEndPad = new HashSet<>();   // who's standing on it RIGHT NOW — advance needs a headcount, not one visit

    DungeonSession(String dungeonName) {
        this.dungeonName = dungeonName;
    }

    /** At least half the party (rounded up) standing on the end pad at once — not just one person having
     *  walked through it. */
    boolean enoughOnEndPad() {
        int required = (int) Math.ceil(participants.size() / 2.0);
        return onEndPad.size() >= required;
    }

    void resetForLevel(int index) {
        levelIndex = index;
        spawnedMobs.clear();
        mobsSpawnedOnce = false;
        countdownStarted = false;
        cleared = false;
        wasInStart.clear();
        wasInEnd.clear();
        onEndPad.clear();
    }
}
