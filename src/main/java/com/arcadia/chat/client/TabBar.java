package com.arcadia.chat.client;

import java.util.ArrayList;
import java.util.List;

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
 * l'espace libre entre la saisie et les messages.
 */
public final class TabBar {

    private static final int HEIGHT = 12;
    /** Distance entre le bas de l'ecran et le haut de la barre. */
    private static final int OFFSET_FROM_BOTTOM = 28;
    private static final int PADDING = 4;
    private static final int GAP = 2;

    private static final int BG_ACTIVE = 0xE0000000;
    private static final int BG_IDLE = 0x80000000;
    private static final int BG_HOVER = 0xB0000000;
    private static final int ACCENT = 0xFFFFAA00;
    private static final int TEXT_ACTIVE = 0xFFFFFFFF;
    private static final int TEXT_IDLE = 0xFFAAAAAA;

    private record Slot(ChatTab tab, int x, int width) {
    }

    private static final List<Slot> slots = new ArrayList<>();
    private static int top;

    private TabBar() {
    }

    public static void render(GuiGraphics graphics, int screenHeight, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        top = screenHeight - OFFSET_FROM_BOTTOM;
        slots.clear();
        int x = 2;
        for (ChatTab tab : TabState.visibleTabs()) {
            Component label = label(tab);
            int width = font.width(label) + PADDING * 2;
            boolean active = tab == TabState.active();
            boolean hover = contains(x, width, mouseX, mouseY);
            graphics.fill(x, top, x + width, top + HEIGHT, active ? BG_ACTIVE : hover ? BG_HOVER : BG_IDLE);
            if (active) {
                graphics.fill(x, top + HEIGHT - 1, x + width, top + HEIGHT, ACCENT);
            }
            graphics.drawString(font, label, x + PADDING, top + 2, active ? TEXT_ACTIVE : TEXT_IDLE, false);
            slots.add(new Slot(tab, x, width));
            x += width + GAP;
        }
        if (TabState.hasCrossServer()) {
            // Ou part ce que le joueur ecrit.
            Component target = TabState.writesGlobal()
                    ? Component.translatable("arcadiachat.scope.global")
                    : Component.translatable("arcadiachat.scope.local", TabState.serverName());
            graphics.drawString(font, target, x + PADDING, top + 2, TEXT_IDLE, false);
        }
    }

    /** @return true si le clic a touche un onglet */
    public static boolean click(double mouseX, double mouseY) {
        for (Slot slot : slots) {
            if (contains(slot.x(), slot.width(), mouseX, mouseY)) {
                TabState.select(slot.tab());
                return true;
            }
        }
        return false;
    }

    private static boolean contains(int x, int width, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= top && mouseY < top + HEIGHT;
    }

    private static Component label(ChatTab tab) {
        MutableComponent label = tab == ChatTab.SERVER && !TabState.serverName().isBlank()
                ? Component.literal(TabState.serverName())
                : Component.translatable(tab.translationKey());
        int count = TabState.unread(tab);
        if (count > 0) {
            label = label.copy().append(Component.literal(" " + (count > 99 ? "99+" : count))
                    .withStyle(ChatFormatting.YELLOW));
        }
        return label;
    }
}
