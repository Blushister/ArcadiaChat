package com.arcadia.chat.tabs;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.arcadia.chat.net.ClientNetwork;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;

/**
 * Etat du chat a onglets pendant une connexion. Tout est appele depuis le thread du client.
 *
 * <p>Les onglets ne s'activent que sur un serveur equipe d'ArcadiaBridge (paquet "hello") : ailleurs,
 * le chat de Minecraft fonctionne comme d'habitude.
 */
public final class TabState {

    /**
     * Messages conserves par connexion, tous onglets confondus. Avec LongerChatHistory, les joueurs s'attendent
     * a remonter loin : on garde davantage.
     */
    private static final int HISTORY = ModList.get().isLoaded("longerchathistory") ? 5000 : 500;

    private record Entry(Category category, GuiMessage message) {
    }

    private static boolean enabled;
    private static boolean crossServer;
    private static String serverName = "";
    /** Onglet actif, conserve d'une connexion a l'autre. */
    private static ChatTab active = ChatTab.ALL;
    /** Le joueur ecrit-il dans le chat global ? (hors onglet Serveur, on garde le dernier choix) */
    private static boolean writesGlobal = true;

    private static final Deque<Entry> history = new ArrayDeque<>();
    private static final Map<ChatTab, Integer> unread = new EnumMap<>(ChatTab.class);
    /** Categorie du prochain message ajoute au chat, fixee juste avant son ajout. */
    private static Category pending;
    /** Categorie imposee par le serveur pour le message en cours de reception (voir {@link #receiveTagged}). */
    private static Category forced;

    private TabState() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    // ---------------------------------------------------------------- connexion

    /** Le serveur gere les onglets. */
    public static void enable(String name, boolean hasCrossServer, boolean global) {
        boolean first = !enabled;
        enabled = true;
        crossServer = hasCrossServer;
        serverName = name;
        writesGlobal = global;
        if (!visibleTabs().contains(active)) {
            active = ChatTab.ALL;
        }
        if (first) {
            // Les messages deja affiches (arrivee sur le serveur...) sont repris dans l'historique.
            history.clear();
            for (GuiMessage message : view().arcadiachat$shown()) {
                history.addLast(new Entry(Category.SYSTEM, message));
            }
        }
        refresh();
        syncScope();
    }

    public static void disable() {
        enabled = false;
        history.clear();
        unread.clear();
        pending = null;
        forced = null;
    }

    // ---------------------------------------------------------------- messages

    /** Fixe la categorie du message que le chat va recevoir juste apres. */
    public static void expect(Category category) {
        pending = forced != null ? forced : category;
    }

    /**
     * Ligne etiquetee par le serveur : elle passe par le circuit normal des messages (pour que les autres mods
     * de chat la traitent comme d'habitude), avec la categorie donnee par le serveur.
     */
    public static void receiveTagged(Category category, Runnable delivery) {
        forced = category;
        pending = category;
        try {
            delivery.run();
        } finally {
            // Si un autre mod a bloque le message, la categorie ne doit pas deteindre sur le suivant.
            forced = null;
            pending = null;
        }
    }

    /** Categorie du message que le chat est en train d'ajouter (systeme par defaut). */
    public static Category takeCategory() {
        Category category = pending != null ? pending : Category.SYSTEM;
        pending = null;
        return category;
    }

    public static boolean shows(Category category) {
        return active.accepts(category);
    }

    /** Range un message dans l'historique et compte les non-lus des autres onglets. */
    public static void record(GuiMessage message, Category category) {
        history.addFirst(new Entry(category, message));
        while (history.size() > HISTORY) {
            history.removeLast();
        }
        for (ChatTab tab : visibleTabs()) {
            if (tab != active && tab.accepts(category)) {
                unread.merge(tab, 1, Integer::sum);
            }
        }
    }

    public static void clearHistory() {
        history.clear();
        unread.clear();
    }

    // ---------------------------------------------------------------- onglets

    public static List<ChatTab> visibleTabs() {
        List<ChatTab> tabs = new ArrayList<>();
        for (ChatTab tab : ChatTab.values()) {
            if (tab != ChatTab.GLOBAL || crossServer) {
                tabs.add(tab);
            }
        }
        return tabs;
    }

    public static ChatTab active() {
        return active;
    }

    public static int unread(ChatTab tab) {
        return unread.getOrDefault(tab, 0);
    }

    public static String serverName() {
        return serverName;
    }

    public static boolean hasCrossServer() {
        return crossServer;
    }

    public static boolean writesGlobal() {
        return crossServer && writesGlobal;
    }

    public static void select(ChatTab tab) {
        if (!enabled || tab == active) {
            return;
        }
        active = tab;
        unread.remove(tab);
        refresh();
        syncScope();
    }

    /** Onglet suivant (+1) ou precedent (-1). */
    public static void cycle(int step) {
        List<ChatTab> tabs = visibleTabs();
        int index = tabs.indexOf(active);
        select(tabs.get(Math.floorMod(index + step, tabs.size())));
    }

    /** Affiche l'historique de l'onglet actif. */
    private static void refresh() {
        List<GuiMessage> shown = new ArrayList<>();
        for (Entry entry : history) {
            if (active.accepts(entry.category())) {
                shown.add(entry.message());
            }
        }
        view().arcadiachat$show(shown);
    }

    /** L'onglet Serveur ecrit sur ce serveur seulement, Tout et Global dans le chat global. */
    private static void syncScope() {
        if (!crossServer) {
            return;
        }
        if (active == ChatTab.SERVER) {
            writesGlobal = false;
        } else if (active == ChatTab.GLOBAL || active == ChatTab.ALL) {
            writesGlobal = true;
        }
        ClientNetwork.sendScope(writesGlobal);
    }

    private static ChatView view() {
        return (ChatView) Minecraft.getInstance().gui.getChat();
    }
}
