package com.arcadia.chat.client;

import java.util.List;

import com.arcadia.chat.mixin.ChatScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.util.Mth;

/** Selecteur d'emojis (bouton de la barre d'onglets) : un clic ecrit :nom: dans le chat ; la molette fait defiler. */
public final class EmojiPicker {

    private static final int COLUMNS = 12;
    private static final int ROWS = 8;
    private static final int CELL = 13;
    private static final int PADDING = 4;
    /** Ligne du bas : le nom de l'emoji survole. */
    private static final int FOOTER = 12;
    private static final int WIDTH = COLUMNS * CELL + PADDING * 2;
    private static final int HEIGHT = ROWS * CELL + PADDING * 2 + FOOTER;
    private static final int WHITE = 0xFFFFFFFF;

    private static boolean open;
    private static int firstRow;
    private static int left;
    private static int top;

    private EmojiPicker() {
    }

    public static boolean isOpen() {
        return open;
    }

    public static void toggle() {
        open = !open;
    }

    public static void close() {
        open = false;
    }

    public static void render(GuiGraphics graphics, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        ChatTheme theme = ChatTheme.current();
        renderAt(graphics, screenWidth - 2, TabBar.top(screenHeight) - 2, mouseX, mouseY, theme.field, theme.outline, theme.hover, theme.muted);
    }

    /** Dessine le selecteur, son coin bas droit a cette position. */
    public static void renderAt(GuiGraphics graphics, int right, int bottom, int mouseX, int mouseY, int background, int border,
                                int hover, int footer) {
        if (!open) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        List<Emojis.Emoji> emojis = Emojis.all();
        left = right - WIDTH;
        top = bottom - HEIGHT;
        firstRow = Mth.clamp(firstRow, 0, maxRow(emojis));
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300);
        graphics.fill(left - 1, top - 1, left + WIDTH + 1, top + HEIGHT + 1, border);
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, background);
        Emojis.Emoji hovered = null;
        int index = indexAt(mouseX, mouseY);
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                int i = (firstRow + row) * COLUMNS + col;
                if (i >= emojis.size()) {
                    break;
                }
                int x = left + PADDING + col * CELL;
                int y = top + PADDING + row * CELL;
                if (i == index) {
                    hovered = emojis.get(i);
                    graphics.fill(x, y, x + CELL, y + CELL, hover);
                }
                graphics.drawString(font, emojis.get(i).text(), x + 2, y + 2, WHITE, false);
            }
        }
        if (hovered != null) {
            graphics.drawString(font, ":" + hovered.name() + ":", left + PADDING, top + HEIGHT - FOOTER, footer, false);
        }
        graphics.pose().popPose();
    }

    /** @return true si le clic a touche le selecteur ; un clic ailleurs le ferme et continue son chemin */
    public static boolean click(ChatScreen screen, double mouseX, double mouseY) {
        return clickInto(((ChatScreenAccessor) screen).arcadiachat$input(), mouseX, mouseY);
    }

    /** @return true si le clic a touche le selecteur ; un clic ailleurs le ferme et continue son chemin */
    public static boolean clickInto(EditBox input, double mouseX, double mouseY) {
        if (!open) {
            return false;
        }
        if (!inside(mouseX, mouseY)) {
            open = false;
            return false;
        }
        int index = indexAt(mouseX, mouseY);
        List<Emojis.Emoji> emojis = Emojis.all();
        if (index >= 0 && index < emojis.size()) {
            input.insertText(":" + emojis.get(index).name() + ":");
        }
        return true;
    }

    /** @return true si la molette a servi au selecteur */
    public static boolean scroll(double mouseX, double mouseY, double amount) {
        if (!open || !inside(mouseX, mouseY)) {
            return false;
        }
        firstRow = Mth.clamp(firstRow - (int) Math.signum(amount) * 2, 0, maxRow(Emojis.all()));
        return true;
    }

    private static int maxRow(List<Emojis.Emoji> emojis) {
        return Math.max(0, (emojis.size() + COLUMNS - 1) / COLUMNS - ROWS);
    }

    private static boolean inside(double mouseX, double mouseY) {
        return mouseX >= left && mouseX < left + WIDTH && mouseY >= top && mouseY < top + HEIGHT;
    }

    private static int indexAt(double mouseX, double mouseY) {
        int col = (int) Math.floor((mouseX - left - PADDING) / CELL);
        int row = (int) Math.floor((mouseY - top - PADDING) / CELL);
        if (col < 0 || col >= COLUMNS || row < 0 || row >= ROWS) {
            return -1;
        }
        return (firstRow + row) * COLUMNS + col;
    }
}
