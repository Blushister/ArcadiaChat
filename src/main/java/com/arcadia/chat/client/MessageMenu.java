package com.arcadia.chat.client;

import java.util.ArrayList;
import java.util.List;

import com.arcadia.chat.mixin.ChatScreenAccessor;
import com.arcadia.chat.net.ClientNetwork;
import com.arcadia.chat.net.TabsProtocol;
import com.arcadia.chat.tabs.ChatView;
import com.arcadia.chat.tabs.TabState;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;

/**
 * Menu du clic droit sur un message : ecrire en prive a son auteur, le mentionner, le signaler, rejoindre le
 * serveur d'ou vient le message, copier le texte ; pour le staff, l'historique et le mute de l'auteur.
 */
public final class MessageMenu {

    private static final int ITEM_HEIGHT = 12;
    private static final int PADDING = 4;
    private static final int BACKGROUND = 0xFF101010;
    private static final int BORDER = 0xFF5A4A8A;
    private static final int HOVER = 0xFF3A2F5F;
    private static final int TEXT = 0xFFE0E0E0;
    private static final int TITLE = 0xFFFFAA00;

    private record Item(Component label, Runnable action) {
    }

    private static List<Item> items = List.of();
    /** Empreinte du message pour les reactions, ou null (pas de rangee de reactions). */
    private static String reactionKey;
    private static final int CELL = 18;
    private static Component title;
    private static int x;
    private static int y;
    private static int width;

    private MessageMenu() {
    }

    public static boolean isOpen() {
        return !items.isEmpty();
    }

    public static void close() {
        items = List.of();
        reactionKey = null;
    }

    /** Lignes au-dessus des actions : le titre, puis la rangee de reactions s'il y en a une. */
    private static int headerLines() {
        return reactionKey == null ? 1 : 2;
    }

    /**
     * Clic droit dans le chat ouvert : ouvre le menu du message sous la souris.
     *
     * @return true si un menu s'est ouvert
     */
    public static boolean open(ChatScreen screen, double mouseX, double mouseY) {
        Minecraft mc = Minecraft.getInstance();
        GuiMessage message = ((ChatView) mc.gui.getChat()).arcadiachat$messageAt(mouseX, mouseY);
        if (message == null) {
            return false;
        }
        TabState.Meta meta = TabState.metaOf(message);
        String sender = meta == null ? "" : meta.sender();
        String origin = meta == null ? "" : meta.origin();
        String self = mc.player == null ? "" : mc.player.getGameProfile().getName();
        boolean other = !sender.isBlank() && !sender.equalsIgnoreCase(self);
        EditBox input = ((ChatScreenAccessor) screen).arcadiachat$input();

        List<Item> list = new ArrayList<>();
        if (other) {
            list.add(new Item(Component.translatable("arcadiachat.menu.private", sender), () -> {
                TabState.openConversation(sender);
                screen.setFocused(input);
            }));
            list.add(new Item(Component.translatable("arcadiachat.menu.mention", sender), () -> {
                input.insertText("@" + sender + " ");
                screen.setFocused(input);
            }));
            list.add(new Item(Component.translatable("arcadiachat.menu.report", sender), () -> {
                input.setValue("/report " + sender + " ");
                screen.setFocused(input);
            }));
            if (hasCommand("ignore")) {
                list.add(new Item(Component.translatable("arcadiachat.menu.ignore", sender), () -> command("ignore " + sender)));
            }
        }
        if (TabState.isJoinable(origin)) {
            TabsProtocol.ServerInfo server = TabState.server(origin);
            list.add(new Item(Component.translatable("arcadiachat.menu.join", server.name()), () -> ServerJoin.confirm(origin)));
        }
        String text = Emojis.toOriginal(message.content().getString());
        list.add(new Item(Component.translatable("arcadiachat.menu.copy"),
                () -> mc.keyboardHandler.setClipboard(text)));
        if (other && TabState.isStaff()) {
            list.add(new Item(Component.translatable("arcadiachat.menu.history", sender), () -> command("arcadia_history " + sender)));
            list.add(new Item(Component.translatable("arcadiachat.menu.mute", sender), () -> {
                input.setValue("/arcadia_tempmute " + sender + " 1h ");
                screen.setFocused(input);
            }));
        }

        Font font = mc.font;
        reactionKey = meta != null && meta.key() != null && ClientNetwork.supportsReactions() ? meta.key() : null;
        title = other ? Component.literal(sender) : Component.translatable("arcadiachat.menu.title");
        int w = Math.max(font.width(title), reactionKey == null ? 0 : Reactions.EMOJIS.size() * CELL);
        for (Item item : list) {
            w = Math.max(w, font.width(item.label()));
        }
        width = w + PADDING * 2;
        int height = (list.size() + headerLines()) * ITEM_HEIGHT + PADDING;
        // Le menu reste dans l'ecran.
        x = (int) Math.min(mouseX, screen.width - width - 2);
        y = (int) Math.max(2, Math.min(mouseY - height, screen.height - height - 2));
        items = list;
        return true;
    }

    public static void render(GuiGraphics graphics, int mouseX, int mouseY) {
        if (items.isEmpty()) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        int height = (items.size() + headerLines()) * ITEM_HEIGHT + PADDING;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, BORDER);
        graphics.fill(x, y, x + width, y + height, BACKGROUND);
        graphics.drawString(font, title, x + PADDING, y + 3, TITLE, false);
        if (reactionKey != null) {
            String self = selfName();
            int rowTop = y + ITEM_HEIGHT + 2;
            for (int i = 0; i < Reactions.EMOJIS.size(); i++) {
                String emoji = Reactions.EMOJIS.get(i);
                int cellX = x + PADDING + i * CELL;
                boolean hover = mouseX >= cellX && mouseX < cellX + CELL && mouseY >= rowTop && mouseY < rowTop + ITEM_HEIGHT;
                if (hover || Reactions.hasReacted(reactionKey, emoji, self)) {
                    graphics.fill(cellX, rowTop, cellX + CELL - 2, rowTop + ITEM_HEIGHT, hover ? HOVER : 0xFF2A4A2A);
                }
                graphics.drawString(font, Reactions.glyph(emoji), cellX + 3, rowTop + 2, 0xFFFFFFFF, false);
            }
        }
        for (int i = 0; i < items.size(); i++) {
            int top = y + (i + headerLines()) * ITEM_HEIGHT + 2;
            if (mouseX >= x && mouseX < x + width && mouseY >= top && mouseY < top + ITEM_HEIGHT) {
                graphics.fill(x, top, x + width, top + ITEM_HEIGHT, HOVER);
            }
            graphics.drawString(font, items.get(i).label(), x + PADDING, top + 2, TEXT, false);
        }
        graphics.pose().popPose();
    }

    /**
     * Clic quand le menu est ouvert : lance l'action touchee, et ferme le menu dans tous les cas.
     *
     * @return true : le clic est consomme
     */
    public static boolean click(double mouseX, double mouseY) {
        if (items.isEmpty()) {
            return false;
        }
        List<Item> current = items;
        String key = reactionKey;
        int header = headerLines();
        close();
        if (key != null) {
            int rowTop = y + ITEM_HEIGHT + 2;
            if (mouseY >= rowTop && mouseY < rowTop + ITEM_HEIGHT && mouseX >= x + PADDING) {
                int cell = (int) ((mouseX - x - PADDING) / CELL);
                if (cell >= 0 && cell < Reactions.EMOJIS.size()) {
                    String emoji = Reactions.EMOJIS.get(cell);
                    // Deja mise : un nouveau clic la retire.
                    ClientNetwork.sendReaction(key, emoji, !Reactions.hasReacted(key, emoji, selfName()));
                }
                return true;
            }
        }
        for (int i = 0; i < current.size(); i++) {
            int top = y + (i + header) * ITEM_HEIGHT + 2;
            if (mouseX >= x && mouseX < x + width && mouseY >= top && mouseY < top + ITEM_HEIGHT) {
                current.get(i).action().run();
                break;
            }
        }
        return true;
    }

    /** Le serveur propose-t-il cette commande ? (ArcadiaBridge assez recent) */
    private static boolean hasCommand(String name) {
        var connection = Minecraft.getInstance().getConnection();
        return connection != null && connection.getCommands().getRoot().getChild(name) != null;
    }

    private static String selfName() {
        var player = Minecraft.getInstance().player;
        return player == null ? "" : player.getGameProfile().getName();
    }

    private static void command(String command) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.sendCommand(command);
        }
    }
}
