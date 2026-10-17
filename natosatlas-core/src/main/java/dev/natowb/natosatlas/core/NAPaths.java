package dev.natowb.natosatlas.core;

import dev.natowb.natosatlas.core.util.LogUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class NAPaths {
    private static Path mcPath;
    private static Path dataPath;
    private static Path worldDataPath;
    private static Path worldSavePath;
    private static String worldSaveName;


    public static String getWorldSaveName() {
        return worldSaveName;
    }

    public static Path getMinecraftPath() {
        return mcPath;
    }

    public static void setBasePaths(Path _mcPath) {
        mcPath = _mcPath;
        dataPath = ensurePathExists(mcPath.resolve("natosatlas"));
        LogUtil.info("Set mcPath to {}", mcPath);
        LogUtil.info("Set dataPath to {}", dataPath);
    }

    /** MP patch: make a server name safe as a folder name on every OS (IPv6 ':', '%', '/', '..', non-ASCII). */
    public static String sanitizeFolderName(String name) {
        if (name == null || name.isEmpty()) return "unknown-server";
        String s = name.replaceAll("[^A-Za-z0-9._-]", "_");
        s = s.replaceAll("^\\.+", "_").replaceAll("[. ]+$", "_");
        if (s.length() > 100) s = s.substring(0, 100);
        return s.isEmpty() ? "unknown-server" : s;
    }

    public static void setWorldPaths(String saveName, boolean isMultiplayerWorld) {
        if (isMultiplayerWorld) saveName = sanitizeFolderName(saveName);
        worldSaveName = saveName;
        if (isMultiplayerWorld) {
            worldDataPath = ensurePathExists(dataPath.resolve(String.format("servers/%s/", saveName)));
            worldSavePath = worldDataPath;
        } else {
            worldDataPath = ensurePathExists(dataPath.resolve(String.format("worlds/%s/", saveName)));
            worldSavePath = ensurePathExists(mcPath.resolve("saves/" + saveName));
        }
        LogUtil.info("Set worldDataPath to {}", worldDataPath);
        LogUtil.info("Set worldSavePath to {}", worldSavePath);
    }

    public static Path ensurePathExists(Path path) {

        if (Files.exists(path)) {
            return path;
        }

        try {
            Files.createDirectories(path);
            LogUtil.debug("Created directories for {}", path);
        } catch (IOException e) {
            LogUtil.error("Failed to create directories for {}", path);
        }
        return path;
    }

    public static Path getDataPath() {
        return dataPath;
    }

    public static Path getWorldDataPath() {
        return worldDataPath;
    }

    public static Path getWorldSavePath() {
        return worldSavePath;
    }

    /** mp7: the folder holding every DIMn/layer_n tile folder of the current world (not created). */
    public static Path getWorldMapRootPath(boolean useSaveFolder) {
        Path base = useSaveFolder ? (worldSavePath == null ? null : worldSavePath.resolve("natosatlas")) : worldDataPath;
        return base == null ? null : base.resolve("regions");
    }

    public static Path getWorldMapStoragePath(int layerId, int dim, boolean useSaveFolder) {
        Path basePath;
        if (useSaveFolder) {
            basePath = worldSavePath.resolve("natosatlas");
        } else {
            basePath = worldDataPath;
        }
        return ensurePathExists(basePath.resolve(String.format("regions/DIM%d/layer_%d", dim, layerId)));
    }
}
