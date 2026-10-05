package dev.servereer.morphedtrials;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

/** One dispatcher for every MorphedTrials GUI screen — see {@link MTMenu}. */
final class GuiListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof MTMenu menu)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() == null || !e.getClickedInventory().equals(e.getInventory())) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;
        menu.click(e.getRawSlot(), e.getClick(), p);
    }
}
