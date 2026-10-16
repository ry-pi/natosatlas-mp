package dev.natowb.natosatlas.client;

import dev.natowb.natosatlas.client.platform.ClientWorldAccess;
import dev.natowb.natosatlas.client.cache.NARegionPixelCache;
import dev.natowb.natosatlas.core.LayerRegistry;
import dev.natowb.natosatlas.core.chunk.ChunkRenderer;
import dev.natowb.natosatlas.core.chunk.ChunkWrapper;
import dev.natowb.natosatlas.core.data.*;
import dev.natowb.natosatlas.core.util.LogUtil;

import java.util.*;

public class MapUpdater {

    private static final int RADIUS = 8;
    private static MapUpdater instance;

    public static MapUpdater get() {
        if (instance == null) instance = new MapUpdater();
        return instance;
    }

    private final List<NACoord> scanOrder = new ArrayList<>();
    private int scanIndex = 0;

    private MapUpdater() {
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                if (dx * dx + dz * dz <= RADIUS * RADIUS) {
                    scanOrder.add(NACoord.from(dx, dz));
                }
            }
        }
    }

    public void tick() {
        if (scanOrder.isEmpty()) return;

        if (scanIndex >= scanOrder.size()) {
            scanIndex = 0;
            LogUtil.trace("[MapUpdater] Completed full scan cycle");
        }

        NAEntity player = ClientWorldAccess.get().getPlayer();
        if (player == null) return;
        NACoord offset = scanOrder.get(scanIndex++);
        NACoord chunkCoord = NACoord.from(player.chunkX + offset.x, player.chunkZ + offset.z);

        try {
            ChunkWrapper chunk = ClientWorldAccess.get().getChunk(chunkCoord);
            // MP patch: chunk not received from the server yet -> skip, it is retried on the next scan cycle
            if (chunk == null || chunk.isAllAir()) return;
            updateChunk(chunkCoord, chunk);
        } catch (Throwable t) {
            LogUtil.warn("[MapUpdater] Skipped chunk {} after error: {}", chunkCoord, t);
        }
    }

    public void updateChunk(NACoord chunkCoord, ChunkWrapper wrapper) {
        NACoord regionCoord = NACoord.from(chunkCoord.x >> 5, chunkCoord.z >> 5);

        for (NALayer layer : LayerRegistry.getLayers()) {
            updateLayer(regionCoord, chunkCoord, wrapper, layer);
        }

        NARegionPixelCache.get().markDirty(regionCoord);
    }

    private void updateLayer(NACoord regionCoord, NACoord chunkCoord, ChunkWrapper wrapper, NALayer layer) {
        NARegionPixelData region = NARegionPixelCache.get().getOrCreateRegion(layer.id, regionCoord);

        NAChunk chunk = layer.builder.build(chunkCoord, wrapper);
        if (chunk.isEmpty) return;

        ChunkRenderer.render(region, chunkCoord, chunk, layer.usesBlockLight);
    }
}