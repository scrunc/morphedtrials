package dev.servereer.morphedtrials;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.InventoryHolder;

/** Common shape every MorphedTrials GUI screen implements — see {@link GuiListener} for the single
 *  click dispatcher shared by all of them. */
interface MTMenu extends InventoryHolder {
    void click(int slot, ClickType ct, Player p);
}
