package com.arcadia.chat.client;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import com.arcadia.chat.ChatConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.UUIDUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.SkullBlockEntity;

/**
 * Tete du joueur devant ses messages dans le chat. Le skin d'un joueur connecte ici vient de la liste des joueurs ;
 * celui d'un joueur d'un autre serveur est demande une fois a Mojang, puis garde (skin par defaut en attendant).
 */
public final class ChatHeads {

    /** Place prise par la tete devant le texte : 8 pixels et un espace. */
    public static final int SPACE = 10;
    private static final int SIZE = 8;
    private static final int MAX_CACHED = 500;

    /** Skins des joueurs absents d'ici : null tant que la demande est en cours. */
    private static final Map<String, PlayerSkin> REMOTE = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> REQUESTED = new ConcurrentHashMap<>();

    private ChatHeads() {
    }

    public static boolean enabled() {
        return ChatConfig.HEADS.get();
    }

    /** Un message de cet auteur prend-il une tete ? */
    public static boolean hasHead(String sender) {
        return enabled() && sender != null && sender.matches("[A-Za-z0-9_]{1,16}");
    }

    /**
     * Auteur d'un message dont on ne connait pas l'auteur (serveur sans ArcadiaBridge, message mis en forme par un
     * mod) : le joueur connecte dont le pseudo apparait avant le premier ":" ou entre "<>", ou null.
     */
    public static String senderIn(Component content) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (!enabled() || connection == null) {
            return null;
        }
        String text = content.getString();
        int end = text.startsWith("<") ? text.indexOf('>') : text.indexOf(':');
        if (end <= 0 || end > 64) {
            return null;
        }
        String head = text.substring(0, end);
        String found = null;
        for (PlayerInfo info : connection.getOnlinePlayers()) {
            String name = info.getProfile().getName();
            if ((found == null || name.length() > found.length()) && containsWord(head, name)) {
                found = name;
            }
        }
        return found;
    }

    private static boolean containsWord(String text, String word) {
        for (int i = text.indexOf(word); i >= 0; i = text.indexOf(word, i + 1)) {
            int end = i + word.length();
            if ((i == 0 || !isNameChar(text.charAt(i - 1))) && (end == text.length() || !isNameChar(text.charAt(end)))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    /** Dessine la tete (transparence du texte comprise) ; {@code color} est la couleur ARGB de la ligne. */
    public static void draw(GuiGraphics graphics, String sender, int x, int y, int color) {
        float alpha = ((color >>> 24) & 0xFF) / 255F;
        graphics.setColor(1F, 1F, 1F, alpha);
        PlayerFaceRenderer.draw(graphics, skinOf(sender), x, y, SIZE);
        graphics.setColor(1F, 1F, 1F, 1F);
    }

    private static PlayerSkin skinOf(String name) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        PlayerInfo info = connection == null ? null : connection.getPlayerInfo(name);
        if (info != null) {
            return info.getSkin();
        }
        String key = name.toLowerCase(Locale.ROOT);
        PlayerSkin known = REMOTE.get(key);
        if (known != null) {
            return known;
        }
        if (REQUESTED.putIfAbsent(key, Boolean.TRUE) == null) {
            request(key);
        }
        return DefaultPlayerSkin.get(UUIDUtil.createOfflinePlayerUUID(key));
    }

    private static void request(String key) {
        if (REQUESTED.size() > MAX_CACHED) {
            REMOTE.clear();
            REQUESTED.clear();
            REQUESTED.put(key, Boolean.TRUE);
        }
        Minecraft mc = Minecraft.getInstance();
        try {
            SkullBlockEntity.fetchGameProfile(key)
                    .thenCompose(profile -> profile.isPresent()
                            ? mc.getSkinManager().getOrLoad(profile.get())
                            : CompletableFuture.completedFuture(DefaultPlayerSkin.get(UUIDUtil.createOfflinePlayerUUID(key))))
                    .thenAccept(skin -> REMOTE.put(key, skin))
                    .exceptionally(error -> {
                        REMOTE.put(key, DefaultPlayerSkin.get(UUIDUtil.createOfflinePlayerUUID(key)));
                        return null;
                    });
        } catch (RuntimeException e) {
            // Profils indisponibles (pas encore de monde charge) : skin par defaut.
            REMOTE.put(key, DefaultPlayerSkin.get(UUIDUtil.createOfflinePlayerUUID(key)));
        }
    }

    public static void clear() {
        REMOTE.clear();
        REQUESTED.clear();
    }
}
