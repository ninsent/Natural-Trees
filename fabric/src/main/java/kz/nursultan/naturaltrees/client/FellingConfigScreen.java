/*
 * Copyright 2026 Nursultan Akim
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package kz.nursultan.naturaltrees.client;

import kz.nursultan.naturaltrees.config.FabricFellingConfig;
import kz.nursultan.naturaltrees.felling.FellingSettings;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * The settings screen Mod Menu opens on Fabric: the five felling keys of spec 14.3 and nothing else. It edits
 * {@code config/naturaltrees-server.toml}; the new values apply at once to a world open on this computer.
 */
public class FellingConfigScreen extends Screen {

    private static final int ROW = 26;
    private static final int WIDTH = 310;

    private final Screen parent;
    private boolean enabled;
    private boolean force;
    private EditBox maxBlocks;
    private EditBox minLeaves;
    private EditBox blocksPerTick;

    public FellingConfigScreen(Screen parent) {
        super(Component.translatable("naturaltrees.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        final FellingSettings settings = FabricFellingConfig.get();
        enabled = settings.enabled();
        force = settings.force();
        final int left = width / 2 - WIDTH / 2;
        int y = 44;

        addRenderableWidget(CycleButton.onOffBuilder(enabled)
                .withTooltip(value -> Tooltip.create(Component.translatable("naturaltrees.config.enabled.tooltip")))
                .create(left, y, WIDTH, 20, Component.translatable("naturaltrees.config.enabled"), (button, value) -> enabled = value));
        y += ROW;
        addRenderableWidget(CycleButton.onOffBuilder(force)
                .withTooltip(value -> Tooltip.create(Component.translatable("naturaltrees.config.force.tooltip")))
                .create(left, y, WIDTH, 20, Component.translatable("naturaltrees.config.force"), (button, value) -> force = value));
        y += ROW;
        maxBlocks = number(left, y, "max_blocks", settings.maxBlocks());
        y += ROW;
        minLeaves = number(left, y, "min_leaves", settings.minLeaves());
        y += ROW;
        blocksPerTick = number(left, y, "blocks_per_tick", settings.blocksPerTick());

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> {
            FabricFellingConfig.save(new FellingSettings(enabled, force,
                    read(maxBlocks, settings.maxBlocks()), read(minLeaves, settings.minLeaves()),
                    read(blocksPerTick, settings.blocksPerTick())));
            onClose();
        }).bounds(width / 2 - 155, height - 28, 150, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(width / 2 + 5, height - 28, 150, 20).build());
    }

    private EditBox number(int left, int y, String key, int value) {
        final EditBox box = new EditBox(font, left + WIDTH - 80, y, 80, 20, Component.translatable("naturaltrees.config." + key));
        box.setValue(Integer.toString(value));
        box.setFilter(text -> text.matches("\\d{0,4}"));
        box.setTooltip(Tooltip.create(Component.translatable("naturaltrees.config." + key + ".tooltip")));
        return addRenderableWidget(box);
    }

    /** An empty or unreadable box keeps the old value; out-of-range numbers are brought inside when saved. */
    private static int read(EditBox box, int fallback) {
        try {
            return Integer.parseInt(box.getValue());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 16, 0xFFFFFF);
        final int left = width / 2 - WIDTH / 2;
        int y = 44 + 2 * ROW + 6;
        for (String key : new String[] {"max_blocks", "min_leaves", "blocks_per_tick"}) {
            graphics.drawString(font, Component.translatable("naturaltrees.config." + key), left, y, 0xFFFFFF);
            y += ROW;
        }
        graphics.drawCenteredString(font, Component.translatable("naturaltrees.config.note"), width / 2, y + 4, 0xA0A0A0);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
