package dev.natowb.natosatlas.client.ui.screens.map.markup;

import java.util.ArrayList;
import java.util.List;

/** mp9: pure markup math (snapping, corner labels, hit tests, export area). No GL; unit tested headless. */
public final class MarkupGeometry {
    private MarkupGeometry() {
    }

    /** Maps a world block point to screen pixels (the map viewport, or identity in tests). */
    public interface Projector {
        double[] toScreen(double blockX, double blockZ);
    }

    /**
     * Rectangle / ellipse box from a drag between two world points, snapped to whole blocks and including both
     * end blocks: {x0, z0, x1, z1} with x1/z1 exclusive edges. square = Shift: the larger side, growing away from
     * the start block.
     */
    public static double[] boxFromDrag(double ax, double az, double bx, double bz, boolean square) {
        int sx = (int) Math.floor(ax), sz = (int) Math.floor(az);
        int ex = (int) Math.floor(bx), ez = (int) Math.floor(bz);
        if (square) {
            int w = Math.abs(ex - sx), h = Math.abs(ez - sz);
            int n = Math.max(w, h);
            ex = sx + (ex >= sx ? n : -n);
            ez = sz + (ez >= sz ? n : -n);
        }
        return new double[]{Math.min(sx, ex), Math.min(sz, ez), Math.max(sx, ex) + 1, Math.max(sz, ez) + 1};
    }

    /** Block coordinates of the 4 corner blocks of a box: NW, NE, SW, SE ({x, z} each). */
    public static int[][] cornerBlocks(double[] box) {
        int x0 = (int) Math.floor(box[0]), z0 = (int) Math.floor(box[1]);
        int x1 = (int) Math.round(box[2]) - 1, z1 = (int) Math.round(box[3]) - 1;
        return new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}};
    }

    /** Size in blocks {width (X), height (Z)} of a box. */
    public static int[] sizeBlocks(double[] box) {
        return new int[]{(int) Math.round(box[2] - box[0]), (int) Math.round(box[3] - box[1])};
    }

    /** Line end snapped to the block centre; constrain = Shift: to the nearest multiple of 45 degrees. */
    public static double[] lineEnd(double ax, double az, double bx, double bz, boolean constrain) {
        double sx = Math.floor(ax) + 0.5, sz = Math.floor(az) + 0.5;
        double ex = Math.floor(bx) + 0.5, ez = Math.floor(bz) + 0.5;
        if (constrain) {
            double dx = ex - sx, dz = ez - sz;
            double ang = Math.atan2(dz, dx);
            double snap = Math.round(ang / (Math.PI / 4)) * (Math.PI / 4);
            double len = Math.hypot(dx, dz);
            if (Math.abs(Math.cos(snap)) < 1e-9 || Math.abs(Math.sin(snap)) < 1e-9) {
                ex = sx + Math.round(Math.cos(snap) * len);
                ez = sz + Math.round(Math.sin(snap) * len);
            } else {
                double n = Math.round(len / Math.sqrt(2));
                ex = sx + Math.signum(Math.cos(snap)) * n;
                ez = sz + Math.signum(Math.sin(snap)) * n;
            }
        }
        return new double[]{ex, ez};
    }

    /** Line start snapped to the block centre. */
    public static double[] lineStart(double ax, double az) {
        return new double[]{Math.floor(ax) + 0.5, Math.floor(az) + 0.5};
    }

    /** Length in blocks between two points. */
    public static double length(double[] a, double[] b) {
        return Math.hypot(b[0] - a[0], b[1] - a[1]);
    }

    /** Ellipse inscribed in a box, as a polygon of n points (world space, so it follows map rotation). */
    public static List<double[]> ellipse(double x0, double z0, double x1, double z1, int n) {
        List<double[]> o = new ArrayList<>(n);
        double cx = (x0 + x1) / 2, cz = (z0 + z1) / 2, rx = (x1 - x0) / 2, rz = (z1 - z0) / 2;
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            o.add(new double[]{cx + Math.cos(a) * rx, cz + Math.sin(a) * rz});
        }
        return o;
    }

    public static double distToSegment(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax, dy = by - ay;
        double l2 = dx * dx + dy * dy;
        double t = l2 == 0 ? 0 : ((px - ax) * dx + (py - ay) * dy) / l2;
        t = Math.max(0, Math.min(1, t));
        return Math.hypot(px - (ax + t * dx), py - (ay + t * dy));
    }

    public static boolean insidePolygon(double px, double py, List<double[]> poly) {
        boolean in = false;
        for (int i = 0, j = poly.size() - 1; i < poly.size(); j = i++) {
            double[] a = poly.get(i), b = poly.get(j);
            if ((a[1] > py) != (b[1] > py) && px < (b[0] - a[0]) * (py - a[1]) / (b[1] - a[1]) + a[0]) in = !in;
        }
        return in;
    }

    /** Eraser hit test in screen space: on the stroke (within tolerance) or inside a filled shape. */
    public static boolean hits(MarkupShape s, Projector proj, double mx, double my, double tolerance) {
        List<double[]> world = s.outline();
        if (world.isEmpty()) return false;
        List<double[]> pts = new ArrayList<>(world.size());
        for (double[] w : world) pts.add(proj.toScreen(w[0], w[1]));
        double tol = tolerance + s.width / 2.0;
        if (pts.size() == 1) return Math.hypot(mx - pts.get(0)[0], my - pts.get(0)[1]) <= tol;
        int n = pts.size();
        int segs = s.isClosed() ? n : n - 1;
        for (int i = 0; i < segs; i++) {
            double[] a = pts.get(i), b = pts.get((i + 1) % n);
            if (distToSegment(mx, my, a[0], a[1], b[0], b[1]) <= tol) return true;
        }
        return s.filled && insidePolygon(mx, my, pts);
    }

    /** Union bounds {minX, minZ, maxX, maxZ} of shapes, or null if there are none. */
    public static double[] bounds(List<MarkupShape> shapes) {
        double[] b = null;
        for (MarkupShape s : shapes) {
            if (s.points.isEmpty()) continue;
            double[] r = s.bounds();
            if (b == null) b = r;
            else b = new double[]{Math.min(b[0], r[0]), Math.min(b[1], r[1]), Math.max(b[2], r[2]), Math.max(b[3], r[3])};
        }
        return b;
    }

    /** What a saved markup image covers. */
    public static final class ExportArea {
        public final int minX, minZ, blocksX, blocksZ; // world blocks
        public final double ppb;                       // image pixels per block
        public final int width, height;                // image pixels

        ExportArea(int minX, int minZ, int blocksX, int blocksZ, double ppb) {
            this.minX = minX;
            this.minZ = minZ;
            this.blocksX = blocksX;
            this.blocksZ = blocksZ;
            this.ppb = ppb;
            this.width = Math.max(1, (int) Math.ceil(blocksX * ppb));
            this.height = Math.max(1, (int) Math.ceil(blocksZ * ppb));
        }
    }

    public static final int EXPORT_MAX_SIDE = 4096;
    public static final int EXPORT_MAX_PPB = 8;
    public static final int EXPORT_MIN_BLOCKS = 32;

    /**
     * Image area for a markup save: the shapes' bounds plus a margin (or the given visible bounds when there are no
     * shapes), at up to 8 px per block (whole pixels per block when >= 1), max 4096 px per side.
     */
    public static ExportArea exportArea(double[] shapeBounds, double[] visibleBounds) {
        double[] b = shapeBounds != null ? shapeBounds : visibleBounds;
        double minX = b[0], minZ = b[1], maxX = b[2], maxZ = b[3];
        if (shapeBounds != null) {
            double side = Math.max(maxX - minX, maxZ - minZ);
            double margin = Math.max(8, side * 0.08);
            minX -= margin;
            minZ -= margin;
            maxX += margin;
            maxZ += margin;
        }
        int x0 = (int) Math.floor(minX), z0 = (int) Math.floor(minZ);
        int x1 = (int) Math.ceil(maxX), z1 = (int) Math.ceil(maxZ);
        int w = x1 - x0, h = z1 - z0;
        if (w < EXPORT_MIN_BLOCKS) {
            x0 -= (EXPORT_MIN_BLOCKS - w) / 2;
            w = EXPORT_MIN_BLOCKS;
        }
        if (h < EXPORT_MIN_BLOCKS) {
            z0 -= (EXPORT_MIN_BLOCKS - h) / 2;
            h = EXPORT_MIN_BLOCKS;
        }
        double ppb = Math.min(EXPORT_MAX_PPB, EXPORT_MAX_SIDE / (double) Math.max(w, h));
        if (ppb >= 1) ppb = Math.floor(ppb);
        return new ExportArea(x0, z0, w, h, ppb);
    }
}
