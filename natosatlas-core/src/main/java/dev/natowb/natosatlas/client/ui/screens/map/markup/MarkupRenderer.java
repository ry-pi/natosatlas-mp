package dev.natowb.natosatlas.client.ui.screens.map.markup;

import dev.natowb.natosatlas.client.platform.NAPainter;
import dev.natowb.natosatlas.client.ui.screens.map.MapContext;
import dev.natowb.natosatlas.client.ui.screens.map.MapProjection;
import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * Draws markup over the world map. mp11: a shape colours whole blocks: each run of cells in a row is one quad whose
 * four corners are block corners projected through the map transform, so it covers exactly those blocks at any
 * zoom, pan and rotation. Cells are unique per shape (no double-painted alpha) and only on-screen rows are drawn.
 */
public final class MarkupRenderer {
    private MarkupRenderer() {
    }

    public static void drawShapes(MapContext ctx, List<MarkupShape> shapes, MarkupShape preview, NAPainter p) {
        double[] vis = MapProjection.visibleWorldBounds(ctx); // {minX, minZ, maxX, maxZ}
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_CULL_FACE); // quads are wound either way (rotation / mirroring)
        for (MarkupShape s : shapes) drawCells(ctx, s, vis);
        if (preview != null) drawCells(ctx, preview, vis);
        if (cull) GL11.glEnable(GL11.GL_CULL_FACE);
        resetGl();

        for (MarkupShape s : shapes) if (s.type == MarkupShape.Type.RECT) drawRectLabels(ctx, s, p);
        if (preview != null) drawPreviewLabels(ctx, preview, p);
        resetGl();
    }

    private static void resetGl() {
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
    }

    private static void drawCells(final MapContext ctx, MarkupShape s, double[] vis) {
        if (s.points.isEmpty()) return;
        BlockCells cells = s.cells();
        if (cells.isEmpty()) return;
        final int minX = (int) Math.floor(vis[0]) - 1, maxX = (int) Math.ceil(vis[2]) + 1;
        int rgb = s.color;
        GL11.glColor4f((rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f, MarkupShape.CELL_ALPHA / 255f);
        GL11.glBegin(GL11.GL_QUADS);
        cells.forEachSpan((int) Math.floor(vis[1]) - 1, (int) Math.ceil(vis[3]) + 1, (z, x0, x1) -> {
            if (x1 < minX || x0 > maxX) return;
            int a = Math.max(x0, minX), b = Math.min(x1, maxX) + 1; // block edges [a, b) x [z, z+1)
            double[] p0 = MapProjection.worldToScreen(ctx, a, z);
            double[] p1 = MapProjection.worldToScreen(ctx, b, z);
            double[] p2 = MapProjection.worldToScreen(ctx, b, z + 1);
            double[] p3 = MapProjection.worldToScreen(ctx, a, z + 1);
            GL11.glVertex2d(p0[0], p0[1]);
            GL11.glVertex2d(p1[0], p1[1]);
            GL11.glVertex2d(p2[0], p2[1]);
            GL11.glVertex2d(p3[0], p3[1]);
        });
        GL11.glEnd();
    }

    // ------------------------------------------------------------------ labels (screen space, always upright)

    /** Label box colour: near-opaque black. */
    public static final int LABEL_BG = 0xE0000000;
    public static final int LABEL_PAD = 3;

    /**
     * A readable label: bright text with a shadow on a near-opaque dark box. alignX/Y: -1 = left/above the point,
     * 1 = right/below, 0 = centred. Text colours are always 0xFFRRGGBB (alpha 0 would mean opaque to the b1.7.3
     * font renderer), and the GL colour / texture / blend state is reset before the text.
     */
    static void label(NAPainter p, String text, double x, double y, int alignX, int alignY, int color) {
        int w = p.getStringWidth(text);
        int pad = LABEL_PAD, gap = 2;
        int tx = (int) Math.round(alignX > 0 ? x + gap + pad : alignX < 0 ? x - gap - pad - w : x - w / 2.0);
        int ty = (int) Math.round(alignY > 0 ? y + gap + pad : alignY < 0 ? y - gap - pad - 8 : y - 4);
        p.drawRect(tx - pad, ty - pad, tx + w + pad, ty + 8 + pad, LABEL_BG);
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        p.drawStringWithShadow(text, tx, ty, 0xFF000000 | color);
        GL11.glDisable(GL11.GL_BLEND);
    }

    /** The shape colour lightened towards white (always opaque), so it reads on the dark label box. */
    public static int labelColor(int rgb) {
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
        r = r + (255 - r) * 55 / 100;
        g = g + (255 - g) * 55 / 100;
        b = b + (255 - b) * 55 / 100;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    static void drawRectLabels(MapContext ctx, MarkupShape s, NAPainter p) {
        if (s.points.size() < 2) return;
        double[] box = s.box(); // outer edges of the corner cells
        int[][] corners = MarkupGeometry.cornerBlocks(box);
        int[] size = MarkupGeometry.sizeBlocks(box);
        double[][] world = {{box[0], box[1]}, {box[2], box[1]}, {box[0], box[3]}, {box[2], box[3]}};
        double[] c = MapProjection.worldToScreen(ctx, (box[0] + box[2]) / 2, (box[1] + box[3]) / 2);
        double[][] scr = new double[4][];
        for (int i = 0; i < 4; i++) scr[i] = MapProjection.worldToScreen(ctx, world[i][0], world[i][1]);
        double screenW = Math.hypot(scr[1][0] - scr[0][0], scr[1][1] - scr[0][1]);
        double screenH = Math.hypot(scr[2][0] - scr[0][0], scr[2][1] - scr[0][1]);
        boolean all = screenW >= 70 && screenH >= 24; // tiny rectangles: only two opposite corners
        int col = labelColor(s.color);
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
                if (s.points.size() < 2) break;
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
                int col = labelColor(s.color);
                label(p, (int) Math.floor(a[0]) + ", " + (int) Math.floor(a[1]), sa[0], sa[1], sa[0] <= sb[0] ? -1 : 1, sa[1] <= sb[1] ? -1 : 1, col);
                label(p, (int) Math.floor(b[0]) + ", " + (int) Math.floor(b[1]), sb[0], sb[1], sb[0] > sa[0] ? 1 : -1, sb[1] > sa[1] ? 1 : -1, col);
                label(p, s.cells().count() + " blocks", (sa[0] + sb[0]) / 2, (sa[1] + sb[1]) / 2, 0, 0, 0xFFFFFFFF);
                break;
            }
            default:
                break;
        }
    }
}
