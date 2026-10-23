package dev.natowb.natosatlas.client;

import dev.natowb.natosatlas.core.NAPaths;
import dev.natowb.natosatlas.core.io.McRegionReader.Spawner;
import dev.natowb.natosatlas.core.util.LogUtil;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * mp14: mob spawners found by the region import, per server and dimension, keyed by chunk. A re-read chunk replaces
 * only its own spawners; chunks skipped as unchanged keep theirs. One line per spawner in spawners.txt (next to the
 * import index, so "Delete all map data" forgets it too): {@code dim <TAB> x <TAB> y <TAB> z <TAB> mob}.
 */
public final class SpawnerStore {
    private SpawnerStore() {}

    private static Path loadedFrom;
    private static final Map<Integer, Map<Long, List<Spawner>>> byDim = new HashMap<>();
    private static final Map<Integer, List<Spawner>> flat = new HashMap<>();
    private static boolean dirty;

    private static long chunkKey(int cx, int cz) { return ((long) cx << 32) | (cz & 0xFFFFFFFFL); }

    private static Path file() {
        Path root = NAPaths.getWorldMapRootPath(false);
        return root == null ? null : root.resolve("spawners.txt");
    }

    /** (Re)loads when the server / world changed or the file was deleted with the map data. */
    private static synchronized void ensureLoaded() {
        Path f = file();
        if (Objects.equals(f, loadedFrom)) {
            // "Delete all map data" removed the file: forget what we had (unless an import is filling it right now)
            if (f != null && !dirty && !byDim.isEmpty() && !Files.isRegularFile(f)) { byDim.clear(); flat.clear(); }
            return;
        }
        byDim.clear();
        flat.clear();
        dirty = false;
        loadedFrom = f;
        if (f == null || !Files.isRegularFile(f)) return;
        try (BufferedReader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] p = line.split("\t");
                if (p.length != 5) continue;
                try {
                    int dim = Integer.parseInt(p[0]), x = Integer.parseInt(p[1]), y = Integer.parseInt(p[2]), z = Integer.parseInt(p[3]);
                    byDim.computeIfAbsent(dim, d -> new HashMap<>())
                            .computeIfAbsent(chunkKey(x >> 4, z >> 4), k -> new ArrayList<>())
                            .add(new Spawner(x, y, z, p[4]));
                } catch (NumberFormatException ignored) {}
            }
        } catch (IOException e) {
            LogUtil.warn("[Spawners] couldn't read {}: {}", f, e);
        }
    }

    /** Replace the spawners of one re-read chunk (an empty list removes them). */
    public static synchronized void setChunk(int dim, int cx, int cz, List<Spawner> list) {
        ensureLoaded();
        Map<Long, List<Spawner>> m = byDim.computeIfAbsent(dim, d -> new HashMap<>());
        long k = chunkKey(cx, cz);
        if (list == null || list.isEmpty()) { if (m.remove(k) == null) return; }
        else m.put(k, new ArrayList<>(list));
        flat.remove(dim);
        dirty = true;
    }

    /** Full re-import: forget a dimension's spawners. */
    public static synchronized void clearDim(int dim) {
        ensureLoaded();
        if (byDim.remove(dim) != null) dirty = true;
        flat.remove(dim);
    }

    public static synchronized List<Spawner> forDim(int dim) {
        ensureLoaded();
        List<Spawner> l = flat.get(dim);
        if (l == null) {
            l = new ArrayList<>();
            Map<Long, List<Spawner>> m = byDim.get(dim);
            if (m != null) for (List<Spawner> c : m.values()) l.addAll(c);
            flat.put(dim, l);
        }
        return l;
    }

    public static synchronized void save() {
        if (!dirty) return;
        Path f = file();
        if (f == null || !Objects.equals(f, loadedFrom)) return;
        try {
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling("spawners.txt.tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                for (Map.Entry<Integer, Map<Long, List<Spawner>>> d : byDim.entrySet())
                    for (List<Spawner> c : d.getValue().values())
                        for (Spawner s : c)
                            w.write(d.getKey() + "\t" + s.x + "\t" + s.y + "\t" + s.z + "\t" + s.mob.replace('\t', ' ') + "\n");
            }
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
            dirty = false;
        } catch (IOException e) {
            LogUtil.warn("[Spawners] couldn't write {}: {}", f, e);
        }
    }
}
