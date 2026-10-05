package dev.servereer.morphedtrials;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /mtrials <gui|wand|reload|join|leave|dungeon>} — see plugin_customized/morphedtrials/meta.yaml
 * for the full command reference. Most authoring now happens through the GUI ({@code /mtrials gui}) and
 * edit mode ({@code /mtrials edit ...}, only valid once you've entered edit mode for a level from the
 * GUI); the commands below are the ones that don't have (or don't need) a menu equivalent.
 */
final class MTCommand implements CommandExecutor, TabCompleter {

    private final MorphedTrialsPlugin plugin;

    MTCommand(MorphedTrialsPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) { usage(sender); return true; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "gui" -> cmdGui(sender);
            case "wand" -> cmdWand(sender, args);
            case "reload" -> cmdReload(sender);
            case "join" -> cmdJoin(sender, args);
            case "leave" -> cmdLeave(sender, args);
            case "edit" -> cmdEdit(sender, args);
            case "dungeon" -> cmdDungeon(sender, args);
            default -> usage(sender);
        }
        return true;
    }

    private void usage(CommandSender s) {
        plugin.msg(s, "&b/mtrials gui&7, &b/mtrials wand <region|start|end>&7, &b/mtrials join <name>&7, &b/mtrials edit ...&7, &b/mtrials dungeon ...");
    }

    private void cmdGui(CommandSender s) {
        if (!(s instanceof Player p)) { plugin.msg(s, "&cPlayers only."); return; }
        if (!p.hasPermission("morphedtrials.admin")) { plugin.msg(s, "&cNo permission."); return; }
        new DungeonListMenu(plugin).open(p);
    }

    private void cmdWand(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) { plugin.msg(s, "&cPlayers only."); return; }
        if (!p.hasPermission("morphedtrials.admin")) { plugin.msg(s, "&cNo permission."); return; }
        String type = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : MTWand.REGION;
        if (!type.equals(MTWand.REGION) && !type.equals(MTWand.START) && !type.equals(MTWand.END)) {
            plugin.msg(s, "&cUsage: /mtrials wand <region|start|end>"); return;
        }
        p.getInventory().addItem(plugin.wand().build(type));
        plugin.msg(p, "&aHere's your " + type + " wand.");
    }

    private void cmdReload(CommandSender s) {
        if (!s.hasPermission("morphedtrials.admin")) { plugin.msg(s, "&cNo permission."); return; }
        plugin.reloadConfig();
        plugin.manager().reloadAll();
        plugin.msg(s, "&aReloaded config + dungeon cache.");
    }

    private void cmdJoin(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) { plugin.msg(s, "&cPlayers only."); return; }
        if (!p.hasPermission("morphedtrials.use")) { plugin.msg(s, "&cNo permission."); return; }
        if (args.length < 2) { plugin.msg(s, "&cUsage: /mtrials join <dungeon>"); return; }
        Dungeon d;
        try { d = plugin.manager().getOrLoad(args[1]); }
        catch (IOException e) { plugin.msg(s, "&cNo dungeon '&f" + args[1] + "&c'."); return; }
        plugin.manager().join(s, p, d);
    }

    private void cmdLeave(CommandSender s, String[] args) {
        Player target = resolveTarget(s, args, 1);
        if (target == null) { plugin.msg(s, "&cSpecify a player, or run this as one."); return; }
        plugin.manager().leave(s, target);
    }

    // --- edit mode: applies to whatever level the sender currently has open via the GUI's "Enter edit mode" ---

    private void cmdEdit(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) { plugin.msg(s, "&cPlayers only."); return; }
        if (!p.hasPermission("morphedtrials.admin")) { plugin.msg(s, "&cNo permission."); return; }
        EditManager.Session session = plugin.editManager().sessionOf(p.getUniqueId());
        if (session == null) { plugin.msg(p, "&cNot in edit mode — open a level in &f/mtrials gui&c and click 'Enter edit mode'."); return; }
        Dungeon d;
        try { d = plugin.manager().getOrLoad(session.dungeonName()); }
        catch (IOException e) { plugin.msg(p, "&cFailed to load dungeon: " + e.getMessage()); return; }
        Level level = d.level(session.levelIndex());
        if (level == null) { plugin.msg(p, "&cThat level no longer exists."); return; }

        if (args.length < 2) { plugin.msg(p, "&cUsage: /mtrials edit <setstart|setend|capture|addmob|clearmobs|style|info|done>"); return; }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "setstart" -> {
                Selection sel = plugin.wand().selectionOf(p.getUniqueId());
                if (!sel.complete()) { plugin.msg(p, "&cComplete a Start Wand selection first."); return; }
                level.setStartRegion(sel.toRegion());
                trySave(p, d, "&aSet level " + level.index() + "'s start pad.");
            }
            case "setend" -> {
                Selection sel = plugin.wand().selectionOf(p.getUniqueId());
                if (!sel.complete()) { plugin.msg(p, "&cComplete an End Wand selection first."); return; }
                level.setEndRegion(sel.toRegion());
                trySave(p, d, "&aSet level " + level.index() + "'s end pad.");
            }
            case "capture" -> {
                if (d.region() == null) { plugin.msg(p, "&cSet the dungeon's region first (&f/mtrials wand region&c + &f/mtrials dungeon " + d.name() + " setregion&c)."); return; }
                long volume = (long) d.region().sizeX() * d.region().sizeY() * d.region().sizeZ();
                long warn = plugin.getConfig().getLong("capture-warn-volume", 500_000L);
                if (volume > warn) { plugin.msg(p, "&cRegion is huge (&f" + volume + "&c blocks). Refusing — raise capture-warn-volume to allow it."); return; }
                org.bukkit.World w = Bukkit.getWorld(d.region().world());
                if (w == null) { plugin.msg(p, "&cThat world isn't loaded."); return; }
                LevelSnapshot snap = LevelSnapshot.capture(w, d.region());
                try {
                    plugin.store().saveSnapshot(d.name(), level.index(), snap);
                    plugin.msg(p, "&aCaptured level " + level.index() + "'s layout &7(" + volume + " blocks)&a.");
                } catch (IOException e) { plugin.msg(p, "&cFailed to save: " + e.getMessage()); }
            }
            case "addmob" -> {
                if (args.length < 3) { plugin.msg(p, "&cUsage: /mtrials edit addmob <EntityType> [count]"); return; }
                EntityType type;
                try { type = EntityType.valueOf(args[2].toUpperCase(Locale.ROOT)); }
                catch (IllegalArgumentException e) { plugin.msg(p, "&cUnknown entity type: " + args[2]); return; }
                int count = args.length >= 4 ? parseIntOr(args[3], 1) : 1;
                level.mobs().add(MobSpawn.at(p.getLocation(), type, Math.max(1, count)));
                trySave(p, d, "&aAdded &f" + count + "x " + type + "&a at your position.");
            }
            case "clearmobs" -> { level.mobs().clear(); trySave(p, d, "&aCleared this level's mob list."); }
            case "style" -> new StyleMenu(plugin, d, level.index(), null).open(p);
            case "info" -> {
                plugin.msg(p, "&bLevel " + level.index() + " &7of &f" + d.name());
                plugin.msg(p, "&7start: &f" + (level.startRegion() != null ? level.startRegion().describe() : "not set"));
                plugin.msg(p, "&7end:   &f" + (level.endRegion() != null ? level.endRegion().describe() : "not set"));
                plugin.msg(p, "&7mobs:  &f" + level.mobs().size());
                plugin.msg(p, "&7captured: &f" + plugin.store().hasSnapshot(d.name(), level.index()));
            }
            case "done" -> plugin.editManager().exit(p);
            default -> plugin.msg(p, "&cUnknown: " + args[1]);
        }
    }

    // --- /mtrials dungeon ... ---

    private void cmdDungeon(CommandSender s, String[] args) {
        if (args.length < 2) { plugin.msg(s, "&cUsage: /mtrials dungeon <create|delete|list|name> ..."); return; }
        String sub = args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create" -> {
                if (!s.hasPermission("morphedtrials.admin")) { plugin.msg(s, "&cNo permission."); return; }
                if (args.length < 3) { plugin.msg(s, "&cUsage: /mtrials dungeon create <name>"); return; }
                String name = DungeonStore.sanitize(args[2]);
                if (name.isEmpty()) { plugin.msg(s, "&cBad name."); return; }
                if (plugin.store().exists(name)) { plugin.msg(s, "&cDungeon '&f" + name + "&c' already exists."); return; }
                Dungeon d = new Dungeon(name);
                try { plugin.manager().save(d); plugin.msg(s, "&aCreated dungeon &f" + name + "&a. &7Open &f/mtrials gui&7 to set it up."); }
                catch (IOException e) { plugin.msg(s, "&cFailed: " + e.getMessage()); }
            }
            case "delete" -> {
                if (!s.hasPermission("morphedtrials.admin")) { plugin.msg(s, "&cNo permission."); return; }
                if (args.length < 3) { plugin.msg(s, "&cUsage: /mtrials dungeon delete <name>"); return; }
                String name = DungeonStore.sanitize(args[2]);
                plugin.store().delete(name);
                plugin.manager().invalidate(name);
                plugin.msg(s, "&aDeleted dungeon &f" + name + "&a.");
            }
            case "list" -> {
                List<String> names = plugin.store().listNames();
                plugin.msg(s, names.isEmpty() ? "&7No dungeons yet." : "&7Dungeons: &f" + String.join("&7, &f", names));
            }
            default -> cmdDungeonScoped(s, args);
        }
    }

    // /mtrials dungeon <name> setregion|start|forcestart|reward ...
    private void cmdDungeonScoped(CommandSender s, String[] args) {
        Dungeon d;
        try { d = plugin.manager().getOrLoad(args[1]); }
        catch (IOException e) { plugin.msg(s, "&cNo dungeon '&f" + args[1] + "&c'."); return; }

        if (args.length < 3) { plugin.msg(s, "&cUsage: /mtrials dungeon " + d.name() + " <setregion|forcestart|reward> ..."); return; }
        String action = args[2].toLowerCase(Locale.ROOT);

        switch (action) {
            case "setregion" -> {
                if (!s.hasPermission("morphedtrials.admin")) { plugin.msg(s, "&cNo permission."); return; }
                if (!(s instanceof Player p)) { plugin.msg(s, "&cPlayers only."); return; }
                Selection sel = plugin.wand().selectionOf(p.getUniqueId());
                if (!sel.complete()) { plugin.msg(s, "&cComplete a Region Wand selection first (left+right click)."); return; }
                Region r = sel.toRegion();
                d.setRegion(r);
                try { plugin.manager().save(d); plugin.msg(s, "&aSet &f" + d.name() + "&a's shared region &7— " + r.describe()); }
                catch (IOException e) { plugin.msg(s, "&cFailed to save: " + e.getMessage()); }
            }
            case "forcestart" -> {
                if (!s.hasPermission("morphedtrials.admin")) { plugin.msg(s, "&cNo permission."); return; }
                plugin.manager().forceStart(s, d);
            }
            case "reward" -> cmdReward(s, d, args);
            default -> plugin.msg(s, "&cUnknown: " + action);
        }
    }

    private void cmdReward(CommandSender s, Dungeon d, String[] args) {
        if (!s.hasPermission("morphedtrials.admin")) { plugin.msg(s, "&cNo permission."); return; }
        if (args.length < 5 || !"add".equalsIgnoreCase(args[3])) { plugin.msg(s, "&cUsage: /mtrials dungeon " + d.name() + " reward add <command with %player%>"); return; }
        String command = String.join(" ", java.util.Arrays.asList(args).subList(4, args.length));
        d.rewardCommands().add(command);
        try { plugin.manager().save(d); plugin.msg(s, "&aAdded reward command."); }
        catch (IOException e) { plugin.msg(s, "&cFailed to save: " + e.getMessage()); }
    }

    private void trySave(Player p, Dungeon d, String okMessage) {
        try { plugin.manager().save(d); plugin.msg(p, okMessage); }
        catch (IOException e) { plugin.msg(p, "&cFailed to save: " + e.getMessage()); }
    }

    private Player resolveTarget(CommandSender s, String[] args, int idx) {
        if (args.length > idx) return Bukkit.getPlayerExact(args[idx]);
        return s instanceof Player p ? p : null;
    }

    private static int parseIntOr(String s, int def) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) out.addAll(List.of("gui", "wand", "reload", "join", "leave", "edit", "dungeon"));
        else if (args.length == 2 && "wand".equalsIgnoreCase(args[0])) out.addAll(List.of("region", "start", "end"));
        else if (args.length == 2 && ("join".equalsIgnoreCase(args[0]) || "leave".equalsIgnoreCase(args[0]))) out.addAll(plugin.store().listNames());
        else if (args.length == 2 && "edit".equalsIgnoreCase(args[0])) out.addAll(List.of("setstart", "setend", "capture", "addmob", "clearmobs", "style", "info", "done"));
        else if (args.length == 2 && "dungeon".equalsIgnoreCase(args[0])) { out.addAll(List.of("create", "delete", "list")); out.addAll(plugin.store().listNames()); }
        else if (args.length == 3 && "dungeon".equalsIgnoreCase(args[0])) out.addAll(List.of("setregion", "forcestart", "reward"));

        String cur = args.length > 0 ? args[args.length - 1].toLowerCase(Locale.ROOT) : "";
        out.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(cur));
        return out;
    }
}
