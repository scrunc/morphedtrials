package dev.servereer.morphedtrials;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One floor of a dungeon: what its captured layout ({@link LevelSnapshot}, in the dungeon's one shared
 * region — see {@link Dungeon#region()}) morphs into, the pad that spawns its mobs, the pad that (once
 * they're all dead) advances the whole party to the next level, and the mob spawn points themselves.
 * {@link #morphStyle} is this level's own style override, picked via the in-GUI style picker — null
 * falls back to the plugin's global default (see {@code config.yml}'s {@code morph.morph-style}).
 */
public final class Level {

    /** Changes what {@link DungeonManager} does once the party lands: see each constant. */
    public enum Type {
        /** Spawn the configured mobs, exit unlocks once they're all dead. The default. */
        NORMAL,
        /** No combat gate — the exit is unlocked the instant the party arrives, whether or not any mobs
         *  are configured. For a breather/treasure room: build the loot in physically (real chests), the
         *  plugin's only job is not making anyone fight for it. */
        LOOT,
        /** Same clear-gate as NORMAL, but its mobs get a shared, live-updating boss health bar shown to
         *  the whole party for as long as any of them are alive. */
        BOSS;

        public static Type parse(String raw, Type def) {
            if (raw == null) return def;
            try { return Type.valueOf(raw.trim().toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException e) { return def; }
        }
    }

    private int index;   // 1-based — level 1, level 2, ... mutable so Dungeon#removeLevel can renumber
                          // everything after a deletion; nothing else should ever call setIndex.
    private Region startRegion;
    private Region endRegion;
    private final List<MobSpawn> mobs = new ArrayList<>();
    private String morphStyle;   // null = use the global default
    private Type type = Type.NORMAL;

    Level(int index) { this.index = index; }

    public int index() { return index; }
    void setIndex(int i) { this.index = i; }

    public Region startRegion() { return startRegion; }
    public void setStartRegion(Region r) { this.startRegion = r; }

    public Region endRegion() { return endRegion; }
    public void setEndRegion(Region r) { this.endRegion = r; }

    public List<MobSpawn> mobs() { return mobs; }

    public String morphStyle() { return morphStyle; }
    public void setMorphStyle(String style) { this.morphStyle = style; }

    public Type type() { return type; }
    public void setType(Type t) { this.type = t == null ? Type.NORMAL : t; }
    public Type nextType() { Type[] vals = Type.values(); return vals[(type.ordinal() + 1) % vals.length]; }

    /** True once start + end are both set — capture is checked separately since it's a file on disk. */
    public boolean hasRegions() { return startRegion != null && endRegion != null; }
}
