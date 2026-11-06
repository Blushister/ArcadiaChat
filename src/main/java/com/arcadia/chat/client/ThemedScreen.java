package com.arcadia.chat.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Base des ecrans du mod : fond et barre de titre aux couleurs du theme, fleche de retour. */
abstract class ThemedScreen extends Screen {

    protected static final int HEADER = 26;
    private static final int BACK_SIZE = 15;

    protected final Screen parent;
    protected ChatTheme theme = ChatTheme.current();

    protected ThemedScreen(Component title, Screen parent) {
        super(title);
        this.parent = parent;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        theme = ChatTheme.current();
        graphics.fill(0, 0, width, height, theme.scrim());
        graphics.fill(0, 0, width, height, theme.panel(theme.ground));
        drawContent(graphics, mouseX, mouseY);
        graphics.fill(0, HEADER - 1, width, HEADER, theme.line);
        boolean hover = overBack(mouseX, mouseY);
        if (hover) {
            Ui.pill(graphics, 5, (HEADER - BACK_SIZE) / 2, BACK_SIZE, BACK_SIZE, theme.hover);
        }
        Ui.icon(graphics, Ui.LEFT, 9, (HEADER - Ui.ICON) / 2, hover ? theme.strong : theme.muted);
        graphics.drawString(font, title.copy().withStyle(UiFont.bold()), 25, (HEADER - 8) / 2, theme.strong, false);
        drawHeader(graphics, mouseX, mouseY);
    }

    /** Sous les boutons : ce que l'ecran dessine lui-meme. */
    protected void drawContent(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    /** Dans la barre de titre. */
    protected void drawHeader(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    private boolean overBack(double mouseX, double mouseY) {
        int y = (HEADER - BACK_SIZE) / 2;
        return mouseX >= 5 && mouseX < 5 + BACK_SIZE && mouseY >= y && mouseY < y + BACK_SIZE;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && overBack(mouseX, mouseY)) {
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Un texte du serveur ou du mod, ses couleurs adaptees au fond. */
    protected void text(GuiGraphics graphics, Component text, int x, int y, int color) {
        graphics.drawString(font, theme.adapt(text.getVisualOrderText()), x, y, color, false);
    }

    protected void centered(GuiGraphics graphics, Component text, int y, int color) {
        Ui.center(graphics, font, text, width / 2, y, color);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
