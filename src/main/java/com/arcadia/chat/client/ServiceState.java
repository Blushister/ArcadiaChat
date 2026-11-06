package com.arcadia.chat.client;

import com.arcadia.chat.ChatConfig;
import com.arcadia.chat.net.TabsProtocol;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Etat des services du serveur : la traduction et le vocal entre serveurs. Une panne se dit en une ligne dans le chat (et dans
 * un bandeau de l'ecran des salons) au lieu d'un silence ; le retour aussi. -1 tant que le serveur n'a rien dit.
 */
public final class ServiceState {

    public static final int OFF = 0;
    public static final int UP = 1;
    public static final int DOWN = 2;
    public static final int UNKNOWN = -1;

    private static int translator = UNKNOWN;
    private static int relay = UNKNOWN;

    private ServiceState() {
    }

    public static void onStatus(TabsProtocol.ServiceStatus status) {
        int previousTranslator = translator;
        int previousRelay = relay;
        translator = status.translator();
        relay = status.relay();
        boolean translating = !"off".equals(ChatConfig.TRANSLATION.get());
        if (translating) {
            announce(previousTranslator, translator, "translator");
        }
        if (ChatVoice.available()) {
            announce(previousRelay, relay, "relay");
        }
    }

    private static void announce(int before, int now, String service) {
        // A la connexion, on ne signale qu'une panne ; ensuite, chaque changement.
        boolean down = now == DOWN && before != DOWN;
        boolean back = now == UP && before == DOWN;
        if (!down && !back) {
            return;
        }
        Minecraft.getInstance().gui.getChat().addMessage(Component.translatable("arcadiachat.service." + service + (down ? "_down" : "_up"))
                .withStyle(down ? ChatFormatting.GOLD : ChatFormatting.GRAY));
    }

    public static void clear() {
        translator = UNKNOWN;
        relay = UNKNOWN;
    }

    public static int translator() {
        return translator;
    }

    public static int relay() {
        return relay;
    }

    /** Ce qui ne marche pas en ce moment et concerne le joueur, pour le bandeau de l'ecran des salons ; null si tout va bien. */
    public static Component problem() {
        if (translator == DOWN && !"off".equals(ChatConfig.TRANSLATION.get())) {
            return Component.translatable("arcadiachat.service.translator_down");
        }
        if (relay == DOWN && ChatVoice.available() && !ChatVoice.current().isEmpty()) {
            return Component.translatable("arcadiachat.service.relay_down");
        }
        return null;
    }
}
