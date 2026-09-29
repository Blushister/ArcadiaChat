package com.arcadia.chat.net;

import com.arcadia.chat.tabs.Category;
import com.arcadia.chat.tabs.TabState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Echanges avec ArcadiaBridge. Les paquets sont optionnels : sans le bridge, rien ne change. */
public final class ClientNetwork {

    private ClientNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(TabsProtocol.VERSION).optional();
        registrar.playToClient(TabsProtocol.Hello.TYPE, TabsProtocol.Hello.STREAM_CODEC, ClientNetwork::onHello);
        registrar.playToClient(TabsProtocol.Line.TYPE, TabsProtocol.Line.STREAM_CODEC, ClientNetwork::onLine);
        // Traites par ArcadiaBridge, cote serveur.
        registrar.playToServer(TabsProtocol.Ready.TYPE, TabsProtocol.Ready.STREAM_CODEC, (payload, context) -> { });
        registrar.playToServer(TabsProtocol.Scope.TYPE, TabsProtocol.Scope.STREAM_CODEC, (payload, context) -> { });
    }

    private static void onHello(TabsProtocol.Hello hello, IPayloadContext context) {
        TabState.enable(hello.serverName(), hello.crossServer(), hello.writesGlobal());
    }

    private static void onLine(TabsProtocol.Line line, IPayloadContext context) {
        Category category = switch (line.channel()) {
            case TabsProtocol.CHANNEL_GLOBAL -> Category.REMOTE;
            case TabsProtocol.CHANNEL_LOCAL, TabsProtocol.CHANNEL_DISCORD -> Category.LOCAL;
            case TabsProtocol.CHANNEL_PRIVATE -> Category.PRIVATE;
            default -> Category.SYSTEM;
        };
        // Comme un message systeme recu du serveur : les autres mods de chat (tetes, filtres...) le voient passer.
        TabState.receiveTagged(category,
                () -> Minecraft.getInstance().getChatListener().handleSystemMessage(line.message(), false));
    }

    /** A la connexion : signale au serveur que ce client a ArcadiaChat (il repond par "hello"). */
    public static void sendReady() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null && connection.hasChannel(TabsProtocol.Ready.TYPE)) {
            PacketDistributor.sendToServer(new TabsProtocol.Ready(1));
        }
    }

    /** Indique au serveur ou partent les messages du joueur : chat global ou ce serveur seulement. */
    public static void sendScope(boolean global) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null && connection.hasChannel(TabsProtocol.Scope.TYPE)) {
            PacketDistributor.sendToServer(new TabsProtocol.Scope(global));
        }
    }
}
