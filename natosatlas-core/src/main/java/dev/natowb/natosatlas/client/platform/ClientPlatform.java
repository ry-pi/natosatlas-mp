package dev.natowb.natosatlas.client.platform;

public abstract class ClientPlatform {
    public final NAPainter painter;
    public final ScreenAccess screen;

    public ClientPlatform(NAPainter painter, ScreenAccess screen) {
        this.painter = painter;
        this.screen = screen;
    }

    /** mp7: show a line in the in-game chat (main thread). Platforms without chat just log it. */
    public void chat(String message) {
        dev.natowb.natosatlas.core.util.LogUtil.info("[Chat] {}", message);
    }
}
