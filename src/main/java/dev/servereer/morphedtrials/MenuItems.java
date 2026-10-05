package dev.servereer.morphedtrials;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Shared GUI item-building helpers so the four menu classes don't each reinvent them. */
final class MenuItems {

    private MenuItems() { }

    static ItemStack pane(MorphedTrialsPlugin plugin, Material m) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) { meta.setDisplayName(" "); it.setItemMeta(meta); }
        return it;
    }

    static ItemStack item(MorphedTrialsPlugin plugin, Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.color(name));
            List<String> l = new ArrayList<>();
            for (String s : lore) l.add(plugin.color(s));
            meta.setLore(l);
            it.setItemMeta(meta);
        }
        return it;
    }

    /** Borders + filler for a 54-slot menu, using {@code border} for the outer ring. */
    static void chrome(MorphedTrialsPlugin plugin, org.bukkit.inventory.Inventory inv, Material border, Material filler) {
        ItemStack b = pane(plugin, border), f = pane(plugin, filler);
        for (int i = 0; i < inv.getSize(); i++) {
            int row = i / 9, col = i % 9;
            boolean edge = row == 0 || row == (inv.getSize() / 9 - 1) || col == 0 || col == 8;
            inv.setItem(i, edge ? b : f);
        }
    }
}
