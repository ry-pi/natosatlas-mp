package dev.natowb.natosatlas.client.ui.screens.map.markup;

import dev.natowb.natosatlas.client.platform.NAPainter;
import dev.natowb.natosatlas.client.ui.screens.map.MapContext;
import dev.natowb.natosatlas.client.ui.screens.map.MapProjection;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * mp9: draws markup shapes over the world map. Shapes are in world blocks; every point is projected to the screen
 * (so zoom, pan and rotation all apply) and strokes are drawn as screen-space quads, so the width stays the same
 * on any GPU (glLineWidth > 1 isn't guaranteed).
 */
public final class MarkupRenderer {
    private MarkupRenderer() {
    }

    public static void drawShapes(MapContext ctx, List<MarkupShape> shapes, MarkupShape preview, NAPainter p) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_CULL_FACE); // quads are wound either way
        for (MarkupShape s : shapes) drawShape(ctx, s);
        if (preview != null) drawShape(ctx, preview);
        if (cull) GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);

        for (MarkupShape s : shapes) if (s.type == MarkupShape.Type.RECT) drawRectLabels(ctx, s, p);
        if (preview != null) drawPreviewLabels(ctx, preview, p);
    }

    private static List<double[]> project(MapContext ctx, List<double[]> world) {
        List<double[]> o = new ArrayList<>(world.size());
        for (double[] w : world) o.add(MapProjection.worldToScreen(ctx, w[0], w[1]));
        return o;
    }

    private static void color(int rgb, int alpha) {
        GL11.glColor4f((rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f, alpha / 255f);
    }

    private static void drawShape(MapContext ctx, MarkupShape s) {
        if (s.points.isEmpty()) return;
        List<double[]> pts = project(ctx, s.outline());
        if (s.filled && pts.size() >= 3) {
            color(s.color, MarkupShape.FILL_ALPHA);
            GL11.glBegin(GL11.GL_TRIANGLE_FAN); // rectangles and ellipses are convex
            for (double[] q : pts) GL11.glVertex2d(q[0], q[1]);
            GL11.glEnd();
        }
        // thin dark halo under the stroke so it reads on any terrain colour
        color(0x000000, 0x70);
        stroke(pts, s.isClosed(), s.width + 2);
        color(s.color, 255);
        stroke(pts, s.isClosed(), s.width);
    }

    static void stroke(List<double[]> pts, boolean closed, double width) {
        double h = width / 2.0;
        int n = pts.size();
        if (n == 1) {
            disc(pts.get(0), Math.max(h, 1));
            return;
        }
        int segs = closed ? n : n - 1;
        GL11.glBegin(GL11.GL_QUADS);
        for (int i = 0; i < segs; i++) {
            double[] a = pts.get(i), b = pts.get((i + 1) % n);
            double dx = b[0] - a[0], dy = b[1] - a[1];
            double len = Math.hypot(dx, dy);
            if (len < 1e-6) continue;
            double nx = -dy / len * h, ny = dx / len * h;
            GL11.glVertex2d(a[0] + nx, a[1] + ny);
            GL11.glVertex2d(b[0] + nx, b[1] + ny);
            GL11.glVertex2d(b[0] - nx, b[1] - ny);
            GL11.glVertex2d(a[0] - nx, a[1] - ny);
        }
        GL11.glEnd();
        if (width >= 2.5) for (double[] q : pts) disc(q, h); // round joins
    }

    private static void disc(double[] c, double r) {
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2d(c[0], c[1]);
        for (int i = 0; i <= 10; i++) {
            double a = i * Math.PI * 2 / 10;
            GL11.glVertex2d(c[0] + Math.cos(a) * r, c[1] + Math.sin(a) * r);
        }
        GL11.glEnd();
    }

    // ------------------------------------------------------------------ labels (screen space, always upright)

    static void label(NAPainter p, String text, double x, double y, int alignX, int alignY, int color) {
        int w = p.getStringWidth(text);
        int tx = (int) Math.round(alignX > 0 ? x + 3 : alignX < 0 ? x - 3 - w : x - w / 2.0);
        int ty = (int) Math.round(alignY > 0 ? y + 3 : alignY < 0 ? y - 11 : y - 4);
        p.drawRect(tx - 2, ty - 2, tx + w + 1, ty + 9, 0xB0000000);
        p.drawString(text, tx, ty, color, true);
    }

    private static int textColor(int rgb) {
        // keep labels readable: very dark colours get white text
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
        return (r * 3 + g * 6 + b) / 10 < 70 ? 0xFFFFFFFF : 0xFF000000 | rgb;
    }

    static void drawRectLabels(MapContext ctx, MarkupShape s, NAPainter p) {
        double[] box = s.box();
        int[][] corners = MarkupGeometry.cornerBlocks(box);
        int[] size = MarkupGeometry.sizeBlocks(box);
        double[][] world = {{box[0], box[1]}, {box[2], box[1]}, {box[0], box[3]}, {box[2], box[3]}};
        double[] c = MapProjection.worldToScreen(ctx, (box[0] + box[2]) / 2, (box[1] + box[3]) / 2);
        double[][] scr = new double[4][];
        for (int i = 0; i < 4; i++) scr[i] = MapProjection.worldToScreen(ctx, world[i][0], world[i][1]);
        double screenW = Math.hypot(scr[1][0] - scr[0][0], scr[1][1] - scr[0][1]);
        double screenH = Math.hypot(scr[2][0] - scr[0][0], scr[2][1] - scr[0][1]);
        boolean all = screenW >= 70 && screenH >= 24; // tiny rectangles: only two opposite corners
        int col = textColor(s.color);
        for (int i = 0; i < 4; i++) {
            if (!all && (i == 1 || i == 2)) continue;
            int ax = scr[i][0] >= c[0] ? 1 : -1;
            int ay = scr[i][1] >= c[1] ? 1 : -1;
            label(p, corners[i][0] + ", " + corners[i][1], scr[i][0], scr[i][1], ax, ay, col);
        }
        label(p, size[0] + " x " + size[1], c[0], c[1], 0, 0, 0xFFFFFFFF);
    }

    static void drawPreviewLabels(MapContext ctx, MarkupShape s, NAPainter p) {
        switch (s.type) {
            case RECT:
                drawRectLabels(ctx, s, p);
                break;
            case ELLIPSE: {
                double[] box = s.box();
                int[] size = MarkupGeometry.sizeBlocks(box);
                double[] c = MapProjection.worldToScreen(ctx, (box[0] + box[2]) / 2, (box[1] + box[3]) / 2);
                label(p, size[0] + " x " + size[1], c[0], c[1], 0, 0, 0xFFFFFFFF);
                break;
            }
            case LINE: {
                if (s.points.size() < 2) break;
                double[] a = s.points.get(0), b = s.points.get(1);
                double[] sa = MapProjection.worldToScreen(ctx, a[0], a[1]);
                double[] sb = MapProjection.worldToScreen(ctx, b[0], b[1]);
                label(p, (int) Math.floor(a[0]) + ", " + (int) Math.floor(a[1]), sa[0], sa[1], sa[0] <= sb[0] ? -1 : 1, sa[1] <= sb[1] ? -1 : 1, 0xFFFFFFFF);
                label(p, (int) Math.floor(b[0]) + ", " + (int) Math.floor(b[1]), sb[0], sb[1], sb[0] > sa[0] ? 1 : -1, sb[1] > sa[1] ? 1 : -1, 0xFFFFFFFF);
                label(p, String.format("%.1f blocks", MarkupGeometry.length(a, b)), (sa[0] + sb[0]) / 2, (sa[1] + sb[1]) / 2, 0, 0, 0xFFFFFFFF);
                break;
            }
            default:
                break;
        }
    }
}
