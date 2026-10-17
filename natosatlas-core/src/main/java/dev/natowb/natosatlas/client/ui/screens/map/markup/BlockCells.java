package dev.natowb.natosatlas.client.ui.screens.map.markup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * mp11: a set of world block cells, stored per row (z) as sorted, non-overlapping, non-touching spans [x0, x1]
 * (inclusive). Every cell is in at most one span, so drawing the spans never paints a block twice (the alpha stays
 * even), and a big filled shape is one quad per row instead of one per block.
 */
public final class BlockCells {

    public interface SpanVisitor {
        void span(int z, int x0, int x1);
    }

    private final TreeMap<Integer, int[]> rows = new TreeMap<>(); // z -> {x0, x1, x0, x1, ...}
    private long count = 0;

    public static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    public static int keyX(long k) {
        return (int) (k >> 32);
    }

    public static int keyZ(long k) {
        return (int) k;
    }

    public static BlockCells fromSet(Set<Long> cells) {
        Map<Integer, List<int[]>> m = new HashMap<>();
        for (long k : cells) m.computeIfAbsent(keyZ(k), z -> new ArrayList<>()).add(new int[]{keyX(k), keyX(k)});
        return fromRows(m);
    }

    /** Rows of possibly overlapping spans -> normalised cells. */
    public static BlockCells fromRows(Map<Integer, List<int[]>> rowSpans) {
        BlockCells c = new BlockCells();
        for (Map.Entry<Integer, List<int[]>> e : rowSpans.entrySet()) {
            List<int[]> sp = new ArrayList<>(e.getValue());
            sp.removeIf(s -> s[1] < s[0]);
            if (sp.isEmpty()) continue;
            sp.sort((a, b) -> Integer.compare(a[0], b[0]));
            List<Integer> flat = new ArrayList<>();
            int cs = sp.get(0)[0], ce = sp.get(0)[1];
            for (int i = 1; i < sp.size(); i++) {
                int[] s = sp.get(i);
                if (s[0] <= ce + 1) ce = Math.max(ce, s[1]);
                else {
                    flat.add(cs);
                    flat.add(ce);
                    cs = s[0];
                    ce = s[1];
                }
            }
            flat.add(cs);
            flat.add(ce);
            int[] arr = new int[flat.size()];
            for (int i = 0; i < arr.length; i++) arr[i] = flat.get(i);
            for (int i = 0; i < arr.length; i += 2) c.count += arr[i + 1] - arr[i] + 1;
            c.rows.put(e.getKey(), arr);
        }
        return c;
    }

    public boolean contains(int x, int z) {
        int[] r = rows.get(z);
        if (r == null) return false;
        for (int i = 0; i < r.length; i += 2) {
            if (x < r[i]) return false;
            if (x <= r[i + 1]) return true;
        }
        return false;
    }

    public long count() {
        return count;
    }

    public boolean isEmpty() {
        return count == 0;
    }

    /** These cells without the given ones. */
    public BlockCells minus(Set<Long> remove) {
        if (remove.isEmpty()) return this;
        Map<Integer, List<Integer>> byRow = new HashMap<>();
        for (long k : remove) byRow.computeIfAbsent(keyZ(k), z -> new ArrayList<>()).add(keyX(k));
        Map<Integer, List<int[]>> out = new HashMap<>();
        for (Map.Entry<Integer, int[]> e : rows.entrySet()) {
            int[] r = e.getValue();
            List<int[]> sp = new ArrayList<>();
            List<Integer> holes = byRow.get(e.getKey());
            int[] h = null;
            if (holes != null) {
                h = new int[holes.size()];
                for (int i = 0; i < h.length; i++) h[i] = holes.get(i);
                Arrays.sort(h);
            }
            for (int i = 0; i < r.length; i += 2) {
                int s = r[i], end = r[i + 1];
                if (h != null) {
                    for (int x : h) {
                        if (x < s || x > end) continue;
                        if (x > s) sp.add(new int[]{s, x - 1});
                        s = x + 1;
                    }
                }
                if (s <= end) sp.add(new int[]{s, end});
            }
            out.put(e.getKey(), sp);
        }
        return fromRows(out);
    }

    public void forEachSpan(SpanVisitor v) {
        for (Map.Entry<Integer, int[]> e : rows.entrySet()) {
            int[] r = e.getValue();
            for (int i = 0; i < r.length; i += 2) v.span(e.getKey(), r[i], r[i + 1]);
        }
    }

    /** Spans of rows minZ..maxZ only (on-screen culling). */
    public void forEachSpan(int minZ, int maxZ, SpanVisitor v) {
        for (Map.Entry<Integer, int[]> e : rows.subMap(minZ, true, maxZ, true).entrySet()) {
            int[] r = e.getValue();
            for (int i = 0; i < r.length; i += 2) v.span(e.getKey(), r[i], r[i + 1]);
        }
    }

    /** Cell bounds {minX, minZ, maxX, maxZ} inclusive, or null if empty. */
    public int[] bounds() {
        if (rows.isEmpty()) return null;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        for (int[] r : rows.values()) {
            minX = Math.min(minX, r[0]);
            maxX = Math.max(maxX, r[r.length - 1]);
        }
        return new int[]{minX, rows.firstKey(), maxX, rows.lastKey()};
    }

    /** Every cell (tests / small sets only). */
    public java.util.Set<Long> toSet() {
        java.util.Set<Long> s = new java.util.HashSet<>();
        forEachSpan((z, x0, x1) -> {
            for (int x = x0; x <= x1; x++) s.add(key(x, z));
        });
        return s;
    }
}
