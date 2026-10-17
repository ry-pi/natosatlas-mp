package dev.natowb.natosatlas.client.ui.screens.map.markup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * mp11: rasterises markup onto the world block grid. All coordinates are inclusive block cells.
 * Thickness t is in blocks (1, 2, 3, 5): rectangles and ellipses grow inwards, lines and the pencil use a t x t brush.
 */
public final class MarkupRaster {
    private MarkupRaster() {
    }

    /** Rectangle x0..x1, z0..z1 (inclusive): its t-block border, or every cell when filled. */
    public static BlockCells rect(int x0, int z0, int x1, int z1, int t, boolean filled) {
        Map<Integer, List<int[]>> rows = new HashMap<>();
        for (int z = z0; z <= z1; z++) {
            List<int[]> sp = new ArrayList<>(2);
            if (filled || z < z0 + t || z > z1 - t) sp.add(new int[]{x0, x1});
            else {
                sp.add(new int[]{x0, Math.min(x1, x0 + t - 1)});
                sp.add(new int[]{Math.max(x0, x1 - t + 1), x1});
            }
            rows.put(z, sp);
        }
        return BlockCells.fromRows(rows);
    }

    /**
     * Thin (8-connected, 1 cell) outline of the ellipse inscribed in the box, midpoint algorithm for a bounding
     * rectangle (A. Zingl, "A Rasterizing Algorithm for Drawing Curves"): exactly symmetric for odd and even sizes,
     * no gaps, no doubled cells.
     */
    public static Set<Long> ellipseOutline(int x0, int z0, int x1, int z1) {
        Set<Long> out = zingl(x0, z0, x1, z1);
        untangleTips(out, Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1));
        return out;
    }

    /**
     * Flat ellipses with an even short side end in a 2x2 clump at each tip (a doubled corner). Move the clump's inner
     * cells one step outwards along the curve: still connected, still symmetric, no doubled cells.
     */
    private static void untangleTips(Set<Long> o, int x0, int z0, int x1, int z1) {
        if (x1 - x0 < 4 || z1 - z0 < 4) return;
        double cx = (x0 + x1) / 2.0, cz = (z0 + z1) / 2.0;
        List<long[]> moves = new ArrayList<>();
        for (long k : o) {
            int x = BlockCells.keyX(k), z = BlockCells.keyZ(k);
            if (!(o.contains(BlockCells.key(x + 1, z)) && o.contains(BlockCells.key(x, z + 1)) && o.contains(BlockCells.key(x + 1, z + 1))))
                continue; // (x, z) is the top-left of a 2x2 clump
            boolean atX = x == x0 || x + 1 == x1, atZ = z == z0 || z + 1 == z1;
            if (atX && !atZ) {
                int inner = x == x0 ? x + 1 : x;
                for (int zz = z; zz <= z + 1; zz++)
                    moves.add(new long[]{BlockCells.key(inner, zz), BlockCells.key(inner, zz < cz ? zz - 1 : zz + 1)});
            } else if (atZ && !atX) {
                int inner = z == z0 ? z + 1 : z;
                for (int xx = x; xx <= x + 1; xx++)
                    moves.add(new long[]{BlockCells.key(xx, inner), BlockCells.key(xx < cx ? xx - 1 : xx + 1, inner)});
            }
        }
        for (long[] m : moves) o.remove(m[0]);
        for (long[] m : moves) o.add(m[1]);
    }

    private static Set<Long> zingl(int x0, int z0, int x1, int z1) {
        Set<Long> out = new HashSet<>();
        long a = Math.abs(x1 - x0), b = Math.abs(z1 - z0), b1 = b & 1;
        double dx = 4 * (1.0 - a) * b * b, dy = 4 * (b1 + 1) * a * a;
        double err = dx + dy + b1 * a * a, e2;
        if (x0 > x1) {
            x0 = x1;
            x1 += (int) a;
        }
        if (z0 > z1) z0 = z1;
        z0 += (int) ((b + 1) / 2);
        z1 = (int) (z0 - b1);
        double aa = 8.0 * a * a, bb = 8.0 * b * b;
        do {
            out.add(BlockCells.key(x1, z0));
            out.add(BlockCells.key(x0, z0));
            out.add(BlockCells.key(x0, z1));
            out.add(BlockCells.key(x1, z1));
            e2 = 2 * err;
            if (e2 <= dy) {
                z0++;
                z1--;
                err += dy += aa;
            }
            if (e2 >= dx || 2 * err > dy) {
                x0++;
                x1--;
                err += dx += bb;
            }
        } while (x0 <= x1);
        while (z0 - z1 <= b) { // flat ellipses: finish the tips
            out.add(BlockCells.key(x0 - 1, z0));
            out.add(BlockCells.key(x1 + 1, z0++));
            out.add(BlockCells.key(x0 - 1, z1));
            out.add(BlockCells.key(x1 + 1, z1--));
        }
        return out;
    }

    /** Row extents {z -> [minX, maxX]} of the ellipse in the box (its outline plus everything inside). */
    static Map<Integer, int[]> ellipseRows(int x0, int z0, int x1, int z1) {
        Map<Integer, int[]> rows = new HashMap<>();
        for (long k : ellipseOutline(x0, z0, x1, z1)) {
            int x = BlockCells.keyX(k), z = BlockCells.keyZ(k);
            int[] r = rows.get(z);
            if (r == null) rows.put(z, new int[]{x, x});
            else {
                r[0] = Math.min(r[0], x);
                r[1] = Math.max(r[1], x);
            }
        }
        return rows;
    }

    /** Ellipse in the box x0..x1, z0..z1: thin outline (t = 1), a t-block ring, or filled. */
    public static BlockCells ellipse(int x0, int z0, int x1, int z1, int t, boolean filled) {
        if (!filled && t <= 1) return BlockCells.fromSet(ellipseOutline(x0, z0, x1, z1));
        Map<Integer, int[]> outer = ellipseRows(x0, z0, x1, z1);
        boolean hasInner = !filled && x1 - x0 >= 2 * t && z1 - z0 >= 2 * t;
        Map<Integer, int[]> inner = hasInner ? ellipseRows(x0 + t, z0 + t, x1 - t, z1 - t) : new HashMap<>();
        Map<Integer, List<int[]>> rows = new HashMap<>();
        for (Map.Entry<Integer, int[]> e : outer.entrySet()) {
            int[] o = e.getValue();
            int[] in = inner.get(e.getKey());
            List<int[]> sp = new ArrayList<>(2);
            if (in == null) sp.add(o);
            else {
                sp.add(new int[]{o[0], in[0] - 1});
                sp.add(new int[]{in[1] + 1, o[1]});
            }
            rows.put(e.getKey(), sp);
        }
        BlockCells c = BlockCells.fromRows(rows);
        if (!filled) {
            // the thin outline is always part of the ring (the row extents alone could leave diagonal gaps)
            Set<Long> s = c.toSet();
            s.addAll(ellipseOutline(x0, z0, x1, z1));
            c = BlockCells.fromSet(s);
        }
        return c;
    }

    /** Bresenham line between two cells, every cell stamped with a t x t brush, into {@code out}. */
    public static void line(int ax, int az, int bx, int bz, int t, Set<Long> out) {
        int dx = Math.abs(bx - ax), dz = -Math.abs(bz - az);
        int sx = ax < bx ? 1 : -1, sz = az < bz ? 1 : -1;
        int err = dx + dz;
        int x = ax, z = az;
        while (true) {
            brush(x, z, t, out);
            if (x == bx && z == bz) break;
            int e2 = 2 * err;
            if (e2 >= dz) {
                err += dz;
                x += sx;
            }
            if (e2 <= dx) {
                err += dx;
                z += sz;
            }
        }
    }

    /** t x t square of cells centred on (x, z) (odd t exactly centred; even t leans to -X/-Z). */
    public static void brush(int x, int z, int t, Set<Long> out) {
        int o = (t - 1) / 2;
        for (int dz = 0; dz < t; dz++)
            for (int dx = 0; dx < t; dx++) out.add(BlockCells.key(x - o + dx, z - o + dz));
    }

    /** Freehand: the block under each sample, joined to the previous one with a Bresenham line. */
    public static BlockCells pencil(List<double[]> points, int t) {
        Set<Long> s = new HashSet<>();
        int px = 0, pz = 0;
        boolean first = true;
        for (double[] p : points) {
            int x = (int) Math.floor(p[0]), z = (int) Math.floor(p[1]);
            if (first) brush(x, z, t, s);
            else line(px, pz, x, z, t, s);
            px = x;
            pz = z;
            first = false;
        }
        return BlockCells.fromSet(s);
    }
}
