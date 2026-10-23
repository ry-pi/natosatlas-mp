package dev.natowb.natosatlas.client.ui.screens.settings;

import dev.natowb.natosatlas.core.util.LogUtil;
import dev.natowb.natosatlas.core.NAPaths;

import java.io.File;

public final class Settings {

    private static final SettingsStorage STORAGE = new SettingsStorage();

    public enum EntityDisplayMode {All, Player, Nothing}

    public enum MapRenderMode {Day, Night, Cave, Auto}

    public enum BlockColors {Classic, TrueColor}

    public enum MinimapPosition {TopLeft, TopRight, BottomLeft, BottomRight}


    // World map settings
    public static BlockColors blockColors = BlockColors.Classic;
    public static boolean mapGrid = true;
    public static boolean debugInfo = false;
    public static boolean showSlimeChunks = false;
    public static float defaultZoom = 0.5f;
    public static EntityDisplayMode entityDisplayMode = EntityDisplayMode.All;
    public static MapRenderMode mapRenderMode = MapRenderMode.Auto;


    // mp13: server folders for the Import tab (region folders, or the world folder holding them)
    public static String importOverworldPath = "";
    public static String importNetherPath = "";

    // mp14: world map spawner markers (Off / All / one mob type) and the Layer view's Y level
    public static String spawnerFilter = "All";
    public static int layerY = 40;


    // Minimap settings
    public static boolean minimapRotateWithPlayer = false;
    public static boolean minimapEnabled = false;
    public static float minimapZoom = 0.5f;
    public static float minimapScale = 1.0f;
    public static MinimapPosition minimapPosition = MinimapPosition.TopRight;
    public static EntityDisplayMode minimapEntityDisplayMode = EntityDisplayMode.All;
    public static MapRenderMode minimapRenderMode = MapRenderMode.Auto;


    private Settings() {
    }

    public static void load() {
        STORAGE.load(new File(NAPaths.getDataPath().toFile(), "settings.txt"));
        LogUtil.debug("Loaded settings");
    }

    public static void save() {
        STORAGE.save(new File(NAPaths.getDataPath().toFile(), "settings.txt"));
        LogUtil.debug("Saved settings");
    }
}
