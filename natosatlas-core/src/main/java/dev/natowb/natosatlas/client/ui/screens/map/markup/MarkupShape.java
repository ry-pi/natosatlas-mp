package dev.natowb.natosatlas.client.ui.screens.map.markup;

import java.util.ArrayList;
import java.util.List;

/**
 * mp9: one markup shape, stored in WORLD block coordinates (double; block b covers [b, b+1)), so it stays put
 * when the map is panned, zoomed or rotated. RECT / ELLIPSE: points[0] = (x0, z0), points[1] = (x1, z1), the
 * block-aligned edges of the box (x1/z1 exclusive). LINE: two block centres. PENCIL: the freehand points.
 */
public final class MarkupShape {
    public enum Type {RECT, ELLIPSE, LINE, PENCIL}

    public final Type type;
    public final int dim;
    public final int color;      // 0xRRGGBB (drawn opaque; fills use FILL_ALPHA)
    public final int width;      // stroke width in screen GUI pixels
    public final boolean filled;
    public final List<double[]> points = new ArrayList<>();

    public static final int FILL_ALPHA = 0x60;

    public MarkupShape(Type type, int dim, int color, int width, boolean filled) {
        this.type = type;
        this.dim = dim;
        this.color = color & 0xFFFFFF;
        this.width = Math.max(1, width);
        this.filled = filled && (type == Type.RECT || type == Type.ELLIPSE);
    }

    public MarkupShape add(double x, double z) {
        points.add(new double[]{x, z});
        return this;
    }

    public void set(int i, double x, double z) {
        while (points.size() <= i) points.add(new double[2]);
        points.get(i)[0] = x;
        points.get(i)[1] = z;
    }

    /** RECT / ELLIPSE box edges {x0, z0, x1, z1} (normalised). */
    public double[] box() {
        double[] a = points.get(0), b = points.get(1);
        return new double[]{Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.max(a[0], b[0]), Math.max(a[1], b[1])};
    }

    /** World outline as a polyline (closed shapes repeat no point; see {@link #isClosed()}). */
    public List<double[]> outline() {
        switch (type) {
            case RECT: {
                double[] r = box();
                List<double[]> o = new ArrayList<>();
                o.add(new double[]{r[0], r[1]});
                o.add(new double[]{r[2], r[1]});
                o.add(new double[]{r[2], r[3]});
                o.add(new double[]{r[0], r[3]});
                return o;
            }
            case ELLIPSE: {
                double[] r = box();
                return MarkupGeometry.ellipse(r[0], r[1], r[2], r[3], 72);
            }
            default:
                return points;
        }
    }

    public boolean isClosed() {
        return type == Type.RECT || type == Type.ELLIPSE;
    }

    /** World bounds {minX, minZ, maxX, maxZ}. */
    public double[] bounds() {
        double minX = Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (double[] p : points) {
            minX = Math.min(minX, p[0]);
            maxX = Math.max(maxX, p[0]);
            minZ = Math.min(minZ, p[1]);
            maxZ = Math.max(maxZ, p[1]);
        }
        return new double[]{minX, minZ, maxX, maxZ};
    }
}
