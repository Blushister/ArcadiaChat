package com.arcadia.chat.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import com.arcadia.chat.ChatConfig;
import com.arcadia.chat.net.ClientNetwork;
import com.arcadia.chat.net.TabsProtocol;
import com.arcadia.chat.tabs.TabState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Salons de l'ecran facon Discord : messages conserves recus du serveur, non-lus et joueurs connectes. Tout est appele
 * depuis le thread du client et oublie a la deconnexion ; l'historique, lui, reste sur le serveur.
 */
public final class ChatRooms {

    /** Delai avant de redemander une page restee sans reponse. */
    private static final long RETRY_MS = 1500;
    /** Messages gardes en memoire par salon. */
    private static final int MAX_MESSAGES = 5000;

    /** Un salon ; {@code channel} : "global", "server", "staff", "#canal", "discord:salon" ou "@correspondant". */
    public static final class Room {
        private final String channel;
        private String label;
        private boolean readOnly;
        private int unread;
        private long firstUnread;
        private boolean mentioned;
        private boolean loaded;
        private boolean more = true;
        private long requestedAt;
        private long maxSeen;
        private long readSent;
        private final TreeMap<Long, TabsProtocol.StoredMessage> messages = new TreeMap<>();

        private Room(String channel, String label) {
            this.channel = channel;
            this.label = label;
        }

        public String channel() {
            return channel;
        }

        public String label() {
            return label;
        }

        public boolean readOnly() {
            return readOnly;
        }

        public int unread() {
            return unread;
        }

        public long firstUnread() {
            return firstUnread;
        }

        public boolean mentioned() {
            return mentioned;
        }

        public boolean loaded() {
            return loaded;
        }

        public boolean more() {
            return more;
        }

        public boolean isDiscord() {
            return channel.startsWith("discord:");
        }

        public boolean isPrivate() {
            return channel.startsWith("@");
        }

        public boolean isChannel() {
            return channel.startsWith("#");
        }

        public Iterable<TabsProtocol.StoredMessage> messages() {
            return messages.values();
        }

        public TabsProtocol.StoredMessage find(long id) {
            return messages.get(id);
        }

        public long lastId() {
            return messages.isEmpty() ? 0 : messages.lastKey();
        }

        public TabsProtocol.StoredMessage lastFrom(String sender) {
            for (TabsProtocol.StoredMessage message : messages.descendingMap().values()) {
                if (message.sender().equalsIgnoreCase(sender) && !message.discord()) {
                    return message;
                }
            }
            return null;
        }
    }

    private static boolean available;
    private static boolean translationSeen;
    private static final Map<String, Room> ROOMS = new LinkedHashMap<>();
    private static List<TabsProtocol.ServerPlayers> presence = List.of();
    private static final Map<String, String> DRAFTS = new HashMap<>();
    private static final Map<Long, String> SHOWN = new HashMap<>();
    /** Les messages derriere ces lignes : un changement de langue les reecrit toutes. */
    private static final Map<Long, TabsProtocol.StoredMessage> SHOWN_MESSAGES = new HashMap<>();
    private static final int MAX_SHOWN = 2000;
    private static long revision;
    private static String activeKey;
    private static String searchChannel = "";
    private static String searchQuery = "";
    private static List<TabsProtocol.StoredMessage> searchResults = List.of();

    private ChatRooms() {
    }

    /** Le serveur gere-t-il l'ecran ? (bridge assez recent, historique active, base disponible) */
    public static boolean available() {
        return available && TabState.isEnabled();
    }

    /** Le serveur traduit-il ? (au moins un message est arrive avec sa langue) */
    public static boolean translationSeen() {
        return translationSeen;
    }

    public static long revision() {
        return revision;
    }

    public static void clear() {
        available = false;
        translationSeen = false;
        ChatTranslations.clear();
        ChatVoice.clear();
        ROOMS.clear();
        presence = List.of();
        DRAFTS.clear();
        welcomed = false;
        ServiceState.clear();
        SHOWN.clear();
        SHOWN_MESSAGES.clear();
        activeKey = null;
        searchChannel = "";
        searchQuery = "";
        searchResults = List.of();
        revision++;
    }

    private static String keyOf(String channel) {
        return channel.startsWith("@") ? channel.toLowerCase(Locale.ROOT) : channel;
    }

    public static Room room(String channel) {
        return ROOMS.get(keyOf(channel));
    }

    public static Room getOrCreate(String channel, String label) {
        return ROOMS.computeIfAbsent(keyOf(channel), k -> new Room(channel, label));
    }

    /** Salons d'un genre : canaux, salons Discord, conversations. */
    public static List<Room> rooms(java.util.function.Predicate<Room> filter) {
        List<Room> list = new ArrayList<>();
        for (Room room : ROOMS.values()) {
            if (filter.test(room)) {
                list.add(room);
            }
        }
        return list;
    }

    public static List<TabsProtocol.ServerPlayers> presence() {
        return presence;
    }

    public static String draft(String channel) {
        return DRAFTS.getOrDefault(keyOf(channel), "");
    }

    public static void saveDraft(String channel, String text) {
        if (text == null || text.isBlank()) {
            DRAFTS.remove(keyOf(channel));
        } else {
            DRAFTS.put(keyOf(channel), text);
        }
    }

    // ---------------------------------------------------------------- recu du serveur

    /** Une seule fois par connexion : le resume de l'absence et, sans l'addon, sa proposition. */
    private static boolean welcomed;

    public static void onRooms(List<TabsProtocol.RoomEntry> entries) {
        available = true;
        if (!welcomed) {
            welcomed = true;
            welcome(entries);
        }
        Map<String, TabsProtocol.RoomEntry> byKey = new HashMap<>();
        entries.forEach(entry -> byKey.put(keyOf(entry.channel()), entry));
        // Canaux quittes et salons retires : ils disparaissent. Les conversations ouvertes dans la session restent.
        ROOMS.entrySet().removeIf(e -> !byKey.containsKey(e.getKey()) && (e.getValue().isChannel() || e.getValue().isDiscord()));
        for (TabsProtocol.RoomEntry entry : entries) {
            Room room = getOrCreate(entry.channel(), entry.label());
            room.label = entry.label();
            room.readOnly = entry.readOnly();
            if (!keyOf(entry.channel()).equals(activeKey)) {
                room.unread = entry.unread();
                if (entry.firstUnread() > 0) {
                    room.firstUnread = entry.firstUnread();
                }
            }
        }
        revision++;
    }

    private static Map<String, TabsProtocol.RosterEntry> roster = Map.of();

    public static void onRoster(List<TabsProtocol.RosterEntry> players) {
        Map<String, TabsProtocol.RosterEntry> map = new HashMap<>();
        players.forEach(entry -> map.put(entry.name().toLowerCase(Locale.ROOT), entry));
        roster = Map.copyOf(map);
        revision++;
    }

    /** "Pendant ton absence : 2 messages prives, 12 dans #global" ; et sans l'addon, une fois, de quoi le trouver. */
    private static void welcome(List<TabsProtocol.RoomEntry> entries) {
        var chat = Minecraft.getInstance().gui.getChat();
        if (ChatConfig.ABSENCE_SUMMARY.get()) {
            int privateUnread = 0;
            List<TabsProtocol.RoomEntry> busy = new ArrayList<>();
            for (TabsProtocol.RoomEntry entry : entries) {
                if (entry.unread() <= 0 || isMuted(entry.channel())) {
                    continue;
                }
                if (entry.channel().startsWith("@")) {
                    privateUnread += entry.unread();
                } else {
                    busy.add(entry);
                }
            }
            busy.sort((a, b) -> Integer.compare(b.unread(), a.unread()));
            List<String> parts = new ArrayList<>();
            if (privateUnread > 0) {
                parts.add(Component.translatable("arcadiachat.absence.private", privateUnread).getString());
            }
            for (TabsProtocol.RoomEntry entry : busy.subList(0, Math.min(3, busy.size()))) {
                String name = entry.label().isEmpty() ? entry.channel() : entry.label();
                parts.add(Component.translatable("arcadiachat.absence.room", entry.unread(), "#" + name.toLowerCase(Locale.ROOT).replace(' ', '-')).getString());
            }
            if (!parts.isEmpty()) {
                chat.addMessage(Component.translatable("arcadiachat.absence.title", String.join(", ", parts)).withStyle(ChatFormatting.GRAY));
            }
        }
        if (!RoomsUi.installed() && !ChatConfig.ADDON_HINT_SEEN.get()) {
            ChatConfig.ADDON_HINT_SEEN.set(true);
            ChatConfig.SPEC.save();
            chat.addMessage(Component.translatable("arcadiachat.addon_hint").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }

    /** Grade et absence d'un joueur connecte, ou null. */
    public static TabsProtocol.RosterEntry roster(String name) {
        return roster.get(name.toLowerCase(Locale.ROOT));
    }

    /** Salon rendu muet par le joueur : ni compteur, ni alerte. */
    public static boolean isMuted(String channel) {
        return ChatConfig.MUTED_ROOMS.get().contains(channel.toLowerCase(Locale.ROOT));
    }

    public static void toggleMuted(String channel) {
        String key = channel.toLowerCase(Locale.ROOT);
        List<String> list = new ArrayList<>(ChatConfig.MUTED_ROOMS.get());
        if (!list.remove(key)) {
            list.add(key);
        }
        ChatConfig.MUTED_ROOMS.set(list);
        ChatConfig.SPEC.save();
        revision++;
    }

    public static void onPresence(List<TabsProtocol.ServerPlayers> servers) {
        presence = List.copyOf(servers);
        revision++;
    }

    public static void onPush(TabsProtocol.StoredMessage message) {
        translationSeen |= !message.lang().isEmpty();
        String self = selfName();
        boolean fresh = !message.deleted() && message.editedAt() == 0 && message.reactions().isEmpty();
        boolean alerted = false;
        for (String channel : channelsOf(message)) {
            Room room = getOrCreate(channel, channel.startsWith("@") ? channel.substring(1) : "");
            if (room.loaded) {
                if (message.deleted()) {
                    room.messages.remove(message.id());
                } else {
                    room.messages.put(message.id(), message);
                    trim(room);
                }
            }
            if (fresh && message.id() > room.maxSeen) {
                room.maxSeen = message.id();
                if (!message.sender().equalsIgnoreCase(self) && !keyOf(channel).equals(activeKey)) {
                    room.unread++;
                    if (room.firstUnread == 0) {
                        room.firstUnread = message.id();
                    }
                    room.mentioned |= room.isPrivate() || TabState.mentionsSelf(message.body());
                    // Une alerte en jeu pour ce qui s'adresse au joueur, sauf s'il regarde deja l'ecran des salons.
                    boolean forMe = room.isPrivate() || TabState.mentionsSelf(message.body());
                    if (!alerted && forMe && !isMuted(channel) && !RoomsUi.isRooms(Minecraft.getInstance().screen)) {
                        alerted = true;
                        String title = room.isPrivate() ? message.sender() : message.sender() + "  #" + channel;
                        ChatToasts.push(message.sender(), title, Emojis.applyText(ChatTranslations.text(message)), room.isPrivate());
                    }
                }
            }
        }
        syncClassicLine(message);
        // Une recherche affichee suit aussi les modifications.
        searchResults = searchResults.stream().map(m -> m.id() == message.id() ? message : m)
                .filter(m -> !m.deleted()).toList();
        revision++;
    }

    /**
     * Le chat habituel suit le message : traduction, modification, suppression. On retient ce que sa ligne affiche, pour la
     * retrouver, et elle n'est touchee que si ce texte change : le serveur peut renvoyer plusieurs fois le meme message.
     */
    private static void syncClassicLine(TabsProtocol.StoredMessage message) {
        String shown = SHOWN.get(message.id());
        if (message.deleted()) {
            if (shown != null) {
                TabState.replaceLine(message.sender(), shown, Component.translatable("arcadiachat.rooms.reply_deleted")
                        .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
                SHOWN.remove(message.id());
                SHOWN_MESSAGES.remove(message.id());
            }
            return;
        }
        Component next = ChatTranslations.body(message);
        String text = next.getString();
        if (shown == null) {
            if (ChatTranslations.applies(message)) {
                TabState.replaceLine(message.sender(), message.raw(), next, false);
            }
        } else if (!shown.equals(text)) {
            TabState.replaceLine(message.sender(), shown, next, message.editedAt() != 0);
        }
        if (SHOWN.size() > MAX_SHOWN) {
            SHOWN.clear();
            SHOWN_MESSAGES.clear();
        }
        SHOWN.put(message.id(), text);
        SHOWN_MESSAGES.put(message.id(), message);
    }

    /**
     * La langue des messages vient de changer : chaque ligne du chat habituel passe tout de suite a la traduction (ou revient a
     * l'original), quel que soit son age, et l'ecran des salons se redessine.
     */
    public static void onTranslationChanged() {
        ChatTranslations.clear();
        for (TabsProtocol.StoredMessage message : List.copyOf(SHOWN_MESSAGES.values())) {
            String shown = SHOWN.get(message.id());
            Component next = ChatTranslations.body(message);
            String text = next.getString();
            if (shown != null && !shown.equals(text)) {
                TabState.replaceLine(message.sender(), shown, next, true);
                SHOWN.put(message.id(), text);
            }
        }
        revision++;
    }

    public static void onPage(TabsProtocol.HistoryPage page) {
        if (!page.query().isBlank()) {
            if (page.channel().equalsIgnoreCase(searchChannel) && page.query().equals(searchQuery)) {
                searchResults = List.copyOf(page.messages());
                revision++;
            }
            return;
        }
        Room room = room(page.channel());
        if (room == null) {
            return;
        }
        room.requestedAt = 0;
        room.loaded = true;
        // La page la plus ancienne demandee dit s'il en reste ; une page recente ne l'efface pas.
        if (page.beforeId() > 0 || room.messages.isEmpty()) {
            room.more = page.more();
        }
        for (TabsProtocol.StoredMessage message : page.messages()) {
            translationSeen |= !message.lang().isEmpty();
            room.messages.put(message.id(), message);
            room.maxSeen = Math.max(room.maxSeen, message.id());
        }
        trim(room);
        revision++;
    }

    private static void trim(Room room) {
        while (room.messages.size() > MAX_MESSAGES) {
            room.messages.pollFirstEntry();
            room.more = true;
        }
    }

    /** Le chat global d'un joueur de ce serveur est aussi dans le salon du serveur. */
    /** Ce message vient-il d'un salon que le joueur ne veut pas voir traduit ? */
    public static boolean untranslated(TabsProtocol.StoredMessage message) {
        List<? extends String> rooms = ChatConfig.UNTRANSLATED_ROOMS.get();
        if (rooms.isEmpty()) {
            return false;
        }
        for (String channel : channelsOf(message)) {
            if (rooms.contains(channel.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    public static boolean isUntranslated(String channel) {
        return ChatConfig.UNTRANSLATED_ROOMS.get().contains(channel.toLowerCase(Locale.ROOT));
    }

    public static void toggleUntranslated(String channel) {
        String key = channel.toLowerCase(Locale.ROOT);
        List<String> list = new ArrayList<>(ChatConfig.UNTRANSLATED_ROOMS.get());
        if (!list.remove(key)) {
            list.add(key);
        }
        ChatConfig.UNTRANSLATED_ROOMS.set(list);
        ChatConfig.SPEC.save();
        onTranslationChanged();
    }

    private static List<String> channelsOf(TabsProtocol.StoredMessage message) {
        return switch (message.channel()) {
            case TabsProtocol.CHANNEL_GLOBAL -> TabState.isCurrentServer(message.origin())
                    ? List.of(TabsProtocol.CHANNEL_GLOBAL, "server") : List.of(TabsProtocol.CHANNEL_GLOBAL);
            case TabsProtocol.CHANNEL_LOCAL -> List.of("server");
            default -> List.of(message.channel());
        };
    }

    private static String selfName() {
        var player = Minecraft.getInstance().player;
        return player == null ? "" : player.getGameProfile().getName();
    }

    // ---------------------------------------------------------------- demandes

    /** L'ecran ouvre ce salon. */
    public static void activate(Room room) {
        Room previous = activeKey == null ? null : ROOMS.get(activeKey);
        if (previous != null && previous != room) {
            previous.firstUnread = 0;
        }
        activeKey = room == null ? null : keyOf(room.channel);
        if (room != null) {
            room.unread = 0;
            room.mentioned = false;
            if (!room.loaded) {
                request(room);
            }
        }
        revision++;
    }

    /** L'ecran se ferme : plus aucun salon n'est lu, le repere des nouveaux messages disparait au retour. */
    public static void deactivate() {
        Room room = activeKey == null ? null : ROOMS.get(activeKey);
        if (room != null) {
            room.firstUnread = 0;
        }
        activeKey = null;
    }

    /** Demande les messages les plus recents d'un salon (ou redemande apres un silence du serveur). */
    public static void request(Room room) {
        room.requestedAt = System.currentTimeMillis();
        ClientNetwork.requestHistory(room.channel, 0, "");
    }

    public static void requestOlder(Room room) {
        if (!room.loaded || !room.more || room.messages.isEmpty() || isLoading(room)) {
            return;
        }
        room.requestedAt = System.currentTimeMillis();
        ClientNetwork.requestHistory(room.channel, room.messages.firstKey(), "");
    }

    public static boolean isLoading(Room room) {
        return room.requestedAt > 0 && System.currentTimeMillis() - room.requestedAt < RETRY_MS;
    }

    /** Chaque seconde ou presque : relance une demande restee sans reponse, signale ce qui a ete lu. */
    public static void tick(Room room) {
        if (!room.loaded && !isLoading(room)) {
            request(room);
        }
        long last = room.lastId();
        if (last > room.readSent) {
            room.readSent = last;
            ClientNetwork.roomAction("read", room.channel, last, "");
        }
    }

    public static void search(String channel, String query) {
        searchChannel = channel;
        searchQuery = query.trim();
        searchResults = List.of();
        if (!searchQuery.isEmpty()) {
            ClientNetwork.requestHistory(channel, 0, searchQuery);
        }
        revision++;
    }

    public static boolean searching(String channel) {
        return !searchQuery.isEmpty() && searchChannel.equalsIgnoreCase(channel);
    }

    public static List<TabsProtocol.StoredMessage> searchResults() {
        return searchResults;
    }

    /** Ferme une conversation : elle revient au prochain message. */
    public static void closeConversation(Room room) {
        ClientNetwork.roomAction("close", room.channel, 0, "");
        ROOMS.remove(keyOf(room.channel));
        revision++;
    }
}
