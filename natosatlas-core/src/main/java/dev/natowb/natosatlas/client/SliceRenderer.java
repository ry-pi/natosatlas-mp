package dev.natowb.natosatlas.client;

import dev.natowb.natosatlas.client.platform.BlockAccess;
import dev.natowb.natosatlas.client.platform.ClientWorldAccess;
import dev.natowb.natosatlas.client.texture.TextureUtils;
import dev.natowb.natosatlas.client.ui.screens.settings.Settings;
import dev.natowb.natosatlas.core.chunk.ChunkWrapper;
import dev.natowb.natosatlas.core.data.NACoord;
import dev.natowb.natosatlas.core.io.McRegionReader;

import java.io.File;
import java.util.*;

/**
 * mp14: the world map's Layer view. Draws the exact horizontal slice of blocks at one Y level, one pixel per block,
 * per 512x512 region tile. Data comes from chunks the client has loaded (dimension you are in) and otherwise from
 * the server region files set in Options > Import (or the old import/ folder). Rendered on demand, time-sliced, and
 * cached per (dimension, Y) for the last few levels.
 */
public final class SliceRenderer {
    private static final SliceRenderer INSTANCE = new SliceRenderer();
    public static SliceRenderer get() { return INSTANCE; }

    public static final int MIN_Y = 0, MAX_Y = 127;
    private static final long BUDGET_NANOS = 7_000_000L;
    private static final int KEEP_LEVELS = 6, MAX_TILES_PER_LEVEL = 64;
    public static final int NO_DATA = 0x00000000;

    private static final class Tile {
        int[] pixels = new int[512 * 512]; // null once finished and uploaded
        final boolean[] done = new boolean[1024];
        int next, tex = -1;
        boolean dirty, complete;
    }

    /** "dim:y" -> region key -> tile, in least-recently-used order. */
    private final LinkedHashMap<String, Map<Long, Tile>> levels = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<Integer, File> regionDirs = new HashMap<>();
    private String dirsFor = null;

    private SliceRenderer() {}

    private static String key(int dim, int y) { return dim + ":" + y; }

    /** Forget everything (e.g. after an import or when the paths change). */
    public void clear() {
        for (Map<Long, Tile> m : levels.values()) for (Tile t : m.values()) if (t.tex != -1) TextureUtils.deleteTexture(t.tex);
        levels.clear();
        regionDirs.clear();
        dirsFor = null;
    }

    private File regionDir(int dim) {
        String paths = Settings.importOverworldPath + "|" + Settings.importNetherPath + "|" + dev.natowb.natosatlas.core.NAPaths.getWorldDataPath();
        if (!paths.equals(dirsFor)) { regionDirs.clear(); dirsFor = paths; }
        if (regionDirs.containsKey(dim)) return regionDirs.get(dim);
        File d = null;
        String p = dim == 0 ? Settings.importOverworldPath : dim == -1 ? Settings.importNetherPath : null;
        if (p != null) d = RegionImporter.resolveRegionDir(p, dim, "", new StringBuilder());
        if (d == null) {
            File legacy = RegionImporter.getImportDir(dim);
            if (legacy != null && !RegionImporter.findRegionFiles(legacy).isEmpty())
                d = new File(legacy, "region").isDirectory() ? new File(legacy, "region") : legacy;
        }
        regionDirs.put(dim, d);
        return d;
    }

    /** Has the given dimension any region-file source? */
    public boolean hasRegionFiles(int dim) { return regionDir(dim) != null; }

    /**
     * Render more of the visible tiles (closest to centre first) within the frame budget; returns the texture of
     * each visible region that has anything drawn yet (others missing). live = the dimension the player is in.
     */
    public Map<Long, Integer> update(int dim, int y, boolean live, Collection<Long> visible, double centreBlockX, double centreBlockZ) {
        Map<Long, Tile> level = levels.computeIfAbsent(key(dim, y), k -> new HashMap<>());
        while (levels.size() > KEEP_LEVELS) {
            Iterator<Map.Entry<String, Map<Long, Tile>>> it = levels.entrySet().iterator();
            Map.Entry<String, Map<Long, Tile>> eldest = it.next();
            for (Tile t : eldest.getValue().values()) if (t.tex != -1) TextureUtils.deleteTexture(t.tex);
            it.remove();
        }
        List<Long> order = new ArrayList<>(visible);
        final double crx = centreBlockX / 512.0 - 0.5, crz = centreBlockZ / 512.0 - 0.5;
        order.sort(Comparator.comparingDouble(k -> {
            NACoord c = NACoord.fromKey(k);
            return (c.x - crx) * (c.x - crx) + (c.z - crz) * (c.z - crz);
        }));
        if (order.size() > MAX_TILES_PER_LEVEL) order = order.subList(0, MAX_TILES_PER_LEVEL);
        File dir = regionDir(dim);
        long end = System.nanoTime() + BUDGET_NANOS;
        for (long k : order) {
            if (System.nanoTime() > end) break;
            Tile t = level.computeIfAbsent(k, x -> new Tile());
            if (t.complete) continue;
            NACoord rc = NACoord.fromKey(k);
            File mcr = dir == null ? null : new File(dir, "r." + rc.x + "." + rc.z + ".mcr");
            boolean hasFile = mcr != null && mcr.isFile();
            while (t.next < 1024 && System.nanoTime() < end) {
                int i = t.next++;
                int lx = i & 31, lz = i >> 5;
                int cx = rc.x * 32 + lx, cz = rc.z * 32 + lz;
                ChunkWrapper w = live ? ClientWorldAccess.get().getChunk(NACoord.from(cx, cz)) : null;
                if (w == null && hasFile) w = McRegionReader.readChunk(mcr, lx, lz);
                if (w == null) continue;
                paintChunk(t.pixels, lx, lz, w, y);
                t.done[i] = true;
                t.dirty = true;
            }
            if (t.next >= 1024) t.complete = true;
        }
        Map<Long, Integer> out = new HashMap<>();
        for (long k : visible) {
            Tile t = level.get(k);
            if (t == null) continue;
            if (t.dirty) {
                if (t.tex == -1) t.tex = TextureUtils.createTextureFromBufferedImage(TextureUtils.pixelsToBufferedImage(t.pixels, 512, 512));
                else TextureUtils.updateTexture(t.tex, 512, 512, t.pixels);
                t.dirty = false;
            }
            if (t.complete && t.pixels != null && t.tex != -1) t.pixels = null;
            if (t.complete && t.tex == -1) t.pixels = null; // nothing there at all
            if (t.tex != -1) out.put(k, t.tex);
        }
        return out;
    }

    /** True while visible tiles at this level still have chunks to read. */
    public boolean isBusy(int dim, int y, Collection<Long> visible) {
        Map<Long, Tile> level = levels.get(key(dim, y));
        if (level == null) return true;
        int n = 0;
        for (long k : visible) {
            if (++n > MAX_TILES_PER_LEVEL) break;
            Tile t = level.get(k);
            if (t == null || !t.complete) return true;
        }
        return false;
    }

    static void paintChunk(int[] px, int lx, int lz, ChunkWrapper w, int y) {
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                px[(lz * 16 + z) * 512 + lx * 16 + x] = sliceColor(w, x, y, z);
            }
        }
    }

    /** Solid: its map colour (slightly darker when buried, brighter when open above). Air: dark, with a faint view of the floor below and torch light. */
    public static int sliceColor(ChunkWrapper w, int x, int y, int z) {
        try {
            return sliceColorUnsafe(w, x, y, z);
        } catch (RuntimeException e) { // unknown block id (e.g. a server-side plugin block) -> grey
            return 0xFF808080;
        }
    }

    private static int sliceColorUnsafe(ChunkWrapper w, int x, int y, int z) {
        int id = w.getBlockId(x, y, z);
        if (id != 0) {
            int c = blockColor(id, w.getBlockMeta(x, y, z));
            boolean openAbove = y >= MAX_Y || w.getBlockId(x, y + 1, z) == 0;
            return shade(c, openAbove ? 255 : 200);
        }
        int below = y > 0 ? w.getBlockId(x, y - 1, z) : 0;
        int base = below != 0 ? shade(blockColor(below, w.getBlockMeta(x, y - 1, z)), 85) : 0xFF141418;
        int light = w.getBlockLight(x, y, z);
        if (light > 0) base = mix(base, 0xFF3A3020, light / 15f * 0.6f);
        return base;
    }

    private static int blockColor(int id, int meta) {
        BlockAccess ba = BlockAccess.get();
        int c = -1;
        if (Settings.blockColors == Settings.BlockColors.TrueColor && !ba.isFluid(id)) c = ba.getTrueColor(id, meta);
        if (c == -1) c = ba.getColor(id, meta);
        if ((c & 0xFFFFFF) == 0) c = 0x808080; // blocks without a map colour (glass, torches, ...)
        return 0xFF000000 | c;
    }

    static int shade(int c, int f) {
        int r = ((c >> 16) & 255) * f / 255, g = ((c >> 8) & 255) * f / 255, b = (c & 255) * f / 255;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }
}
