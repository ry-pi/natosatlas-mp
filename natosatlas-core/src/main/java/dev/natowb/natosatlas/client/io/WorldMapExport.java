package dev.natowb.natosatlas.client.io;

import dev.natowb.natosatlas.client.NAClient;
import dev.natowb.natosatlas.client.platform.ClientWorldAccess;
import dev.natowb.natosatlas.client.ui.screens.settings.Settings;
import dev.natowb.natosatlas.core.NAPaths;
import dev.natowb.natosatlas.core.chunk.ChunkWrapper;
import dev.natowb.natosatlas.core.data.NACoord;
import dev.natowb.natosatlas.core.data.NAEntity;
import dev.natowb.natosatlas.core.data.NAWorldInfo;
import dev.natowb.natosatlas.core.io.WorldMapImage;
import dev.natowb.natosatlas.core.util.LogUtil;

import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * mp7: "Save map image (full / small)". Saves every stored tile of the current world/server, current
 * dimension and the layer the map is showing (Day / Night / Cave) to .minecraft/screenshots/worldmap-<ts>.png.
 * The click runs on the render thread only long enough to queue unsaved tiles; the stitching runs on a worker.
 */
public final class WorldMapExport {

    private static final AtomicBoolean BUSY = new AtomicBoolean(false);
    private static final ConcurrentLinkedQueue<String> MESSAGES = new ConcurrentLinkedQueue<>();
    private static volatile String status = "";
    private static volatile long statusTime = 0;

    private WorldMapExport() {
    }

    public static boolean isBusy() {
        return BUSY.get();
    }

    /** Last message, hidden 10 s after the export finished. */
    public static String getStatus() {
        if (!BUSY.get() && System.currentTimeMillis() - statusTime > 10000) return "";
        return status;
    }

    /** Thread-safe: shows in chat on the next client tick, and as a status line in the map/options screens. */
    public static void post(String msg) {
        status = msg;
        statusTime = System.currentTimeMillis();
        MESSAGES.add(msg);
    }

    /** Called from the client tick (main thread). */
    public static void drainMessages() {
        String m;
        while ((m = MESSAGES.poll()) != null) {
            try {
                NAClient.get().getPlatform().chat(m);
            } catch (Throwable t) {
                LogUtil.info("[MapImage] {}", m);
            }
        }
    }

    /** The layer the world map shows right now (same rules as MapScreen.tick). */
    public static int currentLayer() {
        NAWorldInfo info = ClientWorldAccess.get().getWorldInfo();
        if (info == null || info.hasCeiling()) return 2;
        switch (Settings.mapRenderMode) {
            case Day:
                return 0;
            case Night:
                return 1;
            case Cave:
                return 2;
            default: {
                boolean day = info.getTime() % 24000L < 12000L;
                try {
                    NAEntity p = ClientWorldAccess.get().getPlayer();
                    ChunkWrapper c = p == null ? null : ClientWorldAccess.get().getChunk(NACoord.from(p.chunkX, p.chunkZ));
                    if (c != null && c.getSkyLight(p.localX, (int) Math.floor(p.y), p.localZ) == 0) return 2;
                } catch (Throwable ignored) {
                }
                return day ? 0 : 1;
            }
        }
    }

    /** Render thread. */
    public static void start(final boolean small, final int layerId) {
        NAWorldInfo info = ClientWorldAccess.get().getWorldInfo();
        if (info == null || NAPaths.getMinecraftPath() == null || NAPaths.getWorldDataPath() == null) return;
        if (!BUSY.compareAndSet(false, true)) {
            post("Still saving the last map image, please wait");
            return;
        }
        try {
            final int dim = info.getDimensionId();
            final boolean mp = info.isMultiplayer();
            final File tileDir = NAPaths.getWorldMapStoragePath(layerId, dim, !mp).toFile();
            final File shots = new File(NAPaths.getMinecraftPath().toFile(), "screenshots");
            final String ts = WorldMapImage.timestamp(new Date());

            // write tiles that only exist in memory so far (needs the GL thread for updateTexture)
            MapSaver.get().flushAll(dim, mp);

            post("Saving world map" + (small ? " (small)" : "") + "...");
            Thread t = new Thread(() -> run(small, layerId, tileDir, shots, ts), "NA-MapImage");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            t.start();
        } catch (Throwable e) {
            BUSY.set(false);
            post("Couldn't save world map: " + e);
        }
    }

    private static void run(boolean small, int layerId, File tileDir, File shots, String ts) {
        try {
            // wait (max 20 s) for the save worker to finish writing the tiles we just queued
            long until = System.currentTimeMillis() + 20000;
            while (MapSaver.get().isRunning() && MapSaver.get().getPendingCount() > 0 && System.currentTimeMillis() < until) {
                Thread.sleep(50);
            }
            List<WorldMapImage.Tile> tiles = WorldMapImage.listTiles(tileDir);
            WorldMapImage.Plan plan = WorldMapImage.plan(tiles, small ? WorldMapImage.SMALL_SCALE : 1);
            if (plan.isEmpty()) {
                post("No map data to save yet for this " + (layerId == 2 ? "cave " : "") + "map");
                return;
            }
            if (plan.tooBig()) {
                post("Map is too big for one image (" + plan.blocksX / plan.scale + " x " + plan.blocksZ / plan.scale
                        + " px, max " + WorldMapImage.MAX_SIDE + ")" + (small ? "" : ". Try Save map image (small)"));
                return;
            }
            Runtime rt = Runtime.getRuntime();
            long free = rt.maxMemory() - (rt.totalMemory() - rt.freeMemory());
            if (plan.estimatedBandBytes() > free / 2) {
                post("Not enough memory for a " + plan.width + " x " + plan.height + " image"
                        + (small ? "" : ". Try Save map image (small)"));
                return;
            }
            if (!shots.isDirectory() && !shots.mkdirs()) {
                post("Couldn't create " + shots.getAbsolutePath());
                return;
            }
            File out = WorldMapImage.uniqueFile(shots, ts, small);
            LogUtil.info("[MapImage] Writing {} tiles from {} to {} ({}x{} px)", plan.tiles.size(), tileDir, out, plan.width, plan.height);
            WorldMapImage.write(plan, out);
            post("Saved world map as " + out.getName() + " (" + plan.width + " x " + plan.height + ")");
        } catch (Throwable e) {
            LogUtil.error("[MapImage] failed: {}", e);
            post("Couldn't save world map: " + e.getMessage());
        } finally {
            BUSY.set(false);
        }
    }
}
