package dev.natowb.natosatlas.client.ui.screens.map.markup;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * mp9: renders a markup PNG: the stored map tiles of an area (north up) with the markup shapes and the rectangle
 * corner coordinates on top. Pure Java2D (no GL / Minecraft), so it runs on a worker thread and in headless tests.
 */
public final class MarkupImage {
    private MarkupImage() {
    }

    /** Supplies stored tiles: 512x512 ARGB (1 px per block) of region (rx, rz), or null if none. */
    public interface TileReader {
        int[] read(int rx, int rz);
    }

    public static final int REGION = 512;
    public static final int BACKGROUND = 0xFF181818; // the world map's panel colour, where nothing is mapped

    public static BufferedImage render(MarkupGeometry.ExportArea a, TileReader tiles, List<MarkupShape> shapes, String caption) {
        BufferedImage img = new BufferedImage(a.width, a.height, BufferedImage.TYPE_INT_ARGB);
        int[] row = new int[a.width];
        Map<Long, int[]> band = new HashMap<>();
        int bandRz = Integer.MIN_VALUE;
        for (int py = 0; py < a.height; py++) {
            int bz = a.minZ + (int) Math.floor((py + 0.5) / a.ppb);
            int rz = Math.floorDiv(bz, REGION);
            if (rz != bandRz) { // keep only one row of regions in memory
                band.clear();
                bandRz = rz;
            }
            int lz = Math.floorMod(bz, REGION);
            for (int px = 0; px < a.width; px++) {
                int bx = a.minX + (int) Math.floor((px + 0.5) / a.ppb);
                int rx = Math.floorDiv(bx, REGION);
                long k = rx;
                int[] t;
                if (band.containsKey(k)) t = band.get(k);
                else {
                    t = tiles.read(rx, rz);
                    band.put(k, t);
                }
                int c = t == null ? 0 : t[lz * REGION + Math.floorMod(bx, REGION)];
                row[px] = (c >>> 24) == 0 ? BACKGROUND : (0xFF000000 | c);
            }
            img.setRGB(0, py, a.width, 1, row, 0, a.width);
        }

        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            double scale = Math.max(1, a.ppb / 2);
            for (MarkupShape s : shapes) drawShape(g, a, s, scale);
            int fontPx = (int) Math.max(11, Math.min(26, Math.round(7 + 2 * a.ppb)));
            Font font = new Font(Font.SANS_SERIF, Font.BOLD, fontPx);
            g.setFont(font);
            for (MarkupShape s : shapes) if (s.type == MarkupShape.Type.RECT) drawRectLabels(g, a, s);
            if (caption != null && !caption.isEmpty()) {
                g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, Math.max(11, fontPx - 2)));
                FontMetrics fm = g.getFontMetrics();
                label(g, a, caption, 4, a.height - 4 - fm.getHeight(), 1, 1, Color.WHITE);
            }
        } finally {
            g.dispose();
        }
        return img;
    }

    private static double ix(MarkupGeometry.ExportArea a, double bx) {
        return (bx - a.minX) * a.ppb;
    }

    private static double iy(MarkupGeometry.ExportArea a, double bz) {
        return (bz - a.minZ) * a.ppb;
    }

    static Shape shape(MarkupGeometry.ExportArea a, MarkupShape s) {
        switch (s.type) {
            case RECT: {
                double[] b = s.box();
                return new Rectangle2D.Double(ix(a, b[0]), iy(a, b[1]), (b[2] - b[0]) * a.ppb, (b[3] - b[1]) * a.ppb);
            }
            case ELLIPSE: {
                double[] b = s.box();
                return new Ellipse2D.Double(ix(a, b[0]), iy(a, b[1]), (b[2] - b[0]) * a.ppb, (b[3] - b[1]) * a.ppb);
            }
            default: {
                Path2D.Double p = new Path2D.Double();
                boolean first = true;
                for (double[] q : s.points) {
                    if (first) p.moveTo(ix(a, q[0]), iy(a, q[1]));
                    else p.lineTo(ix(a, q[0]), iy(a, q[1]));
                    first = false;
                }
                if (s.points.size() == 1) p.lineTo(ix(a, s.points.get(0)[0]) + 0.01, iy(a, s.points.get(0)[1]));
                return p;
            }
        }
    }

    private static void drawShape(Graphics2D g, MarkupGeometry.ExportArea a, MarkupShape s, double scale) {
        Shape sh = shape(a, s);
        if (s.filled) {
            g.setColor(new Color(s.color | (MarkupShape.FILL_ALPHA << 24), true));
            g.fill(sh);
        }
        float w = (float) Math.max(1, s.width * scale);
        g.setStroke(new BasicStroke(w + 2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0x70000000, true));
        g.draw(sh);
        g.setStroke(new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0xFF000000 | s.color, true));
        g.draw(sh);
    }

    private static void drawRectLabels(Graphics2D g, MarkupGeometry.ExportArea a, MarkupShape s) {
        double[] b = s.box();
        int[][] corners = MarkupGeometry.cornerBlocks(b);
        int[] size = MarkupGeometry.sizeBlocks(b);
        double x0 = ix(a, b[0]), z0 = iy(a, b[1]), x1 = ix(a, b[2]), z1 = iy(a, b[3]);
        double[][] pos = {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}};
        int[][] align = {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}};
        FontMetrics fm = g.getFontMetrics();
        boolean all = (x1 - x0) >= fm.stringWidth("-0000, -0000") * 1.2 || (z1 - z0) >= fm.getHeight() * 3;
        int r = s.color >> 16 & 255, gg = s.color >> 8 & 255, bl = s.color & 255;
        Color text = (r * 3 + gg * 6 + bl) / 10 < 70 ? Color.WHITE : new Color(0xFF000000 | s.color, true);
        for (int i = 0; i < 4; i++) {
            if (!all && (i == 1 || i == 2)) continue;
            label(g, a, corners[i][0] + ", " + corners[i][1], pos[i][0], pos[i][1], align[i][0], align[i][1], text);
        }
        label(g, a, size[0] + " x " + size[1], (x0 + x1) / 2, (z0 + z1) / 2, 0, 0, Color.WHITE);
    }

    /** Text with a dark box; align -1 = left/above the point, 1 = right/below, 0 = centred. */
    private static void label(Graphics2D g, MarkupGeometry.ExportArea a, String text, double x, double y, int alignX, int alignY, Color color) {
        FontMetrics fm = g.getFontMetrics();
        int w = fm.stringWidth(text), h = fm.getAscent() + fm.getDescent();
        int pad = Math.max(2, h / 6);
        double tx = alignX > 0 ? x + pad * 2 : alignX < 0 ? x - pad * 2 - w : x - w / 2.0;
        double ty = alignY > 0 ? y + pad * 2 : alignY < 0 ? y - pad * 2 - h : y - h / 2.0;
        // keep the whole label inside the picture
        tx = Math.max(pad, Math.min(a.width - w - pad, tx));
        ty = Math.max(pad, Math.min(a.height - h - pad, ty));
        g.setColor(new Color(0xB0000000, true));
        g.fill(new Rectangle2D.Double(tx - pad, ty - pad, w + pad * 2, h + pad * 2));
        g.setColor(color);
        g.drawString(text, (float) tx, (float) (ty + fm.getAscent()));
    }
}
