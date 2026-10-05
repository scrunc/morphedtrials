package dev.servereer.morphedtrials;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * File-backed store for dungeons under {@code plugins/MorphedTrials/dungeons/<name>/}: one {@code
 * dungeon.yml} (region, min-players/countdown, levels' pads/mobs/style, reward commands) plus one {@code
 * level-<N>.snap} per captured level ({@link LevelSnapshot}, gzip binary — same shape as KOTH's {@code
 * ArenaSnapshot} files).
 */
final class DungeonStore {

    private final File root;

    DungeonStore(File pluginDataFolder) {
        this.root = new File(pluginDataFolder, "dungeons");
    }

    static String sanitize(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
    }

    private File dir(String name) { return new File(root, sanitize(name)); }
    private File yml(String name) { return new File(dir(name), "dungeon.yml"); }
    File snapshotFile(String name, int levelIndex) { return new File(dir(name), "level-" + levelIndex + ".snap"); }

    boolean exists(String name) { return yml(name).isFile(); }
    boolean hasSnapshot(String name, int levelIndex) { return snapshotFile(name, levelIndex).isFile(); }

    List<String> listNames() {
        List<String> out = new ArrayList<>();
        File[] dirs = root.listFiles(File::isDirectory);
        if (dirs == null) return out;
        for (File d : dirs) if (new File(d, "dungeon.yml").isFile()) out.add(d.getName());
        out.sort(String::compareTo);
        return out;
    }

    void delete(String name) { deleteRecursive(dir(name)); }

    private static void deleteRecursive(File f) {
        if (!f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteRecursive(k);
        f.delete();
    }

    LevelSnapshot loadSnapshot(String name, int levelIndex) throws IOException {
        return LevelSnapshot.load(snapshotFile(name, levelIndex));
    }

    void saveSnapshot(String name, int levelIndex, LevelSnapshot snap) throws IOException {
        snap.save(snapshotFile(name, levelIndex));
    }

    /** Deletes {@code deletedIndex}'s own snapshot file, then shifts every later level's file down by one
     *  name to match {@link Dungeon#removeLevel} having just renumbered the in-memory levels the same
     *  way. {@code levelCountBeforeDelete} is the dungeon's level count BEFORE the removal (i.e. including
     *  the one just deleted) — call this before or after mutating the in-memory list, doesn't matter, as
     *  long as this count is the pre-delete one. */
    void deleteLevelSnapshotAndShift(String name, int deletedIndex, int levelCountBeforeDelete) {
        File del = snapshotFile(name, deletedIndex);
        if (del.isFile()) del.delete();
        for (int i = deletedIndex + 1; i <= levelCountBeforeDelete; i++) {
            File src = snapshotFile(name, i);
            if (src.isFile()) src.renameTo(snapshotFile(name, i - 1));
        }
    }

    /** Copies {@code fromIndex}'s captured snapshot to {@code toIndex} — used by {@link
     *  Dungeon#duplicateLevel}. No-op (not an error) if {@code fromIndex} was never captured. */
    void copySnapshot(String name, int fromIndex, int toIndex) throws IOException {
        File src = snapshotFile(name, fromIndex);
        if (!src.isFile()) return;
        File dst = snapshotFile(name, toIndex);
        File parent = dst.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        java.nio.file.Files.copy(src.toPath(), dst.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    Dungeon load(String rawName) throws IOException {
        String name = sanitize(rawName);
        File f = yml(name);
        if (!f.isFile()) throw new IOException("No dungeon '" + name + "'.");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        Dungeon d = new Dungeon(name);

        String region = y.getString("region");
        if (region != null) d.setRegion(Region.deserialize(region));
        d.setMinPlayers(y.getInt("min-players", 1));
        d.setCountdownSeconds(y.getInt("countdown-seconds", 15));
        for (String cmd : y.getStringList("reward-commands")) d.rewardCommands().add(cmd);

        ConfigurationSection levels = y.getConfigurationSection("levels");
        if (levels != null) {
            List<String> keys = new ArrayList<>(levels.getKeys(false));
            keys.sort((a, b) -> Integer.compare(Integer.parseInt(a), Integer.parseInt(b)));
            for (String key : keys) {
                ConfigurationSection ls = levels.getConfigurationSection(key);
                if (ls == null) continue;
                Level level = d.addLevel();   // relies on levels.yml keys being contiguous 1..N
                String start = ls.getString("start");
                String end = ls.getString("end");
                if (start != null) level.setStartRegion(Region.deserialize(start));
                if (end != null) level.setEndRegion(Region.deserialize(end));
                level.setMorphStyle(ls.getString("morph-style"));
                level.setType(Level.Type.parse(ls.getString("type"), Level.Type.NORMAL));
                for (String m : ls.getStringList("mobs")) level.mobs().add(MobSpawn.deserialize(m));
            }
        }
        return d;
    }

    void save(Dungeon d) throws IOException {
        YamlConfiguration y = new YamlConfiguration();
        if (d.region() != null) y.set("region", d.region().serialize());
        y.set("min-players", d.minPlayers());
        y.set("countdown-seconds", d.countdownSeconds());
        y.set("reward-commands", d.rewardCommands());
        for (Level level : d.levels()) {
            String base = "levels." + level.index();
            if (level.startRegion() != null) y.set(base + ".start", level.startRegion().serialize());
            if (level.endRegion() != null) y.set(base + ".end", level.endRegion().serialize());
            if (level.morphStyle() != null) y.set(base + ".morph-style", level.morphStyle());
            if (level.type() != Level.Type.NORMAL) y.set(base + ".type", level.type().name());
            List<String> mobs = new ArrayList<>();
            for (MobSpawn m : level.mobs()) mobs.add(m.serialize());
            y.set(base + ".mobs", mobs);
        }
        File f = yml(d.name());
        File parent = f.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        y.save(f);
    }
}
