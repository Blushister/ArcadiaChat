package com.arcadia.chat.client;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.arcadia.chat.net.TabsProtocol;
import net.minecraft.client.Minecraft;

/** Salons vocaux envoyes par le serveur, et qui parle en ce moment. Sans classe du mod de vocal : se charge toujours. */
public final class ChatVoice {

    /** Une voix reste signalee un court instant apres le dernier son recu. */
    private static final long TALK_MS = 400;
    private static final Map<UUID, Long> TALKING = new ConcurrentHashMap<>();
    private static final AtomicLong HEARD = new AtomicLong();

    private static boolean available;
    private static boolean manager;
    private static boolean crossServer;
    private static String current = "";
    private static List<TabsProtocol.VoiceRoomEntry> rooms = List.of();

    private ChatVoice() {
    }

    public static void onState(TabsProtocol.VoiceState state) {
        available = true;
        manager = state.manager();
        crossServer = state.crossServer();
        current = state.current();
        rooms = List.copyOf(state.rooms());
    }

    public static void clear() {
        available = false;
        manager = false;
        current = "";
        rooms = List.of();
        TALKING.clear();
    }

    public static boolean available() {
        return available;
    }

    public static boolean crossServer() {
        return crossServer;
    }

    public static boolean isManager() {
        return manager;
    }

    public static String current() {
        return current;
    }

    public static List<TabsProtocol.VoiceRoomEntry> rooms() {
        return rooms;
    }

    public static void heard(UUID id) {
        if (id != null) {
            TALKING.put(id, System.currentTimeMillis());
            HEARD.incrementAndGet();
        }
    }

    public static void speakingSelf() {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            TALKING.put(player.getUUID(), System.currentTimeMillis());
        }
    }

    public static boolean isTalking(String id) {
        try {
            Long last = TALKING.get(UUID.fromString(id));
            return last != null && System.currentTimeMillis() - last < TALK_MS;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Nombre de paquets de voix recus depuis la connexion. */
    public static long heardCount() {
        return HEARD.get();
    }
}
