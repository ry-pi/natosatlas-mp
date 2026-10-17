package dev.natowb.natosatlas.client.ui.screens.map;

import dev.natowb.natosatlas.core.data.NACoord;

/** mp9: where the world map gets region textures from when it is not the live cache (another dimension). */
public interface MapTileSource {
    /** GL texture id of a region tile, or -1 if there is no saved tile. Render thread only. */
    int getTexture(NACoord region, int layerId);
}
