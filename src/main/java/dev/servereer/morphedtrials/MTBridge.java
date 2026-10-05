package dev.servereer.morphedtrials;

import dev.servereer.morphcore.ArenaMorph;
import dev.servereer.morphcore.MorphCore;
import dev.servereer.morphcore.MorphGrid;
import dev.servereer.morphcore.MorphSettings;
import dev.servereer.morphcore.MorphSpec;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * MorphedTrials' one and only touchpoint for the (soft-depended, optional) MorphCore plugin. Mirrors
 * KOTH's {@code MorphBridge} exactly, including the mistake that shipped there first and got caught live
 * (see ADR 0035's addendum in the servereer repo) — {@link #available()} checks {@code
 * isPluginEnabled("MorphCore")} (zero MorphCore type reference) as the FIRST line of every method here,
 * before anything else touches a MorphCore symbol. Every other file in this plugin only ever sees {@link
 * Handle}, never a real {@code dev.servereer.morphcore.*} type — checkable with {@code javap -c -p
 * *.class | grep morphcore}, which should only ever match this one file.
 */
final class MTBridge {

    private static final String OWNER_KEY_ID = "level_display";

    private final MorphedTrialsPlugin plugin;

    MTBridge(MorphedTrialsPlugin plugin) {
        this.plugin = plugin;
    }

    boolean available() {
        return plugin.getServer().getPluginManager().isPluginEnabled("MorphCore");
    }

    private MorphCore core() {
        if (!available()) return null;
        return plugin.getServer().getServicesManager().load(MorphCore.class);
    }

    // --- style registry (safe defaults when MorphCore isn't installed) — feeds the in-GUI style picker ---

    List<String> styleIds() {
        if (!available()) return List.of();
        MorphCore c = core();
        return c != null ? c.styleIds() : List.of();
    }

    String displayName(String id) {
        if (!available()) return "&f" + id;
        MorphCore c = core();
        return c != null ? c.displayName(id) : ("&f" + id);
    }

    Material icon(String id) {
        if (!available()) return Material.BARRIER;
        MorphCore c = core();
        return c != null ? c.icon(id) : Material.BARRIER;
    }

    List<String> lore(String id) {
        if (!available()) return List.of("&cMorphCore isn't installed.");
        MorphCore c = core();
        return c != null ? c.lore(id) : List.of("&cMorphCore isn't installed.");
    }

    // --- animation ---

    interface Handle {
        void start(Runnable done);
        void cancel();
        boolean isRunning();
        int changeCount();
    }

    private static final class RealHandle implements Handle {
        private final ArenaMorph morph;
        RealHandle(ArenaMorph morph) { this.morph = morph; }
        @Override public void start(Runnable done) { morph.start(done); }
        @Override public void cancel() { morph.cancel(); }
        @Override public boolean isRunning() { return morph.isRunning(); }
        @Override public int changeCount() { return morph.changeCount(); }
    }

    /** Build (not yet started) the animation morphing the region into {@code target}, diffed against
     *  {@code compare} (the level currently live there; {@code null} = paste every cell). {@code
     *  styleOverride} (nullable) picks a specific level's own style; null uses the global default from
     *  {@code cfg}. Returns {@code null} if MorphCore isn't installed. */
    Handle build(LevelSnapshot target, LevelSnapshot compare, int seconds, FileConfiguration cfg, String styleOverride) {
        if (!available()) return null;
        MorphCore c = core();
        if (c == null) return null;
        Map<String, Object> overrides = styleOverride == null || styleOverride.isBlank()
                ? Collections.emptyMap() : Map.of("morph-style", styleOverride);
        MorphSpec spec = MorphSpec.resolve(cfg.getConfigurationSection("morph"), overrides);
        MorphSettings settings = MorphSettings.resolve(cfg.getConfigurationSection("morph"));
        MorphGrid t = toGrid(target);
        MorphGrid cmp = compare != null ? toGrid(compare) : null;
        ArenaMorph morph = c.morph(plugin, t, cmp, seconds, spec, settings, OWNER_KEY_ID);
        return morph != null ? new RealHandle(morph) : null;
    }

    private MorphGrid toGrid(LevelSnapshot snap) {
        List<dev.servereer.morphcore.MorphDisplay> noDisplays = Collections.emptyList();
        return new MorphGrid(snap.world(), snap.minX(), snap.minY(), snap.minZ(),
                snap.sizeX(), snap.sizeY(), snap.sizeZ(), snap.palette(), snap.indices(), noDisplays);
    }
}
