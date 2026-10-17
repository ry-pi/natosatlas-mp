package dev.natowb.natosatlas.core.io;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * mp7: stitches the stored map tiles (regions/DIMn/layer_n/region_X_Z.png, 512x512, 1 px per block)
 * into one PNG. Pure Java (no Minecraft / GL), so it runs on a worker thread and in headless tests.
 *
 * The output is streamed one band of regions at a time with a small built-in PNG encoder, so memory
 * stays at one row of regions no matter how big the explored area is. Unexplored pixels stay
 * transparent (the tiles store them as alpha 0). The image is cropped to the explored pixels.
 */
public final class WorldMapImage {

    public static final int REGION = 512;          // blocks (= pixels) per stored region tile side
    public static final int MAX_SIDE = 32768;       // refuse anything bigger than this per side
    public static final int SMALL_SCALE = 4;        // "small" = 1 pixel per 4x4 blocks (averaged)

    private WorldMapImage() {
    }

    /** One stored tile. */
    public static final class Tile {
        public final int rx, rz;
        public final File file;
        // explored bounds inside the tile (inclusive, tile-local), filled by plan()
        int minX = REGION, minZ = REGION, maxX = -1, maxZ = -1;

        Tile(int rx, int rz, File file) {
            this.rx = rx;
            this.rz = rz;
            this.file = file;
        }

        boolean empty() {
            return maxX < 0;
        }
    }

    /** What will be written. Block coordinates are world block coordinates. */
    public static final class Plan {
        public final List<Tile> tiles;
        public final int scale;
        public final int minBlockX, minBlockZ;   // world block at the image's top-left pixel
        public final int width, height;         // output pixels
        public final long blocksX, blocksZ;     // explored bbox in blocks (aligned to scale)

        Plan(List<Tile> tiles, int scale, int minBlockX, int minBlockZ, long blocksX, long blocksZ) {
            this.tiles = tiles;
            this.scale = scale;
            this.minBlockX = minBlockX;
            this.minBlockZ = minBlockZ;
            this.blocksX = blocksX;
            this.blocksZ = blocksZ;
            this.width = (int) Math.min(Integer.MAX_VALUE, blocksX / scale);
            this.height = (int) Math.min(Integer.MAX_VALUE, blocksZ / scale);
        }

        public boolean isEmpty() {
            return tiles.isEmpty();
        }

        public boolean tooBig() {
            return blocksX / scale > MAX_SIDE || blocksZ / scale > MAX_SIDE;
        }

        /** Rough peak heap need while writing: one decoded tile plus one band of kept tiles. */
        public long estimatedBandBytes() {
            long regionsAcross = (blocksX + REGION - 1) / REGION + 1;
            long perTile = (long) (REGION / scale) * (REGION / scale) * 4L;
            return regionsAcross * perTile + (long) REGION * REGION * 8L + (long) width * 8L;
        }
    }

    /** region_X_Z.png files in dir (negatives allowed). Anything else is ignored. */
    public static List<Tile> listTiles(File dir) {
        List<Tile> out = new ArrayList<>();
        File[] files = dir == null ? null : dir.listFiles();
        if (files == null) return out;
        for (File f : files) {
            int[] rc = parseTileName(f.getName());
            if (rc != null && f.isFile()) out.add(new Tile(rc[0], rc[1], f));
        }
        return out;
    }

    public static int[] parseTileName(String name) {
        if (!name.startsWith("region_") || !name.endsWith(".png")) return null;
        String[] parts = name.substring(7, name.length() - 4).split("_");
        if (parts.length != 2) return null;
        try {
            return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Reads every tile once to find the explored pixels, drops empty/unreadable tiles and returns the crop.
     * scale must divide 512 (1, 2, 4, 8 ...). The crop is aligned to scale so small pixels line up with world blocks.
     */
    public static Plan plan(List<Tile> tiles, int scale) throws IOException {
        if (scale < 1 || REGION % scale != 0) throw new IllegalArgumentException("scale must divide 512: " + scale);
        List<Tile> kept = new ArrayList<>();
        long minX = Long.MAX_VALUE, minZ = Long.MAX_VALUE, maxX = Long.MIN_VALUE, maxZ = Long.MIN_VALUE;
        int[] px = new int[REGION * REGION];
        for (Tile t : tiles) {
            if (!readTile(t.file, px)) continue;
            for (int z = 0; z < REGION; z++) {
                int row = z * REGION;
                for (int x = 0; x < REGION; x++) {
                    if ((px[row + x] >>> 24) != 0) {
                        if (x < t.minX) t.minX = x;
                        if (x > t.maxX) t.maxX = x;
                        if (z < t.minZ) t.minZ = z;
                        if (z > t.maxZ) t.maxZ = z;
                    }
                }
            }
            if (t.empty()) continue;
            kept.add(t);
            minX = Math.min(minX, (long) t.rx * REGION + t.minX);
            minZ = Math.min(minZ, (long) t.rz * REGION + t.minZ);
            maxX = Math.max(maxX, (long) t.rx * REGION + t.maxX);
            maxZ = Math.max(maxZ, (long) t.rz * REGION + t.maxZ);
        }
        if (kept.isEmpty()) return new Plan(kept, scale, 0, 0, 0, 0);
        long ax = Math.floorDiv(minX, (long) scale) * scale;
        long az = Math.floorDiv(minZ, (long) scale) * scale;
        long bx = (Math.floorDiv(maxX, (long) scale) + 1) * scale;
        long bz = (Math.floorDiv(maxZ, (long) scale) + 1) * scale;
        return new Plan(kept, scale, (int) ax, (int) az, bx - ax, bz - az);
    }

    /** Streams the plan to a PNG. Throws if the plan is empty or too big. */
    public static void write(Plan plan, File out) throws IOException {
        if (plan.isEmpty()) throw new IOException("no explored map data");
        if (plan.tooBig()) throw new IOException("image too big: " + plan.blocksX / plan.scale + "x" + plan.blocksZ / plan.scale);

        final int s = plan.scale;
        final int w = plan.width, h = plan.height;
        final int tileOut = REGION / s;

        Map<Integer, List<Tile>> byRow = new HashMap<>();
        for (Tile t : plan.tiles) byRow.computeIfAbsent(t.rz, k -> new ArrayList<>()).add(t);

        int firstRz = Math.floorDiv(plan.minBlockZ, REGION);
        int lastRz = Math.floorDiv(plan.minBlockZ + (int) plan.blocksZ - 1, REGION);
        final int cropEndX = plan.minBlockX + (int) plan.blocksX;
        final int firstRx = Math.floorDiv(plan.minBlockX, REGION);
        final int lastRx = Math.floorDiv(cropEndX - 1, REGION);

        String comment = "Natos Atlas world map. Top-left pixel = block X " + plan.minBlockX + ", Z " + plan.minBlockZ
                + ". " + s + " block(s) per pixel.";

        boolean ok = false;
        try (PngStream png = new PngStream(out, w, h, comment)) {
            int[] full = new int[REGION * REGION];
            int[] line = new int[w];
            int written = 0;
            for (int rz = firstRz; rz <= lastRz; rz++) {
                // load (and downscale) this band's tiles, indexed by rx - firstRx
                int[][] band = new int[lastRx - firstRx + 1][];
                List<Tile> row = byRow.get(rz);
                if (row != null) {
                    for (Tile t : row) {
                        if (t.rx < firstRx || t.rx > lastRx || !readTile(t.file, full)) continue;
                        band[t.rx - firstRx] = s == 1 ? full.clone() : downscale(full, s);
                    }
                }
                // output rows of this band that fall inside the crop
                int bandTop = rz * REGION;                               // world block z of the band's first row
                int fromBlock = Math.max(bandTop, plan.minBlockZ);
                int toBlock = Math.min(bandTop + REGION, plan.minBlockZ + (int) plan.blocksZ);
                for (int bz = fromBlock; bz < toBlock; bz += s) {
                    int localRow = (bz - bandTop) / s;                  // row inside the (scaled) tile
                    for (int rx = firstRx; rx <= lastRx; rx++) {
                        // this tile's columns that are inside the crop, in world blocks
                        int x0 = Math.max(rx * REGION, plan.minBlockX);
                        int x1 = Math.min(rx * REGION + REGION, cropEndX);
                        int dst = (x0 - plan.minBlockX) / s;
                        int n = (x1 - x0) / s;
                        int[] tile = band[rx - firstRx];
                        if (tile == null) {
                            java.util.Arrays.fill(line, dst, dst + n, 0);
                        } else {
                            System.arraycopy(tile, localRow * tileOut + (x0 - rx * REGION) / s, line, dst, n);
                        }
                    }
                    png.writeRow(line);
                    written++;
                }
            }
            if (written != h) throw new IOException("internal: wrote " + written + " of " + h + " rows");
            ok = true;
        } finally {
            if (!ok) //noinspection ResultOfMethodCallIgnored
                out.delete();
        }
    }

    /** Box filter with premultiplied alpha, so transparent (unexplored) pixels don't darken edges. */
    public static int[] downscale(int[] src, int s) {
        int n = REGION / s;
        int[] dst = new int[n * n];
        int count = s * s;
        for (int oz = 0; oz < n; oz++) {
            for (int ox = 0; ox < n; ox++) {
                long a = 0, r = 0, g = 0, b = 0;
                for (int dz = 0; dz < s; dz++) {
                    int base = (oz * s + dz) * REGION + ox * s;
                    for (int dx = 0; dx < s; dx++) {
                        int p = src[base + dx];
                        int pa = p >>> 24;
                        if (pa == 0) continue;
                        a += pa;
                        r += (long) ((p >> 16) & 0xFF) * pa;
                        g += (long) ((p >> 8) & 0xFF) * pa;
                        b += (long) (p & 0xFF) * pa;
                    }
                }
                if (a == 0) continue;
                int oa = (int) ((a + count / 2) / count);
                if (oa == 0) oa = 1;
                int or = (int) ((r + a / 2) / a), og = (int) ((g + a / 2) / a), ob = (int) ((b + a / 2) / a);
                dst[oz * n + ox] = (oa << 24) | (or << 16) | (og << 8) | ob;
            }
        }
        return dst;
    }

    private static boolean readTile(File f, int[] into) {
        try {
            BufferedImage img = ImageIO.read(f);
            if (img == null || img.getWidth() < REGION || img.getHeight() < REGION) return false;
            img.getRGB(0, 0, REGION, REGION, into, 0, REGION);
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    // ---------------------------------------------------------------- file names

    /** Vanilla screenshot style: 2026-10-16_20.38.05 */
    public static String timestamp(Date d) {
        return new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(d);
    }

    /** worldmap-<ts>.png / worldmap-<ts>-small.png, with _1, _2 ... if taken. */
    public static File uniqueFile(File dir, String ts, boolean small) {
        String base = "worldmap-" + ts + (small ? "-small" : "");
        File f = new File(dir, base + ".png");
        for (int i = 1; f.exists(); i++) f = new File(dir, base + "_" + i + ".png");
        return f;
    }

    // ---------------------------------------------------------------- minimal streaming PNG (RGBA8)

    static final class PngStream implements AutoCloseable {
        private final DataOutputStream file;
        private final DeflaterOutputStream idat;
        private final Deflater deflater = new Deflater(6);
        private final int width;
        private byte[] prev, cur, filt;

        PngStream(File f, int width, int height, String comment) throws IOException {
            this.width = width;
            this.file = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(f), 1 << 16));
            file.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
            ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
            DataOutputStream d = new DataOutputStream(ihdr);
            d.writeInt(width);
            d.writeInt(height);
            d.write(new byte[]{8, 6, 0, 0, 0}); // 8-bit RGBA, deflate, adaptive filter, no interlace
            chunk("IHDR", ihdr.toByteArray());
            if (comment != null) {
                chunk("tEXt", ("Comment\0" + comment).getBytes(StandardCharsets.ISO_8859_1));
            }
            this.idat = new DeflaterOutputStream(new IdatOut(), deflater, 1 << 16);
            this.prev = new byte[width * 4];
            this.cur = new byte[width * 4];
            this.filt = new byte[width * 4];
        }

        void writeRow(int[] argb) throws IOException {
            for (int x = 0, i = 0; x < width; x++) {
                int p = argb[x];
                cur[i++] = (byte) (p >> 16);
                cur[i++] = (byte) (p >> 8);
                cur[i++] = (byte) p;
                cur[i++] = (byte) (p >>> 24);
            }
            // "Up" filter: map rows repeat a lot vertically, and it's cheap
            idat.write(2);
            for (int i = 0; i < cur.length; i++) filt[i] = (byte) (cur[i] - prev[i]);
            idat.write(filt);
            byte[] t = prev;
            prev = cur;
            cur = t;
        }

        private void chunk(String type, byte[] data) throws IOException {
            chunk(type, data, 0, data.length);
        }

        private void chunk(String type, byte[] data, int off, int len) throws IOException {
            byte[] t = type.getBytes(StandardCharsets.US_ASCII);
            CRC32 crc = new CRC32();
            crc.update(t);
            crc.update(data, off, len);
            file.writeInt(len);
            file.write(t);
            file.write(data, off, len);
            file.writeInt((int) crc.getValue());
        }

        private final class IdatOut extends OutputStream {
            @Override
            public void write(int b) throws IOException {
                write(new byte[]{(byte) b}, 0, 1);
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                if (len > 0) chunk("IDAT", b, off, len);
            }
        }

        @Override
        public void close() throws IOException {
            try {
                idat.finish();
                deflater.end();
                chunk("IEND", new byte[0]);
            } finally {
                file.close();
            }
        }
    }
}
