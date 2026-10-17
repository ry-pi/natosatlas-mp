package dev.natowb.natosatlas.client;

import dev.natowb.natosatlas.core.util.LogUtil;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * mp8: opens a folder in the OS file manager without blocking or crashing the game.
 * Runs on a daemon thread. Order: the OS's own opener (explorer.exe / open / xdg-open), then
 * java.awt.Desktop (skipped when headless), then LWJGL's Sys.openURL("file://...") like vanilla's
 * "Open texture pack folder". The outcome is shown through {@link #getStatus()}.
 */
public final class FolderOpener {
    private static volatile String status = "";
    private static volatile long statusUntil = 0;

    private FolderOpener() {}

    /** Status line for the last attempt; empty once it is older than 8 seconds. */
    public static String getStatus() {
        return System.currentTimeMillis() < statusUntil ? status : "";
    }

    private static void setStatus(String s) {
        status = s;
        statusUntil = System.currentTimeMillis() + 8000;
    }

    public static void open(File dir) {
        if (dir == null) { setStatus("\u00a7cNo world loaded, nothing to open"); return; }
        final File target = dir.getAbsoluteFile();
        if (!target.isDirectory() && !target.mkdirs() && !target.isDirectory()) {
            setStatus("\u00a7cCouldn't create " + target.getPath());
            return;
        }
        setStatus("Opening " + target.getPath());
        Thread t = new Thread(() -> {
            String how = tryOpen(target);
            if (how != null) {
                LogUtil.info("[Folder] Opened {} via {}", target, how);
                setStatus("Opened " + target.getPath());
            } else {
                LogUtil.warn("[Folder] Couldn't open {} in a file manager", target);
                setStatus("\u00a7cCouldn't open it, go to: \u00a7f" + target.getPath());
            }
        }, "NA-OpenFolder");
        t.setDaemon(true);
        t.start();
    }

    /** @return the method that worked, or null. */
    static String tryOpen(File dir) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String path = dir.getAbsolutePath();
        try {
            if (os.contains("win")) {
                // explorer.exe always exits with 1, so only a failed start counts as failure
                // explorer treats '/' as a switch prefix, so make sure the path only uses '\\'
                new ProcessBuilder("explorer.exe", path.replace('/', '\\')).start();
                return "explorer.exe";
            }
            if (os.contains("mac")) {
                if (runOk("open", path)) return "open";
            } else if (runOk("xdg-open", path)) {
                return "xdg-open";
            }
        } catch (Throwable e) {
            LogUtil.warn("[Folder] OS opener failed: {}", e.toString());
        }
        try {
            if (!java.awt.GraphicsEnvironment.isHeadless() && java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop d = java.awt.Desktop.getDesktop();
                if (d.isSupported(java.awt.Desktop.Action.OPEN)) {
                    d.open(dir);
                    return "java.awt.Desktop";
                }
            }
        } catch (Throwable e) {
            LogUtil.warn("[Folder] Desktop.open failed: {}", e.toString());
        }
        try {
            // LWJGL 2, like vanilla's texture pack folder button; reflection so a missing class can't crash us
            Class.forName("org.lwjgl.Sys").getMethod("openURL", String.class).invoke(null, "file://" + path);
            return "Sys.openURL";
        } catch (Throwable e) {
            LogUtil.warn("[Folder] Sys.openURL failed: {}", e.toString());
        }
        return null;
    }

    private static boolean runOk(String cmd, String path) throws Exception {
        Process p = new ProcessBuilder(cmd, path).redirectErrorStream(true).start();
        p.getOutputStream().close();
        // drain output so the child can't block on a full pipe
        Thread drain = new Thread(() -> {
            try (java.io.InputStream in = p.getInputStream()) {
                byte[] b = new byte[1024];
                while (in.read(b) >= 0) { /* discard */ }
            } catch (Exception ignored) {
            }
        }, "NA-OpenFolder-out");
        drain.setDaemon(true);
        drain.start();
        if (!p.waitFor(10, TimeUnit.SECONDS)) return true; // still running = it's showing the folder
        return p.exitValue() == 0;
    }
}
