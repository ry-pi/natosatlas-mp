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

        private void buildGeneralSettings(UILayout layout) {
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

            dev.natowb.natosatlas.client.RegionImporter imp = dev.natowb.natosatlas.client.RegionImporter.get();
            UIElementButton importButton =
                    new UIElementButton(311, layout, 150, 20, imp.isRunning() ? "Cancel import" : "Import region files", isMultiplayer);
            importButton.setHandler(btn -> {
                if (imp.isRunning()) imp.cancel(); else imp.start();
                rebuildContent();
            });
            addButton(importButton);
        }

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
                if (dir != null) p.drawCenteredString(dir.getAbsolutePath(), width / 2, height - 36, 0xFFAAAAAA);
                if (!imp.getStatus().isEmpty()) p.drawCenteredString(imp.getStatus(), width / 2, height - 24, 0xFF55FF55);
            }

            if (activeTab == Tab.ATLAS && slimeSeedField != null) {
                p.drawCenteredString("Slime seed (blank = server seed)", width / 2, slimeSeedField.y - 11, UITheme.TITLE_TEXT);
                String using = "Slimes using " + dev.natowb.natosatlas.client.SlimeSeed.describe();
                p.drawCenteredString(using, width / 2, slimeSeedField.y + 50, 0xFFAAAAAA);
                p.drawCenteredString("Numbers or text, like the Create World seed box. Ctrl+V pastes.", width / 2, slimeSeedField.y + 62, 0xFFAAAAAA);
            }

            super.render(mouseX, mouseY, delta, scaleInfo);
        }
    }
