package dev.servereer.morphedtrials;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;

/** Draws a particle wireframe around a region's 12 edges — used both for the "level cleared, exit's this
 *  way" highlight and for edit mode's live region/start/end outlines. Client-side only, non-destructive. */
final class RegionHighlighter {

    private RegionHighlighter() { }

    static void drawBox(World w, Region r, Particle particle) {
        walkEdges(r, l -> {
            try { w.spawnParticle(particle, l, 1, 0, 0, 0, 0); } catch (RuntimeException ignored) { }
        });
    }

    static void drawBoxColored(World w, Region r, Color color) {
        Particle.DustOptions opts = new Particle.DustOptions(color, 1.0f);
        walkEdges(r, l -> {
            try { w.spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, opts); } catch (RuntimeException ignored) { }
        });
    }

    private static void walkEdges(Region r, java.util.function.Consumer<Location> sink) {
        double x1 = r.minX(), y1 = r.minY(), z1 = r.minZ();
        double x2 = r.maxX() + 1, y2 = r.maxY() + 1, z2 = r.maxZ() + 1;
        double step = 0.5;
        // 4 vertical edges
        edge(sink,x1, y1, z1, x1, y2, z1, step);
        edge(sink,x2, y1, z1, x2, y2, z1, step);
        edge(sink,x1, y1, z2, x1, y2, z2, step);
        edge(sink,x2, y1, z2, x2, y2, z2, step);
        // 4 bottom edges
        edge(sink,x1, y1, z1, x2, y1, z1, step);
        edge(sink,x1, y1, z2, x2, y1, z2, step);
        edge(sink,x1, y1, z1, x1, y1, z2, step);
        edge(sink,x2, y1, z1, x2, y1, z2, step);
        // 4 top edges
        edge(sink,x1, y2, z1, x2, y2, z1, step);
        edge(sink,x1, y2, z2, x2, y2, z2, step);
        edge(sink,x1, y2, z1, x1, y2, z2, step);
        edge(sink,x2, y2, z1, x2, y2, z2, step);
    }

    // World.spawnParticle(Particle, Location, ...) reads only x/y/z off the Location and uses the
    // receiver (the World it's called on) for placement — a null world on the Location itself is fine.
    private static void edge(java.util.function.Consumer<Location> sink,
                              double x1, double y1, double z1, double x2, double y2, double z2, double step) {
        double len = Math.max(Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1)), Math.abs(z2 - z1));
        int n = Math.max(1, (int) Math.ceil(len / step));
        for (int i = 0; i <= n; i++) {
            double f = (double) i / n;
            sink.accept(new Location(null, x1 + (x2 - x1) * f, y1 + (y2 - y1) * f, z1 + (z2 - z1) * f));
        }
    }
}
