package dev.natowb.natosatlas.client;

import dev.natowb.natosatlas.client.platform.ClientWorldAccess;
import dev.natowb.natosatlas.core.NAPaths;
import dev.natowb.natosatlas.core.chunk.ChunkWrapper;
import dev.natowb.natosatlas.core.data.NACoord;
import dev.natowb.natosatlas.core.data.NAWorldInfo;
import dev.natowb.natosatlas.core.io.McRegionReader;
import dev.natowb.natosatlas.core.util.LogUtil;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * mp5: imports server region files (natosatlas/servers/<server>/import[/DIMn]/) into the map,
 * time-sliced on the main thread (~6 ms per tick) so the game and live scanning keep running.
 */
public final class RegionImporter {
    private static final RegionImporter INSTANCE = new RegionImporter();
    public static RegionImporter get() { return INSTANCE; }

    private static final long BUDGET_NANOS = 6_000_000L;

    private final ArrayDeque<Object[]> queue = new ArrayDeque<>(); // {File, int lx, int lz, int rx, int rz}
    private int total, done, skippedLoaded, failed;
    private boolean running;
    private int dim;
    private String status = "";

    private RegionImporter() {}

    public boolean isRunning() { return running; }

    public String getStatus() { return status; }

    /** import/ for the overworld, import/DIM-1/ for the Nether, etc. Created on demand. */
    public static File getImportDir(int dim) {
        Path base = NAPaths.getWorldDataPath();
        if (base == null) return null;
        Path p = base.resolve("import");
        if (dim != 0) p = p.resolve("DIM" + dim);
        return NAPaths.ensurePathExists(p).toFile();
    }

    public static void ensureFolders() {
        getImportDir(0);
        getImportDir(-1);
    }

    /** Region files directly in the import dir or in its region/ subfolder. */
    public static List<File> findRegionFiles(File dir) {
        List<File> out = new ArrayList<>();
        if (dir == null) return out;
        for (File d : new File[]{dir, new File(dir, "region")}) {
            File[] fs = d.listFiles();
            if (fs == null) continue;
            for (File f : fs) {
                String n = f.getName();
                if (n.endsWith(".mca")) {
                    LogUtil.info("[Import] Ignoring {} (.mca/Anvil is not a b1.7.3 format)", n);
                } else if (n.endsWith(".mcr") && parseRegion(n) != null) {
                    out.add(f);
                }
            }
        }
        return out;
    }

    public static int[] parseRegion(String name) {
        try {
            String[] parts = name.substring(2, name.length() - 4).split("\\.");
            if (!name.startsWith("r.") || parts.length != 2) return null;
            return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
        } catch (RuntimeException e) {
            return null;
        }
    }

    public void start() {
        NAWorldInfo info = ClientWorldAccess.get().getWorldInfo();
        if (info == null || !info.isMultiplayer()) return;
        cancel();
        dim = info.getDimensionId();
        File dir = getImportDir(dim);
        List<File> files = findRegionFiles(dir);
        for (File f : files) {
            int[] rc = parseRegion(f.getName());
            List<int[]> chunks = McRegionReader.listChunks(f);
            if (chunks.isEmpty()) {
                LogUtil.warn("[Import] {} has no readable chunks (corrupt or empty), skipped", f.getName());
                continue;
            }
            for (int[] c : chunks) queue.add(new Object[]{f, c[0], c[1], rc[0], rc[1]});
        }
        total = queue.size();
        done = skippedLoaded = failed = 0;
        running = total > 0;
        status = running ? "Import: 0 / " + total + " chunks"
                : "Import: no .mcr files in " + (dir == null ? "import folder" : dir.getAbsolutePath());
        LogUtil.info("[Import] Starting: {} region files, {} chunks from {}", files.size(), total, dir);
    }

    public void cancel() {
        queue.clear();
        if (running) LogUtil.info("[Import] Cancelled at {}/{}", done, total);
        running = false;
    }

    public void tick() {
        if (!running) return;
        NAWorldInfo info = ClientWorldAccess.get().getWorldInfo();
        if (info == null || info.getDimensionId() != dim) { cancel(); status = "Import: cancelled (dimension changed)"; return; }
        long end = System.nanoTime() + BUDGET_NANOS;
        while (!queue.isEmpty() && System.nanoTime() < end) {
            Object[] t = queue.poll();
            File f = (File) t[0];
            int lx = (Integer) t[1], lz = (Integer) t[2];
            NACoord coord = NACoord.from((Integer) t[3] * 32 + lx, (Integer) t[4] * 32 + lz);
            done++;
            try {
                // live data wins: don't overwrite chunks the client currently has loaded
                if (ClientWorldAccess.get().getChunk(coord) != null) { skippedLoaded++; continue; }
                ChunkWrapper w = McRegionReader.readChunk(f, lx, lz);
                if (w == null) { failed++; continue; }
                MapUpdater.get().updateChunk(coord, w);
            } catch (Throwable e) {
                failed++;
                LogUtil.warn("[Import] chunk {} in {} failed: {}", coord, f.getName(), e);
            }
        }
        status = "Import: " + done + " / " + total + " chunks";
        if (queue.isEmpty()) {
            running = false;
            status = "Import done: " + (done - failed - skippedLoaded) + " imported, " + skippedLoaded
                    + " kept live, " + failed + " unreadable";
            LogUtil.info("[Import] {}", status);
        }
    }
}
