package dev.natowb.natosatlas.client;

import dev.natowb.natosatlas.client.platform.ClientWorldAccess;
import dev.natowb.natosatlas.core.NAPaths;
import dev.natowb.natosatlas.core.chunk.ChunkWrapper;
import dev.natowb.natosatlas.core.data.NACoord;
import dev.natowb.natosatlas.core.data.NAWorldInfo;
import dev.natowb.natosatlas.core.io.McRegionReader;
import dev.natowb.natosatlas.core.util.LogUtil;

import dev.natowb.natosatlas.client.io.MapSaver;
import dev.natowb.natosatlas.core.LayerRegistry;
import dev.natowb.natosatlas.core.chunk.ChunkRenderer;
import dev.natowb.natosatlas.core.data.NAChunk;
import dev.natowb.natosatlas.core.data.NALayer;
import dev.natowb.natosatlas.core.data.NARegionPixelData;
import dev.natowb.natosatlas.core.storage.NARegionStorage;

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

    private final ArrayDeque<Object[]> queue = new ArrayDeque<>(); // mp13: {FileJob, int lx, int lz}
    private int total, done, failed;
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

    /**
     * mp12: a folder as shown on screen, from the game folder down: {@code \minecraft\natosatlas\servers\...\import}
     * (the game folder's own name, e.g. "minecraft" or ".minecraft", with the OS separator). Full path if the folder
     * isn't inside the game folder or anything fails.
     */
    public static String displayPath(File dir, Path gameDir) {
        return displayPath(dir, gameDir, File.separator);
    }

    public static String displayPath(File dir, Path gameDir, String sep) {
        try {
            Path abs = dir.toPath().toAbsolutePath().normalize();
            Path game = gameDir.toAbsolutePath().normalize();
            if (!abs.startsWith(game) || game.getFileName() == null) return dir.getAbsolutePath();
            StringBuilder b = new StringBuilder(sep).append(game.getFileName());
            for (Path part : game.relativize(abs)) {
                if (part.toString().isEmpty()) continue;
                b.append(sep).append(part);
            }
            return b.toString();
        } catch (RuntimeException e) {
            return dir.getAbsolutePath();
        }
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

    /** The old button: the import/ folder of the current dimension (index applies here too). */
    public void start() {
        NAWorldInfo info = ClientWorldAccess.get().getWorldInfo();
        if (info == null || !info.isMultiplayer()) return;
        File dir = getImportDir(info.getDimensionId());
        List<Object[]> dirs = new ArrayList<>();
        if (dir != null) dirs.add(new Object[]{dir, info.getDimensionId()});
        startJobs(dirs, false, true);
        if (!running && total == 0 && changedRegions == 0 && skippedRegions == 0)
            status = "Import: no .mcr files in " + (dir == null ? "import folder" : dir.getAbsolutePath());
    }

    // ---------------------------------------------------------------------------------------------
    // mp13: import straight from server folders (Overworld + Nether boxes), skipping unchanged data.

    /** One region file being imported: its dim, its header (for the index) and how many queued chunks are left. */
    private static final class FileJob {
        final File file; final int dim, rx, rz; final String key; final long size, mtime; final byte[] header;
        int remaining;
        FileJob(File file, int dim, int rx, int rz, String key, long size, long mtime, byte[] header) {
            this.file = file; this.dim = dim; this.rx = rx; this.rz = rz; this.key = key;
            this.size = size; this.mtime = mtime; this.header = header;
        }
    }

    private ImportIndex index;
    private int changedRegions, skippedRegions, skippedChunks;
    private String error = "";
    // region of another dimension being rendered off-screen (saved straight to that dimension's tiles)
    private FileJob foreignJob;
    private NARegionPixelData[] foreignData;

    public String getError() { return error; }

    /** Overworld box: the folder itself or its region/ subfolder. Nether box: also DIM-1/region and DIM-1. */
    public static File resolveRegionDir(String raw, int dim, String label, StringBuilder err) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) s = s.substring(1, s.length() - 1).trim();
        if (s.isEmpty()) return null;
        File f = new File(s);
        if (f.isFile() && f.getName().endsWith(".mcr")) f = f.getAbsoluteFile().getParentFile();
        if (!f.isDirectory()) {
            append(err, label + " folder not found");
            return null;
        }
        File[] candidates = dim == -1
                ? new File[]{f, new File(f, "region"), new File(f, "DIM-1/region"), new File(f, "DIM-1")}
                : new File[]{f, new File(f, "region")};
        boolean sawMca = false;
        for (File c : candidates) {
            File[] fs = c.listFiles();
            if (fs == null) continue;
            for (File x : fs) {
                if (x.getName().endsWith(".mcr") && parseRegion(x.getName()) != null) return c;
                if (x.getName().endsWith(".mca")) sawMca = true;
            }
        }
        append(err, label + ": no .mcr region files" + (sawMca ? " (only .mca, not a b1.7.3 format)" : ""));
        return null;
    }

    private static void append(StringBuilder b, String s) {
        if (b.length() > 0) b.append(" | ");
        b.append(s);
    }

    /** mp13: the settings screen's Import button. force = ignore the index and re-import everything. */
    public void startFromPaths(String overworldPath, String netherPath, boolean force) {
        NAWorldInfo info = ClientWorldAccess.get().getWorldInfo();
        if (info == null || !info.isMultiplayer()) return;
        StringBuilder err = new StringBuilder();
        List<Object[]> dirs = new ArrayList<>();
        File ow = resolveRegionDir(overworldPath, 0, "Overworld", err);
        if (ow != null) dirs.add(new Object[]{ow, 0});
        File ne = resolveRegionDir(netherPath, -1, "Nether", err);
        if (ne != null) dirs.add(new Object[]{ne, -1});
        boolean bothEmpty = (overworldPath == null || overworldPath.trim().isEmpty())
                && (netherPath == null || netherPath.trim().isEmpty());
        startJobs(dirs, force, false);
        error = bothEmpty ? "Type a region folder path into a box first" : err.toString();
        if (dirs.isEmpty()) status = "";
    }

    /** dirs: {File dir, Integer dim}. legacy = old import/ folder button (searches dir and dir/region). */
    private void startJobs(List<Object[]> dirs, boolean force, boolean legacy) {
        cancel();
        NAWorldInfo info = ClientWorldAccess.get().getWorldInfo();
        dim = info.getDimensionId();
        error = "";
        changedRegions = skippedRegions = skippedChunks = 0;
        index = ImportIndex.load();
        int files = 0;
        // other dimensions first, file by file, so only one off-screen region is held at a time
        dirs.sort((a, b) -> Boolean.compare((Integer) a[1] == dim, (Integer) b[1] == dim));
        for (Object[] d : dirs) {
            File dir = (File) d[0];
            int fdim = (Integer) d[1];
            List<File> list = legacy ? findRegionFiles(dir) : listMcr(dir);
            for (File f : list) {
                files++;
                int[] rc = parseRegion(f.getName());
                String key = ImportIndex.key(fdim, f);
                long size = f.length(), mtime = f.lastModified();
                ImportIndex.Entry old = force ? null : index.get(key);
                if (old != null && old.size == size && old.mtime == mtime) { skippedRegions++; continue; }
                byte[] header = McRegionReader.readHeader(f);
                if (header == null) {
                    LogUtil.warn("[Import] {} has no readable header (corrupt or empty), skipped", f.getName());
                    continue;
                }
                FileJob job = new FileJob(f, fdim, rc[0], rc[1], key, size, mtime, header);
                for (int[] c : McRegionReader.listChunks(f)) {
                    int i = c[0] + c[1] * 32;
                    if (old != null && old.header != null && ImportIndex.sameChunk(old.header, header, i)) { skippedChunks++; continue; }
                    queue.add(new Object[]{job, c[0], c[1]});
                    job.remaining++;
                }
                if (job.remaining == 0) { index.put(key, size, mtime, header); skippedRegions++; }
                else changedRegions++;
            }
        }
        total = queue.size();
        done = failed = 0;
        running = total > 0;
        if (running) status = "Import: 0 / " + total + " chunks";
        else {
            index.save(); // headers of files whose chunks were all unchanged
            status = files == 0 ? "Import: no .mcr files found" : summary();
        }
        LogUtil.info("[Import] Starting: {} region files ({} changed, {} unchanged), {} chunks", files, changedRegions, skippedRegions, total);
    }

    private String summary() {
        String s = "Imported " + changedRegions + " changed region" + (changedRegions == 1 ? "" : "s")
                + ", skipped " + skippedRegions + " unchanged";
        if (failed > 0) s += ", " + failed + " chunks unreadable";
        return s;
    }

    private static List<File> listMcr(File dir) {
        List<File> out = new ArrayList<>();
        File[] fs = dir.listFiles();
        if (fs != null) for (File f : fs) if (f.getName().endsWith(".mcr") && parseRegion(f.getName()) != null) out.add(f);
        return out;
    }

    /** Off-screen render of one chunk into another dimension's tiles (loaded from disk, saved when the file is done). */
    private void renderForeign(FileJob job, int lx, int lz, NACoord coord) {
        if (foreignJob != job) {
            flushForeign();
            foreignJob = job;
            NACoord rc = NACoord.from(job.rx, job.rz);
            foreignData = new NARegionPixelData[LayerRegistry.getLayers().size()];
            for (NALayer layer : LayerRegistry.getLayers()) {
                File png = tileFile(layer.id, job.dim, rc);
                foreignData[layer.id] = NARegionStorage.get().loadRegion(rc, png).orElseGet(NARegionPixelData::new);
            }
        }
        ChunkWrapper w = McRegionReader.readChunk(job.file, lx, lz);
        if (w == null) { failed++; return; }
        for (NALayer layer : LayerRegistry.getLayers()) {
            NAChunk chunk = layer.builder.build(coord, w);
            if (chunk.isEmpty) continue;
            ChunkRenderer.render(foreignData[layer.id], coord, chunk, layer.usesBlockLight);
        }
    }

    private void flushForeign() {
        if (foreignJob == null) return;
        NACoord rc = NACoord.from(foreignJob.rx, foreignJob.rz);
        for (NALayer layer : LayerRegistry.getLayers())
            MapSaver.get().enqueueExternal(rc, foreignData[layer.id], tileFile(layer.id, foreignJob.dim, rc));
        foreignJob = null;
        foreignData = null;
    }

    private static File tileFile(int layerId, int dim, NACoord rc) {
        return NAPaths.getWorldMapStoragePath(layerId, dim, false).resolve("region_" + rc.x + "_" + rc.z + ".png").toFile();
    }

    private void finishChunk(FileJob job) {
        if (--job.remaining > 0) return;
        if (job.dim != dim) flushForeign();
        index.put(job.key, job.size, job.mtime, job.header);
    }

    public void cancel() {
        queue.clear();
        foreignJob = null; // half-rendered off-screen region: dropped, its file is not marked done
        foreignData = null;
        if (running) {
            LogUtil.info("[Import] Cancelled at {}/{}", done, total);
            if (index != null) index.save(); // keep the files that did finish
            status = "Import stopped at " + done + " / " + total + " chunks";
        }
        running = false;
    }

    public void tick() {
        if (!running) return;
        NAWorldInfo info = ClientWorldAccess.get().getWorldInfo();
        if (info == null || info.getDimensionId() != dim) { cancel(); status = "Import: cancelled (dimension changed)"; return; }
        long end = System.nanoTime() + BUDGET_NANOS;
        while (!queue.isEmpty() && System.nanoTime() < end) {
            Object[] t = queue.peek();
            FileJob job = (FileJob) t[0];
            // don't let off-screen tiles pile up in the save queue (each one is a full region in memory)
            if (job.dim != dim && foreignJob != job && MapSaver.get().getPendingCount() > 24) break;
            queue.poll();
            int lx = (Integer) t[1], lz = (Integer) t[2];
            NACoord coord = NACoord.from(job.rx * 32 + lx, job.rz * 32 + lz);
            done++;
            try {
                if (job.dim != dim) {
                    renderForeign(job, lx, lz, coord);
                } else {
                    // mp8: no longer skips chunks the client has loaded (see git history); imported pixels
                    // overwrite them, live scanning repaints the circle around the player as usual.
                    ChunkWrapper w = McRegionReader.readChunk(job.file, lx, lz);
                    if (w == null) failed++;
                    else MapUpdater.get().updateChunk(coord, w);
                }
            } catch (Throwable e) {
                failed++;
                LogUtil.warn("[Import] chunk {} in {} failed: {}", coord, job.file.getName(), e);
            }
            finishChunk(job);
        }
        status = "Import: " + done + " / " + total + " chunks";
        if (queue.isEmpty()) {
            running = false;
            flushForeign();
            index.save();
            status = summary();
            LogUtil.info("[Import] {} ({} chunks, {} unchanged chunks skipped)", status, done - failed, skippedChunks);
        }
    }
}
