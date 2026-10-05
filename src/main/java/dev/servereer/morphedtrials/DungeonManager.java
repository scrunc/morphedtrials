package dev.servereer.morphedtrials;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * The gameplay loop, party edition: players {@code /mtrials dungeon <name> join} a lobby; once {@link
 * Dungeon#minPlayers()} are in, a countdown starts; at zero the whole party is teleported into level 1
 * and its mobs spawn once (shared pool — anyone can get the kill). Clearing them lights up the end pad;
 * the FIRST participant to walk into it advances (or finishes) the whole party — remorphing the shared
 * region into the next level and safely teleporting everyone in, which re-arms the same start-pad trigger
 * level 1 used. No special-casing between the first level and the Nth.
 */
final class DungeonManager implements Listener {

    private final MorphedTrialsPlugin plugin;
    private final DungeonStore store;
    private final Map<String, DungeonSession> sessions = new HashMap<>();   // one active session per dungeon
    private final Map<String, BossBar> bossBars = new HashMap<>();          // BOSS-type levels' live HP bar
    private final Map<UUID, String> playerDungeon = new HashMap<>();        // reverse lookup for move events
    private final Map<String, Dungeon> cache = new HashMap<>();             // write-through: commands mutate + save these
    private final Random rng = new Random();

    DungeonManager(MorphedTrialsPlugin plugin, DungeonStore store) {
        this.plugin = plugin;
        this.store = store;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickHighlights, 20L, 8L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickCountdowns, 20L, 20L);
    }

    // --- dungeon cache (avoids re-reading dungeon.yml off disk on every move event) ---

    Dungeon getOrLoad(String rawName) throws IOException {
        String name = DungeonStore.sanitize(rawName);
        Dungeon cached = cache.get(name);
        if (cached != null) return cached;
        Dungeon loaded = store.load(name);
        cache.put(name, loaded);
        return loaded;
    }

    void save(Dungeon d) throws IOException {
        store.save(d);
        cache.put(d.name(), d);
    }

    void invalidate(String name) { cache.remove(DungeonStore.sanitize(name)); }
    void reloadAll() { cache.clear(); }

    // --- joining / leaving the lobby ---

    void join(CommandSender initiator, Player p, Dungeon d) {
        Level level1 = d.level(1);
        if (level1 == null || !level1.hasRegions() || !store.hasSnapshot(d.name(), 1)) {
            plugin.msg(initiator, "&cDungeon '&f" + d.name() + "&c' isn't ready — level 1 needs region/start/end/capture.");
            return;
        }
        String existing = playerDungeon.get(p.getUniqueId());
        if (existing != null) { plugin.msg(initiator, "&c" + p.getName() + " is already in a dungeon — &f/mtrials dungeon " + existing + " leave&c first."); return; }

        DungeonSession session = sessions.computeIfAbsent(d.name(), k -> new DungeonSession(d.name()));
        if (session.state == DungeonSession.State.ACTIVE) {
            plugin.msg(initiator, "&cA run of &f" + d.name() + "&c is already in progress — wait for it to finish.");
            return;
        }
        session.participants.add(p.getUniqueId());
        playerDungeon.put(p.getUniqueId(), d.name());
        broadcast(session, "&a" + p.getName() + " &7joined &f" + d.name() + " &7(&f" + session.participants.size() + "/" + d.minPlayers() + "&7 needed).");

        if (session.state == DungeonSession.State.LOBBY && session.participants.size() >= d.minPlayers()) {
            session.state = DungeonSession.State.COUNTDOWN;
            session.countdownSecondsLeft = d.countdownSeconds();
            broadcast(session, "&dEnough players — starting in &f" + session.countdownSecondsLeft + "s&d…");
        }
    }

    void leave(CommandSender initiator, Player p) {
        String name = playerDungeon.remove(p.getUniqueId());
        if (name == null) { plugin.msg(initiator, "&7" + p.getName() + " isn't in a dungeon."); return; }
        DungeonSession session = sessions.get(name);
        if (session != null) {
            session.participants.remove(p.getUniqueId());
            session.wasInStart.remove(p.getUniqueId());
            session.wasInEnd.remove(p.getUniqueId());
            Dungeon d = safeLoad(name);
            if (session.state == DungeonSession.State.COUNTDOWN && d != null && session.participants.size() < d.minPlayers()) {
                session.state = DungeonSession.State.LOBBY;
                broadcast(session, "&cNot enough players — countdown cancelled.");
            }
            if (session.participants.isEmpty()) endSession(session, d);
        }
        plugin.msg(initiator, "&7Pulled &f" + p.getName() + "&7 out of &f" + name + "&7.");
    }

    void forceStart(CommandSender initiator, Dungeon d) {
        DungeonSession session = sessions.get(d.name());
        if (session == null || session.participants.isEmpty()) { plugin.msg(initiator, "&cNobody's waiting in &f" + d.name() + "&c's lobby."); return; }
        if (session.state == DungeonSession.State.ACTIVE) { plugin.msg(initiator, "&cAlready running."); return; }
        beginSession(session, d);
        plugin.msg(initiator, "&aForce-started &f" + d.name() + "&a with &f" + session.participants.size() + "&a player(s).");
    }

    /** Solo preview of ONE level — skips join/countdown/min-players entirely, reuses the exact same
     *  morph+safe-land+spawn+countdown pipeline everything else goes through. Refuses if a real party
     *  session is already using this dungeon (there's only one session slot per dungeon); leaving the
     *  test the normal way ({@code /mtrials leave}) resets the region back to level 1 like any other
     *  session ending, so nothing needs a special "end test mode" path. */
    void testLevel(CommandSender initiator, Dungeon d, int levelIndex, Player admin) {
        Level level = d.level(levelIndex);
        if (level == null) { plugin.msg(initiator, "&cNo level " + levelIndex + " in &f" + d.name() + "&c."); return; }
        if (!level.hasRegions()) { plugin.msg(initiator, "&cLevel " + levelIndex + " is missing its start/end pad."); return; }
        if (!store.hasSnapshot(d.name(), levelIndex)) { plugin.msg(initiator, "&cLevel " + levelIndex + " hasn't been captured yet."); return; }
        if (sessions.containsKey(d.name())) { plugin.msg(initiator, "&cA lobby or run of &f" + d.name() + "&c already exists — can't test right now."); return; }
        if (playerDungeon.containsKey(admin.getUniqueId())) { plugin.msg(initiator, "&c" + admin.getName() + " is already in a dungeon."); return; }

        LevelSnapshot targetSnap;
        try { targetSnap = store.loadSnapshot(d.name(), levelIndex); }
        catch (IOException e) { plugin.msg(initiator, "&cFailed to load level " + levelIndex + ": " + e.getMessage()); return; }
        LevelSnapshot compareSnap = null;
        if (levelIndex > 1) {
            try { if (store.hasSnapshot(d.name(), levelIndex - 1)) compareSnap = store.loadSnapshot(d.name(), levelIndex - 1); }
            catch (IOException ignored) { }
        }

        DungeonSession session = new DungeonSession(d.name());
        session.participants.add(admin.getUniqueId());
        session.state = DungeonSession.State.ACTIVE;
        sessions.put(d.name(), session);
        playerDungeon.put(admin.getUniqueId(), d.name());

        plugin.msg(initiator, "&aTesting level " + levelIndex + " of &f" + d.name() + "&a solo. &7/mtrials leave&7 to stop.");
        beginLevel(session, d, level, levelIndex, targetSnap, compareSnap);
    }

    // --- countdown tick ---

    private void tickCountdowns() {
        if (sessions.isEmpty()) return;
        for (DungeonSession session : sessions.values()) {
            if (session.state != DungeonSession.State.COUNTDOWN) continue;
            session.countdownSecondsLeft--;
            Dungeon d = safeLoad(session.dungeonName);
            if (d == null) continue;
            if (session.countdownSecondsLeft <= 0) {
                beginSession(session, d);
            } else if (session.countdownSecondsLeft <= 5 || session.countdownSecondsLeft % 5 == 0) {
                broadcast(session, "&d" + session.countdownSecondsLeft + "…");
            }
        }
    }

    private void beginSession(DungeonSession session, Dungeon d) {
        session.state = DungeonSession.State.ACTIVE;
        Level level1 = d.level(1);
        LevelSnapshot snap;
        try { snap = store.loadSnapshot(d.name(), 1); }
        catch (IOException e) { broadcast(session, "&cFailed to load level 1: " + e.getMessage()); return; }
        broadcast(session, "&6&lGO! &r&7Level 1 begins.");
        beginLevel(session, d, level1, 1, snap, null);
    }

    // --- the actual morph + safe-land + teleport, shared by session-start and level-advance ---

    private void beginLevel(DungeonSession session, Dungeon d, Level level, int levelIndex,
                             LevelSnapshot targetSnap, LevelSnapshot compareSnap) {
        World w = Bukkit.getWorld(targetSnap.world());
        if (w == null) { broadcast(session, "&cLevel " + levelIndex + "'s world isn't loaded."); return; }
        removeBossBar(session.dungeonName);   // defensive — a previous BOSS level's bar should already be
                                               // gone by the time advance is even possible, but never carry one over

        // Safe-landing FIRST: force-place the columns around the start pad instantly, regardless of the
        // sweep's own progress, so nobody teleports into a hole or an unbuilt wall.
        forceLandingPad(w, targetSnap, level.startRegion());

        Location standPoint = level.startRegion().standPoint(w);
        session.resetForLevel(levelIndex);
        for (UUID id : session.participants) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.teleport(standPoint);
            // Pre-mark them as "already at start" before the resulting PlayerMoveEvent can fire. Without
            // this, onMove()'s own edge-detection sees the teleport as a fresh "entered start" and calls
            // onEnteredStart() immediately — racing ahead of (and completely bypassing) the
            // morph-completion callback below, since that move event fires before the morph has even
            // started, let alone finished. This was the actual cause of mobs spawning early on every
            // style, not something specific to geyser — geyser's slower rise just made it obvious.
            session.wasInStart.put(id, true);
        }

        int seconds = plugin.getConfig().getInt("morph.seconds", 6);
        MTBridge.Handle morph = plugin.bridge().build(targetSnap, compareSnap, seconds, plugin.getConfig(), level.morphStyle());
        if (morph == null) {
            // No animation to wait for — the landing pad is already solid (forced above), so go straight
            // to the "starting…" countdown.
            broadcast(session, "&eMorphCore isn't installed — the level won't animate in.");
            startCountdown(session, d, level);
        } else {
            // Mobs wait for the sweep to actually FINISH, not just start — the default sweep reveals from
            // the region's centre outward over `morph.seconds`, so a mob spawn point (which can be
            // anywhere in the level, unlike the start pad's forced landing column) might sit over empty
            // air or inside a not-yet-cleared block until the front reaches it. Spawning on completion
            // means the party watches the level finish assembling, THEN the fight starts — never mid-glitch.
            broadcast(session, "&7The level is reshaping…");
            morph.start(() -> {
                // Re-assert the landing pad NOW, on completion — not just once before the sweep started.
                // A diff-based morph (compare != null, i.e. every level after the first) only sweeps cells
                // that actually DIFFER between the two levels; a cell that's identical in both is assumed
                // to already be correct in the live world and never gets touched by the sweep at all. If
                // that assumption is wrong — players damaged that spot during the previous level's fight,
                // or the style's own reveal mechanics touched it in a way the one-time pre-sweep placement
                // didn't survive — the landing pad could still be wrong by the time the sweep reports
                // done. This closes that gap unconditionally, whatever the exact cause turns out to be.
                forceLandingPad(w, targetSnap, level.startRegion());
                startCountdown(session, d, level);
            });
        }
    }

    /** The morph's own boss bar belongs to MorphCore and is torn down the instant the sweep finishes —
     *  not something this plugin can reach into or extend. This is a separate one, own phase: shows
     *  "Starting" then a 3-2-1 (title + boss bar), THEN spawns the level's mobs — so the party gets a
     *  breath to get oriented once the level's done reshaping, instead of enemies appearing the instant
     *  the last block settles. */
    private void startCountdown(DungeonSession session, Dungeon d, Level level) {
        if (session.countdownStarted) return;   // only ONE countdown per level, no matter what triggers it
        session.countdownStarted = true;
        List<Player> viewers = new ArrayList<>();
        for (UUID id : session.participants) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) viewers.add(p);
        }
        int startSeconds = Math.max(0, plugin.getConfig().getInt("morph.spawn-countdown-seconds", 3));
        BossBar bar = Bukkit.createBossBar(plugin.color("&a&lSTARTING"), BarColor.GREEN, BarStyle.SOLID);
        for (Player p : viewers) {
            bar.addPlayer(p);
            p.sendTitle(plugin.color("&a&lStarting"), plugin.color("&7Enemies incoming…"), 5, 15, 5);
        }
        bar.setProgress(1.0);

        if (startSeconds == 0) { bar.removeAll(); onEnteredStart(session, d, level); return; }

        new BukkitRunnable() {
            int secondsLeft = startSeconds;
            @Override public void run() {
                if (secondsLeft <= 0) {
                    bar.removeAll();
                    onEnteredStart(session, d, level);
                    cancel();
                    return;
                }
                bar.setTitle(plugin.color("&a&lSTARTING IN &f" + secondsLeft + "&a…"));
                bar.setProgress(Math.max(0.0, (double) secondsLeft / startSeconds));
                for (Player p : viewers) {
                    if (!p.isOnline()) continue;
                    p.sendTitle(plugin.color("&e&l" + secondsLeft), "", 0, 20, 0);
                    p.playSound(p.getLocation(), "block.note_block.pling", 1f, secondsLeft == 1 ? 1.6f : 1.2f);
                }
                secondsLeft--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    /** Force-place every cell within {@code morph.safe-landing-radius} blocks (XZ) of the start pad's
     *  centre, full height — not just a thin floor, so nearby walls/ceiling are correct too. */
    private void forceLandingPad(World w, LevelSnapshot target, Region startRegion) {
        int radius = Math.max(0, plugin.getConfig().getInt("morph.safe-landing-radius", 3));
        int cx = (startRegion.minX() + startRegion.maxX()) / 2;
        int cz = (startRegion.minZ() + startRegion.maxZ()) / 2;
        int minX = target.minX(), minY = target.minY(), minZ = target.minZ();
        for (int lx = 0; lx < target.sizeX(); lx++) {
            int wx = minX + lx;
            if (Math.abs(wx - cx) > radius) continue;
            for (int lz = 0; lz < target.sizeZ(); lz++) {
                int wz = minZ + lz;
                if (Math.abs(wz - cz) > radius) continue;
                for (int ly = 0; ly < target.sizeY(); ly++) {
                    String data = target.blockAt(target.indexOf(lx, ly, lz));
                    try {
                        w.getBlockAt(wx, minY + ly, wz).setBlockData(Bukkit.createBlockData(data), false);
                    } catch (IllegalArgumentException ignored) { /* unknown block string — skip */ }
                }
            }
        }
    }

    // --- movement: edge-detect entering the start/end pads ---

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (samePosition(e.getFrom(), e.getTo())) return;
        String dungeonName = playerDungeon.get(e.getPlayer().getUniqueId());
        if (dungeonName == null) return;
        DungeonSession session = sessions.get(dungeonName);
        if (session == null || session.state != DungeonSession.State.ACTIVE) return;

        Dungeon d = safeLoad(dungeonName);
        if (d == null) return;
        Level level = d.level(session.levelIndex);
        if (level == null) return;
        int levelBefore = session.levelIndex;   // captured BEFORE anything below can advance/reset it

        UUID id = e.getPlayer().getUniqueId();
        Location to = e.getTo();
        boolean inStart = level.startRegion() != null && level.startRegion().contains(to);
        boolean inEnd = level.endRegion() != null && level.endRegion().contains(to);

        boolean wasStart = session.wasInStart.getOrDefault(id, false);
        boolean wasEnd = session.wasInEnd.getOrDefault(id, false);
        // Goes through the SAME countdown gate as everything else — never call onEnteredStart directly
        // from here. This was the actual leak: this fallback bypassed the "starting…" countdown entirely,
        // so mobs could still appear before 3-2-1 finished even after the morph-completion path was fixed
        // to wait for it. startCountdown()'s own countdownStarted guard makes this a no-op once the real
        // trigger (morph completion, or the immediate branch when MorphCore is absent) already fired.
        if (inStart && !wasStart) startCountdown(session, d, level);

        if (inEnd != wasEnd) {
            if (inEnd) session.onEndPad.add(id); else session.onEndPad.remove(id);
            // Advancing needs a HEADCOUNT (half the party standing there at once, not one visit ever), so
            // only re-check when someone just stepped ON — leaving can only lower the count.
            if (inEnd) checkAdvance(session, d, level);
        }

        // checkAdvance() (or onEnteredStart -> markCleared, if this level had zero mobs and someone was
        // ALREADY on the next end pad) can synchronously call beginLevel() for a DIFFERENT level, which
        // teleports the party and marks wasInStart/wasInEnd correctly for it. If that happened, "inStart"/
        // "inEnd" here are STALE — they describe the OLD level — and writing them now would stomp the
        // correct marking beginLevel() just set. This exact bug shipped once already (level-2 mobs
        // spawning early again despite the earlier same-tick fix): the stale write here undid it.
        if (session.levelIndex == levelBefore) {
            session.wasInStart.put(id, inStart);
            session.wasInEnd.put(id, inEnd);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        String name = playerDungeon.remove(e.getPlayer().getUniqueId());
        if (name == null) return;
        DungeonSession session = sessions.get(name);
        if (session != null) {
            session.participants.remove(e.getPlayer().getUniqueId());
            session.onEndPad.remove(e.getPlayer().getUniqueId());
            if (session.participants.isEmpty()) endSession(session, safeLoad(name));
        }
    }

    /** A session is over, one way or another (finished, or the whole party left/disconnected) — put the
     *  shared region back to level 1's own layout (its natural "idle between runs" appearance) rather
     *  than leaving it stuck wherever the last party got to. No-op if nothing ever actually morphed
     *  (session never left LOBBY, or level 1 was never captured). */
    private void endSession(DungeonSession session, Dungeon d) {
        sessions.remove(session.dungeonName);
        removeBossBar(session.dungeonName);   // in case the party left/disbanded mid boss-fight
        if (session.state == DungeonSession.State.ACTIVE && d != null) resetToLevel1(session, d);
    }

    private void resetToLevel1(DungeonSession session, Dungeon d) {
        if (!store.hasSnapshot(d.name(), 1)) return;
        LevelSnapshot base;
        try { base = store.loadSnapshot(d.name(), 1); } catch (IOException e) { return; }
        LevelSnapshot current = null;
        try { if (store.hasSnapshot(d.name(), session.levelIndex)) current = store.loadSnapshot(d.name(), session.levelIndex); }
        catch (IOException ignored) { }
        if (current != null && !current.sameFootprint(base)) current = null;
        World w = Bukkit.getWorld(base.world());
        if (w == null) return;
        int seconds = plugin.getConfig().getInt("morph.reset-seconds", 4);
        Level level1 = d.level(1);
        MTBridge.Handle morph = plugin.bridge().build(base, current, seconds, plugin.getConfig(),
                level1 != null ? level1.morphStyle() : null);
        if (morph != null) morph.start(null);   // background repaint — nobody's being teleported into this
    }

    private static boolean samePosition(Location a, Location b) {
        return a.getBlockX() == b.getBlockX() && a.getBlockY() == b.getBlockY() && a.getBlockZ() == b.getBlockZ();
    }

    private void onEnteredStart(DungeonSession session, Dungeon d, Level level) {
        if (session.mobsSpawnedOnce) return;
        session.mobsSpawnedOnce = true;
        World w = Bukkit.getWorld(level.startRegion() != null ? level.startRegion().world() : null);
        if (w == null) return;
        for (MobSpawn m : level.mobs()) spawnOne(w, m, session);

        if (level.type() == Level.Type.LOOT) {
            // No combat gate, whatever got spawned above (usually nothing) — this is a breather/treasure
            // room. Build the actual loot in physically; the plugin's only job here is not gating the exit.
            markCleared(session, d, level);
            return;
        }
        if (session.spawnedMobs.isEmpty()) {
            markCleared(session, d, level);   // no mobs configured (or all failed to spawn) — nothing to wait for
        } else {
            broadcast(session, "&c" + session.spawnedMobs.size() + " enemies&7 have spawned. Clear them to open the exit.");
            if (level.type() == Level.Type.BOSS) startBossBar(session);
        }
    }

    // --- BOSS levels: a shared, live-updating health bar over all of this level's spawned mobs ---

    private void startBossBar(DungeonSession session) {
        BossBar bar = Bukkit.createBossBar(plugin.color("&4&lBOSS"), BarColor.RED, BarStyle.SEGMENTED_10);
        bossBars.put(session.dungeonName, bar);
        for (UUID id : session.participants) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) bar.addPlayer(p);
        }
        new BukkitRunnable() {
            @Override public void run() {
                BossBar current = bossBars.get(session.dungeonName);
                if (current != bar) { cancel(); return; }   // superseded (level moved on) or removed already
                if (session.spawnedMobs.isEmpty()) { removeBossBar(session.dungeonName); cancel(); return; }
                double hp = 0, maxHp = 0;
                for (UUID id : session.spawnedMobs) {
                    if (!(Bukkit.getEntity(id) instanceof LivingEntity le)) continue;
                    hp += Math.max(0, le.getHealth());
                    var attr = le.getAttribute(Attribute.MAX_HEALTH);
                    maxHp += attr != null ? attr.getValue() : le.getHealth();
                }
                bar.setProgress(maxHp > 0 ? Math.max(0.0, Math.min(1.0, hp / maxHp)) : 0.0);
            }
        }.runTaskTimer(plugin, 0L, 5L);   // 4x/sec — snappy without being wasteful
    }

    private void removeBossBar(String dungeonName) {
        BossBar b = bossBars.remove(dungeonName);
        if (b != null) b.removeAll();
    }

    private void spawnOne(World w, MobSpawn m, DungeonSession session) {
        for (int i = 0; i < Math.max(1, m.count()); i++) {
            double jx = i == 0 ? 0 : (rng.nextDouble() - 0.5) * 3.0;
            double jz = i == 0 ? 0 : (rng.nextDouble() - 0.5) * 3.0;
            Location loc = m.toLocation(w).add(jx, 0, jz);
            try {
                Entity ent = w.spawnEntity(loc, m.type());
                session.spawnedMobs.add(ent.getUniqueId());
            } catch (IllegalArgumentException ignored) { /* bad entity type / unsupported — skip */ }
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        UUID id = e.getEntity().getUniqueId();
        for (DungeonSession session : sessions.values()) {
            if (!session.spawnedMobs.remove(id) || !session.spawnedMobs.isEmpty() || session.cleared) continue;
            Dungeon d = safeLoad(session.dungeonName);
            Level level = d != null ? d.level(session.levelIndex) : null;
            if (d != null && level != null) markCleared(session, d, level);
        }
    }

    /** {@code d}/{@code level} are only needed to immediately re-check advance for a party that was
     *  ALREADY standing on the end pad when the last mob died — the move listener won't fire a fresh
     *  "entered" edge for them, so without this they'd be stuck until someone stepped off and back on. */
    private void markCleared(DungeonSession session, Dungeon d, Level level) {
        session.cleared = true;
        broadcast(session, "&aLevel cleared! &7Head to the highlighted exit.");
        if (!session.onEndPad.isEmpty()) checkAdvance(session, d, level);
    }

    /** Advance requires the level to be fully cleared AND at least half the party standing on the end pad
     *  AT ONCE — one person walking through isn't enough. Called every time someone new steps onto the
     *  pad; re-checks the whole headcount rather than trusting the single mover, since it's the party's
     *  CURRENT occupancy that matters, not who triggered this particular event. */
    private void checkAdvance(DungeonSession session, Dungeon d, Level level) {
        if (!session.cleared) return;            // exit is locked until the level is actually cleared
        if (!session.enoughOnEndPad()) {
            int required = (int) Math.ceil(session.participants.size() / 2.0);
            broadcast(session, "&7" + session.onEndPad.size() + "/" + required + " &7on the exit pad — need at least half the party.");
            return;
        }
        if (d.isLast(level.index())) {
            completeDungeon(session, d);
            return;
        }
        int next = level.index() + 1;
        LevelSnapshot currentSnap = null;
        try { if (store.hasSnapshot(d.name(), level.index())) currentSnap = store.loadSnapshot(d.name(), level.index()); }
        catch (IOException ignored) { }
        Level nextLevel = d.level(next);
        if (nextLevel == null || !nextLevel.hasRegions() || !store.hasSnapshot(d.name(), next)) {
            broadcast(session, "&cLevel " + next + " isn't fully set up yet — staying here.");
            return;
        }
        LevelSnapshot nextSnap;
        try { nextSnap = store.loadSnapshot(d.name(), next); }
        catch (IOException ex) { broadcast(session, "&cFailed to load level " + next + ": " + ex.getMessage()); return; }

        if (currentSnap != null && !currentSnap.sameFootprint(nextSnap)) currentSnap = null;   // paranoia, should be structurally impossible now

        broadcast(session, "&dThe party opened the way — advancing to level " + next + "…");
        beginLevel(session, d, nextLevel, next, nextSnap, currentSnap);
    }

    private void completeDungeon(DungeonSession session, Dungeon d) {
        broadcast(session, "&6&lDungeon complete! &r&7" + d.name() + " &7— resetting…");
        for (UUID id : session.participants) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            for (String cmd : d.rewardCommands()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", p.getName()));
            }
            playerDungeon.remove(id);
        }
        endSession(session, d);
    }

    private void broadcast(DungeonSession session, String msg) {
        for (UUID id : session.participants) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) plugin.msg(p, msg);
        }
    }

    private Dungeon safeLoad(String name) {
        try { return getOrLoad(name); } catch (IOException e) { return null; }
    }

    // --- end-pad highlight, while cleared and waiting for the party to walk in ---

    private void tickHighlights() {
        if (sessions.isEmpty()) return;
        for (DungeonSession session : sessions.values()) {
            if (!session.cleared) continue;
            Dungeon d = safeLoad(session.dungeonName);
            if (d == null) continue;
            Level level = d.level(session.levelIndex);
            if (level == null || level.endRegion() == null) continue;
            World w = Bukkit.getWorld(level.endRegion().world());
            if (w == null) continue;
            RegionHighlighter.drawBox(w, level.endRegion(), Particle.END_ROD);
        }
    }
}
