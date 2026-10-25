    package dev.natowb.natosatlas.client.ui.screens.settings;

    import dev.natowb.natosatlas.client.NAClient;
    import dev.natowb.natosatlas.client.platform.ClientWorldAccess;
    import dev.natowb.natosatlas.client.ui.screens.generate.GenerateScreen;
    import dev.natowb.natosatlas.client.ui.screens.map.MapConfig;
    import dev.natowb.natosatlas.client.platform.NAPainter;
    import dev.natowb.natosatlas.client.ui.elements.*;
    import dev.natowb.natosatlas.client.ui.themes.UITheme;
    import dev.natowb.natosatlas.client.ui.layout.UILayout;
    import dev.natowb.natosatlas.client.ui.layout.UIVerticalLayout;

    import static dev.natowb.natosatlas.client.texture.TextureProvider.ICON_BACK;

    public class SettingsScreen extends UIScreen {

        private int headerY;

        // mp15: General is gone; its buttons moved to World map, Import and the new Debug tab
        private enum Tab {
            ATLAS,
            MINIMAP,
            IMPORT,
            PREFERENCES, // mp16
            DEBUG
        }

        private Tab activeTab = Tab.ATLAS;

        public SettingsScreen(UIScreen parent) {
            super(parent);
        }

        private UIElementButton worldMapTab;
        private UIElementButton minimapTab;
        private UIElementButton importTab;
        private UIElementButton debugTab;
        private UIElementButton prefsTab;


        @Override
        public void init(int width, int height) {
            super.init(width, height);

            int headerHeight = 20;
            int headerGap = 10;
            int listHeight = height - 140;

            int totalHeight = headerHeight + headerGap + listHeight;
            int contentTop = (height - totalHeight) / 2;

            headerY = contentTop;

            UIElementIconButton closeButton =
                    new UIElementIconButton(101, width / 2 - 100, headerY, 20, 20, ICON_BACK);
            closeButton.setHandler(btn -> {
                NAClient.get().getPlatform().screen.openNacScreen(parent);
            });
            addButton(closeButton);

            int tabY = headerY + headerHeight + 5;

            // mp13: four tabs (Import added), a little narrower so they still fit small windows
            // mp16: five tabs; narrower on small screens (GUI width 320)
            int tabSpacing = 4;
            int tabButtonWidth = Math.min(72, (width - 12 - tabSpacing * 4) / 5);
            int tabButtonHeight = 20;

            int tabWidth = (tabButtonWidth * 5) + (tabSpacing * 4);

            int tabX = (width - tabWidth) / 2;

            worldMapTab = new UIElementButton(202, tabX, tabY, tabButtonWidth, tabButtonHeight, "World map");
            worldMapTab.setHandler(btn -> switchTab(Tab.ATLAS));
            addButton(worldMapTab);

            minimapTab = new UIElementButton(203, tabX + tabButtonWidth + tabSpacing, tabY, tabButtonWidth, tabButtonHeight, "Minimap");
            minimapTab.setHandler(btn -> switchTab(Tab.MINIMAP));
            addButton(minimapTab);

            importTab = new UIElementButton(204, tabX + (tabButtonWidth + tabSpacing) * 2, tabY, tabButtonWidth, tabButtonHeight, "Import");
            importTab.setHandler(btn -> switchTab(Tab.IMPORT));
            addButton(importTab);

            prefsTab = new UIElementButton(206, tabX + (tabButtonWidth + tabSpacing) * 3, tabY, tabButtonWidth, tabButtonHeight, "Preferences");
            prefsTab.setHandler(btn -> switchTab(Tab.PREFERENCES));
            addButton(prefsTab);

            debugTab = new UIElementButton(205, tabX + (tabButtonWidth + tabSpacing) * 4, tabY, tabButtonWidth, tabButtonHeight, "Debug");
            debugTab.setHandler(btn -> switchTab(Tab.DEBUG));
            addButton(debugTab);

            updateTabActiveStates();


            rebuildContent();
        }

        @Override
        public void onClose() {
            applySlimeSeed();
            applyImportPaths();
            Settings.save();
        }

        // mp6: slime seed override field (World map tab)
        private UIElementTextField slimeSeedField;

        private void applySlimeSeed() {
            if (slimeSeedField != null) dev.natowb.natosatlas.client.SlimeSeed.set(slimeSeedField.getText());
        }

        @Override
        public void tick() {
            super.tick();
            applySlimeSeed(); // live: the field's text is the override (blank = server seed); saved only when it changes
            applyImportPaths();
            if (importButton != null) importButton.label = dev.natowb.natosatlas.client.RegionImporter.get().isRunning() ? "Stop import" : "Import";
            if (deleteButton != null && deleteArmedUntil != 0 && System.currentTimeMillis() >= deleteArmedUntil) {
                deleteArmedUntil = 0;
                deleteButton.label = DELETE_LABEL;
            }
        }

        @Override
        public void keyPressed(char character, int keyCode) {
            boolean wasFocused = slimeSeedField != null && slimeSeedField.focused;
            boolean enter = character == '\r' || character == '\n' || keyCode == org.lwjgl.input.Keyboard.KEY_RETURN;
            for (UIElementTextField tf : new UIElementTextField[]{overworldPathField, netherPathField}) {
                if (tf != null && tf.focused && enter) { tf.setFocused(false); applyImportPaths(); return; }
            }
            super.keyPressed(character, keyCode);
            if (wasFocused && (character == '\r' || character == '\n' || keyCode == org.lwjgl.input.Keyboard.KEY_RETURN)) {
                applySlimeSeed();
                slimeSeedField.setFocused(false);
            }
        }

        private void switchTab(Tab tab) {
            this.activeTab = tab;
            updateTabActiveStates();
            rebuildContent();
        }

        private void updateTabActiveStates() {
            debugTab.active = !(activeTab == Tab.DEBUG);
            prefsTab.active = !(activeTab == Tab.PREFERENCES);
            worldMapTab.active = !(activeTab == Tab.ATLAS);
            minimapTab.active = !(activeTab == Tab.MINIMAP);
            importTab.active = !(activeTab == Tab.IMPORT);
        }

        private void rebuildContent() {
            applySlimeSeed();
            applyImportPaths();
            buttons.removeIf(b -> b.id >= 300);
            sliders.removeIf(s -> s.id >= 300);
            textFields.clear();
            slimeSeedField = null;
            overworldPathField = netherPathField = null;
            importButton = null;
            deleteButton = null;

            int listTop = headerY + 50;
            UILayout layout = new UIVerticalLayout(width / 2, listTop, 5);

            switch (activeTab) {
                case ATLAS:
                    buildAtlasSettings(layout);
                    break;

                case MINIMAP:
                    buildMinimapSettings(layout);
                    break;

                case IMPORT:
                    buildImportSettings();
                    break;

                case PREFERENCES:
                    buildPreferences();
                    break;

                case DEBUG:
                    buildDebugSettings();
                    break;
            }
        }

        /** mp16: Preferences: show/hide every world map button (left column: top-left row + the keybind option;
         *  right column: the top-right row, right to left). 16 px rows so all 8 fit at GUI height 270. */
        private void buildPreferences() {
            int rowH = 16, gap = 2, top = headerY + 62;
            int lx = width / 2 - 155, rx = width / 2 + 5;
            int li = 0, ri = 0, id = 400;
            for (MapButtons b : MapButtons.values()) {
                int x = b.topLeft ? lx : rx, y = top + (b.topLeft ? li++ : ri++) * (rowH + gap);
                UIElementButton btn = new UIElementButton(id++, x, y, 150, rowH, prefLabel("Show " + b.label, b.shown));
                btn.setHandler(x2 -> {
                    b.shown = !b.shown;
                    x2.label = prefLabel("Show " + b.label, b.shown);
                    Settings.save();
                });
                addButton(btn);
            }
            // mp17: markup layout (Segregated = left panel, Integrated = bar along the top)
            UIElementButton lay = new UIElementButton(id++, lx, top + li * (rowH + gap) + 12, 150, rowH, layoutLabel());
            lay.setHandler(x2 -> {
                Settings.markupIntegrated = !Settings.markupIntegrated;
                x2.label = layoutLabel();
                Settings.save();
            });
            addButton(lay);
            prefLayoutY = lay.y;
            UIElementButton keys = new UIElementButton(id, lx, lay.y + rowH + 14, 150, rowH,
                    prefLabel("Keys", Settings.hiddenButtonKeys));
            keys.setHandler(x2 -> {
                Settings.hiddenButtonKeys = !Settings.hiddenButtonKeys;
                x2.label = prefLabel("Keys", Settings.hiddenButtonKeys);
                Settings.save();
            });
            addButton(keys);
            prefKeysY = keys.y;
        }

        private int prefKeysY, prefLayoutY;

        private static String layoutLabel() {
            return "Layout: " + (Settings.markupIntegrated ? "Integrated" : "Segregated");
        }

        private static String prefLabel(String name, boolean on) {
            return name + ": " + (on ? "On" : "Off");
        }

        /** mp15: Debug tab: debug info option and "Delete all map data" (two-click confirm, mp7). */
        private void buildDebugSettings() {
            UILayout layout = new UIVerticalLayout(width / 2, headerY + 50, 5);
            UIElementOptionButton debugButton =
                    new UIElementOptionButton(SettingsOption.DEBUG_INFO, layout, 150, 20);
            debugButton.setHandler(btn -> debugButton.cycle());
            debugButton.id = 300;
            addButton(debugButton);

            deleteButton = new UIElementButton(315, layout, 150, 20, DELETE_LABEL);
            deleteArmedUntil = 0;
            deleteButton.setHandler(btn -> {
                long now = System.currentTimeMillis();
                if (now < deleteArmedUntil) {
                    deleteArmedUntil = 0;
                    btn.label = DELETE_LABEL;
                    dev.natowb.natosatlas.client.MapDataReset.deleteCurrentWorld();
                } else {
                    deleteArmedUntil = now + 4000;
                    btn.label = "\u00a7cClick again to confirm";
                }
            });
            addButton(deleteButton);
        }

        // mp13: Import tab: two wide path boxes (Overworld / Nether) and one Import button for both
        private UIElementTextField overworldPathField, netherPathField;
        private UIElementButton importButton;

        private int importBoxWidth() {
            return Math.max(200, Math.min(width - 40, 420));
        }

        private void applyImportPaths() {
            if (overworldPathField != null) Settings.importOverworldPath = overworldPathField.getText();
            if (netherPathField != null) Settings.importNetherPath = netherPathField.getText();
        }

        private void buildImportSettings() {
            int bw = importBoxWidth();
            int bx = width / 2 - bw / 2;
            // mp15: "Multiplayer" section (heading at importBase()), "Singleplayer" section below it
            int y = importBase() + 21;
            overworldPathField = new UIElementTextField(bx, y, bw, 20, Settings.importOverworldPath);
            overworldPathField.setMaxLength(1024);
            addTextField(overworldPathField);
            netherPathField = new UIElementTextField(bx, y + 33, bw, 20, Settings.importNetherPath);
            netherPathField.setMaxLength(1024);
            addTextField(netherPathField);

            dev.natowb.natosatlas.client.RegionImporter imp = dev.natowb.natosatlas.client.RegionImporter.get();
            boolean isMultiplayer = ClientWorldAccess.get().getWorldInfo() != null && ClientWorldAccess.get().getWorldInfo().isMultiplayer();
            importButton = new UIElementButton(320, width / 2 - 75, y + 57, 150, 20, imp.isRunning() ? "Stop import" : "Import");
            importButton.active = isMultiplayer;
            importButton.setHandler(btn -> {
                applyImportPaths();
                Settings.save();
                if (imp.isRunning()) imp.cancel();
                else {
                    boolean shift = org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_LSHIFT)
                            || org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_RSHIFT);
                    imp.startFromPaths(Settings.importOverworldPath, Settings.importNetherPath, shift);
                }
            });
            addButton(importButton);

            // mp15: moved from the old General tab; generating from the save only works in singleplayer
            UIElementButton existingButton = new UIElementButton(302, width / 2 - 75, importBase() + 136, 150, 20,
                    "Generate Existing");
            existingButton.active = !isMultiplayer;
            existingButton.setHandler(btn ->
                    NAClient.get().getPlatform().screen.openNacScreen(new GenerateScreen(this)));
            addButton(existingButton);
        }

        private int importBase() {
            return headerY + 50;
        }

        // mp7: two-click confirm for "Delete all map data"
        private static final String DELETE_LABEL = "Delete all map data";
        private UIElementButton deleteButton;
        private long deleteArmedUntil = 0;

        private void buildAtlasSettings(UILayout ignored) {
            // mp15: two columns; the map image buttons (from the old General tab) on the right
            UILayout layout = new UIVerticalLayout(width / 2 - 80, headerY + 50, 5);
            UILayout right = new UIVerticalLayout(width / 2 + 80, headerY + 50, 5);
            UIElementButton saveFull = new UIElementButton(313, right, 150, 20, "Save map image (full)");
            saveFull.setHandler(btn -> dev.natowb.natosatlas.client.io.WorldMapExport.start(false,
                    dev.natowb.natosatlas.client.io.WorldMapExport.currentLayer()));
            addButton(saveFull);

            UIElementButton saveSmall = new UIElementButton(314, right, 150, 20, "Save map image (small)");
            saveSmall.setHandler(btn -> dev.natowb.natosatlas.client.io.WorldMapExport.start(true,
                    dev.natowb.natosatlas.client.io.WorldMapExport.currentLayer()));
            addButton(saveSmall);

            UIElementSlider zoomSlider =
                    new UIElementSlider(303, layout, 150, 20, Settings.defaultZoom, "Default Zoom");
            zoomSlider.setRange(MapConfig.MIN_ZOOM, MapConfig.MAX_ZOOM);
            zoomSlider.setStep(0.01f);
            addSlider(zoomSlider);

            UIElementOptionButton colorsButton =
                    new UIElementOptionButton(SettingsOption.BLOCK_COLORS, layout, 150, 20);
            colorsButton.setHandler(btn -> colorsButton.cycle());
            colorsButton.id = 310;
            addButton(colorsButton);

            // mp6: slime seed override. Label is drawn in render() above the field.
            layout.next(150, 6);
            slimeSeedField = new UIElementTextField(layout, 150, 20, dev.natowb.natosatlas.client.SlimeSeed.getText());
            slimeSeedField.setMaxLength(32);
            addTextField(slimeSeedField);

            UIElementButton clearSeed = new UIElementButton(312, layout, 150, 20, "Use server seed");
            clearSeed.setHandler(btn -> {
                slimeSeedField = null; // don't let rebuildContent re-apply the old text
                dev.natowb.natosatlas.client.SlimeSeed.set("");
                rebuildContent();
            });
            addButton(clearSeed);
        }

        private void buildMinimapSettings(UILayout ignored) {
            UILayout left = new UIVerticalLayout(width / 2 - 80, headerY + 50, 5);
            UIElementOptionButton enableButton =
                    new UIElementOptionButton(SettingsOption.MINIMAP_ENABLED, left, 150, 20);
            enableButton.setHandler(btn -> enableButton.cycle());
            enableButton.id = 307;
            addButton(enableButton);

            UIElementOptionButton rotateButton =
                    new UIElementOptionButton(SettingsOption.MINIMAP_ROTATE, left, 150, 20);
            rotateButton.setHandler(btn -> rotateButton.cycle());
            rotateButton.id = 304;
            addButton(rotateButton);

            UIElementOptionButton entityButton =
                    new UIElementOptionButton(SettingsOption.MINIMAP_ENTITY_DISPLAY, left, 150, 20);
            entityButton.setHandler(btn -> entityButton.cycle());
            entityButton.id = 305;
            addButton(entityButton);

            UILayout right = new UIVerticalLayout(width / 2 + 80, headerY + 50, 5);

            UIElementOptionButton renderButton =
                    new UIElementOptionButton(SettingsOption.MINIMAP_MAP_RENDER_MODE, right, 150, 20);
            renderButton.setHandler(btn -> renderButton.cycle());
            renderButton.id = 306;
            addButton(renderButton);

            UIElementSlider minimapZoom =
                    new UIElementSlider(3008, right, 150, 20, Settings.minimapZoom, "Minimap Zoom");
            minimapZoom.setRange(0.25f, 1.0f);
            minimapZoom.setStep(0.25f);
            addSlider(minimapZoom);

            UIElementSlider minimapScale =
                    new UIElementSlider(3009, right, 150, 20, Settings.minimapScale, "Minimap Scale");
            minimapScale.setRange(0.5f, 1.0f);
            minimapScale.setStep(0.25f);
            addSlider(minimapScale);

            UIElementOptionButton mapPosition =
                    new UIElementOptionButton(SettingsOption.MINIMAP_POSITION, right, 150, 20);
            mapPosition.setHandler(btn -> mapPosition.cycle());
            mapPosition.id = 3010;
            addButton(mapPosition);
        }

        @Override
        public void onSliderChanged(UIElementSlider slider) {
            switch (slider.id) {
                case 303:
                    Settings.defaultZoom = slider.getValue();
                    break;
                case 3008:
                    Settings.minimapZoom = slider.getValue();
                    break;
                case 3009:
                    Settings.minimapScale = slider.getValue();
                    break;
            }
        }

        /** mp15: section heading with a thin rule, across the import box width. */
        private void drawSection(String title, int y) {
            NAPainter p = NAClient.get().getPlatform().painter;
            int bw = importBoxWidth(), x0 = width / 2 - bw / 2;
            p.drawString(title, x0, y, 0xFFFFFFA0);
            int tw = p.getStringWidth(title);
            p.drawRect(x0 + tw + 4, y + 4, x0 + bw, y + 5, 0x80FFFFA0);
        }

        @Override
        public void render(int mouseX, int mouseY, float delta, UIScaleInfo scaleInfo) {
            NAPainter p = NAClient.get().getPlatform().painter;

            p.drawRect(0, 0, width, height, UITheme.PANEL_BG);
            p.drawCenteredString("Settings", width / 2, headerY + 4, UITheme.TITLE_TEXT);

            if (activeTab == Tab.PREFERENCES) {
                int top = headerY + 62;
                p.drawString("Top-left buttons", width / 2 - 155, top - 11, 0xFFFFFFA0);
                p.drawString("Top-right buttons", width / 2 + 5, top - 11, 0xFFFFFFA0);
                p.drawString("Keybinds for hidden buttons", width / 2 - 155, prefKeysY - 11, 0xFFFFFFA0);
                p.drawString("Markup layout", width / 2 - 155, prefLayoutY - 11, 0xFFFFFFA0);
                p.drawString("Off: hidden = key off (M, D, L)", width / 2 - 155, prefKeysY + 19, 0xFFAAAAAA);
            }

            if (activeTab == Tab.ATLAS) {
                String imageStatus = dev.natowb.natosatlas.client.io.WorldMapExport.getStatus();
                if (!imageStatus.isEmpty()) p.drawCenteredString(imageStatus, width / 2, height - 12, 0xFF55FF55);
                else if (ClientWorldAccess.get().getWorldInfo() != null)
                    p.drawCenteredString("Map images save to .minecraft/screenshots", width / 2, height - 12, 0xFFAAAAAA);
            }

            if (activeTab == Tab.IMPORT && overworldPathField != null) {
                int lx = width / 2 - importBoxWidth() / 2;
                drawSection("Multiplayer", importBase());
                p.drawString("Overworld region folder", lx, overworldPathField.y - 10, UITheme.TITLE_TEXT);
                p.drawString("Nether region folder", lx, netherPathField.y - 10, UITheme.TITLE_TEXT);
                drawSection("Singleplayer", importBase() + 125);
                dev.natowb.natosatlas.client.RegionImporter imp = dev.natowb.natosatlas.client.RegionImporter.get();
                int sy = importButton.y + 24;
                boolean mp = ClientWorldAccess.get().getWorldInfo() != null && ClientWorldAccess.get().getWorldInfo().isMultiplayer();
                if (!mp) {
                    p.drawCenteredString("Join the server to import its map", width / 2, sy, 0xFFFF5555);
                } else {
                    // mp15: two lines at most (status, then the error or the hint) so the Singleplayer section fits at GUI height 270
                    if (!imp.getStatus().isEmpty()) { p.drawCenteredString(imp.getStatus(), width / 2, sy, 0xFF55FF55); sy += 10; }
                    if (!imp.getError().isEmpty()) p.drawCenteredString(imp.getError(), width / 2, sy, 0xFFFF5555);
                    else p.drawCenteredString("Unchanged regions are skipped. Shift-click Import to redo everything.", width / 2, sy, 0xFFAAAAAA);
                }
            }

            if (activeTab == Tab.ATLAS && slimeSeedField != null) {
                int cx = slimeSeedField.x + 75;
                p.drawCenteredString("Slime seed (blank = server seed)", cx, slimeSeedField.y - 11, UITheme.TITLE_TEXT);
                // mp12: no seed number outside the seed box itself
                String using = "Slimes using the " + dev.natowb.natosatlas.client.SlimeSeed.source();
                p.drawCenteredString(using, cx, slimeSeedField.y + 50, 0xFFAAAAAA);
            }

            super.render(mouseX, mouseY, delta, scaleInfo);
        }
    }
