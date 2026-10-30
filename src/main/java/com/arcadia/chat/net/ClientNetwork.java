package com.arcadia.chat.net;

import java.util.List;

import com.arcadia.chat.client.ChannelsScreen;
import com.arcadia.chat.client.StaffPanelScreen;
import com.arcadia.chat.tabs.Category;
import com.arcadia.chat.tabs.TabState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
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
        registrar.playToClient(TabsProtocol.Hello.TYPE, TabsProtocol.Hello.STREAM_CODEC,
                (hello, context) -> TabState.enable(hello));
        registrar.playToClient(TabsProtocol.Servers.TYPE, TabsProtocol.Servers.STREAM_CODEC,
                (payload, context) -> TabState.updateServers(payload.servers()));
        registrar.playToClient(TabsProtocol.Channels.TYPE, TabsProtocol.Channels.STREAM_CODEC, (payload, context) -> {
            TabState.updateChannels(payload.channels());
            ChannelsScreen screen = ChannelsScreen.current();
            if (screen != null) {
                screen.refreshSoon();
            }
        });
        registrar.playToClient(TabsProtocol.Reaction.TYPE, TabsProtocol.Reaction.STREAM_CODEC,
                (payload, context) -> TabState.onReaction(payload.key(), payload.emoji(), payload.reactor(), payload.add()));
        registrar.playToServer(TabsProtocol.React.TYPE, TabsProtocol.React.STREAM_CODEC, (payload, context) -> { });
        registrar.playToClient(TabsProtocol.RemotePlayers.TYPE, TabsProtocol.RemotePlayers.STREAM_CODEC,
                (payload, context) -> TabState.updateRemotePlayers(payload.names()));
        registrar.playToClient(TabsProtocol.ChannelDirectory.TYPE, TabsProtocol.ChannelDirectory.STREAM_CODEC,
                (payload, context) -> {
                    ChannelsScreen screen = ChannelsScreen.current();
                    if (screen != null) {
                        screen.onDirectory(payload);
                    }
                });
        registrar.playToClient(TabsProtocol.Line.TYPE, TabsProtocol.Line.STREAM_CODEC, ClientNetwork::onLine);
        registrar.playToClient(TabsProtocol.TypingState.TYPE, TabsProtocol.TypingState.STREAM_CODEC,
                (payload, context) -> TabState.onTyping(payload.name(), payload.typing(), payload.remote()));
        // Traites par ArcadiaBridge, cote serveur.
        registrar.playToServer(TabsProtocol.Ready.TYPE, TabsProtocol.Ready.STREAM_CODEC, (payload, context) -> { });
        registrar.playToServer(TabsProtocol.Scope.TYPE, TabsProtocol.Scope.STREAM_CODEC, (payload, context) -> { });
        registrar.playToServer(TabsProtocol.Send.TYPE, TabsProtocol.Send.STREAM_CODEC, (payload, context) -> { });
        registrar.playToServer(TabsProtocol.Typing.TYPE, TabsProtocol.Typing.STREAM_CODEC, (payload, context) -> { });
        registrar.playToServer(TabsProtocol.ChannelsRequest.TYPE, TabsProtocol.ChannelsRequest.STREAM_CODEC,
                (payload, context) -> { });
    }

    private static void onLine(TabsProtocol.Line line, IPayloadContext context) {
        String channel = line.channel();
        Category category;
        String key = "";
        if (channel.startsWith(TabsProtocol.CHANNEL_PREFIX)) {
            category = Category.CHANNEL;
            key = channel.substring(TabsProtocol.CHANNEL_PREFIX.length());
        } else {
            category = switch (channel) {
                case TabsProtocol.CHANNEL_GLOBAL -> Category.REMOTE;
                case TabsProtocol.CHANNEL_LOCAL, TabsProtocol.CHANNEL_DISCORD -> Category.LOCAL;
                case TabsProtocol.CHANNEL_PRIVATE -> Category.PRIVATE;
                case TabsProtocol.CHANNEL_STAFF, TabsProtocol.CHANNEL_ALERT -> Category.STAFF;
                case TabsProtocol.CHANNEL_TEAM -> Category.TEAM;
                default -> Category.SYSTEM;
            };
            if (category == Category.PRIVATE) {
                key = line.origin();
            }
        }
        // Comme un message systeme recu du serveur : les autres mods de chat (tetes, filtres...) le voient passer.
        // Auteur et serveur d'origine servent au survol (heure, serveur) et au menu du clic droit.
        TabState.receiveTagged(category, key, line.sender(), line.origin(),
                () -> Minecraft.getInstance().getChatListener().handleSystemMessage(line.message(), false));
    }

    // ---------------------------------------------------------------- vers le serveur

    /** A la connexion : signale au serveur que ce client a ArcadiaChat (il repond par "hello"). */
    public static void sendReady() {
        send(new TabsProtocol.Ready(2));
    }

    /** Indique au serveur ou partent les messages du joueur : chat global ou ce serveur seulement. */
    public static void sendScope(boolean global) {
        send(new TabsProtocol.Scope(global));
    }

    /** Message pour l'onglet Staff ("staff") ou un canal ("#nom"). */
    public static void sendTo(String channel, String text) {
        send(new TabsProtocol.Send(channel, text.length() > 256 ? text.substring(0, 256) : text));
    }

    /** Demande la liste des canaux (ecran Canaux). */
    public static void requestChannels() {
        send(TabsProtocol.ChannelsRequest.INSTANCE);
    }

    /** Le serveur gere-t-il l'ecran Canaux ? (bridge assez recent, canaux actives) */
    public static boolean supportsChannelsScreen() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null && connection.hasChannel(TabsProtocol.ChannelsRequest.TYPE);
    }

    /** Reagit a un message (ou retire sa reaction). */
    public static void sendReaction(String key, String emoji, boolean add) {
        send(new TabsProtocol.React(key, emoji, add));
    }

    public static boolean supportsReactions() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null && connection.hasChannel(TabsProtocol.React.TYPE);
    }

    /** Panel staff : demande son contenu (le serveur repond selon les permissions du joueur). */
    public static void requestPanel() {
        send(TabsProtocol.PanelRequest.INSTANCE);
    }

    /** Action du panel staff (voir {@link TabsProtocol.PanelAction}). */
    public static void panelAction(String kind, String... args) {
        send(new TabsProtocol.PanelAction(kind, List.of(args)));
    }

    public static boolean supportsPanel() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null && connection.hasChannel(TabsProtocol.PanelRequest.TYPE);
    }

    public static void sendTyping(boolean typing) {
        send(new TabsProtocol.Typing(typing));
    }

    private static void send(CustomPacketPayload payload) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null && connection.hasChannel(payload.type())) {
            PacketDistributor.sendToServer(payload);
        }
    }
}
