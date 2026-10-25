package dev.natowb.natosatlas.client.ui.screens.map;

import dev.natowb.natosatlas.client.MapUpdater;
import dev.natowb.natosatlas.client.NAClient;
import dev.natowb.natosatlas.client.platform.ClientWorldAccess;
import dev.natowb.natosatlas.client.io.MapSaver;
import dev.natowb.natosatlas.client.ui.screens.waypoint.Waypoint;
import dev.natowb.natosatlas.core.NAConstants;
import dev.natowb.natosatlas.client.cache.NARegionPixelCache;
import dev.natowb.natosatlas.core.chunk.ChunkWrapper;
import dev.natowb.natosatlas.core.data.NABiome;
import dev.natowb.natosatlas.core.data.NACoord;
import dev.natowb.natosatlas.core.data.NAEntity;
import dev.natowb.natosatlas.client.platform.NAPainter;
import dev.natowb.natosatlas.client.ui.screens.help.HelpScreen;
import dev.natowb.natosatlas.client.ui.screens.settings.Settings;
import dev.natowb.natosatlas.client.ui.screens.settings.MapButtons;
import dev.natowb.natosatlas.client.ui.screens.settings.SettingsOption;
import dev.natowb.natosatlas.client.ui.screens.settings.SettingsScreen;
import dev.natowb.natosatlas.client.ui.elements.UIScaleInfo;
import dev.natowb.natosatlas.client.ui.themes.UITheme;
import dev.natowb.natosatlas.client.ui.elements.UIElementIconButton;
import dev.natowb.natosatlas.client.ui.elements.UIScreen;
import dev.natowb.natosatlas.client.ui.layout.UIHorizontalLayout;
import dev.natowb.natosatlas.client.ui.layout.UILayout;
import dev.natowb.natosatlas.client.ui.screens.waypoint.WaypointCreateScreen;
import dev.natowb.natosatlas.client.ui.screens.waypoint.WaypointListScreen;
import dev.natowb.natosatlas.client.ui.screens.waypoint.Waypoints;
import dev.natowb.natosatlas.client.cache.NAForeignRegionCache;
import dev.natowb.natosatlas.client.io.MarkupExport;
import dev.natowb.natosatlas.client.ui.elements.UIElementButton;
import dev.natowb.natosatlas.client.ui.elements.UIElementToolButton;
import dev.natowb.natosatlas.client.ui.screens.map.markup.MarkupGeometry;
import dev.natowb.natosatlas.client.ui.screens.map.markup.MarkupModel;
import dev.natowb.natosatlas.client.ui.screens.map.markup.MarkupRenderer;
import dev.natowb.natosatlas.client.ui.screens.map.markup.MarkupRaster;
import dev.natowb.natosatlas.client.ui.screens.map.markup.MarkupShape;
import dev.natowb.natosatlas.client.ui.screens.map.markup.MarkupTool;
import dev.natowb.natosatlas.client.ui.screens.map.markup.MarkupToolbar;
import dev.natowb.natosatlas.core.data.NAWorldInfo;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.Set;

import static dev.natowb.natosatlas.client.texture.TextureProvider.*;

public class MapScreen extends UIScreen {

    private final MapViewport viewport = new MapViewport();
    private final NAPainter painter = NAClient.get().getPlatform().painter;
    private final MapStageRegions regionPainter = new MapStageRegions();
    private final MapStageSlime slimePainter = new MapStageSlime();
    private final MapStageGrid gridPainter = new MapStageGrid();
    private final MapStageEntities entitiesPainter = new MapStageEntities();
    private final MapStageWaypoints waypointsPainter = new MapStageWaypoints();
    private final MapStageSpawners spawnersPainter = new MapStageSpawners();

    /** mp16: the world map key (set by the platform from its keybinding, default M). */
    public static int mapKeyCode = Keyboard.KEY_M;
    /** mp16: false until the map key has been seen released after the map opened, so the opening press can't start markup. */
    private boolean mapKeyArmed = false;

    // mp14: Layer view (exact block slice at Settings.layerY) and spawner markers
    private static boolean layerView = false;
    private boolean draggingLayer = false;
    private dev.natowb.natosatlas.core.io.McRegionReader.Spawner hoveredSpawner = null;

    private int activeLayer = 0;
    private long lastLeftClickTime = 0;
    private long lastRightClickTime = 0;

    private static final long DOUBLE_CLICK_TIME_THRESHOLD = 250;

    private boolean firstInit = false;

    // mp9: dimension toggle. viewDim = the dimension the map shows; playerDim = where the player is.
    // Another dimension is drawn from its saved tiles only (read-only cache); live mapping never writes to it.
    private int playerDim = 0;
    private int viewDim = 0;
    private NAForeignRegionCache foreign = null;
    private List<Waypoint> foreignWaypoints = null;
    private boolean foreignHasTiles = true;

    // mp9: markup mode (in memory only; Escape / closing the map forgets it unless saved as an image)
    private boolean markupMode = false;
    private final MarkupModel markup = new MarkupModel();
    private MarkupTool tool = MarkupTool.RECT;
    private int markupColor = MarkupToolbar.DEFAULT_COLOR;
    private int markupWidth = 1; // mp11: thickness in blocks
    private boolean markupFill = false;
    private MarkupToolbar toolbar = null;
    private MarkupShape drawing = null;
    private double[] drawStart = null;
    private int drawStartSX, drawStartSY;
    private boolean drawMoved = false;
    private boolean erasing = false;
    private double[] lastPencil = null;
    private int[] lastErase = null; // mp11: last erased block, so a fast drag erases a connected path

    private String toast = "";
    private long toastTime = 0;

    public MapScreen(UIScreen parent) {
        super(parent);
        Waypoints.load();
        viewport.setZoom(Settings.defaultZoom);

    }

    @Override
    public void init(int width, int height) {
        super.init(width, height);
        viewport.initViewport(0, 0, width, height);

        if (!firstInit) {
            firstInit = true;
            NAWorldInfo info = ClientWorldAccess.get().getWorldInfo();
            playerDim = info == null ? 0 : info.getDimensionId();
            viewDim = playerDim;
            NAEntity player = ClientWorldAccess.get().getPlayer();
            if (player != null) {
                viewport.centerOn((float) player.x * NAConstants.PIXELS_PER_CANVAS_UNIT, (float) player.z * NAConstants.PIXELS_PER_CANVAS_UNIT);
            }
        }
        if (foreign != null) {
            // back from Options (map data may have been deleted) or a resize: reload the other dimension's tiles
            foreign.clear();
            foreignWaypoints = Waypoints.readDimension(viewDim);
            foreignHasTiles = foreign.hasAnyTiles(viewLayerGuess());
        }
        buildButtons();
    }

    /** mp9: (re)creates every button (also after toggling dimension / markup mode), keeping the map state. */
    private void buildButtons() {
        buttons.clear();

        UILayout leftLayout = new UIHorizontalLayout(5, 15, 5, false);

        // mp16: each button can be hidden in Settings > Preferences; hidden ones take no slot, so the row closes up
        if (MapButtons.CLOSE.shown) {
        UIElementIconButton closeButton = new UIElementIconButton(101, leftLayout, 20, 20, ICON_CROSS);
        closeButton.setHandler(btn -> NAClient.get().getPlatform().screen.openNacScreen(parent));
        closeButton.setTooltip(MapTooltips.CLOSE);
        addButton(closeButton);
        }

        if (MapButtons.OPTIONS.shown) {
        UIElementIconButton optionsButton = new UIElementIconButton(102, leftLayout, 20, 20, ICON_COG);
        optionsButton.setHandler(btn -> NAClient.get().getPlatform().screen.openNacScreen(new SettingsScreen(this)));
        optionsButton.setTooltip(MapTooltips.OPTIONS);
        addButton(optionsButton);
        }

        if (MapButtons.HELP.shown) {
        UIElementIconButton helpButton = new UIElementIconButton(106, leftLayout, 20, 20, ICON_HELP);
        helpButton.setHandler(btn -> NAClient.get().getPlatform().screen.openNacScreen(new HelpScreen(this)));
        helpButton.setTooltip(MapTooltips.HELP);
        addButton(helpButton);
        }

        // mp9: markup mode toggle
        if (MapButtons.MARKUP.shown) {
        int[] p = nextPos(leftLayout);
        UIElementToolButton markupButton = new UIElementToolButton(108, p[0], p[1], 20, 20, ICON_MARKUP, () -> markupMode);
        markupButton.setTooltip(MapTooltips.markup(markupMode));
        markupButton.setHandler(btn -> {
            if (markupMode) exitMarkup();
            else enterMarkup();
        });
        addButton(markupButton);
        }



        // mp11: fixed slots (MapTopBar), so nothing moves when the map mode button is disabled in the Nether
        // mp16: only the slots of buttons shown in Preferences, packed from the right
        java.util.Map<MapTopBar.Slot, int[]> top = MapTopBar.layout(width, MapTopBar.visibleSlots());


        if (top.containsKey(MapTopBar.Slot.WAYPOINTS)) {
        UIElementIconButton waypointsButton = new UIElementIconButton(103, top.get(MapTopBar.Slot.WAYPOINTS)[0], top.get(MapTopBar.Slot.WAYPOINTS)[1], 20, 20, ICON_WAYPOINTS);
        waypointsButton.setHandler(btn -> NAClient.get().getPlatform().screen.openNacScreen(new WaypointListScreen(this)));
        waypointsButton.setTooltip(MapTooltips.WAYPOINTS);
        addButton(waypointsButton);
        }

        if (top.containsKey(MapTopBar.Slot.SLIME)) {
        UIElementIconButton slimeChunksButton = new UIElementIconButton(104, top.get(MapTopBar.Slot.SLIME)[0], top.get(MapTopBar.Slot.SLIME)[1], 20, 20, ICON_SLIME_ENABLED);
        if (!Settings.showSlimeChunks) {
            slimeChunksButton.setIcon(ICON_SLIME_DISABLED);
        }
        slimeChunksButton.setHandler(btn -> {
            Settings.showSlimeChunks = !Settings.showSlimeChunks;
            if (Settings.showSlimeChunks) {
                ((UIElementIconButton) btn).setIcon(ICON_SLIME_ENABLED);
            } else {
                ((UIElementIconButton) btn).setIcon(ICON_SLIME_DISABLED);
            }
            Settings.save();
        });
        slimeChunksButton.setTooltip(MapTooltips.SLIME); // mp12: never the seed
        addButton(slimeChunksButton);
        }

        // mp14: spawners (click cycles Off / All / Spider / Zombie / Skeleton / Pig)
        if (top.containsKey(MapTopBar.Slot.SPAWNERS)) {
        UIElementIconButton spawnerButton = new UIElementIconButton(111, top.get(MapTopBar.Slot.SPAWNERS)[0], top.get(MapTopBar.Slot.SPAWNERS)[1], 20, 20,
                spawnerIcon());
        spawnerButton.setTooltip(MapTooltips.spawners(Settings.spawnerFilter));
        if (!"Off".equals(Settings.spawnerFilter) && !"All".equals(Settings.spawnerFilter))
            spawnerButton.setColor(MapSpawners.color(Settings.spawnerFilter));
        spawnerButton.setHandler(btn -> {
            Settings.spawnerFilter = MapSpawners.next(Settings.spawnerFilter);
            Settings.save();
            buildButtons();
        });
        addButton(spawnerButton);
        }

        // mp14: Layer view toggle (+ its Y slider buttons at the right edge)
        if (top.containsKey(MapTopBar.Slot.LAYER)) {
        UIElementIconButton layerButton = new UIElementIconButton(112, top.get(MapTopBar.Slot.LAYER)[0], top.get(MapTopBar.Slot.LAYER)[1], 20, 20,
                layerView ? ICON_LAYER_ENABLED : ICON_LAYER_DISABLED);
        layerButton.setTooltip(MapTooltips.layer(layerView, Settings.layerY));
        layerButton.setHandler(btn -> toggleLayerView());
        addButton(layerButton);
        }
        if (layerView) {
            int[] up = MapLayerSlider.upButton(width, height), dn = MapLayerSlider.downButton(width, height);
            UIElementIconButton upButton = new UIElementIconButton(113, up[0], up[1], 20, 20, ICON_PLUS);
            upButton.setTooltip(MapTooltips.LAYER_UP);
            upButton.setHandler(btn -> setLayerY(Settings.layerY + 1));
            addButton(upButton);
            UIElementIconButton downButton = new UIElementIconButton(114, dn[0], dn[1], 20, 20, ICON_MINUS);
            downButton.setTooltip(MapTooltips.LAYER_DOWN);
            downButton.setHandler(btn -> setLayerY(Settings.layerY - 1));
            addButton(downButton);
        }

        if (top.containsKey(MapTopBar.Slot.GRID)) {
        UIElementIconButton gridButton = new UIElementIconButton(107, top.get(MapTopBar.Slot.GRID)[0], top.get(MapTopBar.Slot.GRID)[1], 20, 20, ICON_GRID_ENABLED);
        gridButton.setTooltip(MapTooltips.GRID);

        if (!Settings.mapGrid) {
            gridButton.setIcon(ICON_GRID_DISABLED);
        }

        gridButton.setHandler(btn -> {
            Settings.mapGrid = !Settings.mapGrid;
            if (Settings.mapGrid) {
                ((UIElementIconButton) btn).setIcon(ICON_GRID_ENABLED);
            } else {
                ((UIElementIconButton) btn).setIcon(ICON_GRID_DISABLED);
            }
            Settings.save();
        });
        addButton(gridButton);
        }


        if (top.containsKey(MapTopBar.Slot.ENTITIES)) {
        UIElementIconButton entityButton = new UIElementIconButton(105, top.get(MapTopBar.Slot.ENTITIES)[0], top.get(MapTopBar.Slot.ENTITIES)[1], 20, 20, 5);

        entityButton.setTooltip(MapTooltips.entities(Settings.entityDisplayMode.name()));
        switch (Settings.entityDisplayMode) {
            case Player:
                entityButton.setIcon(ICON_ENTITY_PLAYER);
                break;
            case All:
                entityButton.setIcon(ICON_ENTITY_ALL);
                break;
            case Nothing:
                entityButton.setIcon(ICON_ENTITY_NONE);
                break;
        }

        entityButton.setHandler(btn -> {
            SettingsOption.ENTITY_DISPLAY.cycle();
            entityButton.setTooltip(MapTooltips.entities(Settings.entityDisplayMode.name()));
            switch (Settings.entityDisplayMode) {
                case Player:
                    entityButton.setIcon(ICON_ENTITY_PLAYER);
                    break;
                case All:
                    entityButton.setIcon(ICON_ENTITY_ALL);
                    break;
                case Nothing:
                    entityButton.setIcon(ICON_ENTITY_NONE);
                    break;
            }
            Settings.save();
        });
        addButton(entityButton);
        }


        // mp11: the map mode button is always there; in the Nether it is greyed out (the Nether is always the cave map)
        final boolean ceiling = viewHasCeiling();
        if (top.containsKey(MapTopBar.Slot.MAP_MODE)) {
        UIElementIconButton modeButton = new UIElementIconButton(110, top.get(MapTopBar.Slot.MAP_MODE)[0], top.get(MapTopBar.Slot.MAP_MODE)[1], 20, 20, modeIcon());
        modeButton.setTooltip(MapTopBar.mapModeTooltip(ceiling, Settings.mapRenderMode.name()));
        if (!MapTopBar.mapModeEnabled(ceiling)) {
            modeButton.active = false;
            modeButton.setIcon(ICON_CAVE);
            modeButton.setColor(MapTopBar.DISABLED_ICON_COLOR);
        }
        modeButton.setHandler(btn -> {
            if (viewHasCeiling()) return;
            SettingsOption.MAP_RENDER_MODE.cycle();
            modeButton.setTooltip(MapTopBar.mapModeTooltip(false, Settings.mapRenderMode.name()));
            modeButton.setIcon(modeIcon());
            Settings.save();
        });
        addButton(modeButton);
        }

        // mp10: dimension toggle, at the left end of the top-right map buttons (was next to markup in mp9)
        if (top.containsKey(MapTopBar.Slot.DIMENSION)) {
        UIElementIconButton dimButton = new UIElementIconButton(109, top.get(MapTopBar.Slot.DIMENSION)[0], top.get(MapTopBar.Slot.DIMENSION)[1], 20, 20,
                viewDim == MapDimensions.NETHER ? ICON_DIM_NETHER : ICON_DIM_OVERWORLD);
        dimButton.setTooltip(MapTooltips.dimension(viewDim));
        dimButton.setHandler(btn -> setViewDim(MapDimensions.next(viewDim, playerDim), true));
        addButton(dimButton);
        }

        if (markupMode) buildToolbar();
    }

    private static int modeIcon() {
        switch (Settings.mapRenderMode) {
            case Night:
                return ICON_NIGHT;
            case Cave:
                return ICON_CAVE;
            case Auto:
                return ICON_AUTO;
            default:
                return ICON_DAY;
        }
    }

    private static int[] nextPos(UILayout layout) {
        dev.natowb.natosatlas.client.ui.layout.UIPoint pt = layout.next(20, 20);
        return new int[]{pt.x, pt.y};
    }


    @Override
    public void tick() {
        super.tick();
        syncDimension();

        if (isForeign()) {
            activeLayer = viewLayerGuess();
            return;
        }

        if (ClientWorldAccess.get().getWorldInfo().hasCeiling()) {
            activeLayer = 2;
            return;
        }

        switch (Settings.mapRenderMode) {
            case Day:
                activeLayer = 0;
                break;
            case Night:
                activeLayer = 1;
                break;
            case Cave:
                activeLayer = 2;
                break;
            case Auto: {
                long time = ClientWorldAccess.get().getWorldInfo().getTime() % 24000L;
                boolean day = time < 12000L;

                NAEntity player = ClientWorldAccess.get().getPlayer();
                ChunkWrapper chunk = ClientWorldAccess.get().getChunk(
                        NACoord.from(player.chunkX, player.chunkZ)
                );

                int skyLight = chunk.getSkyLight(
                        player.localX,
                        (int) Math.floor(player.y),
                        player.localZ
                );

                if (skyLight == 0) {
                    activeLayer = 2;
                } else {
                    activeLayer = day ? 0 : 1;
                }
            }
            break;
            default:
                break;
        }

    }

    @Override
    public void render(int mouseX, int mouseY, float delta, UIScaleInfo scaleInfo) {
        if (!mapKeyArmed && !Keyboard.isKeyDown(mapKeyCode)) mapKeyArmed = true;
        syncDimension();
        painter.drawRect(0, 0, width, height, UITheme.PANEL_BG);


        viewport.updateMouse(mouseX, mouseY);
        viewport.begin(scaleInfo);

        MapContext ctx = viewport.getContext();
        ctx.tiles = foreign; // mp9: null = live tiles of the player's dimension
        Set<Long> visible = viewport.computeVisibleRegions();


        boolean sliceBusy = false;
        if (layerView) {
            // mp14: exact slice at Y, from loaded chunks (your dimension) and the imported region files
            double[] c = MapProjection.screenToWorld(ctx, width / 2.0, height / 2.0);
            java.util.Map<Long, Integer> tex = dev.natowb.natosatlas.client.SliceRenderer.get()
                    .update(viewDim, Settings.layerY, !isForeign(), visible, c[0], c[1]);
            for (java.util.Map.Entry<Long, Integer> e : tex.entrySet()) {
                NACoord rc = NACoord.fromKey(e.getKey());
                painter.drawTexture(e.getValue(), rc.x * NAConstants.PIXELS_PER_CANVAS_REGION, rc.z * NAConstants.PIXELS_PER_CANVAS_REGION,
                        NAConstants.PIXELS_PER_CANVAS_REGION, NAConstants.PIXELS_PER_CANVAS_REGION);
            }
            sliceBusy = dev.natowb.natosatlas.client.SliceRenderer.get().isBusy(viewDim, Settings.layerY, visible);
        } else {
            regionPainter.draw(ctx, visible, activeLayer);
        }
        slimePainter.draw(ctx, visible, activeLayer);
        gridPainter.draw(ctx, visible, activeLayer);
        entitiesPainter.setDisplayMode(Settings.entityDisplayMode);
        waypointsPainter.source = isForeign() ? foreignWaypoints : null;
        waypointsPainter.draw(ctx, visible, activeLayer);
        double[] ghost = null;
        if (!isForeign()) {
            entitiesPainter.draw(ctx, visible, activeLayer);
        } else {
            ghost = ghostPosition();
            NAEntity player = ClientWorldAccess.get().getPlayer();
            if (ghost != null && player != null) entitiesPainter.drawGhostPlayer(ctx, ghost[0], ghost[1], player.yaw);
        }
        java.util.List<dev.natowb.natosatlas.core.io.McRegionReader.Spawner> spawners = dev.natowb.natosatlas.client.SpawnerStore.forDim(viewDim);
        hoveredSpawner = markupMode ? null : MapSpawners.hovered(ctx, spawners, Settings.spawnerFilter, mouseX, mouseY, 6);
        spawnersPainter.draw(ctx, spawners, Settings.spawnerFilter, hoveredSpawner);
        renderMouseBlockHighlight(ctx);
        viewport.end();

        if (markupMode) MarkupRenderer.drawShapes(ctx, markup.forDim(viewDim), drawing, painter);
        if (ghost != null) {
            double[] gs = MapProjection.worldToScreen(ctx, ghost[0], ghost[1]);
            String t = "You (" + MapDimensions.name(playerDim) + ")";
            painter.drawStringWithShadow(t, (int) gs[0] - painter.getStringWidth(t) / 2, (int) gs[1] + 8, 0xFFC0A0FF);
        }

        renderDebugInfo(ctx);
        renderMouseBlockInfo(ctx);
        String importStatus = dev.natowb.natosatlas.client.RegionImporter.get().getStatus();
        if (!importStatus.isEmpty()) painter.drawCenteredString(importStatus, width / 2, 4, 0xFF55FF55);
        int statusY = importStatus.isEmpty() ? 4 : 38;
        if (isForeign()) {
            String v = "Viewing: " + MapDimensions.name(viewDim) + "  (you are in the " + MapDimensions.name(playerDim) + ")";
            if (!foreignHasTiles) v += " - nothing mapped here yet";
            int vw = painter.getStringWidth(v);
            int vy = 30;
            painter.drawRect(width / 2 - vw / 2 - 4, vy - 3, width / 2 + vw / 2 + 4, vy + 10, 0xC0200830);
            painter.drawCenteredString(v, width / 2, vy, 0xFFE0C0FF);
            statusY = Math.max(statusY, 44);
        }
        String imageStatus = dev.natowb.natosatlas.client.io.WorldMapExport.getStatus();
        if (!imageStatus.isEmpty()) {
            painter.drawCenteredString(imageStatus, width / 2, statusY, 0xFF55FF55);
            statusY += 12;
        }
        if (layerView) {
            String ls = "Layer view: Y " + Settings.layerY + (sliceBusy ? " - reading chunks..." : "");
            if (!dev.natowb.natosatlas.client.SliceRenderer.get().hasRegionFiles(viewDim))
                ls += isForeign() ? " - no region folder set (Options > Import)" : " - loaded chunks only (set region folders in Options > Import)";
            int ly = Math.max(statusY, 30); // under the button rows, which can reach the middle on narrow screens
            painter.drawCenteredString(ls, width / 2, ly, 0xFFA0E0FF);
            statusY = ly + 12;
        }
        if (!toast.isEmpty() && System.currentTimeMillis() - toastTime < 4000) {
            painter.drawCenteredString(toast, width / 2, statusY, 0xFFFFFFA0);
        }
        if (markupMode) renderToolbarPanel(mouseX, mouseY);
        super.render(mouseX, mouseY, delta, scaleInfo);
        if (layerView) renderLayerSlider(mouseX, mouseY);
        if (hoveredSpawner != null && !markupMode) {
            dev.natowb.natosatlas.client.ui.elements.UIElement.drawTooltipBox(MapTooltips.spawner(hoveredSpawner.mob, hoveredSpawner.x, hoveredSpawner.y, hoveredSpawner.z), mouseX, mouseY, width, height);
        }
        if (markupMode && toolbar != null) { // mp12: colour names on the palette
            int sw = toolbar.swatchAt(mouseX, mouseY);
            if (sw >= 0) dev.natowb.natosatlas.client.ui.elements.UIElement.drawTooltipBox(MapTooltips.colour(sw), mouseX, mouseY, width, height);
        }
    }

    public void renderMouseBlockHighlight(MapContext ctx) {
        NACoord bc = getMouseBlock(ctx);

        double px = bc.x * NAConstants.PIXELS_PER_CANVAS_UNIT;
        double pz = bc.z * NAConstants.PIXELS_PER_CANVAS_UNIT;

        double size = NAConstants.PIXELS_PER_CANVAS_UNIT;

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1f, 1f, 0f, 0.5f);

        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex2d(px, pz);
        GL11.glVertex2d(px + size, pz);
        GL11.glVertex2d(px + size, pz + size);
        GL11.glVertex2d(px, pz + size);
        GL11.glEnd();

        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }


    private void renderMouseBlockInfo(MapContext ctx) {
        NACoord bc = getMouseBlock(ctx);
        String text;
        if (isForeign()) {
            // mp9: biome lookups only work for the loaded world; show the dimension and the matching spot instead
            text = "(" + bc.x + ", " + bc.z + ") | " + MapDimensions.name(viewDim) + " |";
            double sc = MapDimensions.scale(viewDim, playerDim);
            if (sc != 0) text += " " + MapDimensions.name(playerDim) + " (" + (int) Math.floor((bc.x + 0.5) * sc) + ", " + (int) Math.floor((bc.z + 0.5) * sc) + ") |";
        } else {
            NABiome biome = ClientWorldAccess.get().getBiome(bc);
            text = "(" + bc.x + ", " + bc.z + ") | " + biome.name + " |";
        }

        NAPainter p = NAClient.get().getPlatform().painter;

        int padding = 4;
        int textW = p.getStringWidth(text);
        int textH = 8;

        int tx = (width - textW) / 2;
        int ty = height - textH - padding - 6;

        p.drawRect(tx - padding, ty - padding, tx + textW + padding, ty + textH + padding, 0xF0100010);
        p.drawRect(tx - padding, ty - padding, tx + textW + padding, ty - padding + 1, 0x505000FF);
        p.drawRect(tx - padding, ty + textH + padding - 1, tx + textW + padding, ty + textH + padding, 0x505000FF);

        p.drawStringWithShadow(text, tx, ty, 0xFFFFFFFF);
    }


    public NACoord getMouseBlock(MapContext ctx) {
        // mp9: the block under the cursor (floor). This used to add 1 to X and Z, so the coordinates shown at the
        // bottom (and new waypoints) were one block off towards +X/+Z. The highlight box was drawn at -1 to match.
        int[] b = MapProjection.screenToBlock(ctx, ctx.mouseX, ctx.mouseY);
        return new NACoord(b[0], b[1]);
    }


    @Override
    public void mouseDown(int x, int y, int button) {
        super.mouseDown(x, y, button);
        if (markupMode && markupMouseDown(x, y, button)) return;
        if (button == 0 && layerView && MapLayerSlider.onTrack(width, height, x, y)) { // mp14
            draggingLayer = true;
            setLayerY(MapLayerSlider.yAt(width, height, y));
            return;
        }
        if (button == 0 && hoveredSpawner != null) { // mp14: copy spawner coordinates
            String c = hoveredSpawner.x + " " + hoveredSpawner.y + " " + hoveredSpawner.z;
            try {
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(c), null);
                showToast("Copied " + hoveredSpawner.mob + " spawner: " + c);
            } catch (Throwable t) {
                showToast(hoveredSpawner.mob + " spawner: " + c + " (couldn't copy)");
            }
            return;
        }
        if (button == 2) { // mp9: middle drag pans too
            viewport.dragStart(x, y);
            return;
        }

        long now = System.currentTimeMillis();

        if (button == 0) {
            if (now - lastLeftClickTime <= DOUBLE_CLICK_TIME_THRESHOLD && isForeign()) {
                showToast("Waypoints can only be added in the dimension you are in (" + MapDimensions.name(playerDim) + ")");
                lastLeftClickTime = 0;
                viewport.dragStart(x, y);
                return;
            }
            if (now - lastLeftClickTime <= DOUBLE_CLICK_TIME_THRESHOLD) {
                MapContext ctx = viewport.getContext();
                NACoord blockCoord = getMouseBlock(ctx);
                NAClient.get().getPlatform().screen.openNacScreen(
                        new WaypointCreateScreen(this, blockCoord.x, blockCoord.z)
                );
                return;
            }

            lastLeftClickTime = now;
            viewport.dragStart(x, y);
            return;
        }

        if (button == 1) {
            if (now - lastRightClickTime <= DOUBLE_CLICK_TIME_THRESHOLD && isForeign()) {
                showToast("Waypoints can only be added in the dimension you are in (" + MapDimensions.name(playerDim) + ")");
                lastRightClickTime = 0;
                viewport.rotateStart(x, y);
                return;
            }
            if (now - lastRightClickTime <= DOUBLE_CLICK_TIME_THRESHOLD) {
                long tempCount = Waypoints.getAll().stream()
                        .filter(w -> w.temp).count();

                String name = "Temp #" + (tempCount + 1);

                MapContext ctx = viewport.getContext();
                NACoord blockCoord = getMouseBlock(ctx);

                Waypoint wp = new Waypoint(name, blockCoord.x, 0, blockCoord.z, 0xFFFFFFFF, true);
                Waypoints.add(wp);
                return;
            }


            lastRightClickTime = now;
            viewport.rotateStart(x, y);
        }
    }


    @Override
    public void mouseDrag(int x, int y, int button) {
        super.mouseDrag(x, y, button);
        if (markupMode && markupMouseDrag(x, y, button)) return;
        if (draggingLayer) { setLayerY(MapLayerSlider.yAt(width, height, y)); return; }
        if (button == 0 || button == 2) viewport.dragMove(x, y);
        if (button == 1) viewport.rotateMove(x, y);
    }

    @Override
    public void mouseUp(int x, int y, int button) {
        super.mouseUp(x, y, button);
        if (markupMode && markupMouseUp(x, y, button)) return;
        if (draggingLayer) { draggingLayer = false; Settings.save(); return; }
        if (button == 0 || button == 2) viewport.dragEnd();
        if (button == 1) viewport.rotateEnd();
    }

    @Override
    public void mouseScroll(int amount) {
        if (layerView && shiftDown()) { // mp14: Shift+wheel = layer up / down
            setLayerY(Settings.layerY + Integer.signum(amount));
            return;
        }
        viewport.zoom(amount);
    }

    @Override
    public void keyPressed(char character, int keyCode) {
        if (markupMode && markupKey(keyCode)) return; // mp9: Esc leaves markup mode instead of closing the map
        super.keyPressed(character, keyCode);

        if (keyCode == Keyboard.KEY_SPACE) {
            viewport.setRotation(0);
            NAEntity player = ClientWorldAccess.get().getPlayer();
            if (player != null && !isForeign()) {
                viewport.centerOn((float) player.x * 8f, (float) player.z * 8f);
            } else {
                double[] g = ghostPosition();
                if (g != null) MapProjection.centerOnBlock(viewport.getContext(), g[0], g[1]);
            }
        }

        if (keyCode == Keyboard.KEY_P) {
            // mp7: P = save map image (full), Shift+P = small, to the screenshots folder (was a blocking stitch)
            // mp9: of the dimension the map shows
            boolean small = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
            dev.natowb.natosatlas.client.io.WorldMapExport.start(small, activeLayer, viewDim);
        }

        if (keyCode == Keyboard.KEY_D && !ctrlDown() && MapButtons.DIMENSION.keyWorks()) {
            setViewDim(MapDimensions.next(viewDim, playerDim), true);
        }

        // mp16: markup is on the map key (M): the press that opened the map doesn't count (see mapKeyArmed), the next one does
        if (keyCode == mapKeyCode && mapKeyArmed && !markupMode && MapButtons.MARKUP.keyWorks()) {
            enterMarkup();
        }

        if (keyCode == Keyboard.KEY_L && !ctrlDown() && MapButtons.LAYER.keyWorks()) toggleLayerView(); // mp14
        if (layerView && keyCode == Keyboard.KEY_UP) setLayerY(Settings.layerY + (shiftDown() ? 8 : 1));
        if (layerView && keyCode == Keyboard.KEY_DOWN) setLayerY(Settings.layerY - (shiftDown() ? 8 : 1));
    }

    // =========================================================================================== mp9: dimension view

    @Override
    public void onClose() {
        super.onClose();
        // free the other dimension's GL textures (also runs when Options/Help open; init() reloads them lazily)
        if (foreign != null) foreign.clear();
    }

    // =========================================================================================== mp14: layer view

    private int spawnerIcon() {
        return "Off".equals(Settings.spawnerFilter) ? ICON_SPAWNER_DISABLED : ICON_SPAWNER_ENABLED;
    }

    private void toggleLayerView() {
        layerView = !layerView;
        if (layerView) dev.natowb.natosatlas.client.SliceRenderer.get().clear(); // pick up new imports / paths
        buildButtons();
    }

    private void setLayerY(int y) {
        y = Math.max(0, Math.min(127, y));
        if (y == Settings.layerY) return;
        Settings.layerY = y;
        if (!draggingLayer) Settings.save();
        buildButtons(); // tooltip of the layer button shows Y
    }

    private void renderLayerSlider(int mx, int my) {
        int[] t = MapLayerSlider.track(width, height);
        painter.drawRect(t[0] - 1, t[1] - 1, t[2] + 1, t[3] + 1, 0xFF000000);
        painter.drawRect(t[0], t[1], t[2], t[3], 0xFF404048);
        // sea level (Y 64) tick
        int sea = MapLayerSlider.handleY(width, height, 64);
        painter.drawRect(t[0], sea, t[2], sea + 1, 0xFF4060C0);
        int hy = MapLayerSlider.handleY(width, height, Settings.layerY);
        boolean hover = draggingLayer || MapLayerSlider.onTrack(width, height, mx, my);
        painter.drawRect(t[0] - 4, hy - 3, t[2] + 4, hy + 3, 0xFF000000);
        painter.drawRect(t[0] - 3, hy - 2, t[2] + 3, hy + 2, hover ? 0xFFFFFFA0 : 0xFFE0E0E0);
        String label = "Y " + Settings.layerY;
        int lw = painter.getStringWidth(label);
        int lx = t[0] - 8 - lw;
        painter.drawRect(lx - 3, hy - 6, lx + lw + 2, hy + 6, 0xC0000000);
        painter.drawStringWithShadow(label, lx, hy - 4, 0xFFFFFFFF);
        if (hover && !draggingLayer)
            dev.natowb.natosatlas.client.ui.elements.UIElement.drawTooltipBox(MapTooltips.layerSlider(Settings.layerY), mx, my, width, height);
    }

    private boolean isForeign() {
        return viewDim != playerDim;
    }

    private boolean viewHasCeiling() {
        boolean foreign = isForeign();
        NAWorldInfo info = foreign ? null : ClientWorldAccess.get().getWorldInfo();
        return MapTopBar.viewHasCeiling(foreign, info != null && info.hasCeiling(), viewDim);
    }

    /** Layer shown for another dimension: cave for the Nether, else the chosen mode (Auto = by time of day). */
    private int viewLayerGuess() {
        if (!isForeign()) return activeLayer;
        if (MapDimensions.hasCeiling(viewDim)) return 2;
        switch (Settings.mapRenderMode) {
            case Day:
                return 0;
            case Night:
                return 1;
            case Cave:
                return 2;
            default: {
                NAWorldInfo info = ClientWorldAccess.get().getWorldInfo();
                long time = info == null ? 0 : info.getTime() % 24000L;
                return time < 12000L ? 0 : 1;
            }
        }
    }

    /** The player's position converted into the shown dimension (x8 / /8), or null if there is no such place. */
    private double[] ghostPosition() {
        NAEntity player = ClientWorldAccess.get().getPlayer();
        if (player == null) return null;
        double sc = MapDimensions.scale(playerDim, viewDim);
        if (sc == 0) return null;
        return new double[]{player.x * sc, player.z * sc};
    }

    /** The player changed dimension (portal, death) while the map is open: show where they are now. */
    private void syncDimension() {
        NAWorldInfo info = ClientWorldAccess.get().getWorldInfo();
        if (info == null) return;
        int d = info.getDimensionId();
        if (d == playerDim) return;
        playerDim = d;
        setViewDim(d, false);
        NAEntity player = ClientWorldAccess.get().getPlayer();
        if (player != null) viewport.centerOn((float) player.x * 8f, (float) player.z * 8f);
    }

    /** Show another dimension's saved map (or the player's own again). keepPlace: convert the view centre x8 / /8. */
    private void setViewDim(int dim, boolean keepPlace) {
        if (dim == viewDim && (foreign == null) == (dim == playerDim)) return;
        MapContext ctx = viewport.getContext();
        double[] c = MapProjection.centerBlock(ctx);
        double sc = MapDimensions.scale(viewDim, dim);
        cancelDrawing();
        if (foreign != null) {
            foreign.clear(); // frees its textures (render thread)
            foreign = null;
        }
        foreignWaypoints = null;
        foreignHasTiles = true;
        viewDim = dim;
        if (dim != playerDim) {
            NAWorldInfo info = ClientWorldAccess.get().getWorldInfo();
            foreign = new NAForeignRegionCache(dim, info != null && info.isMultiplayer());
            foreignWaypoints = Waypoints.readDimension(dim);
            activeLayer = viewLayerGuess();
            foreignHasTiles = foreign.hasAnyTiles(activeLayer);
        } else {
            tick(); // pick the live layer again right away
        }
        if (keepPlace && sc != 0) MapProjection.centerOnBlock(ctx, c[0] * sc, c[1] * sc);
        buildButtons();
    }

    // =========================================================================================== mp9: markup

    private void showToast(String s) {
        toast = s;
        toastTime = System.currentTimeMillis();
    }

    private static boolean ctrlDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)
                || Keyboard.isKeyDown(Keyboard.KEY_LMETA) || Keyboard.isKeyDown(Keyboard.KEY_RMETA);
    }

    private static boolean shiftDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }

    private void enterMarkup() {
        markupMode = true;
        buildButtons();
    }

    private void exitMarkup() {
        cancelDrawing();
        markup.discard();
        markupMode = false;
        toolbar = null;
        buildButtons();
    }

    private void cancelDrawing() {
        drawing = null;
        drawStart = null;
        if (erasing) markup.endErase();
        erasing = false;
        lastPencil = null;
        lastErase = null;
    }

    private void saveMarkup() {
        MapContext ctx = viewport.getContext();
        MarkupExport.start(markup.forDim(viewDim), viewDim, activeLayer, MapProjection.visibleWorldBounds(ctx));
    }

    private void selectTool(MarkupTool t) {
        cancelDrawing();
        tool = t;
    }

    private void buildToolbar() {
        toolbar = MarkupToolbar.layout(4, 29, height - 4); // below the top button row; the bottom info box is centred, so the panel may go down to the bottom
        for (final MarkupToolbar.Cell c : toolbar.cells) {
            UIElementToolButton b;
            switch (c.item) {
                case TOOL_PAN:
                case TOOL_RECT:
                case TOOL_ELLIPSE:
                case TOOL_LINE:
                case TOOL_PENCIL:
                case TOOL_ERASER_BLOCK:
                case TOOL_ERASER_SHAPE: {
                    final MarkupTool t = MarkupTool.values()[c.item.ordinal() - MarkupToolbar.Item.TOOL_PAN.ordinal()];
                    b = new UIElementToolButton(300 + t.ordinal(), c.x, c.y, c.w, c.h, t.icon, () -> tool == t);
                    b.setTooltip(t.tooltip());
                    b.setHandler(btn -> selectTool(t));
                    break;
                }
                case FILL:
                    b = new UIElementToolButton(310, c.x, c.y, c.w, c.h, markupFill ? ICON_FILL_ON : ICON_FILL_OFF, () -> markupFill);
                    b.setTooltip(MapTooltips.fill(markupFill));
                    b.setHandler(btn -> {
                        markupFill = !markupFill;
                        buildButtons();
                    });
                    break;
                case WIDTH:
                    b = new UIElementToolButton(311, c.x, c.y, c.w, c.h, MarkupToolbar.widthIcon(markupWidth), null);
                    b.setTooltip(MapTooltips.thickness(markupWidth));
                    b.setHandler(btn -> {
                        markupWidth = MarkupToolbar.nextWidth(markupWidth);
                        buildButtons();
                    });
                    break;
                case UNDO:
                    b = new UIElementToolButton(312, c.x, c.y, c.w, c.h, ICON_UNDO, null);
                    b.setTooltip(MapTooltips.UNDO);
                    b.setHandler(btn -> markup.undo());
                    break;
                case CLEAR:
                    b = new UIElementToolButton(313, c.x, c.y, c.w, c.h, ICON_CLEAR, null);
                    b.setTooltip(MapTooltips.CLEAR);
                    b.setHandler(btn -> {
                        cancelDrawing();
                        markup.clear(viewDim);
                    });
                    break;
                case SAVE:
                    b = new UIElementToolButton(314, c.x, c.y, c.w, c.h, ICON_SAVE, null);
                    b.setTooltip(MapTooltips.SAVE);
                    b.setHandler(btn -> saveMarkup());
                    break;
                default:
                    continue;
            }
            addButton(b);
        }
    }

    /** Panel and palette (the buttons draw themselves in super.render). */
    private void renderToolbarPanel(int mouseX, int mouseY) {
        MarkupToolbar t = toolbar;
        if (t == null) return;
        // same look as the map's info box: dark panel with a thin purple line top and bottom
        painter.drawRect(t.panelX, t.panelY, t.panelX + t.panelW, t.panelY + t.panelH, 0xE0100010);
        painter.drawRect(t.panelX, t.panelY, t.panelX + t.panelW, t.panelY + 1, 0x805000FF);
        painter.drawRect(t.panelX, t.panelY + t.panelH - 1, t.panelX + t.panelW, t.panelY + t.panelH, 0x805000FF);
        for (int i = 0; i < MarkupToolbar.PALETTE.length; i++) {
            int[] s = t.swatches[i];
            int col = MarkupToolbar.PALETTE[i];
            boolean sel = col == markupColor;
            boolean hov = mouseX >= s[0] && mouseX < s[0] + MarkupToolbar.SWATCH && mouseY >= s[1] && mouseY < s[1] + MarkupToolbar.SWATCH;
            // mp12: the chosen colour gets a white frame around a black ring (the colour bar is gone)
            if (sel) painter.drawRect(s[0] - 1, s[1] - 1, s[0] + MarkupToolbar.SWATCH + 1, s[1] + MarkupToolbar.SWATCH + 1, 0xFFFFFFFF);
            painter.drawRect(s[0], s[1], s[0] + MarkupToolbar.SWATCH, s[1] + MarkupToolbar.SWATCH, hov && !sel ? 0xFFFFFFA0 : 0xFF000000);
            painter.drawRect(s[0] + 1, s[1] + 1, s[0] + MarkupToolbar.SWATCH - 1, s[1] + MarkupToolbar.SWATCH - 1, 0xFF000000 | col);
        }
        for (UIElementButton b : buttons) {
            if (b.id == 312) b.active = markup.canUndo();
        }
    }

    private boolean markupMouseDown(int x, int y, int button) {
        if (button == 1) { // right drag rotates as usual (no temp waypoints in markup mode)
            viewport.rotateStart(x, y);
            return true;
        }
        if (button == 2) {
            viewport.dragStart(x, y);
            return true;
        }
        if (button != 0) return false;
        if (toolbar != null && toolbar.inside(x, y)) {
            int sw = toolbar.swatchAt(x, y);
            if (sw >= 0) {
                markupColor = MarkupToolbar.PALETTE[sw];
                NAClient.get().getPlatform().screen.playSound("random.click", 1.0F, 1.0F);
            }
            return true;
        }
        MapContext ctx = viewport.getContext();
        double[] w = MapProjection.screenToWorld(ctx, x, y);
        switch (tool) {
            case PAN:
                viewport.dragStart(x, y);
                return true;
            case ERASER_BLOCK:
            case ERASER_SHAPE:
                erasing = true;
                lastErase = null;
                markup.beginErase();
                eraseAt(x, y);
                return true;
            default:
                break;
        }
        drawStart = w;
        drawStartSX = x;
        drawStartSY = y;
        drawMoved = false;
        MarkupShape.Type type = tool == MarkupTool.RECT ? MarkupShape.Type.RECT
                : tool == MarkupTool.ELLIPSE ? MarkupShape.Type.ELLIPSE
                : tool == MarkupTool.LINE ? MarkupShape.Type.LINE : MarkupShape.Type.PENCIL;
        drawing = new MarkupShape(type, viewDim, markupColor, markupWidth, markupFill);
        if (type == MarkupShape.Type.PENCIL) {
            drawing.add(w[0], w[1]);
            lastPencil = new double[]{w[0], w[1]};
        } else {
            updateDrawing(x, y);
        }
        return true;
    }

    private void updateDrawing(int x, int y) {
        if (drawing == null || drawStart == null) return;
        MapContext ctx = viewport.getContext();
        double[] w = MapProjection.screenToWorld(ctx, x, y);
        if (Math.hypot(x - drawStartSX, y - drawStartSY) >= 3) drawMoved = true;
        switch (drawing.type) {
            case RECT:
            case ELLIPSE: {
                double[] b = MarkupGeometry.boxFromDrag(drawStart[0], drawStart[1], w[0], w[1], shiftDown());
                drawing.set(0, b[0], b[1]);
                drawing.set(1, b[2], b[3]);
                break;
            }
            case LINE: {
                double[] a = MarkupGeometry.lineStart(drawStart[0], drawStart[1]);
                double[] e = MarkupGeometry.lineEnd(drawStart[0], drawStart[1], w[0], w[1], shiftDown());
                drawing.set(0, a[0], a[1]);
                drawing.set(1, e[0], e[1]);
                break;
            }
            case PENCIL:
                // mp11: one point per block entered; the rasteriser joins them with Bresenham, so there are no gaps
                if (lastPencil == null || Math.floor(w[0]) != Math.floor(lastPencil[0]) || Math.floor(w[1]) != Math.floor(lastPencil[1])) {
                    drawing.add(w[0], w[1]);
                    lastPencil = new double[]{w[0], w[1]};
                }
                break;
        }
    }

    private boolean markupMouseDrag(int x, int y, int button) {
        if (button == 1) {
            viewport.rotateMove(x, y);
            return true;
        }
        if (button == 2 || (button == 0 && tool == MarkupTool.PAN)) {
            viewport.dragMove(x, y);
            return true;
        }
        if (button != 0) return false;
        if (erasing) eraseAt(x, y);
        else updateDrawing(x, y);
        return true;
    }

    private boolean markupMouseUp(int x, int y, int button) {
        if (button == 1) {
            viewport.rotateEnd();
            return true;
        }
        if (button == 2 || (button == 0 && tool == MarkupTool.PAN)) {
            viewport.dragEnd();
            return true;
        }
        if (button != 0) return false;
        if (drawing != null) {
            updateDrawing(x, y);
            boolean keep = drawing.type == MarkupShape.Type.PENCIL || drawMoved;
            if (keep) markup.add(drawing);
        }
        cancelDrawing();
        return true;
    }

    /** Block eraser (mp11): removes the blocks under it (brush = thickness). Shape eraser (mp12): removes whole shapes it touches. */
    private void eraseAt(int x, int y) {
        MapContext ctx = viewport.getContext();
        int[] b = MapProjection.screenToBlock(ctx, x, y);
        java.util.Set<Long> cells = new java.util.HashSet<>();
        int brush = tool == MarkupTool.ERASER_SHAPE ? MarkupGeometry.shapeEraserBrush(MapProjection.pixelsPerBlock(ctx)) : markupWidth;
        if (lastErase == null) MarkupRaster.brush(b[0], b[1], brush, cells);
        else MarkupRaster.line(lastErase[0], lastErase[1], b[0], b[1], brush, cells);
        lastErase = b;
        if (tool == MarkupTool.ERASER_SHAPE) markup.eraseShapes(viewDim, cells);
        else markup.erase(viewDim, cells);
    }

    /** Markup mode keys; true = handled (not passed on to the map). */
    private boolean markupKey(int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            exitMarkup();
            return true;
        }
        boolean ctrl = ctrlDown();
        if (ctrl && keyCode == Keyboard.KEY_Z) {
            cancelDrawing();
            markup.undo();
            return true;
        }
        if (ctrl && keyCode == Keyboard.KEY_S) {
            saveMarkup();
            return true;
        }
        if (ctrl) return false;
        for (MarkupTool t : MarkupTool.values()) {
            if (keyCode == t.key) {
                selectTool(t);
                return true;
            }
        }
        if (keyCode == Keyboard.KEY_F) {
            markupFill = !markupFill;
            buildButtons();
            return true;
        }
        if (keyCode == Keyboard.KEY_LBRACKET || keyCode == Keyboard.KEY_RBRACKET) {
            markupWidth = keyCode == Keyboard.KEY_RBRACKET ? MarkupToolbar.nextWidth(markupWidth) : MarkupToolbar.prevWidth(markupWidth);
            buildButtons();
            showToast("Thickness: " + markupWidth + (markupWidth == 1 ? " block" : " blocks"));
            return true;
        }
        return false;
    }

    private void renderDebugInfo(MapContext ctx) {
        if (!Settings.debugInfo) return;


        GL11.glEnable(GL11.GL_TEXTURE_2D);

        int y = 30;
        painter.drawString("Canvas", 5, y, 0xFFFFFF);
        y += 10;
        painter.drawString(String.format("Size: %d x %d", ctx.canvasW, ctx.canvasH), 5, y, 0xFFFFFF);
        y += 10;
        painter.drawString(String.format("Scroll: %.2f, %.2f", ctx.scrollX, ctx.scrollY), 5, y, 0xFFFFFF);
        y += 10;
        painter.drawString(String.format("Zoom: %.2f", ctx.zoom), 5, y, 0xFFFFFF);

        y += 15;
        painter.drawString("Cache", 5, y, 0xFFFFFF);
        y += 10;
        painter.drawString(String.format("Cache size: r=%d, t=%d", NARegionPixelCache.get().getRegionCount(), NARegionPixelCache.get().getTotalCount()), 5, y, 0xFFFFFF);
        y += 10;
        painter.drawString(String.format("Dirty Queue Size: %d", NARegionPixelCache.get().getDirtyQueueSize()), 5, y, 0xFFFFFF);
        y += 10;
    }
}
