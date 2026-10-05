package dev.servereer.morphedtrials;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MorphedTrials' three region-selection tools — a separate item per selection kind, each PDC-tagged under
 * {@link MorphedTrialsPlugin#wandKey()} with a distinct value ({@link #REGION}/{@link #START}/{@link
 * #END}), so a plain item of the material is NOT a wand. Materials are deliberately picked to NOT collide
 * with anything already in use on this server: GriefPrevention's claim tool is {@code GOLDEN_SHOVEL} and
 * its investigation tool is {@code STICK}; WorldEdit's wand is {@code WOODEN_AXE} — none of those, or
 * KOTH's own {@code GOLDEN_AXE}/{@code GOLDEN_HOE}, are used here.
 *
 * <p>All three share ONE per-player {@link Selection} (left-click = corner 1, right-click = corner 2) —
 * which wand you're holding only changes the label/lore and which command reads it back
 * ({@code /mtrials dungeon <name> setregion} for the region wand; {@code /mtrials edit setstart}/{@code
 * setend} for the level wands, gated behind an active edit session — see {@link EditManager}).
 */
final class MTWand implements Listener {

    static final String REGION = "region";
    static final String START = "start";
    static final String END = "end";

    private final MorphedTrialsPlugin plugin;
    private final Map<UUID, Selection> selections = new ConcurrentHashMap<>();

    MTWand(MorphedTrialsPlugin plugin) {
        this.plugin = plugin;
    }

    Selection selectionOf(UUID player) { return selections.computeIfAbsent(player, k -> new Selection()); }
    void clear(UUID player) { selections.remove(player); }

    ItemStack build(String type) {
        Material mat = switch (type) {
            case START -> Material.DIAMOND_HOE;
            case END -> Material.IRON_HOE;
            default -> Material.NETHERITE_SHOVEL;
        };
        String name = switch (type) {
            case START -> "&b&lLevel Start Wand";
            case END -> "&e&lLevel End Wand";
            default -> "&d&lDungeon Region Wand";
        };
        List<String> lore = switch (type) {
            case START -> List.of(
                    plugin.color("&7Marks a level's START pad."),
                    plugin.color("&8» &fLeft-click&7 = corner 1, &fRight-click&7 = corner 2"),
                    plugin.color("&8» then, while in edit mode: &f/mtrials edit setstart"));
            case END -> List.of(
                    plugin.color("&7Marks a level's END pad."),
                    plugin.color("&8» &fLeft-click&7 = corner 1, &fRight-click&7 = corner 2"),
                    plugin.color("&8» then, while in edit mode: &f/mtrials edit setend"));
            default -> List.of(
                    plugin.color("&7Marks the dungeon's ONE shared region — set ONCE."),
                    plugin.color("&8» &fLeft-click&7 = corner 1, &fRight-click&7 = corner 2"),
                    plugin.color("&8» then: &f/mtrials dungeon <name> setregion"));
        };
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(plugin.wandKey(), PersistentDataType.STRING, type);
            meta.setDisplayName(plugin.color(name));
            meta.setLore(lore);
            it.setItemMeta(meta);
        }
        return it;
    }

    private String wandType(ItemStack item) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(plugin.wandKey(), PersistentDataType.STRING);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) return;
        String type = wandType(e.getItem());
        if (type == null) return;
        if (e.getAction() != Action.LEFT_CLICK_BLOCK && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        e.setCancelled(true);

        Selection sel = selectionOf(e.getPlayer().getUniqueId());
        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            sel.pos1 = e.getClickedBlock().getLocation();
            plugin.msg(e.getPlayer(), "&bCorner 1&7 set: &f" + fmt(sel.pos1));
        } else {
            sel.pos2 = e.getClickedBlock().getLocation();
            plugin.msg(e.getPlayer(), "&bCorner 2&7 set: &f" + fmt(sel.pos2));
        }
        if (sel.complete()) {
            plugin.msg(e.getPlayer(), "&aSelection ready &7— " + sel.toRegion().describe());
        }
    }

    private static String fmt(org.bukkit.Location l) {
        return l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }
}
