package dev.servereer.morphedtrials;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** {@code /mtrials gui} — every dungeon, click to open its {@link DungeonMenu}. Creating a new one is
 *  still a command ({@code /mtrials dungeon create <name>}) — naming something over chat isn't worth a
 *  GUI flow for a one-off, rare action. */
final class DungeonListMenu implements MTMenu {

    private final MorphedTrialsPlugin plugin;
    private final List<String> slotDungeon = new ArrayList<>();
    private Inventory inv;

    DungeonListMenu(MorphedTrialsPlugin plugin) { this.plugin = plugin; }

    @Override public Inventory getInventory() { return inv; }

    void open(Player p) {
        inv = Bukkit.createInventory(this, 54, plugin.color("&b&lMorphedTrials &7— Dungeons"));
        render();
        p.openInventory(inv);
    }

    @Override
    public void click(int slot, ClickType ct, Player p) {
        if (slot < 0 || slot >= slotDungeon.size() || slotDungeon.get(slot) == null) return;
        String name = slotDungeon.get(slot);
        try {
            Dungeon d = plugin.manager().getOrLoad(name);
            new DungeonMenu(plugin, d).open(p);
        } catch (IOException e) {
            plugin.msg(p, "&cFailed to open: " + e.getMessage());
        }
    }

    private void render() {
        slotDungeon.clear();
        for (int i = 0; i < 54; i++) slotDungeon.add(null);
        MenuItems.chrome(plugin, inv, Material.CYAN_STAINED_GLASS_PANE, Material.BLACK_STAINED_GLASS_PANE);

        List<String> names = plugin.store().listNames();
        int slot = 10;
        for (String name : names) {
            while (slot < 45 && (slot % 9 == 0 || slot % 9 == 8)) slot++;
            if (slot >= 45) break;
            Dungeon d;
            try { d = plugin.manager().getOrLoad(name); } catch (IOException e) { continue; }
            inv.setItem(slot, MenuItems.item(plugin, Material.CHEST, "&b&l" + name, List.of(
                    "&7Levels&8: &f" + d.levels().size(),
                    "&7Region&8: " + (d.region() != null ? "&aset" : "&cnot set"),
                    "&7Min players&8: &f" + d.minPlayers(), "&7Countdown&8: &f" + d.countdownSeconds() + "s",
                    "&8Click to manage")));
            slotDungeon.set(slot, name);
            slot++;
        }
        if (names.isEmpty()) {
            inv.setItem(22, MenuItems.item(plugin, Material.BARRIER, "&7No dungeons yet",
                    List.of("&8/mtrials dungeon create <name>")));
        }
    }
}
