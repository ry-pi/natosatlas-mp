package dev.natowb.natosatlas.client;

import dev.natowb.natosatlas.client.cache.NARegionPixelCache;
import dev.natowb.natosatlas.client.io.MapSaver;
import dev.natowb.natosatlas.client.io.WorldMapExport;
import dev.natowb.natosatlas.client.platform.ClientWorldAccess;
import dev.natowb.natosatlas.core.NAPaths;
import dev.natowb.natosatlas.core.data.NAWorldInfo;
import dev.natowb.natosatlas.core.io.MapDataFiles;
import dev.natowb.natosatlas.core.util.LogUtil;

import java.io.File;
import java.nio.file.Path;

/**
 * mp7: "Delete all map data" for the current world/server. Deletes regions/ (every dimension and layer)
 * and the in-memory tiles/textures; keeps import/, slime_seed.txt, waypoints (*.points) and settings.
 */
public final class MapDataReset {
    private MapDataReset() {
    }

    /** Render thread. Returns false (with a chat message) if it couldn't run. */
    public static boolean deleteCurrentWorld() {
        NAWorldInfo info = ClientWorldAccess.get().getWorldInfo();
        if (info == null || NAPaths.getWorldDataPath() == null) return false;
        if (WorldMapExport.isBusy()) {
            WorldMapExport.post("A map image is being saved, try again in a moment");
            return false;
        }
        final Path root = NAPaths.getWorldMapRootPath(!info.isMultiplayer());
        boolean wasRunning = MapSaver.get().isRunning();
        try {
            // 1. no new tiles from the importer, 2. wait for the save worker to finish its current write and
            //    drop everything still queued, 3. forget every tile in memory (incl. dirty queue) and its texture
            RegionImporter.get().cancel();
            MapSaver.get().stopAndDiscard();
            NARegionPixelCache.get().clearAndDeleteTextures();

            // 4. one rename takes the whole folder away, so nothing can reload an old tile; delete it off-thread
            final Path trash = MapDataFiles.detach(root);
            final File[] leftovers = MapDataFiles.leftoverTrash(root == null ? null : root.getParent());
            Thread t = new Thread(() -> {
                int n = 0;
                try {
                    for (File f : leftovers) n += MapDataFiles.deleteTree(f.toPath());
                    LogUtil.info("[Reset] Deleted map data in {} ({} files)", root, n);
                } catch (Throwable e) {
                    LogUtil.warn("[Reset] Couldn't fully delete {}: {}", trash, e);
                }
            }, "NA-DeleteMap");
            t.setDaemon(true);
            t.start();
            WorldMapExport.post("Deleted all map data for this " + (info.isMultiplayer() ? "server" : "world")
                    + ". Waypoints, slime seed and import folder kept.");
            return true;
        } catch (Throwable e) {
            LogUtil.error("[Reset] failed: {}", e);
            WorldMapExport.post("Couldn't delete map data: " + e.getMessage());
            return false;
        } finally {
            if (wasRunning) MapSaver.get().start(); // starts re-mapping right away as chunks are scanned
        }
    }
}
