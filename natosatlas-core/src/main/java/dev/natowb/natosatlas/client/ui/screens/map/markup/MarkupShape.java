package dev.natowb.natosatlas.client.ui.screens.map.markup;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * One markup shape in WORLD block coordinates (block b covers [b, b+1)), so it stays put when the map is panned,
 * zoomed or rotated. RECT / ELLIPSE: points[0] = (x0, z0), points[1] = (x1, z1), the block-aligned edges of the box
 * (x1/z1 exclusive). LINE: two block centres. PENCIL: the freehand points.
 * <p>
 * mp11: a shape colours whole map blocks. {@link #cells()} is the shape's set of unique block cells (rasterised by
 * {@link MarkupRaster}, minus anything the eraser removed), cached until the points change.
 */
public final class MarkupShape {
    public enum Type {RECT, ELLIPSE, LINE, PENCIL}

    public final Type type;
    public final int dim;
    public final int color;      // 0xRRGGBB
    public final int width;      // mp11: thickness in blocks (1, 2, 3, 5)
    public final boolean filled;
    public final List<double[]> points = new ArrayList<>();

    /** Alpha of a coloured block cell (~0.66): the terrain still shows through. */
    public static final int CELL_ALPHA = 0xA8;

    private final Set<Long> erased = new HashSet<>();
    private int version = 0, cachedVersion = -1;
    private BlockCells cache;

    public MarkupShape(Type type, int dim, int color, int width, boolean filled) {
        this.type = type;
        this.dim = dim;
        this.color = color & 0xFFFFFF;
        this.width = Math.max(1, width);
        this.filled = filled && (type == Type.RECT || type == Type.ELLIPSE);
    }

    public MarkupShape add(double x, double z) {
        points.add(new double[]{x, z});
        version++;
        return this;
    }

    public void set(int i, double x, double z) {
        while (points.size() <= i) points.add(new double[2]);
        points.get(i)[0] = x;
        points.get(i)[1] = z;
        version++;
    }

    /** RECT / ELLIPSE box edges {x0, z0, x1, z1} (normalised). */
    public double[] box() {
        double[] a = points.get(0), b = points.get(1);
        return new double[]{Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.max(a[0], b[0]), Math.max(a[1], b[1])};
    }

    /** Inclusive cell box {x0, z0, x1, z1} of a RECT / ELLIPSE. */
    public int[] cellBox() {
        double[] b = box();
        return new int[]{(int) Math.floor(b[0]), (int) Math.floor(b[1]), (int) Math.round(b[2]) - 1, (int) Math.round(b[3]) - 1};
    }

    public boolean isClosed() {
        return type == Type.RECT || type == Type.ELLIPSE;
    }

    /** The unique block cells this shape colours (cached). */
    public BlockCells cells() {
        if (cachedVersion != version || cache == null) {
            cache = rasterise().minus(erased);
            cachedVersion = version;
        }
        return cache;
    }

    private BlockCells rasterise() {
        if (points.isEmpty()) return BlockCells.fromSet(new HashSet<>());
        switch (type) {
            case RECT: {
                if (points.size() < 2) break;
                int[] c = cellBox();
                return MarkupRaster.rect(c[0], c[1], c[2], c[3], width, filled);
            }
            case ELLIPSE: {
                if (points.size() < 2) break;
                int[] c = cellBox();
                return MarkupRaster.ellipse(c[0], c[1], c[2], c[3], width, filled);
            }
            case LINE: {
                double[] a = points.get(0), b = points.get(points.size() - 1);
                Set<Long> s = new HashSet<>();
                MarkupRaster.line((int) Math.floor(a[0]), (int) Math.floor(a[1]), (int) Math.floor(b[0]), (int) Math.floor(b[1]), width, s);
                return BlockCells.fromSet(s);
            }
            default:
                return MarkupRaster.pencil(points, width);
        }
        return BlockCells.fromSet(new HashSet<>());
    }

    /** Eraser: removes these cells from the shape; returns the ones it actually had. */
    Set<Long> erase(Set<Long> cells) {
        BlockCells have = cells();
        Set<Long> hit = new HashSet<>();
        for (long k : cells) if (have.contains(BlockCells.keyX(k), BlockCells.keyZ(k))) hit.add(k);
        if (!hit.isEmpty()) {
            erased.addAll(hit);
            version++;
        }
        return hit;
    }

    void unerase(Set<Long> cells) {
        erased.removeAll(cells);
        version++;
    }

    /** World bounds {minX, minZ, maxX, maxZ} of the coloured cells (block edges). */
    public double[] bounds() {
        int[] b = cells().bounds();
        if (b == null) {
            double minX = Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
            for (double[] p : points) {
                minX = Math.min(minX, p[0]);
                maxX = Math.max(maxX, p[0]);
                minZ = Math.min(minZ, p[1]);
                maxZ = Math.max(maxZ, p[1]);
            }
            return new double[]{minX, minZ, maxX, maxZ};
        }
        return new double[]{b[0], b[1], b[2] + 1, b[3] + 1};
    }
}
