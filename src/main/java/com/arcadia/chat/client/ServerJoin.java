package com.arcadia.chat.client;

import com.arcadia.chat.net.TabsProtocol;
import com.arcadia.chat.tabs.TabState;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * "/arcadiachat join <serveur>" (commande du client, declenchee en cliquant sur un message venu d'un autre
 * serveur) : propose de rejoindre ce serveur. Seuls les serveurs annonces par ArcadiaBridge sont accessibles.
 */
public final class ServerJoin {

    private ServerJoin() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("arcadiachat")
                .then(Commands.literal("join")
                        .then(Commands.argument("server", StringArgumentType.word())
                                .executes(ctx -> {
                                    Minecraft mc = Minecraft.getInstance();
                                    String id = StringArgumentType.getString(ctx, "server");
                                    // Apres la commande : le chat se ferme d'abord, puis on ouvre la confirmation.
                                    mc.tell(() -> confirm(id));
                                    return 1;
                                }))));
    }

    private static void confirm(String id) {
        Minecraft mc = Minecraft.getInstance();
        if (!TabState.isJoinable(id)) {
            return;
        }
        TabsProtocol.ServerInfo server = TabState.server(id);
        mc.setScreen(new ConfirmScreen(accepted -> {
            if (accepted) {
                join(server);
            } else {
                mc.setScreen(null);
            }
        }, Component.translatable("arcadiachat.join.title", server.name()),
                Component.translatable("arcadiachat.join.text", server.name())));
    }

    private static void join(TabsProtocol.ServerInfo server) {
        Minecraft mc = Minecraft.getInstance();
        JoinMultiplayerScreen parent = new JoinMultiplayerScreen(new TitleScreen());
        if (mc.level != null) {
            mc.level.disconnect();
        }
        mc.disconnect(parent);
        ServerData data = new ServerData(server.name(), server.address(), ServerData.Type.OTHER);
        ConnectScreen.startConnecting(parent, mc, ServerAddress.parseString(server.address()), data, false, null);
    }
}
