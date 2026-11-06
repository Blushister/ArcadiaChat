package com.arcadia.chat.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import com.arcadia.chat.ChatConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

/** Petites alertes en bas a droite, en jeu (le haut est souvent pris par une mini-carte) : un message prive ou une mention. */
public final class ChatToasts {

    private static final long SHOW_MS = 5000;
    private static final long SLIDE_MS = 200;
    private static final int MAX = 3;
    private static final int WIDTH = 170;
    private static final int HEIGHT = 30;

    private record Toast(String sender, String title, String text, long at) {
    }

    private static final Deque<Toast> TOASTS = new ArrayDeque<>();

    private ChatToasts() {
    }

    static void push(String sender, String title, String text, boolean sound) {
        if (!ChatConfig.TOASTS.get()) {
            return;
        }
        TOASTS.addLast(new Toast(sender, title, text, System.currentTimeMillis()));
        while (TOASTS.size() > MAX) {
            TOASTS.removeFirst();
        }
        if (sound && ChatConfig.MENTION_SOUND.get()) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.6F, 0.6F));
        }
    }

    static void render(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        TOASTS.removeIf(toast -> now - toast.at() > SHOW_MS);
        if (TOASTS.isEmpty() || mc.options.hideGui) {
            return;
        }
        Font font = UiFont.get();
        ChatTheme theme = ChatTheme.current();
        int screenWidth = graphics.guiWidth();
        int y = graphics.guiHeight() - 30 - HEIGHT;
        for (Toast toast : new ArrayList<>(TOASTS)) {
            long age = now - toast.at();
            float slide = Mth.clamp(Math.min(age, SHOW_MS - age) / (float) SLIDE_MS, 0F, 1F);
            int x = screenWidth - 4 - Math.round(WIDTH * slide);
            int panel = (theme.rail & 0xFFFFFF) | 0xF0000000;
            Ui.round(graphics, x, y, WIDTH, HEIGHT, 5, panel);
            Ui.round(graphics, x, y + 4, 3, HEIGHT - 8, 1, theme.accent);
            ChatHeads.drawSized(graphics, toast.sender(), x + 8, y + 7, 16);
            graphics.drawString(font, Component.literal(Ui.fit(font, toast.title(), WIDTH - 36)).withStyle(UiFont.bold()), x + 30, y + 5, theme.strong, false);
            graphics.drawString(font, Ui.fit(font, toast.text(), WIDTH - 36), x + 30, y + 16, theme.text, false);
            y -= HEIGHT + 4;
        }
    }

    static List<String> pending() {
        return TOASTS.stream().map(Toast::title).toList();
    }
}
