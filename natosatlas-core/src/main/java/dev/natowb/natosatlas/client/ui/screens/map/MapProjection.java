package dev.natowb.natosatlas.client.ui.screens.map;

import dev.natowb.natosatlas.core.NAConstants;

/**
 * mp9: screen (GUI pixels) <-> world block coordinates for the world map viewport, including zoom and
 * rotation. Same transform as {@link MapViewport#begin}: canvas + pivot + R(rot) * zoom * (canvas units - scroll - pivot).
 * Pure math (no GL), so it is unit tested headless.
 */
public final class MapProjection {
    private MapProjection() {
    }

    /** World block coordinates (double; block b covers [b, b+1)) -> screen GUI pixels. */
    public static double[] worldToScreen(MapContext ctx, double blockX, double blockZ) {
        double pivotX = ctx.canvasW / 2.0;
        double pivotY = ctx.canvasH / 2.0;
        double x = blockX * NAConstants.PIXELS_PER_CANVAS_UNIT - ctx.scrollX - pivotX;
        double y = blockZ * NAConstants.PIXELS_PER_CANVAS_UNIT - ctx.scrollY - pivotY;
        x *= ctx.zoom;
        y *= ctx.zoom;
        double cos = Math.cos(ctx.rotation);
        double sin = Math.sin(ctx.rotation);
        double rx = x * cos - y * sin;
        double ry = x * sin + y * cos;
        return new double[]{ctx.canvasX + pivotX + rx, ctx.canvasY + pivotY + ry};
    }

    /** Screen GUI pixels -> world block coordinates (double). Inverse of {@link #worldToScreen}. */
    public static double[] screenToWorld(MapContext ctx, double screenX, double screenY) {
        double pivotX = ctx.canvasW / 2.0;
        double pivotY = ctx.canvasH / 2.0;
        double x = screenX - ctx.canvasX - pivotX;
        double y = screenY - ctx.canvasY - pivotY;
        double cos = Math.cos(-ctx.rotation);
        double sin = Math.sin(-ctx.rotation);
        double rx = (x * cos - y * sin) / ctx.zoom;
        double ry = (x * sin + y * cos) / ctx.zoom;
        double canvasX = ctx.scrollX + pivotX + rx;
        double canvasY = ctx.scrollY + pivotY + ry;
        return new double[]{canvasX / NAConstants.PIXELS_PER_CANVAS_UNIT, canvasY / NAConstants.PIXELS_PER_CANVAS_UNIT};
    }

    /** The block under a screen point (floor of {@link #screenToWorld}). */
    public static int[] screenToBlock(MapContext ctx, double screenX, double screenY) {
        double[] w = screenToWorld(ctx, screenX, screenY);
        return new int[]{(int) Math.floor(w[0]), (int) Math.floor(w[1])};
    }

    /** Screen pixels per block at the current zoom. */
    public static double pixelsPerBlock(MapContext ctx) {
        return ctx.zoom * NAConstants.PIXELS_PER_CANVAS_UNIT;
    }

    /** Axis-aligned world bounds (blocks) of everything visible on the canvas: {minX, minZ, maxX, maxZ}. */
    public static double[] visibleWorldBounds(MapContext ctx) {
        double[][] corners = {
                screenToWorld(ctx, ctx.canvasX, ctx.canvasY),
                screenToWorld(ctx, ctx.canvasX + ctx.canvasW, ctx.canvasY),
                screenToWorld(ctx, ctx.canvasX, ctx.canvasY + ctx.canvasH),
                screenToWorld(ctx, ctx.canvasX + ctx.canvasW, ctx.canvasY + ctx.canvasH)};
        double minX = Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (double[] c : corners) {
            minX = Math.min(minX, c[0]);
            maxX = Math.max(maxX, c[0]);
            minZ = Math.min(minZ, c[1]);
            maxZ = Math.max(maxZ, c[1]);
        }
        return new double[]{minX, minZ, maxX, maxZ};
    }

    /** Scroll so that the given world block point is in the middle of the canvas (rotation-independent). */
    public static void centerOnBlock(MapContext ctx, double blockX, double blockZ) {
        ctx.scrollX = (float) (blockX * NAConstants.PIXELS_PER_CANVAS_UNIT - ctx.canvasW / 2.0);
        ctx.scrollY = (float) (blockZ * NAConstants.PIXELS_PER_CANVAS_UNIT - ctx.canvasH / 2.0);
    }

    /** World block point currently in the middle of the canvas. */
    public static double[] centerBlock(MapContext ctx) {
        return new double[]{(ctx.scrollX + ctx.canvasW / 2.0) / NAConstants.PIXELS_PER_CANVAS_UNIT,
                (ctx.scrollY + ctx.canvasH / 2.0) / NAConstants.PIXELS_PER_CANVAS_UNIT};
    }
}
