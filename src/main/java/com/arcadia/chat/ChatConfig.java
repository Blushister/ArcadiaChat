package com.arcadia.chat;

import java.util.List;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Reglages du joueur, modifiables depuis le menu des mods (bouton Config d'ArcadiaChat). */
public final class ChatConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.ConfigValue<List<? extends String>> TAB_ORDER;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> HIDDEN_TABS;
    public static final ModConfigSpec.BooleanValue SYSTEM_IN_ALL;
    public static final ModConfigSpec.BooleanValue REMEMBER_TAB;
    public static final ModConfigSpec.ConfigValue<String> LAST_TAB;
    public static final ModConfigSpec.IntValue HISTORY;
    public static final ModConfigSpec.BooleanValue ROOMS_VIEW;
    public static final ModConfigSpec.ConfigValue<String> ROOMS_THEME;
    public static final ModConfigSpec.ConfigValue<String> TRANSLATION;

    public static final ModConfigSpec.BooleanValue MENTIONS;
    public static final ModConfigSpec.BooleanValue MENTION_PLAIN_NAME;
    public static final ModConfigSpec.BooleanValue MENTION_SOUND;

    public static final ModConfigSpec.BooleanValue TIMESTAMPS;
    public static final ModConfigSpec.BooleanValue TIMESTAMP_HOVER;
    public static final ModConfigSpec.BooleanValue HEADS;
    public static final ModConfigSpec.BooleanValue GROUP_REPEATS;
    public static final ModConfigSpec.BooleanValue VOICE_HUD;
    public static final ModConfigSpec.BooleanValue ABSENCE_SUMMARY;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> UNTRANSLATED_ROOMS;
    public static final ModConfigSpec.BooleanValue ADDON_HINT_SEEN;
    public static final ModConfigSpec.BooleanValue TOASTS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> MUTED_ROOMS;

    public static final ModConfigSpec.BooleanValue UNSIGNED_CHAT;
    public static final ModConfigSpec.BooleanValue HIDE_UNSECURE_WARNING;

    public static final ModConfigSpec.BooleanValue BUBBLES;
    public static final ModConfigSpec.IntValue BUBBLE_DISTANCE;

    public static final ModConfigSpec.BooleanValue SHOW_TYPING;
    public static final ModConfigSpec.BooleanValue SEND_TYPING;

    public static final ModConfigSpec.ConfigValue<List<? extends String>> FILTERS;

    /** Onglets fixes, dans l'ordre par defaut. */
    public static final List<String> DEFAULT_ORDER = List.of("all", "global", "server", "private", "team", "staff", "system");

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("tabs");
        TAB_ORDER = b.comment("Ordre des onglets : all, global, server, private, team, staff, system.",
                        "Les onglets des canaux et des conversations privees se placent a la suite.")
                .defineListAllowEmpty("order", DEFAULT_ORDER, () -> "", o -> o instanceof String s && DEFAULT_ORDER.contains(s));
        HIDDEN_TABS = b.comment("Onglets a masquer (memes noms que ci-dessus). L'onglet \"all\" ne peut pas etre masque.")
                .defineListAllowEmpty("hidden", List.of(), () -> "", o -> o instanceof String s && DEFAULT_ORDER.contains(s));
        SYSTEM_IN_ALL = b.comment("Afficher aussi les messages systeme (annonces, reponses des commandes) dans l'onglet Tout.")
                .define("systemInAll", true);
        REMEMBER_TAB = b.comment("Rouvrir le dernier onglet utilise a la connexion suivante.")
                .define("rememberTab", true);
        LAST_TAB = b.comment("Dernier onglet utilise (mis a jour automatiquement).")
                .define("lastTab", "all");
        HISTORY = b.comment("Nombre de messages gardes en memoire (tous onglets confondus) et que l'on peut faire",
                        "defiler dans le chat (Minecraft s'arrete a 100).")
                .defineInRange("history", 1000, 100, 20000);
        ROOMS_VIEW = b.comment("Ouvrir l'ecran facon Discord avec la touche du chat, au lieu du chat habituel",
                        "(sur les serveurs qui le proposent). L'icone en haut a gauche du chat change ce choix.")
                .define("roomsView", false);
        ROOMS_THEME = b.comment("Theme de l'ecran des salons : dark (sombre), light (clair) ou arcadia (cuivre et patine).")
                .define("roomsTheme", "arcadia", o -> "dark".equals(o) || "light".equals(o) || "arcadia".equals(o));
        TRANSLATION = b.comment("Traduction des messages : auto (langue du jeu), fr, en ou off (toujours l'original).")
                .define("translation", "auto", o -> "auto".equals(o) || "fr".equals(o) || "en".equals(o) || "off".equals(o));
        b.pop();

        b.push("mentions");
        MENTIONS = b.comment("Mettre en avant les messages qui te mentionnent (@pseudo).")
                .define("enabled", true);
        MENTION_PLAIN_NAME = b.comment("Compter aussi ton pseudo ecrit sans @.")
                .define("plainName", false);
        MENTION_SOUND = b.comment("Jouer un son quand on te mentionne.")
                .define("sound", true);
        b.pop();

        b.push("display");
        TIMESTAMPS = b.comment("Afficher l'heure devant chaque message.")
                .define("timestamps", false);
        TIMESTAMP_HOVER = b.comment("Afficher l'heure d'un message en le survolant.")
                .define("timestampHover", true);
        HEADS = b.comment("Afficher la tete du joueur devant ses messages.")
                .define("heads", true);
        GROUP_REPEATS = b.comment("Regrouper les messages identiques qui se suivent en un seul, avec le nombre de repetitions (x3).")
                .define("groupRepeats", true);
        UNTRANSLATED_ROOMS = b.comment("Salons dont les messages ne sont jamais traduits (par exemple staff).")
                .defineListAllowEmpty("untranslatedRooms", List.of(), () -> "", o -> o instanceof String);
        ABSENCE_SUMMARY = b.comment("A la connexion, une ligne resume ce qui s'est dit pendant l'absence (mentions, messages prives).")
                .define("absenceSummary", true);
        ADDON_HINT_SEEN = b.comment("La proposition de l'addon ArcadiaChat Rooms a deja ete montree.")
                .define("addonHintSeen", false);
        VOICE_HUD = b.comment("En jeu, afficher le salon vocal et qui parle (en haut a gauche).")
                .define("voiceHud", true);
        TOASTS = b.comment("En jeu, une petite alerte pour les messages prives et les mentions.")
                .define("toasts", true);
        MUTED_ROOMS = b.comment("Salons rendus muets dans l'ecran des salons : ni compteur, ni alerte.")
                .defineListAllowEmpty("mutedRooms", List.of(), () -> "", o -> o instanceof String);
        b.pop();

        b.push("privacy");
        UNSIGNED_CHAT = b.comment("Ne pas signer tes messages quand le serveur ne l'exige pas : ils ne peuvent alors pas",
                        "etre signales a Mojang. Sur un serveur qui exige la signature, rien ne change.")
                .define("unsignedChat", true);
        HIDE_UNSECURE_WARNING = b.comment("Ne pas afficher l'avertissement \"Le chat ne peut pas etre verifie\" a la connexion.")
                .define("hideUnsecureWarning", true);
        b.pop();

        b.push("bubbles");
        BUBBLES = b.comment("Afficher une petite bulle au-dessus de la tete des joueurs proches quand ils ecrivent dans le chat.")
                .define("visible", true);
        BUBBLE_DISTANCE = b.comment("Distance maximale d'affichage des bulles, en blocs.")
                .defineInRange("distance", 24, 4, 64);
        b.pop();

        b.push("typing");
        SHOW_TYPING = b.comment("Afficher qui est en train d'ecrire (quand le chat est ouvert).")
                .define("show", true);
        SEND_TYPING = b.comment("Signaler aux autres joueurs que tu es en train d'ecrire.")
                .define("send", true);
        b.pop();

        b.push("filters");
        FILTERS = b.comment("Messages systeme a masquer : tout message contenant l'un de ces textes (sans tenir compte",
                        "des majuscules) n'apparait dans aucun onglet. Exemple : [\"Nettoyage des objets\"]")
                .defineListAllowEmpty("hide", List.of(), () -> "", o -> o instanceof String s && !s.isBlank());
        b.pop();

        SPEC = b.build();
    }

    private ChatConfig() {
    }
}
