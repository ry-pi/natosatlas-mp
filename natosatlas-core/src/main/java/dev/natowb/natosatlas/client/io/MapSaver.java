package dev.natowb.natosatlas.client.io;

import dev.natowb.natosatlas.client.platform.ClientWorldAccess;
import dev.natowb.natosatlas.client.cache.NARegionPixelCache;
import dev.natowb.natosatlas.core.LayerRegistry;
import dev.natowb.natosatlas.core.NAPaths;
import dev.natowb.natosatlas.core.data.*;
import dev.natowb.natosatlas.core.storage.NARegionStorage;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class MapSaver {

    private static MapSaver instance;

    public static MapSaver get() {
        if (instance == null) instance = new MapSaver();
        return instance;
    }

    private static final int BATCH_SIZE = 64;

    private final NARegionPixelCache cache = NARegionPixelCache.get();
    private final NARegionStorage storage = NARegionStorage.get();

    private final BlockingQueue<SaveTask> queue = new LinkedBlockingQueue<>();
    private final Map<String, SaveTask> pending = new ConcurrentHashMap<>();

    private Thread workerThread;
    private volatile boolean running = false;
    private volatile boolean shutdownRequested = false;

    private MapSaver() {
    }

    public void start() {
        if (running) return;
        running = true;
        shutdownRequested = false;

        workerThread = new Thread(this::processTasks, "NA-SaveWorker");
        workerThread.setDaemon(true);
        workerThread.start();
    }

    public void stop() {
        shutdownRequested = true;
        running = false;

        if (workerThread != null) {
            workerThread.interrupt();
            try {
                workerThread.join();
            } catch (InterruptedException ignored) {
            }
        }

        workerThread = null;
        pending.clear();
    }

    /** mp7: stop the worker and throw away every queued save (stop() alone leaves queued tasks behind). */
    public synchronized void stopAndDiscard() {
        stop();
        synchronized (pendingLock) {
            SaveTask t;
            while ((t = queue.poll()) != null) cache.unpinAfterSave(t.coord.toKey());
            pending.clear();
        }
    }

    public boolean isRunning() {
        return running;
    }

    public void saveNextBatch() {
        if (!running) return;

        for (int i = 0; i < BATCH_SIZE; i++) {
            Long key = cache.pollDirty();
            if (key == null) break;
            enqueueRegion(NACoord.fromKey(key));
        }
    }

    public int getPendingCount() {
        return pending.size();
    }

    /** MP patch: save every dirty region under an explicit dim (used before dimension change / leave). */
    public void flushAll(int dim, boolean isMultiplayer) {
        if (!running) return;
        Long key;
        while ((key = cache.pollDirty()) != null) {
            try {
                enqueueRegion(NACoord.fromKey(key), dim, isMultiplayer);
            } catch (Throwable t) {
                dev.natowb.natosatlas.core.util.LogUtil.warn("[MapSaver] flush failed: {}", t);
            }
        }
    }

    private void enqueueRegion(NACoord coord) {
        NAWorldInfo info = ClientWorldAccess.get().getWorldInfo();
        if (info == null) return;
        enqueueRegion(coord, info.getDimensionId(), info.isMultiplayer());
    }

    private void enqueueRegion(NACoord coord, int dim, boolean isMultiplayer) {

        for (NALayer layer : LayerRegistry.getLayers()) {
            NARegionPixelData region = cache.getRegion(layer.id, coord);
            if (region == null) continue;

            // mp8: the GL texture is refreshed on the render thread when the region is drawn
            // (NARegionPixelData.markTextureDirty, set by ChunkRenderer), so nothing to upload here.

            Path baseDir = NAPaths.getWorldMapStoragePath(layer.id, dim, !isMultiplayer);
            File file = baseDir.resolve("region_" + coord.x + "_" + coord.z + ".png").toFile();

            enqueueTask(new SaveTask(storage, coord, region, file));
        }
    }

    /** mp13: save a region that isn't in the live cache (the importer's off-screen tiles of another dimension). */
    public void enqueueExternal(NACoord coord, NARegionPixelData region, File file) {
        if (!running) {
            storage.saveRegionBlocking(coord, region, file);
            return;
        }
        enqueueTask(new SaveTask(storage, coord, region, file));
    }

    // mp8: guards pending + pins against the worker. Separate from 'this' because stopAndDiscard()
    // holds 'this' while it joins the worker.
    private final Object pendingLock = new Object();

    private void enqueueTask(SaveTask task) {
        synchronized (pendingLock) {
            if (shutdownRequested) return;

            String key = task.regionFile.getAbsolutePath();
            SaveTask existing = pending.get(key);

            if (existing != null) {
                // not started yet (the worker removes a task from pending before it reads the pixels)
                existing.region = task.region;
                return;
            }

            pending.put(key, task);
            cache.pinForSave(task.coord.toKey());
            queue.offer(task);
        }
    }

    private void processTasks() {
        while (true) {
            SaveTask task;

            try {
                if (running) {
                    task = queue.take();
                } else {
                    task = queue.poll();
                    if (task == null) break;
                }
            } catch (InterruptedException e) {
                if (!running) break;
                continue;
            }

            // mp8: leave 'pending' BEFORE reading the pixels. Previously the task stayed in 'pending' while
            // it was being written, so a change made during the write was merged into this already-running
            // task and never reached disk. Now such a change queues a fresh save.
            synchronized (pendingLock) {
                pending.remove(task.regionFile.getAbsolutePath(), task);
            }
            try {
                task.storage.saveRegionBlocking(task.coord, task.region, task.regionFile);
            } finally {
                cache.unpinAfterSave(task.coord.toKey());
            }
        }
    }

    private static final class SaveTask {
        final NARegionStorage storage;
        final NACoord coord;
        final File regionFile;
        NARegionPixelData region;

        SaveTask(NARegionStorage storage, NACoord coord, NARegionPixelData region, File regionFile) {
            this.storage = storage;
            this.coord = coord;
            this.region = region;
            this.regionFile = regionFile;
        }
    }
}
