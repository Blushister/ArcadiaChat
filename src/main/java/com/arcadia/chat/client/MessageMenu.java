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

    private MessageMenu() {
    }

    public static boolean isOpen() {
        return !items.isEmpty();
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
        }
        if (TabState.isJoinable(origin)) {
            TabsProtocol.ServerInfo server = TabState.server(origin);
            list.add(new Item(Component.translatable("arcadiachat.menu.join", server.name()), () -> ServerJoin.confirm(origin)));
        }
        String text = message.content().getString();
        list.add(new Item(Component.translatable("arcadiachat.menu.copy"),
                () -> mc.keyboardHandler.setClipboard(text)));
        if (other && TabState.isStaff()) {
            list.add(new Item(Component.translatable("arcadiachat.menu.history", sender), () -> command("arcadia_history " + sender)));
            list.add(new Item(Component.translatable("arcadiachat.menu.mute", sender), () -> {
                input.setValue("/arcadia_tempmute " + sender + " 1h ");
                screen.setFocused(input);
            }));
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
