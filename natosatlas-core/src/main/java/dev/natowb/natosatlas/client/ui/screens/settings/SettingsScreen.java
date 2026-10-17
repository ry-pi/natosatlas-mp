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

        private enum Tab {
            GENERAL,
            ATLAS,
            MINIMAP
        }

        private Tab activeTab = Tab.GENERAL;

        public SettingsScreen(UIScreen parent) {
            super(parent);
        }

        private UIElementButton generalTab;
        private UIElementButton worldMapTab;
        private UIElementButton minimapTab;


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

            int tabButtonWidth = 80;
            int tabButtonHeight = 20;
            int tabSpacing = 10;

            int tabWidth = (tabButtonWidth * 3) + (tabSpacing * 2);

            int tabX = (width - tabWidth) / 2;

            generalTab = new UIElementButton(201, tabX, tabY, tabButtonWidth, tabButtonHeight, "General");
            generalTab.setHandler(btn -> switchTab(Tab.GENERAL));
            addButton(generalTab);

            worldMapTab = new UIElementButton(202, tabX + tabButtonWidth + tabSpacing, tabY, tabButtonWidth, tabButtonHeight, "World map");
            worldMapTab.setHandler(btn -> switchTab(Tab.ATLAS));
            addButton(worldMapTab);

            minimapTab = new UIElementButton(203, tabX + (tabButtonWidth + tabSpacing) * 2, tabY, tabButtonWidth, tabButtonHeight, "Minimap");
            minimapTab.setHandler(btn -> switchTab(Tab.MINIMAP));
            addButton(minimapTab);

            updateTabActiveStates();


            rebuildContent();
        }

        @Override
        public void onClose() {
            applySlimeSeed();
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
            if (deleteButton != null && deleteArmedUntil != 0 && System.currentTimeMillis() >= deleteArmedUntil) {
                deleteArmedUntil = 0;
                deleteButton.label = DELETE_LABEL;
            }
        }

        @Override
        public void keyPressed(char character, int keyCode) {
            boolean wasFocused = slimeSeedField != null && slimeSeedField.focused;
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
            generalTab.active = !(activeTab == Tab.GENERAL);
            worldMapTab.active = !(activeTab == Tab.ATLAS);
            minimapTab.active = !(activeTab == Tab.MINIMAP);
        }

        private void rebuildContent() {
            applySlimeSeed();
            buttons.removeIf(b -> b.id >= 300);
            sliders.removeIf(s -> s.id >= 300);
            textFields.clear();
            slimeSeedField = null;
            deleteButton = null;

            int listTop = headerY + 50;
            UILayout layout = new UIVerticalLayout(width / 2, listTop, 5);

            switch (activeTab) {
                case GENERAL:
                    buildGeneralSettings(layout);
                    break;

                case ATLAS:
                    buildAtlasSettings(layout);
                    break;

                case MINIMAP:
                    buildMinimapSettings(layout);
                    break;
            }
        }

        private void buildGeneralSettings(UILayout ignored) {
            // mp7: two columns (like the Minimap tab) so the map image / delete buttons fit without pushing
            // the import folder text at the bottom
            UILayout layout = new UIVerticalLayout(width / 2 - 80, headerY + 50, 5);
            UIElementOptionButton debugButton =
                    new UIElementOptionButton(SettingsOption.DEBUG_INFO, layout, 150, 20);
            debugButton.setHandler(btn -> debugButton.cycle());
            debugButton.id = 300;
            addButton(debugButton);
            boolean isMultiplayer = ClientWorldAccess.get().getWorldInfo().isMultiplayer();
            UIElementButton existingButton =
                    new UIElementButton(302, layout, 150, 20, "Generate Existing", !isMultiplayer);
            existingButton.setHandler(btn ->
                    NAClient.get().getPlatform().screen.openNacScreen(new GenerateScreen(this)));
            addButton(existingButton);

            // mp8: the import row is split: [Import .mcr][Open folder] in the same 150 px as the other buttons
            dev.natowb.natosatlas.client.RegionImporter imp = dev.natowb.natosatlas.client.RegionImporter.get();
            dev.natowb.natosatlas.client.ui.layout.UIPoint row = layout.next(150, 20);
            UIElementButton importButton =
                    new UIElementButton(311, row.x, row.y, 76, 20, imp.isRunning() ? "Stop import" : "Import .mcr");
            importButton.active = isMultiplayer;
            importButton.setHandler(btn -> {
                if (imp.isRunning()) imp.cancel(); else imp.start();
                rebuildContent();
            });
            addButton(importButton);

            UIElementButton openImportButton =
                    new UIElementButton(316, row.x + 80, row.y, 70, 20, "Open folder");
            openImportButton.active = isMultiplayer;
            openImportButton.setHandler(btn -> {
                dev.natowb.natosatlas.client.RegionImporter.ensureFolders(); // import/ and import/DIM-1/
                dev.natowb.natosatlas.client.FolderOpener.open(dev.natowb.natosatlas.client.RegionImporter
                        .getImportDir(ClientWorldAccess.get().getWorldInfo().getDimensionId()));
            });
            addButton(openImportButton);

            UILayout right = new UIVerticalLayout(width / 2 + 80, headerY + 50, 5);
            UIElementButton saveFull = new UIElementButton(313, right, 150, 20, "Save map image (full)");
            saveFull.setHandler(btn -> dev.natowb.natosatlas.client.io.WorldMapExport.start(false,
                    dev.natowb.natosatlas.client.io.WorldMapExport.currentLayer()));
            addButton(saveFull);

            UIElementButton saveSmall = new UIElementButton(314, right, 150, 20, "Save map image (small)");
            saveSmall.setHandler(btn -> dev.natowb.natosatlas.client.io.WorldMapExport.start(true,
                    dev.natowb.natosatlas.client.io.WorldMapExport.currentLayer()));
            addButton(saveSmall);

            deleteButton = new UIElementButton(315, right, 150, 20, DELETE_LABEL);
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

        // mp7: two-click confirm for "Delete all map data"
        private static final String DELETE_LABEL = "Delete all map data";
        private UIElementButton deleteButton;
        private long deleteArmedUntil = 0;

        private void buildAtlasSettings(UILayout layout) {
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

        @Override
        public void render(int mouseX, int mouseY, float delta, UIScaleInfo scaleInfo) {
            NAPainter p = NAClient.get().getPlatform().painter;

            p.drawRect(0, 0, width, height, UITheme.PANEL_BG);
            p.drawCenteredString("Settings", width / 2, headerY + 4, UITheme.TITLE_TEXT);

            if (activeTab == Tab.GENERAL && ClientWorldAccess.get().getWorldInfo() != null
                    && ClientWorldAccess.get().getWorldInfo().isMultiplayer()) {
                dev.natowb.natosatlas.client.RegionImporter imp = dev.natowb.natosatlas.client.RegionImporter.get();
                int dim = ClientWorldAccess.get().getWorldInfo().getDimensionId();
                java.io.File dir = dev.natowb.natosatlas.client.RegionImporter.getImportDir(dim);
                p.drawCenteredString("Copy the server's region/*.mcr files into:", width / 2, height - 46, UITheme.TITLE_TEXT);
                if (dir != null) p.drawCenteredString(dev.natowb.natosatlas.client.RegionImporter.displayPath(dir, dev.natowb.natosatlas.core.NAPaths.getMinecraftPath()), width / 2, height - 36, 0xFFAAAAAA);
                String folderStatus = dev.natowb.natosatlas.client.FolderOpener.getStatus();
                if (!folderStatus.isEmpty()) p.drawCenteredString(folderStatus, width / 2, height - 24, 0xFF55FF55);
                else if (!imp.getStatus().isEmpty()) p.drawCenteredString(imp.getStatus(), width / 2, height - 24, 0xFF55FF55);
            }

            if (activeTab == Tab.GENERAL) {
                String imageStatus = dev.natowb.natosatlas.client.io.WorldMapExport.getStatus();
                if (!imageStatus.isEmpty()) p.drawCenteredString(imageStatus, width / 2, height - 12, 0xFF55FF55);
                else if (ClientWorldAccess.get().getWorldInfo() != null)
                    p.drawCenteredString("Map images save to .minecraft/screenshots", width / 2, height - 12, 0xFFAAAAAA);
            }

            if (activeTab == Tab.ATLAS && slimeSeedField != null) {
                p.drawCenteredString("Slime seed (blank = server seed)", width / 2, slimeSeedField.y - 11, UITheme.TITLE_TEXT);
                // mp12: no seed number outside the seed box itself
                String using = "Slimes using the " + dev.natowb.natosatlas.client.SlimeSeed.source();
                p.drawCenteredString(using, width / 2, slimeSeedField.y + 50, 0xFFAAAAAA);
            }

            super.render(mouseX, mouseY, delta, scaleInfo);
        }
    }
