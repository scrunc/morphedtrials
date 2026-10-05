package dev.servereer.morphedtrials;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;

import java.io.IOException;
import java.util.List;

/** One dungeon's overview: region/wand, min-players + countdown (click to adjust), reward commands,
 *  delete — and one button into the level list ({@link LevelListMenu}), which used to be crammed onto
 *  this same page and got its own screen once there was more than a couple of levels to show. */
final class DungeonMenu implements MTMenu {

    private static final int REGION = 10, WAND = 11, MINPLAYERS = 12, COUNTDOWN = 13, REWARDS = 14, LEVELS = 15;
    private static final int DELETE = 20, BACK = 24;

    private final MorphedTrialsPlugin plugin;
    private final Dungeon d;
    private Inventory inv;

    DungeonMenu(MorphedTrialsPlugin plugin, Dungeon d) { this.plugin = plugin; this.d = d; }

    @Override public Inventory getInventory() { return inv; }

    void open(Player p) {
        inv = Bukkit.createInventory(this, 27, plugin.color("&b&l" + d.name()));
        render();
        p.openInventory(inv);
    }

    @Override
    public void click(int slot, ClickType ct, Player p) {
        switch (slot) {
            case WAND -> { p.getInventory().addItem(plugin.wand().build(MTWand.REGION)); plugin.msg(p, "&aHere's the Region Wand."); }
            case MINPLAYERS -> { d.setMinPlayers(d.minPlayers() + step(ct, 1, 5)); trySave(p); }
            case COUNTDOWN -> { d.setCountdownSeconds(d.countdownSeconds() + step(ct, 5, 30)); trySave(p); }
            case LEVELS -> new LevelListMenu(plugin, d, 0).open(p);
            case DELETE -> {
                if (ct.isShiftClick()) {
                    plugin.store().delete(d.name());
                    plugin.manager().invalidate(d.name());
                    plugin.msg(p, "&aDeleted &f" + d.name() + "&a.");
                    new DungeonListMenu(plugin).open(p);
                    return;
                }
                plugin.msg(p, "&cShift-click to confirm delete.");
            }
            case BACK -> new DungeonListMenu(plugin).open(p);
            default -> { }
        }
    }

    private int step(ClickType ct, int small, int big) {
        int m = ct.isShiftClick() ? big : small;
        return ct.isRightClick() ? -m : m;
    }

    private void trySave(Player p) {
        try { plugin.manager().save(d); render(); p.playSound(p.getLocation(), "ui.button.click", 1f, 1.2f); }
        catch (IOException e) { plugin.msg(p, "&cFailed to save: " + e.getMessage()); }
    }

    private void render() {
        MenuItems.chrome(plugin, inv, Material.CYAN_STAINED_GLASS_PANE, Material.BLACK_STAINED_GLASS_PANE);

        inv.setItem(REGION, MenuItems.item(plugin, d.region() != null ? Material.MAP : Material.BARRIER,
                d.region() != null ? "&aRegion set" : "&cRegion not set",
                d.region() != null ? List.of("&7" + d.region().describe()) : List.of("&7Get the Region Wand, select the whole", "&7dungeon box, then run:", "&f/mtrials dungeon " + d.name() + " setregion")));
        inv.setItem(WAND, MenuItems.item(plugin, Material.NETHERITE_SHOVEL, "&dGet Region Wand",
                List.of("&7Marks the ONE shared box every", "&7level's layout lives in.")));
        inv.setItem(MINPLAYERS, MenuItems.item(plugin, Material.PLAYER_HEAD, "&eMin players&8: &f" + d.minPlayers(),
                List.of("&8Left +1  Right -1  Shift ±5")));
        inv.setItem(COUNTDOWN, MenuItems.item(plugin, Material.CLOCK, "&eCountdown&8: &f" + d.countdownSeconds() + "s",
                List.of("&8Left +5s  Right -5s  Shift ±30s")));
        inv.setItem(REWARDS, MenuItems.item(plugin, Material.CHEST_MINECART, "&eReward commands&8: &f" + d.rewardCommands().size(),
                List.of("&7Run once, on full clear.", "&8/mtrials dungeon " + d.name() + " reward add <cmd>")));
        inv.setItem(LEVELS, MenuItems.item(plugin, Material.BOOKSHELF, "&bLevels&8: &f" + d.levels().size(),
                List.of("&7View, add, edit, reorder,", "&7duplicate, or test levels.", "&8Click to open")));

        inv.setItem(DELETE, MenuItems.item(plugin, Material.TNT, "&cDelete dungeon", List.of("&8Shift-click to confirm")));
        inv.setItem(BACK, MenuItems.item(plugin, Material.ARROW, "&fBack", List.of()));
    }
}
