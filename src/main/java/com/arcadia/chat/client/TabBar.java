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

    private static final int BG_ACTIVE = 0xE0000000;
    private static final int BG_IDLE = 0x80000000;
    private static final int BG_HOVER = 0xB0000000;
    private static final int BG_MENTION = 0xC0704000;
    private static final int ACCENT = 0xFFFFAA00;
    private static final int TEXT_ACTIVE = 0xFFFFFFFF;
    private static final int TEXT_IDLE = 0xFFAAAAAA;
    private static final int TEXT_MENTION = 0xFFFFDD55;

    private record Slot(ChatTab tab, int x, int width) {
    }

    /** Bouton des reglages, au bout de la barre. */
    private static final int GEAR_WIDTH = 14;
    private static int gearX = -1;
    /** Bouton "#" (ecran Canaux), juste apres la roue dentee ; -1 si le serveur ne le gere pas. */
    private static int channelsX = -1;

    private static final List<Slot> slots = new ArrayList<>();
    private static int top;

    private TabBar() {
    }

    public static int top(int screenHeight) {
        return screenHeight - OFFSET_FROM_BOTTOM;
    }

    /** @return l'abscisse ou s'arrete la barre d'onglets */
    public static int render(GuiGraphics graphics, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        top = top(screenHeight);
        slots.clear();
        // Place gardee pour la roue dentee, dessinee juste apres le dernier onglet : le coin droit de l'ecran
        // est souvent pris par les boutons d'autres mods (No Chat Reports...).
        int limit = screenWidth - 2 - GEAR_WIDTH - GAP;
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
            int background = active ? BG_ACTIVE : mention ? BG_MENTION : hover ? BG_HOVER : BG_IDLE;
            graphics.fill(x, top, x + width, top + HEIGHT, background);
            if (active) {
                graphics.fill(x, top + HEIGHT - 1, x + width, top + HEIGHT, ACCENT);
            }
            int color = active ? TEXT_ACTIVE : mention ? TEXT_MENTION : TEXT_IDLE;
            graphics.drawString(font, label, x + PADDING, top + 2, color, false);
            slots.add(new Slot(tab, x, width));
            x += width + GAP;
        }
        gearX = x;
        boolean gearHover = contains(gearX, GEAR_WIDTH, mouseX, mouseY);
        graphics.fill(gearX, top, gearX + GEAR_WIDTH, top + HEIGHT, gearHover ? BG_HOVER : BG_IDLE);
        graphics.drawCenteredString(font, "⚙", gearX + GEAR_WIDTH / 2 + 1, top + 2, gearHover ? TEXT_ACTIVE : TEXT_MENTION);
        x += GEAR_WIDTH + GAP;
        channelsX = -1;
        if (ClientNetwork.supportsChannelsScreen()) {
            channelsX = x;
            boolean channelsHover = contains(channelsX, GEAR_WIDTH, mouseX, mouseY);
            graphics.fill(channelsX, top, channelsX + GEAR_WIDTH, top + HEIGHT, channelsHover ? BG_HOVER : BG_IDLE);
            graphics.drawCenteredString(font, "#", channelsX + GEAR_WIDTH / 2 + 1, top + 2,
                    channelsHover ? TEXT_ACTIVE : TEXT_MENTION);
            x += GEAR_WIDTH + GAP;
        }
        int end = x;
        limit = screenWidth - 2;

        // Ou part ce que le joueur ecrit.
        int infoX = x + PADDING;
        Component target = TabState.targetLabel();
        if (infoX + font.width(target) < limit) {
            graphics.drawString(font, target, infoX, top + 2, TEXT_IDLE, false);
        }
        return end;
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

    private static boolean contains(int x, int width, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= top && mouseY < top + HEIGHT;
    }

    private static Component label(ChatTab tab) {
        MutableComponent label = tab.label(TabState.serverName()).copy();
        int count = TabState.unread(tab);
        if (count > 0) {
            label.append(Component.literal(" " + (count > 99 ? "99+" : count)).withStyle(ChatFormatting.YELLOW));
        }
        return label;
    }
}
