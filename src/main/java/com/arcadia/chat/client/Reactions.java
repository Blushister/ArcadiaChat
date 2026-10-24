package com.arcadia.chat.client;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.minecraft.ChatFormatting;
import net.minecraft.client.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.fml.ModList;

/**
 * Reactions aux messages (❤, ★...). Un message est reconnu par une empreinte de son auteur et de son texte, la
 * meme chez tous les joueurs quel que soit leur serveur. Avec Emojiful, les reactions sont de vraies emojis ;
 * sans lui, des symboles.
 */
public final class Reactions {

    /** Ordre d'affichage. */
    public static final List<String> EMOJIS = List.of("like", "laugh", "love", "wow", "sad");
    private static final Map<String, String> EMOJIFUL = Map.of(
            "like", ":thumbsup:", "laugh", ":joy:", "love", ":heart:", "wow", ":open_mouth:", "sad", ":cry:");
    private static final Map<String, String> SYMBOLS = Map.of(
            "like", "✔", "laugh", "☺", "love", "❤", "wow", "✦", "sad", "☹");

    /** Empreinte -> reaction -> joueurs qui l'ont mise. */
    private static final Map<String, Map<String, Set<String>>> STATE = new LinkedHashMap<>();
    private static final int MAX_TRACKED = 500;

    private Reactions() {
    }

    public static String glyph(String emoji) {
        return (ModList.get().isLoaded("emojiful") ? EMOJIFUL : SYMBOLS).getOrDefault(emoji, "?");
    }

    /** Empreinte d'un message : auteur + texte apres "Pseudo: " (identique d'un serveur a l'autre). Null sans auteur. */
    public static String keyOf(String sender, String text) {
        if (sender == null || sender.isBlank()) {
            return null;
        }
        int colon = text.indexOf(": ");
        String body = (colon >= 0 ? text.substring(colon + 2) : text).trim();
        try {
            byte[] hash = MessageDigest.getInstance("SHA-1")
                    .digest((sender.toLowerCase(Locale.ROOT) + "|" + body).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    /** Applique une reaction recue. @return true si l'etat a change */
    public static boolean apply(String key, String emoji, String reactor, boolean add) {
        if (!EMOJIS.contains(emoji)) {
            return false;
        }
        Map<String, Set<String>> byEmoji = STATE.computeIfAbsent(key, k -> new LinkedHashMap<>());
        Set<String> names = byEmoji.computeIfAbsent(emoji, k -> new LinkedHashSet<>());
        boolean changed = add ? names.add(reactor) : names.remove(reactor);
        while (STATE.size() > MAX_TRACKED) {
            STATE.remove(STATE.keySet().iterator().next());
        }
        return changed;
    }

    public static boolean hasReacted(String key, String emoji, String player) {
        Map<String, Set<String>> byEmoji = STATE.get(key);
        return byEmoji != null && byEmoji.getOrDefault(emoji, Set.of()).contains(player);
    }

    public static void clear() {
        STATE.clear();
    }

    /** Le texte d'origine suivi des reactions ("❤ 2 ★ 1"), avec au survol la liste de ceux qui ont reagi. */
    public static Component decorate(Component original, String key) {
        Map<String, Set<String>> byEmoji = STATE.get(key);
        if (byEmoji == null || byEmoji.values().stream().allMatch(Set::isEmpty)) {
            return original;
        }
        MutableComponent out = Component.empty().append(original);
        for (String emoji : EMOJIS) {
            Set<String> names = byEmoji.getOrDefault(emoji, Set.of());
            if (names.isEmpty()) {
                continue;
            }
            Component hover = Component.literal(String.join(", ", names)).withStyle(ChatFormatting.GRAY);
            out.append(Component.literal(" " + glyph(emoji) + " " + names.size()).withStyle(style -> style
                    .withColor(ChatFormatting.GRAY).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover))));
        }
        return out;
    }

    /**
     * Copie d'un message avec un autre texte, en gardant ce que d'autres mods y ont attache (la tete du joueur
     * de Chat Heads, par exemple) : tous les champs qui ne sont pas ceux du record sont recopies.
     */
    public static GuiMessage withContent(GuiMessage old, Component content) {
        GuiMessage fresh = new GuiMessage(old.addedTime(), content, old.signature(), old.tag());
        Set<String> recordFields = Set.of("addedTime", "content", "signature", "tag");
        for (Field field : GuiMessage.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || recordFields.contains(field.getName())) {
                continue;
            }
            try {
                field.setAccessible(true);
                field.set(fresh, field.get(old));
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Champ non recopiable : la copie reste utilisable.
            }
        }
        return fresh;
    }
}
