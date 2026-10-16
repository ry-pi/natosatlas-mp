package dev.natowb.natosatlas.core.chunk;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;

/** mp3: caches the average top-face texture colour per (block id, meta). */
public final class TrueColorCache {
    private final ConcurrentHashMap<Long, Integer> cache = new ConcurrentHashMap<>();
    private volatile int[] atlas;   // ARGB pixels of terrain.png
    private volatile int atlasW, atlasH;

    public void setAtlas(int[] argb, int w, int h) {
        this.atlas = argb; this.atlasW = w; this.atlasH = h;
        cache.clear();
    }

    public boolean hasAtlas() { return atlas != null; }

    public void clear() { cache.clear(); }

    public int size() { return cache.size(); }

    public int get(int blockId, int meta, IntSupplier compute) {
        long key = ((long) blockId << 8) | (meta & 0xFF);
        Integer v = cache.get(key);
        if (v != null) return v;
        int c = compute.getAsInt();
        cache.put(key, c);
        return c;
    }

    /** Average opaque pixels of tile `index` in a 16x16-tile atlas; returns -1 if unavailable. */
    public int averageTile(int index) {
        int[] a = atlas;
        if (a == null) return -1;
        return averageTile(a, atlasW, atlasH, index);
    }

    public static int averageTile(int[] argb, int w, int h, int index) {
        if (index < 0 || index > 255 || w < 16 || h < 16) return -1;
        int ts = w / 16;
        int tx = (index % 16) * ts, ty = (index / 16) * (h / 16);
        long r = 0, g = 0, b = 0, n = 0;
        for (int y = ty; y < ty + h / 16; y++) {
            for (int x = tx; x < tx + ts; x++) {
                int p = argb[y * w + x];
                if (((p >>> 24) & 255) < 128) continue;
                r += (p >> 16) & 255; g += (p >> 8) & 255; b += p & 255; n++;
            }
        }
        if (n == 0) return -1;
        return 0xFF000000 | ((int) (r / n) << 16) | ((int) (g / n) << 8) | (int) (b / n);
    }

    /** Multiply tint like the game does for grass/leaves greyscale textures. */
    public static int multiply(int base, int tint) {
        int r = ((base >> 16) & 255) * ((tint >> 16) & 255) / 255;
        int g = ((base >> 8) & 255) * ((tint >> 8) & 255) / 255;
        int b = (base & 255) * (tint & 255) / 255;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
