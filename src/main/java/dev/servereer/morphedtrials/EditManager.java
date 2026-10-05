package dev.servereer.morphedtrials;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-admin "level editing mode" — the gate that stops the start/end wands (and {@code /mtrials edit
 * ...}) from writing anything unless you've deliberately entered edit mode for one specific level first.
 * While active, the dungeon's region + this level's start/end pads are continuously highlighted (three
 * colours) so you can see everything without guessing coordinates.
 */
final class EditManager {

    record Session(String dungeonName, int levelIndex) { }

    private final MorphedTrialsPlugin plugin;
    private final Map<UUID, Session> editing = new HashMap<>();

    EditManager(MorphedTrialsPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 6L);
    }

    Session sessionOf(UUID player) { return editing.get(player); }

    void enter(Player p, Dungeon d, int levelIndex) {
        editing.put(p.getUniqueId(), new Session(d.name(), levelIndex));
        plugin.msg(p, "&aEditing &f" + d.name() + " &7level &f" + levelIndex + "&7 — region/start/end are highlighted.");
        plugin.msg(p, "&7Use the wands + &f/mtrials edit <setstart|setend|capture|addmob|clearmobs|style|info|done>");
    }

    void exit(Player p) {
        Session s = editing.remove(p.getUniqueId());
        if (s != null) plugin.msg(p, "&7Left edit mode for &f" + s.dungeonName() + " level " + s.levelIndex() + "&7.");
    }

    private void tick() {
        if (editing.isEmpty()) return;
        for (Map.Entry<UUID, Session> en : editing.entrySet()) {
            Player p = Bukkit.getPlayer(en.getKey());
            if (p == null) { continue; }
            Dungeon d;
            try { d = plugin.manager().getOrLoad(en.getValue().dungeonName()); }
            catch (java.io.IOException e) { continue; }
            Level level = d.level(en.getValue().levelIndex());
            if (level == null) continue;
            World w = p.getWorld();
            if (d.region() != null) drawBox(w, d.region(), Color.fromRGB(0x9B59FF));   // dungeon box: purple
            if (level.startRegion() != null) drawBox(w, level.startRegion(), Color.fromRGB(0x41E0FF));   // start: cyan
            if (level.endRegion() != null) drawBox(w, level.endRegion(), Color.fromRGB(0xFFD24C));       // end: gold
        }
    }

    private void drawBox(World w, Region r, Color color) {
        RegionHighlighter.drawBoxColored(w, r, color);
    }
}
