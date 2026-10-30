package com.arcadia.chat.client;

import java.util.List;

import com.arcadia.chat.tabs.ChatView;
import com.arcadia.chat.tabs.TabState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Elements ajoutes au chat ouvert : le bandeau des messages epingles en haut, et le bouton "nouveaux messages"
 * en bas quand le joueur a remonte le chat.
 */
public final class ChatOverlay {

    private static final int PIN_BACKGROUND = 0xD0201A08;
    private static final int PIN_ACCENT = 0xFFFFAA00;
    private static final int BUTTON = 0xE0303A60;
    private static final int BUTTON_HOVER = 0xF0405090;
    private static final int TEXT = 0xFFFFFFFF;

    /** Position du bouton "nouveaux messages" au dernier affichage ; largeur 0 : pas de bouton. */
    private static int buttonX;
    private static int buttonY;
    private static int buttonWidth;

    private ChatOverlay() {
    }

    public static void render(GuiGraphics graphics, int screenHeight, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        ChatComponent chat = mc.gui.getChat();
        Font font = mc.font;
        double scale = chat.getScale();
        int chatWidth = (int) Math.ceil(chat.getWidth() * scale) + 8;
        renderPins(graphics, font, chat, scale, chatWidth, screenHeight);

        buttonWidth = 0;
        ChatView view = (ChatView) chat;
        if (!view.arcadiachat$scrolledUp()) {
            return;
        }
        int unseen = view.arcadiachat$unseen();
        Component label = unseen > 0
                ? Component.translatable("arcadiachat.overlay.new_messages", unseen)
                : Component.translatable("arcadiachat.overlay.bottom");
        buttonWidth = font.width(label) + 10;
        buttonX = Math.max(2, chatWidth - buttonWidth);
        buttonY = TabBar.top(screenHeight) - 14;
        boolean hover = contains(mouseX, mouseY);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 200);
        graphics.fill(buttonX, buttonY, buttonX + buttonWidth, buttonY + 12, hover ? BUTTON_HOVER : BUTTON);
        graphics.drawString(font, label, buttonX + 5, buttonY + 2, TEXT, false);
        graphics.pose().popPose();
    }

    /** Bandeau epingle, juste au-dessus de la zone du chat. */
    private static void renderPins(GuiGraphics graphics, Font font, ChatComponent chat, double scale, int chatWidth,
                                   int screenHeight) {
        List<Component> pins = TabState.pinned();
        if (pins.isEmpty()) {
            return;
        }
        int lineHeight = font.lineHeight + 2;
        int chatTop = screenHeight - 40 - (int) Math.ceil(chat.getHeight() * scale);
        int lines = 0;
        for (Component pin : pins) {
            lines += font.split(pin, chatWidth - 10).size();
        }
        int top = Math.max(2, chatTop - lines * lineHeight - 4);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 150);
        graphics.fill(0, top, chatWidth, top + lines * lineHeight + 2, PIN_BACKGROUND);
        graphics.fill(0, top, 2, top + lines * lineHeight + 2, PIN_ACCENT);
        int y = top + 2;
        for (Component pin : pins) {
            for (FormattedCharSequence line : font.split(pin, chatWidth - 10)) {
                graphics.drawString(font, line, 6, y, TEXT, false);
                y += lineHeight;
            }
        }
        graphics.pose().popPose();
    }

    /** @return true si le clic a touche le bouton (le chat revient en bas) */
    public static boolean click(double mouseX, double mouseY) {
        if (buttonWidth > 0 && contains(mouseX, mouseY)) {
            Minecraft.getInstance().gui.getChat().resetChatScroll();
            buttonWidth = 0;
            return true;
        }
        return false;
    }

    private static boolean contains(double mouseX, double mouseY) {
        return mouseX >= buttonX && mouseX < buttonX + buttonWidth && mouseY >= buttonY && mouseY < buttonY + 12;
    }
}
