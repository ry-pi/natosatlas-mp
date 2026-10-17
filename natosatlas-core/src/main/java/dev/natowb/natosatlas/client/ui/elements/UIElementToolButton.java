package dev.natowb.natosatlas.client.ui.elements;

import dev.natowb.natosatlas.client.NAClient;
import dev.natowb.natosatlas.client.platform.NAPainter;
import dev.natowb.natosatlas.client.ui.themes.UIThemeMinecraft;

import java.util.function.BooleanSupplier;

/** mp9: the map's square icon button, plus a highlighted frame while it is the selected tool / option. */
public class UIElementToolButton extends UIElementIconButton {

    private final BooleanSupplier selected;

    public UIElementToolButton(int id, int x, int y, int w, int h, int iconIndex, BooleanSupplier selected) {
        super(id, x, y, w, h, iconIndex);
        this.selected = selected;
    }

    public boolean isSelected() {
        return selected != null && selected.getAsBoolean();
    }

    @Override
    public void render(int mouseX, int mouseY) {
        boolean sel = isSelected();
        if (sel) {
            // pressed look: the hover background, then the icon, then a bright frame
            drawButtonBackground(active ? 2 : 0);
            super.renderIconOnly();
            NAPainter p = NAClient.get().getPlatform().painter;
            int c = UIThemeMinecraft.TEXT_HOVER;
            p.drawRect(x, y, x + w, y + 1, c);
            p.drawRect(x, y + h - 1, x + w, y + h, c);
            p.drawRect(x, y, x + 1, y + h, c);
            p.drawRect(x + w - 1, y, x + w, y + h, c);
        } else {
            super.render(mouseX, mouseY);
        }
    }
}
