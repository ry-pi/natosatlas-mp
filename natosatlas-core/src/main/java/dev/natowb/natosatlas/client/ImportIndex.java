package dev.natowb.natosatlas.client;

import dev.natowb.natosatlas.core.NAPaths;
import dev.natowb.natosatlas.core.util.LogUtil;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * mp13: what was last imported, per server, so a re-import only reads region files (and chunks) that changed.
 * One line per region file: {@code dim|path <TAB> size <TAB> lastModified <TAB> base64(deflate(8 KB header))}.
 * The header holds every chunk's sector location and save timestamp, which is what the per-chunk skip compares.
 * Lives inside the regions/ folder, so "Delete all map data" also forgets it (and the next import is a full one).
 */
final class ImportIndex {

    static final class Entry {
        final long size, mtime;
        final byte[] header;
        Entry(long size, long mtime, byte[] header) { this.size = size; this.mtime = mtime; this.header = header; }
    }

    private final Map<String, Entry> entries = new HashMap<>();
    private final File file;

    private ImportIndex(File file) { this.file = file; }

    static String key(int dim, File f) {
        String p;
        try { p = f.getCanonicalPath(); } catch (IOException e) { p = f.getAbsolutePath(); }
        return dim + "|" + p;
    }

    Entry get(String key) { return entries.get(key); }

    void put(String key, long size, long mtime, byte[] header) { entries.put(key, new Entry(size, mtime, header)); }

    /** Chunk i (x + z*32) is unchanged if its location and its non-zero save timestamp are both the same. */
    static boolean sameChunk(byte[] a, byte[] b, int i) {
        int ts = readInt(b, 4096 + i * 4);
        return ts != 0 && ts == readInt(a, 4096 + i * 4) && readInt(a, i * 4) == readInt(b, i * 4);
    }

    private static int readInt(byte[] h, int o) {
        return (h[o] & 0xFF) << 24 | (h[o + 1] & 0xFF) << 16 | (h[o + 2] & 0xFF) << 8 | (h[o + 3] & 0xFF);
    }

    static ImportIndex load() {
        Path root = NAPaths.getWorldMapRootPath(false);
        ImportIndex idx = new ImportIndex(root == null ? null : root.resolve("import-index.txt").toFile());
        if (idx.file == null || !idx.file.isFile()) return idx;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(idx.file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] p = line.split("\t");
                if (p.length != 4) continue;
                try {
                    byte[] h = inflate(Base64.getDecoder().decode(p[3]));
                    if (h.length != 8192) continue;
                    idx.entries.put(p[0], new Entry(Long.parseLong(p[1]), Long.parseLong(p[2]), h));
                } catch (RuntimeException | IOException ignored) {}
            }
        } catch (IOException e) {
            LogUtil.warn("[Import] couldn't read {}: {}", idx.file, e);
        }
        return idx;
    }

    void save() {
        if (file == null) return;
        try {
            Files.createDirectories(file.toPath().getParent());
            File tmp = new File(file.getPath() + ".tmp");
            try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
                for (Map.Entry<String, Entry> e : entries.entrySet()) {
                    Entry v = e.getValue();
                    w.write(e.getKey() + "\t" + v.size + "\t" + v.mtime + "\t" + Base64.getEncoder().encodeToString(deflate(v.header)) + "\n");
                }
            }
            Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LogUtil.warn("[Import] couldn't write {}: {}", file, e);
        }
    }

    private static byte[] deflate(byte[] b) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (DeflaterOutputStream d = new DeflaterOutputStream(out)) { d.write(b); }
        return out.toByteArray();
    }

    private static byte[] inflate(byte[] b) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InflaterInputStream in = new InflaterInputStream(new ByteArrayInputStream(b))) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
