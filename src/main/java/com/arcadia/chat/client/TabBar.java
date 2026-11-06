package com.arcadia.chat.client;

import java.util.ArrayList;
import java.util.List;

import com.arcadia.chat.ArcadiaChat;
import com.arcadia.chat.net.ClientNetwork;
import com.arcadia.chat.tabs.ChatTab;
import com.arcadia.chat.tabs.TabState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Barre d'onglets, dessinee juste au-dessus de la zone de saisie quand le chat est ouvert. Elle occupe
 * l'espace libre entre la saisie et les messages. Apres les onglets : le bouton des reglages et la
 * destination de ce qu'on ecrit.
 */
public final class TabBar {

    public static final int HEIGHT = 12;
    /** Distance entre le bas de l'ecran et le haut de la barre. */
    public static final int OFFSET_FROM_BOTTOM = 28;
    private static final int PADDING = 4;
    private static final int GAP = 2;


    private record Slot(ChatTab tab, int x, int width) {
    }

    /** Bouton des reglages, au bout de la barre. */
    private static final int GEAR_WIDTH = 14;
    private static int gearX = -1;
    /** Bouton "#" (ecran Canaux), juste apres la roue dentee ; -1 si le serveur ne le gere pas. */
    private static int channelsX = -1;
    /** Bouton du panel staff, a la suite ; -1 s'il n'est pas propose. */
    private static int panelX = -1;
    /** Bouton du selecteur d'emojis. */
    private static int emojiX = -1;

    private static final List<Slot> slots = new ArrayList<>();
    private static int top;

    private TabBar() {
    }

    public static int top(int screenHeight) {
        return screenHeight - OFFSET_FROM_BOTTOM;
    }

    private static int alpha(int color, int alpha) {
        return (color & 0xFFFFFF) | alpha << 24;
    }

    /** @return l'abscisse ou s'arrete la barre d'onglets */
    public static int render(GuiGraphics graphics, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        ChatTheme theme = ChatTheme.current();
        int idle = alpha(theme.rail, 0xB0);
        int hovered = alpha(theme.hover, 0xE0);
        top = top(screenHeight);
        slots.clear();
        // Place gardee pour les boutons, dessines juste apres le dernier onglet : le coin droit de l'ecran
        // est souvent pris par les boutons d'autres mods (No Chat Reports...).
        int limit = screenWidth - 2 - (GEAR_WIDTH + GAP) * 4;
        int x = 2;
        for (ChatTab tab : TabState.visibleTabs()) {
            Component label = label(tab);
            int width = font.width(label) + PADDING * 2;
            if (x + width > limit) {
                break; // plus de place : les onglets suivants restent accessibles au clavier (Ctrl+Tab)
            }
            boolean active = tab.equals(TabState.active());
            boolean mention = !active && TabState.isMentioned(tab);
            boolean hover = contains(x, width, mouseX, mouseY);
            Ui.pill(graphics, x, top, width, HEIGHT, active ? alpha(theme.selected, 0xF0) : mention ? alpha(theme.accent, 0x70) : hover ? hovered : idle);
            if (active) {
                graphics.fill(x + 1, top + HEIGHT - 1, x + width - 1, top + HEIGHT, theme.accent);
            }
            int color = active || hover ? theme.strong : mention ? theme.accent : theme.text;
            graphics.drawString(font, theme.adapt(label.getVisualOrderText()), x + PADDING, top + 2, color, false);
            slots.add(new Slot(tab, x, width));
            x += width + GAP;
        }
        gearX = x;
        x = iconButton(graphics, x, Ui.GEAR, theme, mouseX, mouseY);
        channelsX = -1;
        if (ClientNetwork.supportsChannelsScreen()) {
            channelsX = x;
            x = iconButton(graphics, x, Ui.HASH, theme, mouseX, mouseY);
        }
        panelX = -1;
        if (TabState.isStaff() && ClientNetwork.supportsPanel()) {
            panelX = x;
            x = iconButton(graphics, x, Ui.SHIELD, theme, mouseX, mouseY);
        }
        emojiX = x;
        boolean emojiHover = contains(emojiX, GEAR_WIDTH, mouseX, mouseY);
        Ui.pill(graphics, emojiX, top, GEAR_WIDTH, HEIGHT, emojiHover || EmojiPicker.isOpen() ? hovered : idle);
        graphics.drawString(font, Emojis.glyph("smile"), emojiX + 3, top + 2, 0xFFFFFFFF, false);
        x += GEAR_WIDTH + GAP;
        int end = x;
        limit = screenWidth - 2;

        // Ou part ce que le joueur ecrit.
        int infoX = x + PADDING;
        Component target = TabState.targetLabel();
        if (infoX + font.width(target) < limit) {
            graphics.fill(infoX - 2, top, infoX + font.width(target) + 2, top + HEIGHT, idle);
            graphics.drawString(font, target, infoX, top + 2, theme.muted, false);
        }
        return end;
    }

    private static int iconButton(GuiGraphics graphics, int x, Ui.Icon icon, ChatTheme theme, int mouseX, int mouseY) {
        boolean hover = contains(x, GEAR_WIDTH, mouseX, mouseY);
        Ui.pill(graphics, x, top, GEAR_WIDTH, HEIGHT, hover ? alpha(theme.hover, 0xE0) : alpha(theme.rail, 0xB0));
        Ui.icon(graphics, icon, x + (GEAR_WIDTH - Ui.ICON) / 2, top + (HEIGHT - Ui.ICON) / 2, hover ? theme.strong : theme.muted);
        return x + GEAR_WIDTH + GAP;
    }

    /**
     * Clic gauche : ouvre l'onglet ; clic droit sur une conversation privee : la ferme.
     *
     * @return true si le clic a touche un onglet
     */
    public static boolean click(double mouseX, double mouseY, int button) {
        if (gearX >= 0 && button == 0 && contains(gearX, GEAR_WIDTH, mouseX, mouseY)) {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(ArcadiaChat.settingsScreen(mc.screen));
            return true;
        }
        if (channelsX >= 0 && button == 0 && contains(channelsX, GEAR_WIDTH, mouseX, mouseY)) {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(new ChannelsScreen(mc.screen));
            return true;
        }
        if (emojiX >= 0 && button == 0 && contains(emojiX, GEAR_WIDTH, mouseX, mouseY)) {
            EmojiPicker.toggle();
            return true;
        }
        if (panelX >= 0 && button == 0 && contains(panelX, GEAR_WIDTH, mouseX, mouseY)) {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(new StaffPanelScreen(mc.screen));
            return true;
        }
        for (Slot slot : slots) {
            if (contains(slot.x(), slot.width(), mouseX, mouseY)) {
                if (button == 1) {
                    TabState.close(slot.tab());
                } else {
                    TabState.select(slot.tab());
                }
                return true;
            }
        }
        return false;
    }

    public static boolean isEmojiButton(double mouseX, double mouseY) {
        return emojiX >= 0 && contains(emojiX, GEAR_WIDTH, mouseX, mouseY);
    }

    private static boolean contains(int x, int width, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= top && mouseY < top + HEIGHT;
    }

    private static Component label(ChatTab tab) {
        MutableComponent label = tab.label(TabState.serverName()).copy();
        int count = TabState.unread(tab);
        if (count > 0) {
            label.append(Component.literal(" " + (count > 99 ? "99+" : count)).withStyle(ChatFormatting.GOLD));
        }
        return label;
    }
}
