package com.arcadia.chat.client;

import com.arcadia.chat.ChatConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;

/** Petite icone en haut a gauche pour passer du chat habituel a l'ecran facon Discord, et inversement. Le choix est retenu. */
public final class ViewSwitch {

    public static final int SIZE = 12;

    private ViewSwitch() {
    }

    public static boolean contains(int x, int y, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + SIZE && mouseY >= y && mouseY < y + SIZE;
    }

    /** Dans le chat habituel, par-dessus le jeu. */
    public static void render(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        ChatTheme theme = ChatTheme.current();
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300);
        Ui.pill(graphics, x, y, SIZE, SIZE, (theme.rail & 0xFFFFFF) | 0xC0000000);
        render(graphics, x, y, mouseX, mouseY, false, theme);
        graphics.pose().popPose();
    }

    /** Dans l'ecran des salons : l'icone suit le theme. */
    public static void render(GuiGraphics graphics, int x, int y, int mouseX, int mouseY, boolean rooms, ChatTheme theme) {
        boolean hover = contains(x, y, mouseX, mouseY);
        if (hover) {
            Ui.pill(graphics, x, y, SIZE, SIZE, theme.hover);
        }
        int color = hover ? theme.strong : theme.muted;
        graphics.fill(x + 2, y + 2, x + 10, y + 3, color);
        graphics.fill(x + 2, y + 9, x + 10, y + 10, color);
        graphics.fill(x + 2, y + 2, x + 3, y + 10, color);
        graphics.fill(x + 9, y + 2, x + 10, y + 10, color);
        graphics.fill(x + 3, y + 3, x + 5, y + 9, theme.accent);
    }

    public static void toRooms(String text) {
        ChatConfig.ROOMS_VIEW.set(true);
        ChatConfig.SPEC.save();
        if (RoomsUi.installed()) {
            Minecraft.getInstance().setScreen(RoomsUi.create(text));
        }
    }

    public static void toClassic(String text) {
        ChatConfig.ROOMS_VIEW.set(false);
        ChatConfig.SPEC.save();
        Minecraft.getInstance().setScreen(new ChatScreen(text));
    }
}
