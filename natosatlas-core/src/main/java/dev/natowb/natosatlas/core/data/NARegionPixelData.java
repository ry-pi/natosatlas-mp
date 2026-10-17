package dev.natowb.natosatlas.core.data;

import dev.natowb.natosatlas.client.texture.TextureUtils;

import static dev.natowb.natosatlas.core.NAConstants.BLOCKS_PER_CANVAS_REGION;

public class NARegionPixelData {

    private final int[] pixels = new int[BLOCKS_PER_CANVAS_REGION * BLOCKS_PER_CANVAS_REGION];
    private int textureId = -1;
    // mp8: set whenever pixels change (live scan or import); the GL texture is re-uploaded on the next
    // getTextureId() call, which only happens on the render thread. Replaces the upload that used to
    // happen only when the save queue picked the region up.
    private volatile boolean textureDirty = false;

    public NARegionPixelData() {
    }

    public int[] getPixels() {
        return pixels;
    }


    public int getTextureId() {
        if (textureId == -1) {
            textureDirty = false;
            textureId = TextureUtils.createBlankTexture(BLOCKS_PER_CANVAS_REGION, BLOCKS_PER_CANVAS_REGION);
            TextureUtils.updateTexture(textureId, BLOCKS_PER_CANVAS_REGION, BLOCKS_PER_CANVAS_REGION, pixels);
        } else if (textureDirty) {
            textureDirty = false;
            TextureUtils.updateTexture(textureId, BLOCKS_PER_CANVAS_REGION, BLOCKS_PER_CANVAS_REGION, pixels);
        }
        return textureId;
    }

    /** mp8: pixels changed; re-upload the texture the next time it is drawn (any thread may call this). */
    public void markTextureDirty() {
        textureDirty = true;
    }

    public boolean isTextureDirty() {
        return textureDirty;
    }

    public void updateTexture() {
        textureDirty = false;
        if (textureId == -1) {
            textureId = TextureUtils.createBlankTexture(BLOCKS_PER_CANVAS_REGION, BLOCKS_PER_CANVAS_REGION);
        }

        TextureUtils.updateTexture(textureId, BLOCKS_PER_CANVAS_REGION, BLOCKS_PER_CANVAS_REGION, pixels);
    }

    public void deleteTexture() {
        if (textureId != -1) {
            TextureUtils.deleteTexture(textureId);
            textureId = -1;
        }
    }
}

