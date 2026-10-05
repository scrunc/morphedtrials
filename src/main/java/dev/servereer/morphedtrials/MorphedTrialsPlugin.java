package dev.servereer.morphedtrials;

import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class MorphedTrialsPlugin extends JavaPlugin {

    private NamespacedKey wandKey;
    private MTWand wand;
    private MTBridge bridge;
    private DungeonStore store;
    private DungeonManager manager;
    private EditManager editManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        wandKey = new NamespacedKey(this, "wand");
        wand = new MTWand(this);
        bridge = new MTBridge(this);
        store = new DungeonStore(getDataFolder());
        manager = new DungeonManager(this, store);
        editManager = new EditManager(this);

        getServer().getPluginManager().registerEvents(wand, this);
        getServer().getPluginManager().registerEvents(manager, this);
        getServer().getPluginManager().registerEvents(new GuiListener(), this);

        MTCommand cmd = new MTCommand(this);
        var pc = getCommand("mtrials");
        if (pc != null) { pc.setExecutor(cmd); pc.setTabCompleter(cmd); }
    }

    NamespacedKey wandKey() { return wandKey; }
    MTWand wand() { return wand; }
    MTBridge bridge() { return bridge; }
    DungeonStore store() { return store; }
    DungeonManager manager() { return manager; }
    EditManager editManager() { return editManager; }

    String color(String s) { return s == null ? "" : ChatColor.translateAlternateColorCodes('&', s); }

    void msg(CommandSender to, String s) { to.sendMessage(color("&b[MorphedTrials] &r" + color(s))); }
}
