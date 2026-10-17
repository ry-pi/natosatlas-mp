package dev.natowb.natosatlas.core.io;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * mp7: removes the stored map tiles of one world/server. Only the regions/ folder (every DIMn/layer_n
 * tile) is touched; import/, slime_seed.txt, waypoint .points files and settings live next to it and stay.
 */
public final class MapDataFiles {
    public static final String TRASH_PREFIX = "regions.deleting-";

    private MapDataFiles() {
    }

    /**
     * Moves regionsDir out of the way in one rename (so nothing can load a stale tile from it any more)
     * and returns the moved folder, which the caller deletes with {@link #deleteTree}. If the rename
     * fails (e.g. Windows file lock) it deletes in place and returns null. Returns null if there was nothing.
     */
    public static Path detach(Path regionsDir) throws IOException {
        if (regionsDir == null || !Files.isDirectory(regionsDir)) return null;
        Path trash = regionsDir.resolveSibling(TRASH_PREFIX + System.currentTimeMillis());
        try {
            Files.move(regionsDir, trash);
            return trash;
        } catch (IOException e) {
            deleteTree(regionsDir);
            return null;
        }
    }

    /** Leftover regions.deleting-* folders from a delete that was cut short (game closed mid-delete). */
    public static File[] leftoverTrash(Path base) {
        File[] fs = base == null ? null : base.toFile().listFiles((d, n) -> n.startsWith(TRASH_PREFIX));
        return fs == null ? new File[0] : fs;
    }

    /** Recursively deletes dir. Returns how many files were removed. */
    public static int deleteTree(Path dir) throws IOException {
        if (dir == null || !Files.exists(dir)) return 0;
        final int[] count = {0};
        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                count[0]++;
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                if (exc != null) throw exc;
                Files.delete(d);
                return FileVisitResult.CONTINUE;
            }
        });
        return count[0];
    }
}
