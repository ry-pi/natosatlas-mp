package dev.natowb.natosatlas.core.io;

import dev.natowb.natosatlas.core.chunk.ChunkWrapper;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * mp5: minimal, defensive reader for b1.7.3 McRegion (.mcr) files.
 * Reads only Level.Blocks/Data/BlockLight/SkyLight (vanilla 32768-byte layout, index = x<<11 | z<<7 | y).
 * Any corrupt header, sector, compression or NBT error returns null / skips instead of throwing.
 */
public final class McRegionReader {

    private McRegionReader() {}

    /** Local (x,z) 0..31 of chunks present in the header; empty list if file is unreadable. */
    public static List<int[]> listChunks(File file) {
        List<int[]> out = new ArrayList<>();
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long len = raf.length();
            if (len < 8192) return out;
            for (int i = 0; i < 1024; i++) {
                int loc = raf.readInt();
                int sector = loc >>> 8, count = loc & 0xFF;
                if (sector < 2 || count == 0) continue;
                if ((long) sector * 4096 + 5 > len) continue; // points past end -> partial file
                out.add(new int[]{i & 31, i >> 5});
            }
        } catch (IOException | RuntimeException e) {
            out.clear();
        }
        return out;
    }

    /** mp13: the 8 KB header (1024 locations + 1024 save timestamps), or null if the file is shorter. */
    public static byte[] readHeader(File file) {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            if (raf.length() < 8192) return null;
            byte[] h = new byte[8192];
            raf.readFully(h);
            return h;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    public static ChunkWrapper readChunk(File file, int lx, int lz) {
        try {
            List<Spawner> sp = new ArrayList<>();
            byte[][] arrays = readArrays(file, lx, lz, sp);
            if (arrays == null) return null;
            ArrayChunk c = new ArrayChunk(arrays[0], arrays[1], arrays[2], arrays[3]);
            c.spawners = sp;
            return c;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Returns {blocks, data, blockLight, skyLight} or null. */
    static byte[][] readArrays(File file, int lx, int lz, List<Spawner> sp) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long len = raf.length();
            if (len < 8192) return null;
            raf.seek(4L * ((lx & 31) + (lz & 31) * 32));
            int loc = raf.readInt();
            int sector = loc >>> 8, count = loc & 0xFF;
            if (sector < 2 || count == 0) return null;
            long off = (long) sector * 4096;
            if (off + 5 > len) return null;
            raf.seek(off);
            int length = raf.readInt();
            if (length <= 1 || length > count * 4096 || off + 4 + length > len) return null;
            int type = raf.readByte();
            byte[] comp = new byte[length - 1];
            raf.readFully(comp);
            InputStream in;
            if (type == 1) in = new GZIPInputStream(new ByteArrayInputStream(comp));
            else if (type == 2) in = new InflaterInputStream(new ByteArrayInputStream(comp));
            else return null;
            DataInputStream din = new DataInputStream(new BufferedInputStream(in));
            byte[][] res = new byte[4][];
            if (din.readByte() != 10) return null; // root compound
            skipString(din);
            readCompound(din, res, 0, sp);
            if (res[0] == null || res[0].length != 32768) return null;
            if (res[1] == null || res[1].length != 16384) res[1] = new byte[16384];
            if (res[2] == null || res[2].length != 16384) res[2] = new byte[16384];
            if (res[3] == null || res[3].length != 16384) { res[3] = new byte[16384]; java.util.Arrays.fill(res[3], (byte) 0xFF); }
            return res;
        }
    }

    /** mp14: a mob spawner tile entity (block coordinates). */
    public static final class Spawner {
        public final int x, y, z;
        public final String mob;
        public Spawner(int x, int y, int z, String mob) { this.x = x; this.y = y; this.z = z; this.mob = mob; }
    }

    /** mp14: Level.TileEntities is a list of compounds; keep the ones with id "MobSpawner". */
    private static void readTileEntities(DataInputStream in, List<Spawner> out, int depth) throws IOException {
        int et = in.readByte();
        int n = in.readInt();
        if (n < 0 || n > 1 << 20) throw new IOException("bad list");
        if (et != 10) { for (int i = 0; i < n; i++) skipPayload(in, et, depth + 1); return; }
        for (int i = 0; i < n; i++) {
            String id = null, mob = null;
            int x = 0, y = 0, z = 0;
            while (true) {
                int t = in.readByte();
                if (t == 0) break;
                String name = in.readUTF();
                if (t == 8 && name.equals("id")) id = in.readUTF();
                else if (t == 8 && name.equals("EntityId")) mob = in.readUTF();
                else if (t == 3 && name.equals("x")) x = in.readInt();
                else if (t == 3 && name.equals("y")) y = in.readInt();
                else if (t == 3 && name.equals("z")) z = in.readInt();
                else skipPayload(in, t, depth + 1);
            }
            if ("MobSpawner".equals(id)) out.add(new Spawner(x, y, z, mob == null || mob.isEmpty() ? "Pig" : mob));
        }
    }

    private static void readCompound(DataInputStream in, byte[][] res, int depth, List<Spawner> sp) throws IOException {
        if (depth > 16) throw new IOException("nbt too deep");
        while (true) {
            int t = in.readByte();
            if (t == 0) return;
            String name = in.readUTF();
            if (t == 7) {
                int n = in.readInt();
                if (n < 0 || n > 1 << 22) throw new IOException("bad array");
                byte[] b = new byte[n];
                in.readFully(b);
                if (name.equals("Blocks")) res[0] = b;
                else if (name.equals("Data")) res[1] = b;
                else if (name.equals("BlockLight")) res[2] = b;
                else if (name.equals("SkyLight")) res[3] = b;
            } else if (t == 9 && name.equals("TileEntities")) {
                readTileEntities(in, sp, depth);
            } else if (t == 10) {
                readCompound(in, res, depth + 1, sp);
            } else {
                skipPayload(in, t, depth);
            }
        }
    }

    private static void skipString(DataInputStream in) throws IOException {
        int n = in.readUnsignedShort();
        in.skipNBytes(n);
    }

    private static void skipPayload(DataInputStream in, int t, int depth) throws IOException {
        if (depth > 16) throw new IOException("nbt too deep");
        switch (t) {
            case 1: in.skipNBytes(1); break;
            case 2: in.skipNBytes(2); break;
            case 3: case 5: in.skipNBytes(4); break;
            case 4: case 6: in.skipNBytes(8); break;
            case 7: { int n = in.readInt(); if (n < 0) throw new IOException(); in.skipNBytes(n); break; }
            case 8: skipString(in); break;
            case 9: {
                int et = in.readByte(); int n = in.readInt();
                if (n < 0 || n > 1 << 20) throw new IOException("bad list");
                for (int i = 0; i < n; i++) skipPayload(in, et, depth + 1);
                break;
            }
            case 10: {
                while (true) { int ct = in.readByte(); if (ct == 0) break; skipString(in); skipPayload(in, ct, depth + 1); }
                break;
            }
            case 11: { int n = in.readInt(); if (n < 0) throw new IOException(); in.skipNBytes(4L * n); break; }
            default: throw new IOException("bad tag " + t);
        }
    }

    /** ChunkWrapper over vanilla b1.7.3 arrays, with bounds clamping. */
    public static final class ArrayChunk extends ChunkWrapper {
        private final byte[] blocks, data, blockLight, skyLight;
        /** mp14: mob spawners in this chunk (from TileEntities), never null. */
        public List<Spawner> spawners = new ArrayList<>();

        public ArrayChunk(byte[] blocks, byte[] data, byte[] blockLight, byte[] skyLight) {
            super(null, 128);
            this.blocks = blocks; this.data = data; this.blockLight = blockLight; this.skyLight = skyLight;
        }

        private static int idx(int x, int y, int z) { return x << 11 | z << 7 | y; }

        private static int nibble(byte[] a, int i) {
            int b = a[i >> 1];
            return (i & 1) == 0 ? b & 15 : (b >> 4) & 15;
        }

        @Override public int getBlockId(int x, int y, int z) {
            if (!inBounds(x, y, z, 128)) return 0;
            return blocks[idx(x, y, z)] & 255;
        }
        @Override public int getBlockMeta(int x, int y, int z) {
            if (!inBounds(x, y, z, 128)) return 0;
            return nibble(data, idx(x, y, z));
        }
        @Override public int getBlockLight(int x, int y, int z) {
            if (!inBounds(x, y, z, 128)) return 0;
            return nibble(blockLight, idx(x, y, z));
        }
        @Override public int getSkyLight(int x, int y, int z) {
            if (!inBounds(x, y, z, 128)) return 15;
            return nibble(skyLight, idx(x, y, z));
        }
    }
}
