package com.arcadia.chat.client;

import java.util.HashSet;
import java.util.Set;

import com.arcadia.chat.ChatConfig;
import com.arcadia.chat.net.TabsProtocol;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

/**
 * Choisit la version d'un message a montrer : la traduction si le message n'est pas dans la langue du joueur, sinon
 * l'original. Ses propres messages restent tels qu'il les a ecrits, et chaque message peut revenir a l'original d'un clic.
 */
public final class ChatTranslations {

    /** Messages que le joueur a remis en version originale, le temps de la session. */
    private static final Set<Long> ORIGINALS = new HashSet<>();

    private ChatTranslations() {
    }

    /** Langue voulue ("fr", "en"), ou vide : pas de traduction. */
    private static String target() {
        String choice = ChatConfig.TRANSLATION.get();
        if ("off".equals(choice)) {
            return "";
        }
        if (!"auto".equals(choice)) {
            return choice;
        }
        return Minecraft.getInstance().options.languageCode.startsWith("fr") ? "fr" : "en";
    }

    /** Ce message a-t-il une traduction utile a ce joueur ? */
    public static boolean applies(TabsProtocol.StoredMessage message) {
        var player = Minecraft.getInstance().player;
        boolean own = player != null && player.getGameProfile().getName().equalsIgnoreCase(message.sender());
        String target = target();
        return !own && !target.isEmpty() && !message.lang().isEmpty() && !message.lang().equals(target)
                && !message.translated().getString().isEmpty() && !ChatRooms.untranslated(message);
    }

    public static boolean showsTranslation(TabsProtocol.StoredMessage message) {
        return applies(message) && !ORIGINALS.contains(message.id());
    }

    public static void toggle(TabsProtocol.StoredMessage message) {
        if (!ORIGINALS.remove(message.id())) {
            ORIGINALS.add(message.id());
        }
    }

    public static void clear() {
        ORIGINALS.clear();
    }

    /** Le texte montre dans l'ecran des salons. */
    public static Component display(TabsProtocol.StoredMessage message) {
        return showsTranslation(message) ? message.translated() : message.body();
    }

    /** Le texte montre, pour le copier. */
    public static String text(TabsProtocol.StoredMessage message) {
        return showsTranslation(message) ? message.translated().getString() : message.raw();
    }

    /** "Traduit du francais", pour la bulle d'aide. */
    public static Component source(TabsProtocol.StoredMessage message) {
        return Component.translatable("arcadiachat.translation.from." + message.lang());
    }

    /** Le texte a mettre dans le chat habituel : traduit, avec l'original au survol. */
    public static Component body(TabsProtocol.StoredMessage message) {
        if (!applies(message)) {
            return message.body();
        }
        Component hover = Component.translatable("arcadiachat.translation.original").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(" ").append(message.body()).withStyle(ChatFormatting.WHITE));
        return Component.empty().append(message.translated())
                .withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
    }
}
