package dev.natowb.natosatlas.client.ui.screens.map.markup;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * mp9 (mp11: block cells, readable labels): renders a markup PNG: the stored map tiles of an area (north up) with the markup shapes and the rectangle
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
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            for (MarkupShape s : shapes) drawShape(g, a, s);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
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

    /** mp11: the shape's block cells, each run of a row one rectangle covering exactly those blocks. */
    private static void drawShape(final Graphics2D g, final MarkupGeometry.ExportArea a, MarkupShape s) {
        if (s.points.isEmpty()) return;
        g.setColor(new Color(s.color | (MarkupShape.CELL_ALPHA << 24), true));
        s.cells().forEachSpan(a.minZ - 1, a.minZ + a.blocksZ + 1, (z, x0, x1) -> {
            // snap to whole pixels so neighbouring cells meet without seams or overlap
            int px0 = (int) Math.round(ix(a, x0)), px1 = (int) Math.round(ix(a, x1 + 1));
            int py0 = (int) Math.round(iy(a, z)), py1 = (int) Math.round(iy(a, z + 1));
            if (px1 > px0 && py1 > py0) g.fillRect(px0, py0, px1 - px0, py1 - py0);
        });
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
        Color text = new Color(MarkupRenderer.labelColor(s.color), true);
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
        int pad = Math.max(3, h / 4);
        double tx = alignX > 0 ? x + pad * 2 : alignX < 0 ? x - pad * 2 - w : x - w / 2.0;
        double ty = alignY > 0 ? y + pad * 2 : alignY < 0 ? y - pad * 2 - h : y - h / 2.0;
        // keep the whole label inside the picture
        tx = Math.max(pad, Math.min(a.width - w - pad, tx));
        ty = Math.max(pad, Math.min(a.height - h - pad, ty));
        g.setColor(new Color(MarkupRenderer.LABEL_BG, true));
        g.fill(new Rectangle2D.Double(tx - pad, ty - pad, w + pad * 2, h + pad * 2));
        int sh = Math.max(1, h / 12);
        g.setColor(new Color((color.getRGB() & 0xFCFCFC) >> 2 | 0xFF000000, true)); // text shadow, like the game font
        g.drawString(text, (float) tx + sh, (float) (ty + fm.getAscent()) + sh);
        g.setColor(color);
        g.drawString(text, (float) tx, (float) (ty + fm.getAscent()));
    }
}
