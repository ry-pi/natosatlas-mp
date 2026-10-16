package dev.natowb.natosatlas.client;

import dev.natowb.natosatlas.client.platform.ClientWorldAccess;
import dev.natowb.natosatlas.core.NAPaths;
import dev.natowb.natosatlas.core.data.NAWorldInfo;
import dev.natowb.natosatlas.core.util.FileUtil;
import dev.natowb.natosatlas.core.util.LogUtil;

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 * MP patch (mp6): optional per-world / per-server seed override for the slime chunk overlay.
 * Stored in natosatlas/servers/&lt;server&gt;/slime_seed.txt (or natosatlas/worlds/&lt;world&gt;/slime_seed.txt).
 * Empty = use the seed the client got from the world/server (original behaviour).
 */
public final class SlimeSeed {
    private static final String FILE_NAME = "slime_seed.txt";

    private static String text = "";
    private static Long custom = null;
    private static Path loadedFrom = null;

    private SlimeSeed() {
    }

    /** Same conversion as the b1.7.3 Create World screen: a number is used as-is, anything else uses String.hashCode(). */
    public static Long parse(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty()) return null;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return (long) s.hashCode();
        }
    }

    private static File file() {
        Path dir = NAPaths.getWorldDataPath();
        return dir == null ? null : new File(dir.toFile(), FILE_NAME);
    }

    /** Called on world join (paths already set). */
    public static void load() {
        text = "";
        custom = null;
        loadedFrom = NAPaths.getWorldDataPath();
        File f = file();
        if (f == null || !f.exists()) return;
        try {
            List<String> lines = FileUtil.readLines(f);
            for (String line : lines) {
                if (line == null) continue;
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("#")) continue;
                if (t.startsWith("seed=")) t = t.substring(5).trim();
                text = t;
                custom = parse(t);
                break;
            }
            LogUtil.info("Loaded slime seed override '{}' -> {}", text, custom);
        } catch (Exception e) {
            LogUtil.warn("Failed to read slime seed from {}", f);
        }
    }

    private static void ensureLoaded() {
        Path cur = NAPaths.getWorldDataPath();
        if (cur != null && !cur.equals(loadedFrom)) load();
    }

    /** Set the override (empty/blank clears it) and save immediately. */
    public static void set(String newText) {
        ensureLoaded();
        String t = newText == null ? "" : newText.trim();
        if (t.equals(text)) return;
        text = t;
        custom = parse(t);
        File f = file();
        if (f == null) return;
        try {
            if (t.isEmpty()) {
                if (f.exists() && !f.delete()) FileUtil.writeLines(f, Collections.emptyList());
            } else {
                FileUtil.writeLines(f, Collections.singletonList(t));
            }
        } catch (Exception e) {
            LogUtil.warn("Failed to save slime seed to {}", f);
        }
    }

    public static String getText() {
        ensureLoaded();
        return text;
    }

    public static boolean isCustom() {
        ensureLoaded();
        return custom != null;
    }

    /** Seed the client received from the world/server (0 if no world). */
    public static long getWorldSeed() {
        ClientWorldAccess access = ClientWorldAccess.get();
        NAWorldInfo info = access == null ? null : access.getWorldInfo();
        return info == null ? 0L : info.getSeed();
    }

    /** Seed used for the slime overlay: custom override if set, otherwise the world seed. */
    public static long getEffectiveSeed() {
        ensureLoaded();
        return custom != null ? custom : getWorldSeed();
    }

    /** "custom seed 123" / "server seed 123" / "world seed 123" */
    public static String describe() {
        if (isCustom()) return "custom seed " + getEffectiveSeed();
        ClientWorldAccess access = ClientWorldAccess.get();
        NAWorldInfo info = access == null ? null : access.getWorldInfo();
        boolean mp = info != null && info.isMultiplayer();
        return (mp ? "server seed " : "world seed ") + getWorldSeed();
    }
}
