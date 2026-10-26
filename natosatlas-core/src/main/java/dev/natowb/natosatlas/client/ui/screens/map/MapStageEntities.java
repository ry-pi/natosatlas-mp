package dev.natowb.natosatlas.client.ui.screens.map;

import dev.natowb.natosatlas.client.NAClient;
import dev.natowb.natosatlas.client.platform.ClientWorldAccess;
import dev.natowb.natosatlas.client.ui.screens.settings.Settings;
import dev.natowb.natosatlas.core.data.NAEntity;
import dev.natowb.natosatlas.core.NAConstants;
import org.lwjgl.opengl.GL11;

import java.util.Set;

public class MapStageEntities implements MapStage {

    private static final double ENTITY_RENDER_RADIUS = 128.0;
    private static final int ENTITY_MAX_DEPTH = 32;

    private Settings.EntityDisplayMode displayMode = Settings.EntityDisplayMode.Player;

    public void setDisplayMode(Settings.EntityDisplayMode mode) {
        displayMode = mode;
    }

    @Override
    public void draw(MapContext ctx, Set<Long> visibleRegions, int activeLayer) {
        drawEntities(ctx);
    }

    private void drawEntities(MapContext ctx) {
        if (displayMode == Settings.EntityDisplayMode.Nothing) return;

        NAEntity localPlayer = ClientWorldAccess.get().getPlayer();
        double px = localPlayer.x;
        double py = localPlayer.y;
        double pz = localPlayer.z;

        if (displayMode == Settings.EntityDisplayMode.All) {
            for (NAEntity e : ClientWorldAccess.get().getEntities()) {

                double dx = e.x - px;
                double dy = e.y - py;
                double dz = e.z - pz;

                double distSq = dx * dx + dy * dy + dz * dz;

                if (distSq > ENTITY_RENDER_RADIUS * ENTITY_RENDER_RADIUS) {
                    continue;
                }

                int argb = getEntityHeightARGB(e, py);
                renderEntity(ctx, e, argb);
            }
        }

        for (NAEntity p : ClientWorldAccess.get().getPlayers()) {

            if (p.name.equals(localPlayer.name)) {
                continue;
            }

            // mp19: sneaking players stay on the map. Spectators are still hidden.
            if (p.isSpectating) continue;

            double dx = p.x - px;
            double dy = p.y - py;
            double dz = p.z - pz;

            double distSq = dx * dx + dy * dy + dz * dz;

            if (distSq > ENTITY_RENDER_RADIUS * ENTITY_RENDER_RADIUS) {
                continue;
            }


            renderMapMarker(ctx, p);
        }

        renderMapMarker(ctx, localPlayer);
    }

    private static int getEntityHeightARGB(NAEntity e, double py) {
        double entityHeightDiff = e.y - py;

        double brightness;
        if (entityHeightDiff >= 0) {
            brightness = 1.0;
        } else {
            double depth = -entityHeightDiff;


            brightness = 1.0 - (depth / ENTITY_MAX_DEPTH);
            if (brightness < 0) brightness = 0;
        }

        int shade = (int) (brightness * 255);
        return (0xFF << 24) | (shade << 16) | (shade << 8) | shade;
    }

    private void renderEntity(MapContext ctx, NAEntity e, int argb) {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D,
                NAClient.get().getPlatform().painter.getMinecraftTextureId(e.texturePath));

        double x = e.x * NAConstants.PIXELS_PER_CANVAS_UNIT;
        double z = e.z * NAConstants.PIXELS_PER_CANVAS_UNIT;
        double s = 4 / ctx.zoom;

        NAEntity.UV uv = NAEntity.getUV(e.texturePath);

        drawUpright(ctx, x, z, s, 180, () ->
                NAClient.get().getPlatform().painter.drawTexturedQuad(argb, uv.u1, uv.v1, uv.u2, uv.v2)
        );
    }

    private void renderMapMarker(MapContext ctx, NAEntity e) {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, NAClient.get().getPlatform().painter.getMinecraftTextureId("/misc/mapicons.png"));

        double x = e.x * NAConstants.PIXELS_PER_CANVAS_UNIT;
        double z = e.z * NAConstants.PIXELS_PER_CANVAS_UNIT;
        double s = 6 / ctx.zoom;

        int idx;
        switch (e.type) {
            case Player:
                idx = 0;
                break;
            case Animal:
                idx = 1;
                break;
            case Mob:
                idx = 2;
                break;
            case Waypoint:
                idx = 4;
                break;
            default:
                idx = 3;
                break;
        }

        float u1 = (idx % 4) / 4f;
        float v1 = (idx / 4) / 4f;
        float u2 = u1 + 0.25f;
        float v2 = v1 + 0.25f;

        if (e.type == NAEntity.NAEntityType.Player) {
            drawPlayerMarker(x, z, s, e.yaw, () ->
                    NAClient.get().getPlatform().painter.drawTexturedQuad(u1, v1, u2, v2)
            );
        } else {
            drawUpright(ctx, x, z, s, e.yaw, () ->
                    NAClient.get().getPlatform().painter.drawTexturedQuad(u1, v1, u2, v2)
            );
        }
    }


    /**
     * mp9: the player's position converted into another dimension (x8 / /8) while the world map shows that
     * dimension: a see-through copy of the player arrow.
     */
    public void drawGhostPlayer(MapContext ctx, double blockX, double blockZ, double yaw) {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, NAClient.get().getPlatform().painter.getMinecraftTextureId("/misc/mapicons.png"));
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        double x = blockX * NAConstants.PIXELS_PER_CANVAS_UNIT;
        double z = blockZ * NAConstants.PIXELS_PER_CANVAS_UNIT;
        double s = 6 / ctx.zoom;
        drawPlayerMarker(x, z, s, yaw, () ->
                NAClient.get().getPlatform().painter.drawTexturedQuad(0x90C0A0FF, 0f, 0f, 0.25f, 0.25f)
        );
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GL11.glDisable(GL11.GL_BLEND);
    }

    private void drawPlayerMarker(double worldX, double worldZ, double scale, double yaw, Runnable draw) {
        GL11.glPushMatrix();
        GL11.glTranslated(worldX, worldZ, 0);
        GL11.glRotated(yaw, 0, 0, 1);
        GL11.glScaled(scale, scale, 1);
        draw.run();
        GL11.glPopMatrix();
    }


    private void drawUpright(MapContext ctx, double worldX, double worldZ, double scale, double yaw, Runnable draw) {
        GL11.glPushMatrix();
        GL11.glTranslated(worldX, worldZ, 0);
        GL11.glRotated(-Math.toDegrees(ctx.rotation), 0, 0, 1);
        if (yaw != 0) GL11.glRotated(yaw, 0, 0, 1);
        GL11.glScaled(scale, scale, 1);
        draw.run();
        GL11.glPopMatrix();
    }
}
