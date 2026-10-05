package dev.servereer.morphedtrials;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** A dungeon's levels, paginated (28 per page — 4 rows × 7 columns) — its own screen so a dungeon with
 *  more than a couple of levels doesn't cram everything onto the overview page. */
final class LevelListMenu implements MTMenu {

    static final int PER_PAGE = 28;
    private static final int PREV = 45, BACK = 46, ADD_LEVEL = 49, NEXT = 53;

    private final MorphedTrialsPlugin plugin;
    private final Dungeon d;
    private final int page;   // 0-based
    private final List<Integer> slotLevel = new ArrayList<>();
    private Inventory inv;

    LevelListMenu(MorphedTrialsPlugin plugin, Dungeon d, int page) {
        this.plugin = plugin; this.d = d; this.page = Math.max(0, page);
    }

    @Override public Inventory getInventory() { return inv; }

    void open(Player p) {
        inv = Bukkit.createInventory(this, 54, plugin.color("&b&lLevels &7— " + d.name() + " &8(page " + (page + 1) + ")"));
        render();
        p.openInventory(inv);
    }

    @Override
    public void click(int slot, ClickType ct, Player p) {
        switch (slot) {
            case PREV -> { if (page > 0) new LevelListMenu(plugin, d, page - 1).open(p); }
            case NEXT -> { if ((page + 1) * PER_PAGE < d.levels().size()) new LevelListMenu(plugin, d, page + 1).open(p); }
            case ADD_LEVEL -> {
                Level l = d.addLevel();
                try { plugin.manager().save(d); plugin.msg(p, "&aAdded level " + l.index() + "."); }
                catch (IOException e) { plugin.msg(p, "&cFailed to save: " + e.getMessage()); }
                new LevelListMenu(plugin, d, page).open(p);
            }
            case BACK -> new DungeonMenu(plugin, d).open(p);
            default -> {
                int idx = slotLevel.indexOf(slot);
                if (idx >= 0) new LevelMenu(plugin, d, idx + 1 + page * PER_PAGE).open(p);
            }
        }
    }

    private void render() {
        MenuItems.chrome(plugin, inv, Material.CYAN_STAINED_GLASS_PANE, Material.BLACK_STAINED_GLASS_PANE);
        slotLevel.clear();

        int from = page * PER_PAGE;
        int to = Math.min(d.levels().size(), from + PER_PAGE);
        int slot = 9;
        for (int i = from; i < to; i++) {
            while (slot < 45 && (slot % 9 == 0 || slot % 9 == 8)) slot++;
            if (slot >= 45) break;
            Level level = d.levels().get(i);
            boolean ready = level.hasRegions() && plugin.store().hasSnapshot(d.name(), level.index());
            inv.setItem(slot, MenuItems.item(plugin, typeIcon(level, ready),
                    (ready ? "&a" : "&e") + "Level " + level.index() + " &8[" + level.type() + "]",
                    List.of("&7start&8: " + (level.startRegion() != null ? "&aset" : "&8not set"),
                            "&7end&8: " + (level.endRegion() != null ? "&aset" : "&8not set"),
                            "&7captured&8: " + (plugin.store().hasSnapshot(d.name(), level.index()) ? "&ayes" : "&8no"),
                            "&7mobs&8: &f" + level.mobs().size(),
                            "&7style&8: &f" + (level.morphStyle() != null ? level.morphStyle() : "(default)"),
                            "&8Click to edit")));
            slotLevel.add(slot);
            slot++;
        }
        if (d.levels().isEmpty()) {
            inv.setItem(22, MenuItems.item(plugin, Material.BARRIER, "&7No levels yet", List.of("&8Click + Add level below")));
        }

        inv.setItem(PREV, page > 0
                ? MenuItems.item(plugin, Material.ARROW, "&f« Previous page", List.of())
                : MenuItems.item(plugin, Material.GRAY_DYE, "&8« Previous page", List.of()));
        inv.setItem(NEXT, (page + 1) * PER_PAGE < d.levels().size()
                ? MenuItems.item(plugin, Material.ARROW, "&fNext page »", List.of())
                : MenuItems.item(plugin, Material.GRAY_DYE, "&8Next page »", List.of()));
        inv.setItem(ADD_LEVEL, MenuItems.item(plugin, Material.LIME_DYE, "&a+ Add level", List.of("&7Appends a new empty level.")));
        inv.setItem(BACK, MenuItems.item(plugin, Material.ARROW, "&fBack", List.of()));
    }

    private static Material typeIcon(Level level, boolean ready) {
        return switch (level.type()) {
            case LOOT -> Material.CHEST;
            case BOSS -> Material.WITHER_SKELETON_SKULL;
            case NORMAL -> ready ? Material.END_CRYSTAL : Material.MAGMA_CREAM;
        };
    }
}
