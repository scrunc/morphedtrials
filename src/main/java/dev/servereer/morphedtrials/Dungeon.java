package dev.servereer.morphedtrials;

import java.util.ArrayList;
import java.util.List;

/** A named dungeon: ONE shared physical region (set once, via the Region Wand) that a chain of {@link
 *  Level}s morph through in sequence as a party of players clears them together. */
public final class Dungeon {

    private final String name;
    private Region region;                 // the one shared box — every level captures FROM this
    private int minPlayers = 1;
    private int countdownSeconds = 15;
    private final List<Level> levels = new ArrayList<>();
    private final List<String> rewardCommands = new ArrayList<>();   // run once, on full clear; %player% token

    Dungeon(String name) { this.name = name; }

    public String name() { return name; }
    public Region region() { return region; }
    public void setRegion(Region r) { this.region = r; }
    public int minPlayers() { return minPlayers; }
    public void setMinPlayers(int n) { this.minPlayers = Math.max(1, n); }
    public int countdownSeconds() { return countdownSeconds; }
    public void setCountdownSeconds(int s) { this.countdownSeconds = Math.max(0, s); }
    public List<Level> levels() { return levels; }
    public List<String> rewardCommands() { return rewardCommands; }

    /** 1-based lookup — level(1) is the first level, null if out of range. */
    public Level level(int index) {
        return (index >= 1 && index <= levels.size()) ? levels.get(index - 1) : null;
    }

    public Level addLevel() {
        Level l = new Level(levels.size() + 1);
        levels.add(l);
        return l;
    }

    /** Removes level {@code index} and renumbers every level after it down by one, so the chain stays
     *  contiguous 1..N. Caller is responsible for shifting the on-disk snapshot files to match — see
     *  {@link DungeonStore#deleteLevelSnapshotAndShift}. */
    public boolean removeLevel(int index) {
        Level l = level(index);
        if (l == null) return false;
        levels.remove(index - 1);
        for (int i = index - 1; i < levels.size(); i++) levels.get(i).setIndex(i + 1);
        return true;
    }

    /** Appends a NEW level at the end, copying {@code source}'s start/end/style/mobs — a starting point
     *  for the next level rather than building one from scratch. Does NOT copy the captured snapshot
     *  file; caller does that (the new level's index is only known once this returns) — see {@link
     *  DungeonStore#copySnapshot}. {@code null} if {@code sourceIndex} doesn't exist. */
    public Level duplicateLevel(int sourceIndex) {
        Level source = level(sourceIndex);
        if (source == null) return null;
        Level copy = addLevel();
        copy.setStartRegion(source.startRegion());
        copy.setEndRegion(source.endRegion());
        copy.setMorphStyle(source.morphStyle());
        copy.setType(source.type());
        copy.mobs().addAll(source.mobs());   // MobSpawn is an immutable record — safe to share instances
        return copy;
    }

    public boolean isLast(int index) { return index >= levels.size(); }
}
