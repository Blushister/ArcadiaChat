package com.arcadia.chat.client;

import java.util.List;

import com.arcadia.chat.tabs.TabState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.ChatVisiblity;

/**
 * "Dev2 ecrit...", "Dev2 et Alex ecrivent...", "Dev2, Alex, Sam et 2 autres ecrivent..." (comme Discord),
 * juste sous les messages du chat, sur un fond sombre comme le chat. Visible chat ouvert ou ferme, et mis a
 * jour en continu (points animes, disparition quand le joueur s'arrete).
 */
public final class TypingIndicator {

    /** Le chat de Minecraft s'arrete a 40 pixels du bas de l'ecran ; l'indicateur se place juste en dessous. */
    private static final int BOTTOM_OF_CHAT = 40;
    private static final int PADDING = 2;
    private static final int BACKGROUND = 0x90000000;
    private static final int TEXT = 0xFFD0D0D0;

    private TypingIndicator() {
    }

    /** Comme Discord : jusqu'a trois pseudos, puis "et N autres". */
    static Component describe(List<String> names, String dots) {
        Component a = name(names.get(0));
        return switch (names.size()) {
            case 1 -> Component.translatable("arcadiachat.typing.one", a, dots);
            case 2 -> Component.translatable("arcadiachat.typing.two", a, name(names.get(1)), dots);
            case 3 -> Component.translatable("arcadiachat.typing.three", a, name(names.get(1)), name(names.get(2)), dots);
            default -> Component.translatable("arcadiachat.typing.more", a, name(names.get(1)), name(names.get(2)),
                    names.size() - 3, dots);
        };
    }

    private static Component name(String name) {
        return Component.literal(name).withStyle(ChatFormatting.BOLD, ChatFormatting.WHITE);
    }

    public static void render(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (!TabState.typingEnabled() || mc.options.hideGui
                || mc.options.chatVisibility().get() == ChatVisiblity.HIDDEN) {
            return;
        }
        List<String> names = TabState.typingNames();
        if (names.isEmpty()) {
            return;
        }
        String dots = ".".repeat(1 + (int) (System.currentTimeMillis() / 400 % 3));
        Component text = describe(names, dots);
        Font font = mc.font;
        int y = graphics.guiHeight() - BOTTOM_OF_CHAT + PADDING;
        int width = font.width(text) + PADDING * 2;
        graphics.fill(0, y - 1, 2 + width, y + font.lineHeight, BACKGROUND);
        graphics.drawString(font, text, 2 + PADDING, y, TEXT, false);
    }
}
