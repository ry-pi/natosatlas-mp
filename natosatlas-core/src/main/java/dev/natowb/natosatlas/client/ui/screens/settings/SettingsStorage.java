package dev.natowb.natosatlas.client.ui.screens.settings;

import dev.natowb.natosatlas.core.storage.TextStorage;


public final class SettingsStorage extends TextStorage {


    @Override
    protected String getName() {
        return "Settings";
    }

    @Override
    protected void onLoad() {
        Settings.blockColors = getEnum("blockColors", Settings.BlockColors.class, Settings.BlockColors.Classic);
        Settings.mapGrid = getBoolean("mapGrid", true);
        Settings.debugInfo = getBoolean("debugInfo", false);
        Settings.defaultZoom = getFloat("defaultZoom", 1f);
        Settings.entityDisplayMode = getEnum("entityDisplayMode", Settings.EntityDisplayMode.class, Settings.EntityDisplayMode.Player);
        Settings.mapRenderMode = getEnum("mapRenderMode", Settings.MapRenderMode.class, Settings.MapRenderMode.Auto);
        Settings.showSlimeChunks = getBoolean("showSlimeChunks", false);

        Settings.minimapRotateWithPlayer = getBoolean("minimapRotateWithPlayer", false);
        Settings.minimapZoom = getFloat("minimapZoom", 1f);
        Settings.minimapEntityDisplayMode = getEnum("minimapEntityDisplayMode", Settings.EntityDisplayMode.class, Settings.EntityDisplayMode.Player);
        Settings.minimapRenderMode = getEnum("minimapRenderMode", Settings.MapRenderMode.class, Settings.MapRenderMode.Auto);
        Settings.minimapEnabled = getBoolean("minimapEnabled", false);
        Settings.minimapPosition = getEnum("minimapPosition", Settings.MinimapPosition.class, Settings.MinimapPosition.TopRight);
        Settings.minimapScale = getFloat("minimapScale", 1f);
        Settings.importOverworldPath = getString("importOverworldPath", "");
        Settings.importNetherPath = getString("importNetherPath", "");
        Settings.spawnerFilter = getString("spawnerFilter", "All");
        Settings.layerY = Math.max(0, Math.min(127, getInt("layerY", 40)));
        Settings.hiddenButtonKeys = getBoolean("hiddenButtonKeys", true);
        for (MapButtons b : MapButtons.values()) b.shown = getBoolean("showButton." + b.key, true);


    }

    @Override
    protected void onSave() {
        put("blockColors", Settings.blockColors.name());
        put("mapGrid", Settings.mapGrid);
        put("debugInfo", Settings.debugInfo);
        put("defaultZoom", Settings.defaultZoom);
        put("entityDisplayMode", Settings.entityDisplayMode.name());
        put("mapRenderMode", Settings.mapRenderMode.name());
        put("showSlimeChunks", Settings.showSlimeChunks);

        put("minimapEnabled", Settings.minimapEnabled);
        put("minimapRotateWithPlayer", Settings.minimapRotateWithPlayer);
        put("minimapZoom", Settings.minimapZoom);
        put("minimapEntityDisplayMode", Settings.minimapEntityDisplayMode);
        put("minimapRenderMode", Settings.minimapRenderMode);
        put("minimapScale", Settings.minimapScale);
        put("minimapPosition", Settings.minimapPosition);
        put("importOverworldPath", Settings.importOverworldPath.replace("\n", "").replace("\r", ""));
        put("importNetherPath", Settings.importNetherPath.replace("\n", "").replace("\r", ""));
        put("spawnerFilter", Settings.spawnerFilter);
        put("layerY", Settings.layerY);
        put("hiddenButtonKeys", Settings.hiddenButtonKeys);
        for (MapButtons b : MapButtons.values()) put("showButton." + b.key, b.shown);
    }
}
