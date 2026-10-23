package dev.natowb.natosatlas.client.ui.screens.map;

import dev.natowb.natosatlas.core.NAConstants;
import dev.natowb.natosatlas.core.io.McRegionReader.Spawner;
import org.lwjgl.opengl.GL11;

import java.util.List;

/** mp14: a small square per spawner (constant size on screen), coloured by mob, with a dark outline. */
public class MapStageSpawners {
    public void draw(MapContext ctx, List<Spawner> list, String filter, Spawner hovered) {
        if (list.isEmpty() || !hasAny(list, filter)) return;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        double u = NAConstants.PIXELS_PER_CANVAS_UNIT;
        for (Spawner s : list) {
            if (!MapSpawners.shows(filter, s.mob)) continue;
            double cx = (s.x + 0.5) * u, cz = (s.z + 0.5) * u;
            double r = (s == hovered ? 4.5 : 3.5) / ctx.zoom, o = r + 1.0 / ctx.zoom;
            quad(cx, cz, o, 0xFF000000);
            quad(cx, cz, r, MapSpawners.color(s.mob));
        }
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    private static boolean hasAny(List<Spawner> list, String f) {
        for (Spawner s : list) if (MapSpawners.shows(f, s.mob)) return true;
        return false;
    }

    private static void quad(double cx, double cz, double r, int argb) {
        GL11.glColor4f(((argb >> 16) & 255) / 255f, ((argb >> 8) & 255) / 255f, (argb & 255) / 255f, ((argb >>> 24) & 255) / 255f);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2d(cx - r, cz - r);
        GL11.glVertex2d(cx - r, cz + r);
        GL11.glVertex2d(cx + r, cz + r);
        GL11.glVertex2d(cx + r, cz - r);
        GL11.glEnd();
    }
}
