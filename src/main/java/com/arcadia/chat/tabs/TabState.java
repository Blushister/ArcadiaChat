package com.arcadia.chat.tabs;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.arcadia.chat.ChatConfig;
import com.arcadia.chat.net.ClientNetwork;
import com.arcadia.chat.net.TabsProtocol;
import com.google.common.collect.MapMaker;
import net.minecraft.ChatFormatting;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;

/**
 * Etat du chat a onglets pendant une connexion. Tout est appele depuis le thread du client.
 *
 * <p>Les onglets ne s'activent que sur un serveur equipe d'ArcadiaBridge (paquet "hello") : ailleurs, le
 * chat de Minecraft reste tel quel. Les mentions, l'heure des messages et les filtres fonctionnent partout.
 */
public final class TabState {

    /** Ce qu'on sait du message que le chat est en train d'ajouter. */
    public record Incoming(Category category, String key, String sender, String origin) {
        static final Incoming UNKNOWN = new Incoming(Category.SYSTEM, "", "", "");
    }

    /** Par message affiche (cle faible : un message oublie du chat disparait aussi d'ici). */
    private static final Map<GuiMessage, Meta> META = new MapMaker().weakKeys().makeMap();

    private record Entry(Category category, String key, GuiMessage message, String text) {
    }

    /**
     * Ou part ce que le joueur ecrit, selon l'onglet actif. NONE : nulle part (onglet Prive, qui regroupe
     * toutes les conversations sans savoir a qui ecrire).
     */
    public enum Target { GLOBAL, LOCAL, STAFF, TEAM, CHANNEL, CONVERSATION, NONE }

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final int MENTION_COLOR = 0xFFAA00;
    private static final long TYPING_TIMEOUT_MS = 6000;

    // ---------------------------------------------------------------- connexion
    private static boolean enabled;
    private static String serverId = "";
    private static String serverName = "";
    private static boolean crossServer;
    private static boolean staff;
    private static boolean typingEnabled;
    /** Le serveur propose le chat d'equipe (FTB Teams). */
    private static boolean team;
    private static final Map<String, TabsProtocol.ServerInfo> servers = new HashMap<>();
    private static final List<String> channels = new ArrayList<>();
    private static final Set<String> conversations = new LinkedHashSet<>();
    /** Joueurs connectes aux autres serveurs (completion des pseudos). */
    private static List<String> remotePlayers = List.of();

    // ---------------------------------------------------------------- onglets
    private static ChatTab active = ChatTab.ALL;
    /** Onglet a rouvrir des qu'il existera (canal pas encore connu a la connexion). */
    private static ChatTab restore;
    /** Le joueur ecrit-il dans le chat global ? (hors onglets Tout, Global et Serveur, on garde le dernier choix) */
    private static boolean writesGlobal = true;
    private static final Deque<Entry> history = new ArrayDeque<>();
    private static final Map<ChatTab, Integer> unread = new HashMap<>();
    private static final Set<ChatTab> mentioned = new HashSet<>();
    private static String search = "";

    // ---------------------------------------------------------------- message en cours d'ajout
    private static Incoming pending;
    private static Incoming forced;

    /** Qui ecrit : jusqu'a quand, et s'il est sur un autre serveur. */
    private record Typing(long until, boolean remote) {
    }

    private static final Map<String, Typing> typing = new HashMap<>();

    private TabState() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    // ================================================================ connexion

    public static void enable(TabsProtocol.Hello hello) {
        boolean first = !enabled;
        enabled = true;
        serverId = hello.serverId();
        serverName = hello.serverName();
        crossServer = hello.crossServer();
        staff = hello.staff();
        typingEnabled = hello.typing();
        team = hello.team();
        writesGlobal = hello.writesGlobal();
        if (first) {
            // Les messages deja affiches (arrivee sur le serveur...) sont repris dans l'historique.
            history.clear();
            for (GuiMessage message : view().arcadiachat$shown()) {
                history.addLast(new Entry(Category.SYSTEM, "", message, lower(message.content())));
            }
            active = ChatTab.ALL;
            restore = ChatConfig.REMEMBER_TAB.get() ? ChatTab.parse(ChatConfig.LAST_TAB.get()) : null;
            if (restore != null && restore.kind() == ChatTab.Kind.CONVERSATION) {
                restore = null; // les conversations ne sont pas rouvertes d'une session a l'autre
            }
        }
        applyRestore();
        if (!visibleTabs().contains(active)) {
            active = ChatTab.ALL;
        }
        refresh();
        syncScope();
    }

    public static void updateServers(List<TabsProtocol.ServerInfo> list) {
        servers.clear();
        list.forEach(info -> servers.put(info.id(), info));
    }

    public static void updateChannels(List<String> list) {
        channels.clear();
        list.forEach(name -> channels.add(name.toLowerCase(Locale.ROOT)));
        applyRestore();
        if (!visibleTabs().contains(active)) {
            select(ChatTab.ALL);
        }
    }

    public static void updateRemotePlayers(List<String> names) {
        remotePlayers = List.copyOf(names);
    }

    public static List<String> remotePlayers() {
        return enabled ? remotePlayers : List.of();
    }

    public static void disable() {
        enabled = false;
        history.clear();
        unread.clear();
        mentioned.clear();
        conversations.clear();
        channels.clear();
        servers.clear();
        typing.clear();
        pending = null;
        forced = null;
        restore = null;
        search = "";
    }

    private static void applyRestore() {
        if (restore != null && visibleTabs().contains(restore)) {
            active = restore;
            restore = null;
            refresh();
            syncScope();
        }
    }

    // ================================================================ messages

    /** Fixe la categorie (et l'auteur, s'il est connu) du message que le chat va recevoir juste apres. */
    public static void expect(Category category, String key, String sender) {
        pending = forced != null ? forced : new Incoming(category, key == null ? "" : key, sender == null ? "" : sender, "");
    }

    /**
     * Ligne etiquetee par le serveur : elle passe par le circuit normal des messages (pour que les autres mods
     * de chat la traitent comme d'habitude), avec la categorie donnee par le serveur.
     */
    public static void receiveTagged(Category category, String key, String sender, String origin, Runnable delivery) {
        forced = new Incoming(category, key == null ? "" : key, sender == null ? "" : sender, origin == null ? "" : origin);
        pending = forced;
        try {
            delivery.run();
        } finally {
            // Si un autre mod a bloque le message, la categorie ne doit pas deteindre sur le suivant.
            forced = null;
            pending = null;
        }
    }

    /**
     * Le chat ajoute un message : sa categorie finale (filtres compris). Ne consomme rien : les differentes
     * etapes de l'ajout interrogent la meme information, liberee par {@link #endMessage()}.
     */
    public static Incoming currentMessage(Component content) {
        Incoming in = enabled && pending != null ? pending : Incoming.UNKNOWN;
        if (in.category() == Category.SYSTEM && isFiltered(content)) {
            return new Incoming(Category.HIDDEN, "", "", "");
        }
        return in;
    }

    public static void endMessage() {
        pending = null;
    }

    /** Le message doit-il apparaitre a l'ecran maintenant ? */
    public static boolean displays(Incoming in, Component content) {
        if (in.category() == Category.HIDDEN) {
            return false;
        }
        if (!enabled) {
            return true;
        }
        return active.accepts(in.category(), in.key()) && matchesSearch(lower(content));
    }

    /** Range un message dans l'historique, ouvre sa conversation, compte les non-lus des autres onglets. */
    public static void record(GuiMessage message, Incoming in) {
        if (!enabled || in.category() == Category.HIDDEN) {
            return;
        }
        history.addFirst(new Entry(in.category(), in.key(), message, lower(message.content())));
        int max = ChatConfig.HISTORY.get();
        while (history.size() > max) {
            history.removeLast();
        }
        if (in.category() == Category.PRIVATE && !in.key().isEmpty()
                && conversations.stream().noneMatch(p -> p.equalsIgnoreCase(in.key()))) {
            conversations.add(in.key());
        }
        boolean mention = isMention(message.content(), in);
        for (ChatTab tab : visibleTabs()) {
            if (!tab.equals(active) && tab.accepts(in.category(), in.key())) {
                unread.merge(tab, 1, Integer::sum);
                if (mention || tab.kind() == ChatTab.Kind.CONVERSATION) {
                    mentioned.add(tab);
                }
            }
        }
    }

    public static void clearHistory() {
        history.clear();
        unread.clear();
        mentioned.clear();
    }

    // ================================================================ mentions, heure, filtres

    /** Le message mentionne-t-il le joueur ? (jamais ses propres messages) */
    public static boolean isMention(Component content, Incoming in) {
        if (!ChatConfig.MENTIONS.get() || in.category() == Category.HIDDEN
                || (enabled && in.category() == Category.SYSTEM)) {
            return false;
        }
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        String name = player.getGameProfile().getName();
        String text = content.getString();
        int colon = text.indexOf(':');
        // L'auteur est avant le premier ":" (format LPC et vanilla "<Pseudo>") : pas de mention de soi-meme.
        String author = colon < 0 ? "" : text.substring(0, colon);
        String body = colon < 0 ? text : text.substring(colon + 1);
        if (author.contains(name) || text.startsWith("<" + name + ">")) {
            return false;
        }
        return mentionPattern(name).matcher(body).find();
    }

    /** Barre orange a gauche des messages qui mentionnent le joueur, et un son. */
    public static GuiMessageTag mentionTag(Component content, GuiMessageTag tag) {
        if (!isMention(content, currentMessage(content))) {
            return tag;
        }
        if (ChatConfig.MENTION_SOUND.get()) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.4F));
        }
        return new GuiMessageTag(MENTION_COLOR, null, Component.translatable("arcadiachat.mention"), "Mention");
    }

    /**
     * Heure du message (en tete de ligne en option, et au survol, avec le serveur d'origine s'il vient d'ailleurs) ;
     * mention du joueur mise en valeur.
     */
    public static Component decorate(Component content) {
        String time = LocalTime.now().format(CLOCK);
        Incoming in = currentMessage(content);
        Component result = isMention(content, in) ? highlightMention(content) : content;
        if (ChatConfig.TIMESTAMP_HOVER.get() && result.getStyle().getHoverEvent() == null) {
            MutableComponent hover = Component.literal(time).withStyle(ChatFormatting.GRAY);
            TabsProtocol.ServerInfo origin = in.origin().isEmpty() || in.origin().equals(serverId) ? null : servers.get(in.origin());
            if (origin != null) {
                hover.append(Component.literal(" \u00b7 " + origin.name()).withStyle(ChatFormatting.AQUA));
            }
            result = result.copy().withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
        }
        if (ChatConfig.TIMESTAMPS.get()) {
            MutableComponent stamped = Component.literal("[" + time + "] ").withStyle(ChatFormatting.DARK_GRAY);
            result = stamped.append(result);
        }
        return result;
    }

    /**
     * Reecrit le message en mettant "@Pseudo" (et le pseudo seul si l'option est active) en or et en gras.
     * Le reste du texte garde sa mise en forme, liens et survols compris.
     */
    private static Component highlightMention(Component content) {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return content;
        }
        Pattern pattern = mentionPattern(player.getGameProfile().getName());
        MutableComponent out = Component.empty();
        content.visit((style, text) -> {
            var matcher = pattern.matcher(text);
            int last = 0;
            while (matcher.find()) {
                if (matcher.start() > last) {
                    out.append(Component.literal(text.substring(last, matcher.start())).withStyle(style));
                }
                out.append(Component.literal(matcher.group()).withStyle(style.withColor(MENTION_COLOR).withBold(true)));
                last = matcher.end();
            }
            if (last < text.length()) {
                out.append(Component.literal(text.substring(last)).withStyle(style));
            }
            return java.util.Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        return out;
    }


    private static Pattern mentionPattern(String name) {
        String key = ChatConfig.MENTION_PLAIN_NAME.get() + "|" + name;
        return Pattern.compile((ChatConfig.MENTION_PLAIN_NAME.get() ? "(?<![\\w@])@?" : "@")
                + Pattern.quote(name) + "(?!\\w)", Pattern.CASE_INSENSITIVE);
    }

    private static boolean isFiltered(Component content) {
        List<? extends String> filters = ChatConfig.FILTERS.get();
        if (filters.isEmpty()) {
            return false;
        }
        String text = lower(content);
        for (String filter : filters) {
            if (!filter.isBlank() && text.contains(filter.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    // ================================================================ onglets

    public static List<ChatTab> visibleTabs() {
        List<ChatTab> tabs = new ArrayList<>();
        List<? extends String> hidden = ChatConfig.HIDDEN_TABS.get();
        List<String> order = new ArrayList<>(ChatConfig.TAB_ORDER.get());
        for (String id : ChatConfig.DEFAULT_ORDER) {
            if (!order.contains(id)) {
                order.add(id); // un onglet oublie dans la liste reste disponible
            }
        }
        for (String id : order) {
            ChatTab.Kind kind = ChatTab.Kind.byId(id);
            if (kind == null || (kind != ChatTab.Kind.ALL && hidden.contains(id))
                    || (kind == ChatTab.Kind.GLOBAL && !crossServer) || (kind == ChatTab.Kind.STAFF && !staff)
                    || (kind == ChatTab.Kind.TEAM && !team)) {
                continue;
            }
            tabs.add(ChatTab.fixed(kind));
        }
        channels.forEach(name -> tabs.add(ChatTab.channel(name)));
        conversations.forEach(partner -> tabs.add(ChatTab.conversation(partner)));
        return tabs;
    }

    public static ChatTab active() {
        return active;
    }

    public static int unread(ChatTab tab) {
        return unread.getOrDefault(tab, 0);
    }

    public static boolean isMentioned(ChatTab tab) {
        return mentioned.contains(tab);
    }

    public static String serverName() {
        return serverName;
    }

    public static boolean hasCrossServer() {
        return crossServer;
    }

    public static void select(ChatTab tab) {
        if (!enabled || tab.equals(active)) {
            return;
        }
        active = tab;
        unread.remove(tab);
        mentioned.remove(tab);
        refresh();
        syncScope();
        if (ChatConfig.REMEMBER_TAB.get()) {
            ChatConfig.LAST_TAB.set(tab.id());
            ChatConfig.SPEC.save();
        }
    }

    /** Onglet suivant (+1) ou precedent (-1). */
    public static void cycle(int step) {
        List<ChatTab> tabs = visibleTabs();
        int index = tabs.indexOf(active);
        select(tabs.get(Math.floorMod(index + step, tabs.size())));
    }

    /** Ferme l'onglet d'une conversation privee. */
    public static void close(ChatTab tab) {
        if (tab.kind() != ChatTab.Kind.CONVERSATION) {
            return;
        }
        conversations.removeIf(partner -> partner.equalsIgnoreCase(tab.key()));
        unread.remove(tab);
        mentioned.remove(tab);
        if (tab.equals(active)) {
            select(ChatTab.fixed(ChatTab.Kind.PRIVATE));
        }
    }

    /** Ou part ce que le joueur ecrit dans l'onglet actif. */
    public static Target target() {
        return switch (active.kind()) {
            case STAFF -> Target.STAFF;
            case TEAM -> Target.TEAM;
            case CHANNEL -> Target.CHANNEL;
            case CONVERSATION -> Target.CONVERSATION;
            case PRIVATE -> Target.NONE;
            default -> crossServer && writesGlobal ? Target.GLOBAL : Target.LOCAL;
        };
    }

    public static Component targetLabel() {
        return switch (target()) {
            case GLOBAL -> Component.translatable("arcadiachat.scope.global");
            case LOCAL -> Component.translatable("arcadiachat.scope.local", serverName);
            case STAFF -> Component.translatable("arcadiachat.scope.staff");
            case TEAM -> Component.translatable("arcadiachat.scope.team");
            case CHANNEL -> Component.translatable("arcadiachat.scope.channel", active.key());
            case CONVERSATION -> Component.translatable("arcadiachat.scope.conversation", active.key());
            case NONE -> Component.translatable("arcadiachat.scope.private");
        };
    }

    // ================================================================ recherche

    public static void setSearch(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (!normalized.equals(search)) {
            search = normalized;
            refresh();
        }
    }

    private static boolean matchesSearch(String text) {
        return search.isEmpty() || text.contains(search);
    }

    // ================================================================ serveurs


    public static Meta metaOf(GuiMessage message) {
        return message == null ? null : META.get(message);
    }

    public static boolean isStaff() {
        return staff;
    }

    /** Ouvre (ou rouvre) la conversation privee avec un joueur, et l'affiche. */
    public static void openConversation(String partner) {
        if (!enabled || partner.isBlank()) {
            return;
        }
        String existing = conversations.stream().filter(p -> p.equalsIgnoreCase(partner)).findFirst().orElse(null);
        if (existing == null) {
            conversations.add(partner);
            existing = partner;
        }
        select(ChatTab.conversation(existing));
    }

    public static TabsProtocol.ServerInfo server(String id) {
        return servers.get(id);
    }

    /** Peut-on proposer de rejoindre ce serveur depuis un message ? */
    public static boolean isJoinable(String id) {
        TabsProtocol.ServerInfo info = servers.get(id);
        return info != null && !info.address().isBlank() && !id.equals(serverId);
    }

    // ================================================================ "est en train d'ecrire"

    public static void onTyping(String name, boolean isTyping, boolean remote) {
        if (isTyping) {
            typing.put(name, new Typing(System.currentTimeMillis() + TYPING_TIMEOUT_MS, remote));
        } else {
            typing.remove(name);
        }
    }
    }

    public static boolean typingEnabled() {
        return enabled && typingEnabled;
    }

    /**
     * Joueurs en train d'ecrire : ceux du serveur dans les onglets qui montrent son chat, ceux des autres
     * serveurs dans les onglets qui montrent le chat global.
     */
    public static List<String> typingNames() {
        long now = System.currentTimeMillis();
        typing.values().removeIf(t -> t.until() < now);
        if (!ChatConfig.SHOW_TYPING.get()) {
            return List.of();
        }
        boolean local = active.accepts(Category.LOCAL, "");
        boolean remote = active.accepts(Category.REMOTE, "");
        return typing.entrySet().stream()
                .filter(e -> e.getValue().remote() ? remote : local)
                .map(Map.Entry::getKey).sorted().toList();
    }

    // ================================================================ affichage

    /** Au retour dans le chat (apres les reglages par exemple) : applique les options modifiees. */
    public static void reapplySettings() {
        if (enabled && !visibleTabs().contains(active)) {
            select(ChatTab.ALL);
        }
        refresh();
    }

    /** Affiche l'historique de l'onglet actif (et de la recherche en cours). */
    private static void refresh() {
        if (!enabled) {
            return;
        }
        List<GuiMessage> shown = new ArrayList<>();
        for (Entry entry : history) {
            if (active.accepts(entry.category(), entry.key()) && matchesSearch(entry.text())) {
                shown.add(entry.message());
            }
        }
        view().arcadiachat$show(shown);
    }

    /** Onglet Serveur : ce serveur seulement ; Tout et Global : chat global. */
    private static void syncScope() {
        if (!crossServer) {
            return;
        }
        if (active.kind() == ChatTab.Kind.SERVER) {
            writesGlobal = false;
        } else if (active.kind() == ChatTab.Kind.GLOBAL || active.kind() == ChatTab.Kind.ALL) {
            writesGlobal = true;
        }
        ClientNetwork.sendScope(writesGlobal);
    }

    private static String lower(Component content) {
        return content.getString().toLowerCase(Locale.ROOT);
    }

    private static ChatView view() {
        return (ChatView) Minecraft.getInstance().gui.getChat();
    }
}
