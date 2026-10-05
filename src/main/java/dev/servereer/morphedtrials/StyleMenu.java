package dev.servereer.morphedtrials;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** A level's morph-style picker — mirrors KOTH's MorphMenu style-cycling slot exactly: left/right-click
 *  cycles through {@link MTBridge#styleIds()}, saves immediately. */
final class StyleMenu implements MTMenu {

    private static final int STYLE = 13, USE_DEFAULT = 15, BACK = 18;

    private final MorphedTrialsPlugin plugin;
    private final Dungeon d;
    private final int levelIndex;
    private final Runnable onBack;
    private Inventory inv;

    StyleMenu(MorphedTrialsPlugin plugin, Dungeon d, int levelIndex, Runnable onBack) {
        this.plugin = plugin; this.d = d; this.levelIndex = levelIndex; this.onBack = onBack;
    }

    @Override public Inventory getInventory() { return inv; }

    void open(Player p) {
        inv = Bukkit.createInventory(this, 27, plugin.color("&b&lStyle &7— Level " + levelIndex));
        render();
        p.openInventory(inv);
    }

    @Override
    public void click(int slot, ClickType ct, Player p) {
        Level level = d.level(levelIndex);
        if (level == null) return;
        if (slot == STYLE) {
            List<String> ids = plugin.bridge().styleIds();
            if (ids.isEmpty()) { plugin.msg(p, "&cMorphCore isn't installed — no styles available."); return; }
            String cur = level.morphStyle() != null ? level.morphStyle() : ids.get(0);
            int idx = ids.indexOf(cur);
            level.setMorphStyle(ids.get((idx + 1 + ids.size()) % ids.size()));
            trySave(p);
        } else if (slot == USE_DEFAULT) {
            level.setMorphStyle(null);
            trySave(p);
        } else if (slot == BACK) {
            if (onBack != null) onBack.run(); else p.closeInventory();
        }
    }

    private void trySave(Player p) {
        try { plugin.manager().save(d); render(); p.playSound(p.getLocation(), "ui.button.click", 1f, 1.2f); }
        catch (IOException e) { plugin.msg(p, "&cFailed to save: " + e.getMessage()); }
    }

    private void render() {
        MenuItems.chrome(plugin, inv, Material.PURPLE_STAINED_GLASS_PANE, Material.BLACK_STAINED_GLASS_PANE);
        Level level = d.level(levelIndex);
        String style = level.morphStyle() != null ? level.morphStyle() : "(default)";
        String effective = level.morphStyle() != null ? level.morphStyle()
                : plugin.getConfig().getString("morph.morph-style", "sweep");

        List<String> lore = new ArrayList<>(plugin.bridge().lore(effective));
        lore.add("&8Left/Right to cycle");
        inv.setItem(STYLE, MenuItems.item(plugin, plugin.bridge().icon(effective),
                "&bStyle&8: &f" + plugin.bridge().displayName(effective) + (level.morphStyle() == null ? " &7(default)" : ""), lore));
        inv.setItem(USE_DEFAULT, MenuItems.item(plugin, Material.GRAY_DYE, "&7Use dungeon default",
                List.of("&8Clears this level's own override")));
        inv.setItem(BACK, MenuItems.item(plugin, Material.ARROW, "&fBack", List.of()));
    }
}
