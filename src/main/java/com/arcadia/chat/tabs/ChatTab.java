package com.arcadia.chat.tabs;

import java.util.Locale;

import com.arcadia.chat.ChatConfig;
import net.minecraft.network.chat.Component;

/**
 * Un onglet du chat : les onglets fixes (Tout, Global, Serveur, Prive, Staff, Systeme), et les onglets
 * ouverts a la volee pour une conversation privee ou un canal thematique.
 */
public record ChatTab(Kind kind, String key) {

    public enum Kind {
        ALL("all"), GLOBAL("global"), SERVER("server"), PRIVATE("private"), STAFF("staff"), SYSTEM("system"),
        CONVERSATION("conversation"), CHANNEL("channel");

        private final String id;

        Kind(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static Kind byId(String id) {
            for (Kind kind : values()) {
                if (kind.id.equals(id)) {
                    return kind;
                }
            }
            return null;
        }
    }

    public static final ChatTab ALL = new ChatTab(Kind.ALL, "");

    public static ChatTab fixed(Kind kind) {
        return new ChatTab(kind, "");
    }

    public static ChatTab conversation(String partner) {
        return new ChatTab(Kind.CONVERSATION, partner);
    }

    public static ChatTab channel(String name) {
        return new ChatTab(Kind.CHANNEL, name.toLowerCase(Locale.ROOT));
    }

    /** Identifiant stable (pour retenir le dernier onglet) : "all", "conversation:Dev2", "channel:commerce". */
    public String id() {
        return key.isEmpty() ? kind.id() : kind.id() + ":" + key;
    }

    public static ChatTab parse(String id) {
        int sep = id.indexOf(':');
        Kind kind = Kind.byId(sep < 0 ? id : id.substring(0, sep));
        if (kind == null) {
            return ALL;
        }
        return new ChatTab(kind, sep < 0 ? "" : id.substring(sep + 1));
    }

    public boolean isDynamic() {
        return kind == Kind.CONVERSATION || kind == Kind.CHANNEL;
    }

    public boolean accepts(Category category, String entryKey) {
        return switch (kind) {
            case ALL -> category != Category.HIDDEN && (category != Category.SYSTEM || ChatConfig.SYSTEM_IN_ALL.get());
            case GLOBAL -> category == Category.REMOTE || category == Category.LOCAL;
            case SERVER -> category == Category.LOCAL;
            case PRIVATE -> category == Category.PRIVATE;
            case STAFF -> category == Category.STAFF;
            case SYSTEM -> category == Category.SYSTEM;
            case CONVERSATION -> category == Category.PRIVATE && key.equalsIgnoreCase(entryKey);
            case CHANNEL -> category == Category.CHANNEL && key.equalsIgnoreCase(entryKey);
        };
    }

    public Component label(String serverName) {
        return switch (kind) {
            case SERVER -> serverName.isBlank() ? Component.translatable("arcadiachat.tab.server")
                    : Component.literal(serverName);
            case CONVERSATION -> Component.literal("@" + key);
            case CHANNEL -> Component.literal("#" + key);
            default -> Component.translatable("arcadiachat.tab." + kind.id());
        };
    }
}
