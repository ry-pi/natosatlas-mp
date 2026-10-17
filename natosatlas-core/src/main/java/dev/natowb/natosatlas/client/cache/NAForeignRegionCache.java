package dev.natowb.natosatlas.client.cache;

import dev.natowb.natosatlas.client.ui.screens.map.MapTileSource;
import dev.natowb.natosatlas.core.NAPaths;
import dev.natowb.natosatlas.core.data.NACoord;
import dev.natowb.natosatlas.core.data.NARegionPixelData;
import dev.natowb.natosatlas.core.storage.NARegionStorage;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * mp9: read-only tiles of a dimension the player is NOT in, for the world map's dimension toggle.
 * Loaded from the saved PNGs only; nothing here is ever marked dirty or saved, and live mapping keeps
 * writing to {@link NARegionPixelCache} (the player's own dimension) only. Render thread only.
 */
public class NAForeignRegionCache implements MapTileSource {

    private static final int CACHE_SIZE = 48; // regions (all layers) kept; ~1 MB pixels + 1 MB texture each

    private final int dim;
    private final boolean useSaveFolder;
    private final Map<Integer, File> dirs = new HashMap<>();
    private final Set<Long> missing = new HashSet<>();
    private final LinkedHashMap<Long, NARegionPixelData> regions = new LinkedHashMap<>(64, 0.75f, true);

    public NAForeignRegionCache(int dim, boolean isMultiplayer) {
        this.dim = dim;
        this.useSaveFolder = !isMultiplayer;
    }

    public int getDim() {
        return dim;
    }

    private static long key(NACoord c, int layerId) {
        // region coords are tiny (|x|,|z| < 2^25), layer < 16
        return ((long) c.x << 34) ^ (((long) c.z & 0x3FFFFFFFL) << 4) ^ layerId;
    }

    private File dir(int layerId) {
        File d = dirs.get(layerId);
        if (d == null) {
            Path root = NAPaths.getWorldMapRootPath(useSaveFolder);
            d = root == null ? null : root.resolve(String.format("DIM%d/layer_%d", dim, layerId)).toFile();
            dirs.put(layerId, d);
        }
        return d;
    }

    /** Whether any tile of this dimension and layer has been saved. */
    public boolean hasAnyTiles(int layerId) {
        File d = dir(layerId);
        if (d == null || !d.isDirectory()) return false;
        String[] names = d.list((f, n) -> n.startsWith("region_") && n.endsWith(".png"));
        return names != null && names.length > 0;
    }

    public NARegionPixelData getRegion(NACoord coord, int layerId) {
        long k = key(coord, layerId);
        NARegionPixelData r = regions.get(k);
        if (r != null) return r;
        if (missing.contains(k)) return null;
        File d = dir(layerId);
        if (d == null) return null;
        File f = new File(d, "region_" + coord.x + "_" + coord.z + ".png");
        Optional<NARegionPixelData> loaded = NARegionStorage.get().loadRegion(coord, f);
        if (!loaded.isPresent()) {
            missing.add(k);
            return null;
        }
        r = loaded.get();
        regions.put(k, r);
        trim();
        return r;
    }

    @Override
    public int getTexture(NACoord region, int layerId) {
        NARegionPixelData r = getRegion(region, layerId);
        return r == null ? -1 : r.getTextureId();
    }

    private void trim() {
        Iterator<Map.Entry<Long, NARegionPixelData>> it = regions.entrySet().iterator();
        while (regions.size() > CACHE_SIZE && it.hasNext()) {
            Map.Entry<Long, NARegionPixelData> e = it.next();
            e.getValue().deleteTexture();
            it.remove();
        }
    }

    /** Frees every texture (render thread). */
    public void clear() {
        for (NARegionPixelData r : regions.values()) r.deleteTexture();
        regions.clear();
        missing.clear();
    }
}
