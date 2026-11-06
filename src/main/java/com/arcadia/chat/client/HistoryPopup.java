package com.arcadia.chat.client;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.arcadia.chat.net.ClientNetwork;
import com.arcadia.chat.net.TabsProtocol;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * Sous-menu de l'historique des sanctions d'un joueur (staff), ouvert depuis un clic droit : il s'affiche a cote du curseur
 * au lieu d'ecrire dans le chat. Le meme sous-menu sert dans l'ecran des salons, le chat habituel et le panneau staff.
 */
public final class HistoryPopup {

    private static final int WIDTH = 230;
    private static final int MAX_HEIGHT = 240;
    private static final int TYPE_BAN = 0xFFED4245;
    private static final int TYPE_MUTE = 0xFFF0A232;
    private static final int TYPE_WARN = 0xFFE8D44D;
    private static final int TYPE_NOTE = 0xFF5B9BD5;
    private static final int ACTIONS = 22;
    /** Les actions du staff, en bas : chacune ouvre le chat avec la commande prete, il ne reste qu'a ecrire la raison. */
    private static final String[][] BUTTONS = {
            {"warn", "/arcadia_warn %s "}, {"mute", "/arcadia_tempmute %s 1h "}, {"ban", "/arcadia_ban %s "}, {"note", "/arcadia_note %s "}};

    private static String name;
    private static Screen owner;
    private static int x;
    private static int y;
    private static int height;
    private static int scroll;
    private static int content;
    /** null tant que le serveur n'a pas repondu. */
    private static TabsProtocol.PlayerHistory data;
    /** Sans reponse passe ce delai, on le dit plutot que de tourner sans fin. */
    private static final long TIMEOUT_MS = 8000;
    private static long openedAt;

    private HistoryPopup() {
    }

    /** Ouvre l'historique de ce joueur pres de ce point ; sans le mod a jour cote serveur, la commande ecrit dans le chat. */
    public static void open(String player, int atX, int atY) {
        Minecraft mc = Minecraft.getInstance();
        if (!ClientNetwork.supportsPlayerHistory()) {
            if (mc.getConnection() != null) {
                mc.getConnection().sendCommand("arcadia_history " + player);
            }
            return;
        }
        name = player;
        owner = mc.screen;
        x = atX + 4;
        y = atY;
        scroll = 0;
        data = null;
        openedAt = System.currentTimeMillis();
        ClientNetwork.requestPlayerHistory(player);
    }

    public static boolean isOpen() {
        if (name != null && Minecraft.getInstance().screen != owner) {
            close();
        }
        return name != null;
    }

    public static void close() {
        name = null;
        owner = null;
        data = null;
    }

    public static void onData(TabsProtocol.PlayerHistory history) {
        if (name != null && name.equalsIgnoreCase(history.name())) {
            data = history;
        }
    }

    private static int typeColor(String type) {
        return switch (type) {
            case "BAN" -> TYPE_BAN;
            case "MUTE" -> TYPE_MUTE;
            case "NOTE" -> TYPE_NOTE;
            default -> TYPE_WARN;
        };
    }

    public static void render(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!isOpen()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Font font = UiFont.get();
        ChatTheme theme = ChatTheme.current();
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        SimpleDateFormat date = new SimpleDateFormat("dd/MM/yyyy", Locale.ROOT);

        // Hauteur du contenu : une carte par sanction.
        List<TabsProtocol.HistoryEntry> entries = data == null ? List.of() : data.entries();
        int inner = 0;
        for (TabsProtocol.HistoryEntry entry : entries) {
            inner += cardHeight(font, entry);
        }
        int body = data == null || entries.isEmpty() ? 22 : inner + 4;
        content = body;
        height = Math.min(MAX_HEIGHT, 26 + body) + ACTIONS;
        int px = Mth.clamp(x, 2, Math.max(2, screenWidth - WIDTH - 2));
        int py = Mth.clamp(y - 10, 2, Math.max(2, screenHeight - height - 2));
        x = px;
        y = py + 10;
        scroll = Mth.clamp(scroll, 0, Math.max(0, body - (height - 26 - ACTIONS)));

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500);
        Ui.round(graphics, px - 1, py + 1, WIDTH + 2, height + 2, 7, 0x50000000);
        Ui.round(graphics, px, py, WIDTH, height, 6, theme.rail);
        Ui.icon(graphics, Ui.SHIELD, px + 9, py + 8, theme.accent);
        graphics.drawString(font, Component.translatable("arcadiachat.history.title", name).withStyle(UiFont.bold()), px + 21, py + 8, theme.strong, false);
        boolean closeHover = over(mouseX, mouseY, px + WIDTH - 20, py + 4, 16, 16);
        if (closeHover) {
            Ui.round(graphics, px + WIDTH - 20, py + 4, 16, 16, 3, theme.hover);
        }
        Ui.icon(graphics, Ui.CLOSE, px + WIDTH - 16, py + 8, closeHover ? theme.strong : theme.muted);
        graphics.fill(px + 8, py + 22, px + WIDTH - 8, py + 23, theme.line);

        int top = py + 26;
        int bottom = py + height - 2 - ACTIONS;
        // Les actions du staff
        int bw = (WIDTH - 16 - 3 * 4) / 4;
        for (int i = 0; i < BUTTONS.length; i++) {
            int bx = px + 8 + i * (bw + 4);
            int by = py + height - ACTIONS + 2;
            boolean hover = over(mouseX, mouseY, bx, by, bw, 16);
            Ui.round(graphics, bx, by, bw, 16, 3, hover ? theme.hover : theme.ground);
            String label = Component.translatable("arcadiachat.history." + BUTTONS[i][0]).getString();
            Ui.center(graphics, font, Ui.fit(font, label, bw - 4), bx + bw / 2, by + 4, i == 2 ? TYPE_BAN : theme.text);
        }
        if (data == null) {
            boolean late = System.currentTimeMillis() - openedAt > TIMEOUT_MS;
            graphics.drawString(font, Component.translatable(late ? "arcadiachat.history.unavailable" : "arcadiachat.history.loading"), px + 10, top + 4, theme.muted, false);
        } else if (!data.found()) {
            graphics.drawString(font, Component.translatable("arcadiachat.history.unknown"), px + 10, top + 4, theme.muted, false);
        } else if (entries.isEmpty()) {
            graphics.drawString(font, Component.translatable("arcadiachat.history.empty"), px + 10, top + 4, theme.muted, false);
        } else {
            graphics.enableScissor(px, top, px + WIDTH, bottom);
            int cy = top + 2 - scroll;
            for (TabsProtocol.HistoryEntry entry : entries) {
                int h = cardHeight(font, entry);
                if (cy + h > top && cy < bottom) {
                    drawEntry(graphics, font, theme, date, entry, px + 6, cy, h - 4);
                }
                cy += h;
            }
            graphics.disableScissor();
            if (content > height - 26 - ACTIONS) {
                int view = height - 26 - ACTIONS;
                int thumb = Math.max(12, view * view / content);
                int ty = top + (view - thumb) * scroll / Math.max(1, content - view);
                Ui.round(graphics, px + WIDTH - 4, ty, 2, thumb, 1, theme.selected);
            }
        }
        graphics.pose().popPose();
    }

    private static int cardHeight(Font font, TabsProtocol.HistoryEntry entry) {
        int lines = Math.max(1, Math.min(3, font.split(Component.literal(entry.reason()), WIDTH - 32).size()));
        return 30 + lines * 10;
    }

    private static void drawEntry(GuiGraphics graphics, Font font, ChatTheme theme, SimpleDateFormat date, TabsProtocol.HistoryEntry entry,
                                  int left, int top, int h) {
        int w = WIDTH - 12;
        int color = typeColor(entry.type());
        Ui.round(graphics, left, top, w, h, 4, theme.ground);
        Ui.round(graphics, left, top + 3, 3, h - 6, 1, color);
        String type = Component.translatable("arcadiachat.history.type." + entry.type()).getString();
        int tagW = font.width(type) + 6;
        Ui.round(graphics, left + 9, top + 4, tagW, 10, 2, (color & 0xFFFFFF) | 0x30000000);
        graphics.drawString(font, type, left + 12, top + 5, color, false);
        String when = date.format(new Date(entry.createdAt()));
        graphics.drawString(font, when, left + w - 6 - font.width(when), top + 5, theme.muted, false);
        int ry = top + 17;
        List<FormattedCharSequence> lines = font.split(Component.literal(entry.reason()), w - 20);
        for (int i = 0; i < Math.min(3, lines.size()); i++) {
            graphics.drawString(font, lines.get(i), left + 9, ry, theme.text, false);
            ry += 10;
        }
        if (lines.isEmpty()) {
            ry += 10;
        }
        String state;
        if ("WARN".equals(entry.type()) || "NOTE".equals(entry.type())) {
            state = "";
        } else if (entry.inEffect()) {
            state = entry.expiresAt() == 0 ? Component.translatable("arcadiachat.history.active_permanent").getString()
                    : Component.translatable("arcadiachat.history.active_until",
                            new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ROOT).format(new Date(entry.expiresAt()))).getString();
        } else {
            state = Component.translatable("arcadiachat.history.ended").getString();
        }
        String by = Component.translatable("arcadiachat.history.by", entry.actor()).getString();
        graphics.drawString(font, Ui.fit(font, state.isEmpty() ? by : by + " · " + state, w - 18), left + 9, ry + 1,
                entry.inEffect() && !state.isEmpty() ? color : theme.muted, false);
    }

    private static boolean over(double mouseX, double mouseY, int left, int top, int w, int h) {
        return mouseX >= left && mouseX < left + w && mouseY >= top && mouseY < top + h;
    }

    /** Un clic ouvert : la croix ou a cote ferme ; dedans, rien ne passe a l'ecran dessous. */
    public static boolean click(double mouseX, double mouseY) {
        if (!isOpen()) {
            return false;
        }
        int py = y - 10;
        if (!over(mouseX, mouseY, x, py, WIDTH, height) || over(mouseX, mouseY, x + WIDTH - 20, py + 4, 16, 16)) {
            close();
            return true;
        }
        int bw = (WIDTH - 16 - 3 * 4) / 4;
        for (int i = 0; i < BUTTONS.length; i++) {
            int bx = x + 8 + i * (bw + 4);
            if (over(mouseX, mouseY, bx, py + height - ACTIONS + 2, bw, 16)) {
                String command = String.format(BUTTONS[i][1], name);
                close();
                Minecraft.getInstance().setScreen(new net.minecraft.client.gui.screens.ChatScreen(command));
                return true;
            }
        }
        return true;
    }

    public static boolean scroll(double mouseX, double mouseY, double amount) {
        if (!isOpen() || !over(mouseX, mouseY, x, y - 10, WIDTH, height)) {
            return false;
        }
        scroll -= (int) (amount * 20);
        return true;
    }
}
