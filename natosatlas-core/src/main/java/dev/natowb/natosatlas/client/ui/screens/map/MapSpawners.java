package dev.natowb.natosatlas.client.ui.screens.map;

import dev.natowb.natosatlas.core.io.McRegionReader.Spawner;

import java.util.List;

/** mp14: spawner marker colours, the filter cycle and hover lookup (pure, unit tested headless). */
public final class MapSpawners {
    private MapSpawners() {}

    /** Button cycle: Off, All, then the four b1.7.3 dungeon/mob types. */
    public static final String[] FILTERS = {"Off", "All", "Spider", "Zombie", "Skeleton", "Pig"};

    public static String next(String f) {
        for (int i = 0; i < FILTERS.length; i++) if (FILTERS[i].equalsIgnoreCase(f)) return FILTERS[(i + 1) % FILTERS.length];
        return "All";
    }

    public static boolean shows(String filter, String mob) {
        if (filter == null || filter.equalsIgnoreCase("Off")) return false;
        return filter.equalsIgnoreCase("All") || filter.equalsIgnoreCase(mob);
    }

    public static int color(String mob) {
        if (mob == null) return 0xFFFFFFFF;
        switch (mob) {
            case "Spider": return 0xFFE03030;
            case "Zombie": return 0xFF30C040;
            case "Skeleton": return 0xFFE8E8E8;
            case "Pig": return 0xFFFF9EC0;
            case "CaveSpider": return 0xFF2090A0;
            case "Creeper": return 0xFF80FF40;
            default: return 0xFFFFD040;
        }
    }

    /** Closest shown spawner within radius GUI pixels of the mouse, or null. */
    public static Spawner hovered(MapContext ctx, List<Spawner> list, String filter, int mx, int my, double radius) {
        Spawner best = null;
        double bd = radius * radius;
        for (Spawner s : list) {
            if (!shows(filter, s.mob)) continue;
            double[] p = MapProjection.worldToScreen(ctx, s.x + 0.5, s.z + 0.5);
            double dx = p[0] - mx, dy = p[1] - my, d = dx * dx + dy * dy;
            if (d <= bd) { bd = d; best = s; }
        }
        return best;
    }
}
