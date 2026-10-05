package dev.servereer.morphedtrials;

import org.bukkit.World;
import org.bukkit.block.Block;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * An immutable, region-relative capture of every block in a cuboid — a level's morph target. Palette-
 * compressed the same way KOTH's {@code ArenaSnapshot} is (a table of distinct {@code BlockData} strings
 * + one palette index per cell), which is exactly the shape {@link MTBridge} hands MorphCore, so building
 * a {@code MorphGrid} from one of these is a direct array handoff, no re-encoding.
 *
 * <p>Blocks only — no display-entity capture in this v1 (a level's walls/floor/mobs are what matters for
 * a dungeon; decorative display entities aren't captured or animated). Add a {@code DisplayCapture}-style
 * companion later if a level ever needs one.
 */
public final class LevelSnapshot {

    private static final String MAGIC = "MTRIALSLVL";
    private static final int FORMAT = 1;

    private final String world;
    private final int minX, minY, minZ, sizeX, sizeY, sizeZ;
    private final String[] palette;
    private final short[] indices;

    private LevelSnapshot(String world, int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ,
                           String[] palette, short[] indices) {
        this.world = world;
        this.minX = minX; this.minY = minY; this.minZ = minZ;
        this.sizeX = sizeX; this.sizeY = sizeY; this.sizeZ = sizeZ;
        this.palette = palette;
        this.indices = indices;
    }

    public String world() { return world; }
    public int minX() { return minX; }
    public int minY() { return minY; }
    public int minZ() { return minZ; }
    public int sizeX() { return sizeX; }
    public int sizeY() { return sizeY; }
    public int sizeZ() { return sizeZ; }
    public int cellCount() { return indices.length; }

    public String blockAt(int i) { return palette[indices[i] & 0xFFFF]; }

    /** Package-private: {@link MTBridge} builds a MorphCore {@code MorphGrid} from these two arrays
     *  directly (no per-cell re-encode). Never mutated after construction — safe to share. */
    String[] palette() { return palette; }
    short[] indices() { return indices; }

    /** Linear x→y→z index for a cell at local (x,y,z) — used for the safe-landing force-place. */
    public int indexOf(int localX, int localY, int localZ) {
        return (localX * sizeY + localY) * sizeZ + localZ;
    }

    public boolean inBounds(int localX, int localY, int localZ) {
        return localX >= 0 && localX < sizeX && localY >= 0 && localY < sizeY && localZ >= 0 && localZ < sizeZ;
    }

    /** True if two snapshots cover the exact same world+origin+dimensions — required for one to be a
     *  valid diff-compare against the other (see DungeonManager, which falls back to a full paste rather
     *  than risk an out-of-bounds diff if this is ever false — e.g. after a manually hand-edited YAML). */
    public boolean sameFootprint(LevelSnapshot o) {
        return o != null && world.equals(o.world)
                && minX == o.minX && minY == o.minY && minZ == o.minZ
                && sizeX == o.sizeX && sizeY == o.sizeY && sizeZ == o.sizeZ;
    }

    // --- capture (MAIN THREAD) ---

    public static LevelSnapshot capture(World w, Region region) {
        int minX = region.minX(), minY = region.minY(), minZ = region.minZ();
        int sizeX = region.sizeX(), sizeY = region.sizeY(), sizeZ = region.sizeZ();

        Map<String, Integer> paletteMap = new HashMap<>();
        List<String> paletteList = new ArrayList<>();
        short[] indices = new short[sizeX * sizeY * sizeZ];
        int idx = 0;
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    Block b = w.getBlockAt(minX + x, minY + y, minZ + z);
                    String s = b.getBlockData().getAsString();
                    Integer pi = paletteMap.get(s);
                    if (pi == null) {
                        pi = paletteList.size();
                        if (pi > 65535) throw new IllegalStateException("Palette exceeded 65535 unique blocks.");
                        paletteMap.put(s, pi);
                        paletteList.add(s);
                    }
                    indices[idx++] = (short) (int) pi;
                }
            }
        }
        return new LevelSnapshot(w.getName(), minX, minY, minZ, sizeX, sizeY, sizeZ,
                paletteList.toArray(new String[0]), indices);
    }

    // --- disk IO ---

    public void save(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                new GZIPOutputStream(new FileOutputStream(file))))) {
            out.writeUTF(MAGIC);
            out.writeInt(FORMAT);
            out.writeUTF(world);
            out.writeInt(minX); out.writeInt(minY); out.writeInt(minZ);
            out.writeInt(sizeX); out.writeInt(sizeY); out.writeInt(sizeZ);
            out.writeInt(palette.length);
            for (String s : palette) out.writeUTF(s);
            for (short v : indices) out.writeShort(v);
        }
    }

    public static LevelSnapshot load(File file) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                new GZIPInputStream(new FileInputStream(file))))) {
            String magic = in.readUTF();
            if (!MAGIC.equals(magic)) throw new IOException("Not a MorphedTrials level snapshot: " + file);
            int format = in.readInt();
            if (format != FORMAT) throw new IOException("Unsupported level snapshot format " + format);
            String world = in.readUTF();
            int minX = in.readInt(), minY = in.readInt(), minZ = in.readInt();
            int sizeX = in.readInt(), sizeY = in.readInt(), sizeZ = in.readInt();
            int paletteLen = in.readInt();
            String[] palette = new String[paletteLen];
            for (int i = 0; i < paletteLen; i++) palette[i] = in.readUTF();
            short[] indices = new short[sizeX * sizeY * sizeZ];
            for (int i = 0; i < indices.length; i++) indices[i] = in.readShort();
            return new LevelSnapshot(world, minX, minY, minZ, sizeX, sizeY, sizeZ, palette, indices);
        }
    }
}
