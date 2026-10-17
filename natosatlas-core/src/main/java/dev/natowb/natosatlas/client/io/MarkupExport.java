package dev.natowb.natosatlas.client.io;

import dev.natowb.natosatlas.client.platform.ClientWorldAccess;
import dev.natowb.natosatlas.client.ui.screens.map.MapDimensions;
import dev.natowb.natosatlas.client.ui.screens.map.markup.MarkupGeometry;
import dev.natowb.natosatlas.client.ui.screens.map.markup.MarkupImage;
import dev.natowb.natosatlas.client.ui.screens.map.markup.MarkupShape;
import dev.natowb.natosatlas.core.NAPaths;
import dev.natowb.natosatlas.core.data.NAWorldInfo;
import dev.natowb.natosatlas.core.io.WorldMapImage;
import dev.natowb.natosatlas.core.util.LogUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * mp9: "Save markup" on the world map: the map (the shown dimension and layer) around the markup, with the markup on
 * top, to .minecraft/screenshots/worldmap-markup-yyyy-MM-dd_HH.mm.ss.png. Same flow as the mp7 map image: unsaved
 * tiles are queued on the render thread, the picture is put together on a worker thread from the stored tiles.
 */
public final class MarkupExport {
    private static final AtomicBoolean BUSY = new AtomicBoolean(false);

    private MarkupExport() {
    }

    public static boolean isBusy() {
        return BUSY.get();
    }

    /** worldmap-markup-<ts>.png, with _1, _2 ... if taken. */
    public static File uniqueFile(File dir, String ts) {
        String base = "worldmap-markup-" + ts;
        File f = new File(dir, base + ".png");
        for (int i = 1; f.exists(); i++) f = new File(dir, base + "_" + i + ".png");
        return f;
    }

    /**
     * Render thread. shapes are copied, so the markup can keep changing (or be discarded) while it saves.
     * visibleBounds = the map view in world blocks, used when there are no shapes.
     */
    public static void start(List<MarkupShape> shapes, final int viewDim, final int layerId, double[] visibleBounds) {
        NAWorldInfo info = ClientWorldAccess.get().getWorldInfo();
        if (info == null || NAPaths.getMinecraftPath() == null || NAPaths.getWorldDataPath() == null) return;
        if (!BUSY.compareAndSet(false, true)) {
            WorldMapExport.post("Still saving the last markup image, please wait");
            return;
        }
        try {
            final List<MarkupShape> copy = new ArrayList<>(shapes);
            final boolean mp = info.isMultiplayer();
            final int playerDim = info.getDimensionId();
            final MarkupGeometry.ExportArea area = MarkupGeometry.exportArea(MarkupGeometry.bounds(copy), visibleBounds);
            final File root = NAPaths.getWorldMapRootPath(!mp).toFile();
            final File tileDir = new File(root, String.format("DIM%d/layer_%d", viewDim, layerId));
            final File shots = new File(NAPaths.getMinecraftPath().toFile(), "screenshots");
            final String ts = WorldMapImage.timestamp(new Date());
            // tiles of the player's dimension that only exist in memory so far -> disk (other dimensions are on disk)
            if (viewDim == playerDim) MapSaver.get().flushAll(playerDim, mp);
            WorldMapExport.post("Saving markup image...");
            Thread t = new Thread(() -> run(copy, area, viewDim, layerId, tileDir, shots, ts), "NA-MarkupImage");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            t.start();
        } catch (Throwable e) {
            BUSY.set(false);
            WorldMapExport.post("Couldn't save markup image: " + e);
        }
    }

    private static void run(List<MarkupShape> shapes, MarkupGeometry.ExportArea area, int dim, int layerId, File tileDir, File shots, String ts) {
        try {
            long until = System.currentTimeMillis() + 20000;
            while (MapSaver.get().isRunning() && MapSaver.get().getPendingCount() > 0 && System.currentTimeMillis() < until) {
                Thread.sleep(50);
            }
            if (!shots.isDirectory() && !shots.mkdirs()) {
                WorldMapExport.post("Couldn't create " + shots.getAbsolutePath());
                return;
            }
            String caption = String.format("%s  X %d..%d  Z %d..%d  (%s px/block)", MapDimensions.name(dim),
                    area.minX, area.minX + area.blocksX - 1, area.minZ, area.minZ + area.blocksZ - 1,
                    area.ppb >= 1 ? String.valueOf((int) area.ppb) : String.format("%.2f", area.ppb));
            BufferedImage img = MarkupImage.render(area, (rx, rz) -> {
                int[] px = new int[MarkupImage.REGION * MarkupImage.REGION];
                return WorldMapImage.readTile(new File(tileDir, "region_" + rx + "_" + rz + ".png"), px) ? px : null;
            }, shapes, caption);
            File out = uniqueFile(shots, ts);
            ImageIO.write(img, "png", out);
            LogUtil.info("[Markup] Saved {} ({}x{})", out, img.getWidth(), img.getHeight());
            WorldMapExport.post("Saved markup as " + out.getName() + " (" + img.getWidth() + " x " + img.getHeight() + ")");
        } catch (Throwable e) {
            LogUtil.error("[Markup] failed: {}", e);
            WorldMapExport.post("Couldn't save markup image: " + e.getMessage());
        } finally {
            BUSY.set(false);
        }
    }
}
