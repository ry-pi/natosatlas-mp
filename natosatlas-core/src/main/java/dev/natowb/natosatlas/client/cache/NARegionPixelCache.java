package dev.natowb.natosatlas.client.cache;

import dev.natowb.natosatlas.core.NAPaths;
import dev.natowb.natosatlas.client.platform.ClientWorldAccess;
import dev.natowb.natosatlas.core.LayerRegistry;
import dev.natowb.natosatlas.core.data.NACoord;
import dev.natowb.natosatlas.core.data.NARegionPixelData;
import dev.natowb.natosatlas.core.storage.NARegionStorage;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

public class NARegionPixelCache {

    private final Queue<Long> dirtyQueue = new ArrayDeque<>();
    private final Set<Long> dirtySet = new HashSet<>();

    private static final int CACHE_SIZE = 256;

    private static NARegionPixelCache instance;

    private NARegionPixelCache() {
    }

    public static NARegionPixelCache get() {
        if (instance == null) {
            instance = new NARegionPixelCache();
        }
        return instance;
    }

    // mp8: LRU, but a region is never evicted while it has unsaved changes (dirty) or a save queued /
    // in progress (pinned by MapSaver). Evicting such a region made the next getRegion() reload the
    // older PNG from disk; updates then went into that stale copy, which was later saved over the newer
    // data (e.g. a big import). Trimmed by hand in trim() instead of removeEldestEntry, which can only
    // look at the single eldest entry.
    private final Map<Long, NARegionPixelData[]> regions =
            new LinkedHashMap<Long, NARegionPixelData[]>(CACHE_SIZE, 0.75f, true);

    // region key -> number of save tasks queued/running for it (written by the save worker too)
    private final java.util.concurrent.ConcurrentHashMap<Long, Integer> savePins = new java.util.concurrent.ConcurrentHashMap<>();

    /** mp8: called by MapSaver when it queues a save of this region (any layer). */
    public void pinForSave(long key) {
        savePins.merge(key, 1, Integer::sum);
    }

    /** mp8: called by the save worker when that save has been written (or discarded). */
    public void unpinAfterSave(long key) {
        savePins.computeIfPresent(key, (k, v) -> v <= 1 ? null : v - 1);
    }

    public boolean isPinned(long key) {
        return dirtySet.contains(key) || savePins.containsKey(key);
    }

    private void trim() {
        if (regions.size() <= CACHE_SIZE) return;
        Iterator<Map.Entry<Long, NARegionPixelData[]>> it = regions.entrySet().iterator();
        while (regions.size() > CACHE_SIZE && it.hasNext()) {
            Map.Entry<Long, NARegionPixelData[]> e = it.next(); // eldest first; iterating does not reorder
            if (isPinned(e.getKey())) continue;
            it.remove();
        }
    }


    public NARegionPixelData getOrCreateRegion(int layerId, NACoord region) {
        NARegionPixelData r = getRegion(layerId, region);
        if (r == null) {
            r = new NARegionPixelData();
            put(layerId, region, r);
        }
        return r;
    }


    public NARegionPixelData getRegion(int layerId, NACoord coord) {
        long key = coord.toKey();

        NARegionPixelData[] arr = regions.get(key);
        if (arr != null && arr[layerId] != null)
            return arr[layerId];
        int dim = ClientWorldAccess.get().getWorldInfo().getDimensionId();
        boolean isMultiplayer = ClientWorldAccess.get().getWorldInfo().isMultiplayer();
        Path dir = NAPaths.getWorldMapStoragePath(layerId, dim, !isMultiplayer);
        File rFile = dir.resolve("region_" + coord.x + "_" + coord.z + ".png").toFile();
        Optional<NARegionPixelData> loaded = NARegionStorage.get().loadRegion(coord, rFile);
        if (loaded.isPresent()) {
            if (arr == null) {
                arr = new NARegionPixelData[LayerRegistry.getLayers().size()];
                regions.put(key, arr);
            }

            arr[layerId] = loaded.get();
            NARegionPixelData r = arr[layerId];
            trim();
            return r;
        }

        return null;
    }

    public void put(int layerId, NACoord coord, NARegionPixelData region) {
        long key = coord.toKey();
        NARegionPixelData[] arr = regions.computeIfAbsent(key, k -> new NARegionPixelData[LayerRegistry.getLayers().size()]);
        arr[layerId] = region;
        trim();
    }

    public void markDirty(NACoord coord) {
        long key = coord.toKey();
        if (dirtySet.add(key)) {
            dirtyQueue.add(key);
        }
    }

    public Long pollDirty() {
        Long key = dirtyQueue.poll();
        if (key != null) dirtySet.remove(key);
        return key;
    }

    public void clear() {
        for (NARegionPixelData[] arr : regions.values()) {
            if (arr == null) continue;
            Arrays.fill(arr, null);
        }

        regions.clear();
        dirtyQueue.clear();
        dirtySet.clear();
        savePins.clear();
    }

    /** mp7: like clear(), but also frees the GL textures (call on the render thread). */
    public void clearAndDeleteTextures() {
        for (NARegionPixelData[] arr : regions.values()) {
            if (arr == null) continue;
            for (NARegionPixelData r : arr) if (r != null) r.deleteTexture();
        }
        clear();
    }

    public int getRegionCount() {
        return regions.size();
    }

    public int getTotalCount() {
        int total = 0;
        for (NARegionPixelData[] arr : regions.values()) {
            if (arr == null) continue;
            for (NARegionPixelData region : arr) {
                if (region != null) total++;
            }
        }
        return total;
    }

    public int getDirtyQueueSize() {
        return dirtyQueue.size();
    }
}
