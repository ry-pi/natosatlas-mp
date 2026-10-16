package dev.natowb.natosatlas.client.ui.screens.map;

import dev.natowb.natosatlas.client.NAClient;
import dev.natowb.natosatlas.core.data.NACoord;
import dev.natowb.natosatlas.client.ui.screens.settings.Settings;
import dev.natowb.natosatlas.core.NAConstants;
import dev.natowb.natosatlas.client.texture.TextureProvider;

import java.util.Random;
import java.util.Set;

import static dev.natowb.natosatlas.core.NAConstants.CHUNKS_PER_MINECRAFT_REGION;
import static dev.natowb.natosatlas.core.NAConstants.PIXELS_PER_CANVAS_CHUNK;

public class MapStageSlime implements MapStage {
    @Override
    public void draw(MapContext ctx, Set<Long> visibleRegions, int activeLayer) {
        if (!Settings.showSlimeChunks) return;
        final long seed = dev.natowb.natosatlas.client.SlimeSeed.getEffectiveSeed(); // mp6: custom seed override, read once per draw
        for (long key : visibleRegions) {
            NACoord coord = NACoord.fromKey(key);
            int texId = TextureProvider.getTexture(coord, activeLayer);
            if (texId != -1) {
                for (int x = 0; x < CHUNKS_PER_MINECRAFT_REGION; x++) {
                    for (int z = 0; z < CHUNKS_PER_MINECRAFT_REGION; z++) {
                        int worldChunkX = coord.x * CHUNKS_PER_MINECRAFT_REGION + x;
                        int worldChunkZ = coord.z * CHUNKS_PER_MINECRAFT_REGION + z;
                        if (!isSlimeChunk(seed, worldChunkX, worldChunkZ)) continue;
                        drawSlimeChunkSquare(worldChunkX, worldChunkZ);
                    }
                }
            }
        }
    }

    // FIXME: move this to a global acessor at some point
    public static boolean isSlimeChunk(long seed, int worldChunkX, int worldChunkZ) {
        // mp6: exact b1.7.3 Chunk.getSlimeRandom arithmetic (x terms and z*389711 overflow as int, z*z widened before multiply).
        // The original code widened every term to long, which drifted from vanilla beyond ~|chunkX| 21.
        long s = seed
                + (long) (worldChunkX * worldChunkX * 4987142)
                + (long) (worldChunkX * 5947611)
                + (long) (worldChunkZ * worldChunkZ) * 4392871L
                + (long) (worldChunkZ * 389711)
                ^ 987234911L;
        return new Random(s).nextInt(10) == 0;
    }

    private void drawSlimeChunkSquare(int chunkX, int chunkZ) {
        double worldX = chunkX * 16;
        double worldZ = chunkZ * 16;

        int px = (int) (worldX * NAConstants.PIXELS_PER_CANVAS_UNIT);
        int pz = (int) (worldZ * NAConstants.PIXELS_PER_CANVAS_UNIT);

        int size = PIXELS_PER_CANVAS_CHUNK;

        int x1 = px + size;
        int y1 = pz + size;
        int x2 = px;
        int y2 = pz;

        NAClient.get().getPlatform().painter.drawRect(
                x1, y1, x2, y2,
                0x8000FF00
        );
    }

}
