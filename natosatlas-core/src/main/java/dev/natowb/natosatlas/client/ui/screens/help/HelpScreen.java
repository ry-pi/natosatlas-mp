package dev.natowb.natosatlas.client.ui.screens.help;

import dev.natowb.natosatlas.client.NAClient;
import dev.natowb.natosatlas.client.ui.elements.UIScaleInfo;
import dev.natowb.natosatlas.client.ui.themes.UITheme;
import dev.natowb.natosatlas.client.ui.elements.UIElementIconButton;
import dev.natowb.natosatlas.client.ui.elements.UIScreen;
import dev.natowb.natosatlas.client.platform.NAPainter;

import static dev.natowb.natosatlas.client.texture.TextureProvider.ICON_BACK;

public class HelpScreen extends UIScreen {

    private final NAPainter painter = NAClient.get().getPlatform().painter;

    private int headerY;

    String[][] entries = {
            {"[L Mouse Drag]", "Pan the map"},
            {"[R Mouse Drag]", "Rotate the map"},
            {"[L Mouse DBClick]", "Create Waypoint"},
            {"[R Mouse DBClick]", "Temp Waypoint"},
            {"[Mouse Wheel]", "Zoom in/out"},
            {"[Space]", "Reset camera"},
            {"[P] / [Shift+P]", "Save map image (full / small)"},
            {"[Middle Mouse Drag]", "Pan the map"},
            {"[D]", "Show Overworld / Nether map"},
            {"[B]", "Markup mode (draw on the map)"},
            {"Markup [1]-[6]", "Pan, Rect, Ellipse, Line, Pencil, Eraser"},
            {"Markup [Shift]", "Square / circle / 45 degree line"},
            {"Markup [F], [ and ]", "Fill on/off, thickness (blocks)"},
            {"Markup [Ctrl+Z]", "Undo"},
            {"Markup [Ctrl+S]", "Save map + markup as PNG"},
            {"Markup [Esc]", "Close markup and forget it"}
    };

    private int lineH = 18; // mp9: tighter rows when the screen is short

    public HelpScreen(UIScreen parent) {
        super(parent);
    }

    @Override
    public void init(int width, int height) {
        super.init(width, height);

        int headerHeight = 20;
        int headerGap = 10;
        lineH = Math.max(10, Math.min(18, (height - 80) / entries.length));
        int contentHeight = entries.length * lineH + 40;

        int totalHeight = headerHeight + headerGap + contentHeight;
        int contentTop = (height - totalHeight) / 2;

        headerY = contentTop;

        UIElementIconButton closeButton = new UIElementIconButton(200, width / 2 - 100, headerY, 20, 20, ICON_BACK);
        closeButton.setHandler(btn -> NAClient.get().getPlatform().screen.openNacScreen(parent));
        addButton(closeButton);
    }

    @Override
    public void render(int mouseX, int mouseY, float delta, UIScaleInfo scaleInfo) {
        painter.drawRect(0, 0, width, height, UITheme.PANEL_BG);

        int centerX = width / 2;
        int y = headerY + 4;

        painter.drawCenteredString("Help & Controls", centerX, y, UITheme.TITLE_TEXT);
        y += 30;

        int maxKeyWidth = 0;
        for (String[] e : entries) {
            int w = painter.getStringWidth(e[0]);
            if (w > maxKeyWidth) maxKeyWidth = w;
        }

        int gap = 12;

        for (String[] e : entries) {
            String key = e[0];
            String desc = e[1];

            int keyWidth = painter.getStringWidth(key);

            int keyRight = centerX - (gap / 2);
            int keyX = keyRight - keyWidth;

            int descX = centerX + (gap / 2);

            painter.drawString(key, keyX, y, 0xCCCCCC);
            painter.drawString(desc, descX, y, 0xCCCCCC);

            y += lineH;
        }

        super.render(mouseX, mouseY, delta, scaleInfo);
    }
}