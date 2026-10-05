package dev.servereer.morphedtrials;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;

import java.io.IOException;
import java.util.List;

/** One level's editor: enter edit mode (arms the wands + starts live highlighting), pick its morph
 *  style, cycle its type (Normal/Loot/Boss), test it solo, duplicate it as a starting point for the next
 *  level, delete it, or clear its mobs. */
final class LevelMenu implements MTMenu {

    private static final int STATUS = 13;
    private static final int TYPE = 19, EDIT = 21, STYLE = 23, TEST = 25;
    private static final int CLEAR_MOBS = 28, DUPLICATE = 30, DELETE = 32, BACK = 34;

    private final MorphedTrialsPlugin plugin;
    private final Dungeon d;
    private final int index;
    private Inventory inv;

    LevelMenu(MorphedTrialsPlugin plugin, Dungeon d, int index) { this.plugin = plugin; this.d = d; this.index = index; }

    @Override public Inventory getInventory() { return inv; }

    void open(Player p) {
        inv = Bukkit.createInventory(this, 36, plugin.color("&b&lLevel " + index + " &7— " + d.name()));
        render();
        p.openInventory(inv);
    }

    @Override
    public void click(int slot, ClickType ct, Player p) {
        Level level = d.level(index);
        if (level == null) return;
        switch (slot) {
            case TYPE -> {
                level.setType(level.nextType());
                trySave(p, "&aLevel " + index + " is now &f" + level.type() + "&a.");
            }
            case EDIT -> {
                p.closeInventory();
                plugin.editManager().enter(p, d, index);
            }
            case STYLE -> new StyleMenu(plugin, d, index, () -> open(p)).open(p);
            case TEST -> {
                p.closeInventory();
                plugin.manager().testLevel(p, d, index, p);
            }
            case CLEAR_MOBS -> {
                level.mobs().clear();
                trySave(p, "&aCleared this level's mobs.");
            }
            case DUPLICATE -> {
                Level copy = d.duplicateLevel(index);
                if (copy == null) return;
                try {
                    plugin.store().copySnapshot(d.name(), index, copy.index());
                    plugin.manager().save(d);
                    plugin.msg(p, "&aDuplicated level " + index + " as level " + copy.index() + ".");
                } catch (IOException e) { plugin.msg(p, "&cFailed: " + e.getMessage()); }
                back(p);
            }
            case DELETE -> {
                if (!ct.isShiftClick()) { plugin.msg(p, "&cShift-click to confirm delete."); return; }
                int countBefore = d.levels().size();
                if (!d.removeLevel(index)) return;
                plugin.store().deleteLevelSnapshotAndShift(d.name(), index, countBefore);
                try { plugin.manager().save(d); plugin.msg(p, "&aDeleted level " + index + " — later levels renumbered."); }
                catch (IOException e) { plugin.msg(p, "&cFailed to save: " + e.getMessage()); }
                back(p);
            }
            case BACK -> back(p);
            default -> { }
        }
    }

    private void back(Player p) {
        new LevelListMenu(plugin, d, (index - 1) / LevelListMenu.PER_PAGE).open(p);
    }

    private void trySave(Player p, String okMessage) {
        try { plugin.manager().save(d); render(); plugin.msg(p, okMessage); }
        catch (IOException e) { plugin.msg(p, "&cFailed to save: " + e.getMessage()); }
    }

    private void render() {
        MenuItems.chrome(plugin, inv, Material.CYAN_STAINED_GLASS_PANE, Material.BLACK_STAINED_GLASS_PANE);
        Level level = d.level(index);
        boolean captured = plugin.store().hasSnapshot(d.name(), index);

        inv.setItem(STATUS, MenuItems.item(plugin, captured ? Material.END_CRYSTAL : Material.MAGMA_CREAM,
                "&bLevel " + index, List.of(
                        "&7type&8: &f" + level.type(),
                        "&7start&8: " + (level.startRegion() != null ? "&aset" : "&cnot set"),
                        "&7end&8: " + (level.endRegion() != null ? "&aset" : "&cnot set"),
                        "&7captured&8: " + (captured ? "&ayes" : "&cno"),
                        "&7mobs&8: &f" + level.mobs().size(),
                        "&7style&8: &f" + (level.morphStyle() != null ? level.morphStyle() : "(default)"))));

        inv.setItem(TYPE, MenuItems.item(plugin, typeIcon(level.type()), "&bType&8: &f" + level.type(), typeLore(level.type())));
        inv.setItem(EDIT, MenuItems.item(plugin, Material.WRITABLE_BOOK, "&aEnter edit mode",
                List.of("&7Arms the Start/End wands and", "&7highlights region+start+end.", "&7Use &f/mtrials edit ...&7 while active.")));
        inv.setItem(STYLE, MenuItems.item(plugin, plugin.bridge().icon(level.morphStyle() != null ? level.morphStyle() : "sweep"),
                "&bMorph style", List.of("&7This level's own animation style.", "&8Click to pick")));
        inv.setItem(TEST, MenuItems.item(plugin, Material.FIREWORK_ROCKET, "&aTest this level solo",
                List.of("&7Morphs it in and plays it out", "&7just for you — no party needed.", "&8/mtrials leave to stop")));

        inv.setItem(CLEAR_MOBS, MenuItems.item(plugin, Material.BONE, "&cClear mobs", List.of("&7Wipes this level's spawn list.")));
        inv.setItem(DUPLICATE, MenuItems.item(plugin, Material.ARMOR_STAND, "&eDuplicate",
                List.of("&7Appends a copy of this level", "&7(start/end/mobs/style/type)", "&7as a starting point for the next.")));
        inv.setItem(DELETE, MenuItems.item(plugin, Material.TNT, "&cDelete level",
                List.of("&7Later levels renumber down.", "&8Shift-click to confirm")));
        inv.setItem(BACK, MenuItems.item(plugin, Material.ARROW, "&fBack", List.of()));
    }

    private static Material typeIcon(Level.Type type) {
        return switch (type) {
            case NORMAL -> Material.ZOMBIE_HEAD;
            case LOOT -> Material.CHEST;
            case BOSS -> Material.WITHER_SKELETON_SKULL;
        };
    }

    private static List<String> typeLore(Level.Type type) {
        return switch (type) {
            case NORMAL -> List.of("&7Spawn mobs, clear to advance.", "&8Click to cycle");
            case LOOT -> List.of("&7No combat gate — exit unlocks", "&7the instant the party arrives.", "&8Build the treasure in physically.", "&8Click to cycle");
            case BOSS -> List.of("&7Same clear-gate as Normal, plus", "&7a shared live boss health bar", "&7while its mobs are alive.", "&8Click to cycle");
        };
    }
}
