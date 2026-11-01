package com.arcadia.chat.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.slf4j.Logger;

/**
 * Emojis du chat (images Twemoji, dans une police ajoutee a celle de Minecraft) : :nom: et les emojis Unicode des
 * messages recus deviennent des images. Remplace Emojiful.
 *
 * <p>Les symboles qui existent aussi hors emojis (❤, ✔...) ont un caractere prive dans la police : ils ne deviennent
 * des images que par :nom: ou suivis du selecteur d'emoji (U+FE0F), pour que l'interface et les grades colores d'un
 * serveur gardent leur apparence.
 */
public final class Emojis {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String RESOURCE = "/assets/arcadiachat/emoji/emojis.txt";
    private static final Pattern SHORTCODE = Pattern.compile("(?<![\\p{L}\\p{N}_]):([a-z0-9_+-]{1,40}):");
    private static final int VARIATION_SELECTOR = 0xFE0F;
    private static final int JOINER = 0x200D;
    private static final int SKIN_TONE_FIRST = 0x1F3FB;
    private static final int SKIN_TONE_LAST = 0x1F3FF;
    private static final char SECTION = '§';

    /** Un emoji : son caractere Unicode, celui de la police, son nom principal. */
    public record Emoji(int original, int glyph, String name) {
        public String text() {
            return Character.toString(glyph);
        }
    }

    private static List<Emoji> all;
    private static final Map<String, Emoji> BY_NAME = new HashMap<>();
    /** Symboles a caractere prive : caractere d'origine -> emoji. */
    private static final Map<Integer, Emoji> SYMBOLS = new HashMap<>();
    /** Caracteres de la police -> caractere d'origine. */
    private static final Map<Integer, Integer> ORIGINALS = new HashMap<>();
    private static final Set<Integer> GLYPHS = new HashSet<>();
    private static List<String> suggestions = List.of();

    private Emojis() {
    }

    private static synchronized void load() {
        if (all != null) {
            return;
        }
        List<Emoji> list = new ArrayList<>();
        List<String> names = new ArrayList<>();
        try (InputStream in = Emojis.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IOException("ressource absente");
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                String[] parts = line.trim().split(" ");
                if (parts.length != 3) {
                    continue;
                }
                int original = Integer.parseInt(parts[0], 16);
                int glyph = Integer.parseInt(parts[1], 16);
                String[] aliases = parts[2].split(",");
                Emoji emoji = new Emoji(original, glyph, aliases[0]);
                list.add(emoji);
                GLYPHS.add(glyph);
                ORIGINALS.put(glyph, original);
                if (glyph != original) {
                    SYMBOLS.put(original, emoji);
                }
                for (String alias : aliases) {
                    BY_NAME.put(alias, emoji);
                    names.add(":" + alias + ":");
                }
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.error("[ArcadiaChat] Liste des emojis illisible : {}", e.toString());
        }
        suggestions = List.copyOf(names);
        all = List.copyOf(list);
    }

    /** Tous les emojis, dans l'ordre du selecteur. */
    public static List<Emoji> all() {
        load();
        return all;
    }

    /** ":nom:" de tous les emojis, pour l'autocompletion. */
    public static List<String> suggestions() {
        load();
        return suggestions;
    }

    /** Caractere de la police pour un nom d'emoji ("smile"), ou "?" s'il n'existe pas. */
    public static String glyph(String name) {
        load();
        Emoji emoji = BY_NAME.get(name);
        return emoji == null ? "?" : emoji.text();
    }

    /** Texte copiable : les caracteres prives de la police redeviennent les vrais emojis. */
    public static String toOriginal(String text) {
        load();
        StringBuilder out = new StringBuilder(text.length());
        text.codePoints().forEach(cp -> out.appendCodePoint(ORIGINALS.getOrDefault(cp, cp)));
        return out.toString();
    }

    /**
     * Message recu : :nom: et emojis Unicode deviennent des images. Le style de chaque morceau est garde (clics,
     * survols) ; seules les images passent en blanc, car Minecraft multiplie les glyphes par la couleur du texte.
     *
     * @return le message d'origine s'il n'y a rien a convertir
     */
    public static Component apply(Component content) {
        load();
        String plain = content.getString();
        if (!mayContainEmoji(plain)) {
            return content;
        }
        MutableComponent out = Component.empty();
        boolean[] changed = {false};
        content.visit((style, text) -> {
            changed[0] |= appendConverted(out, style, text);
            return Optional.empty();
        }, Style.EMPTY);
        return changed[0] ? out : content;
    }

    private static boolean mayContainEmoji(String text) {
        if (text.indexOf(':') >= 0 || text.indexOf(VARIATION_SELECTOR) >= 0) {
            return true;
        }
        return text.codePoints().anyMatch(GLYPHS::contains);
    }

    private static boolean appendConverted(MutableComponent out, Style style, String source) {
        String text = replaceShortcodes(source);
        StringBuilder plain = new StringBuilder();
        StringBuilder images = new StringBuilder();
        // Codes de couleur (§) en cours : un morceau decoupe les perdrait, ils sont repris apres chaque image.
        String legacy = "";
        boolean changed = !text.equals(source);
        boolean afterImage = false;
        int[] points = text.codePoints().toArray();
        for (int i = 0; i < points.length; i++) {
            int cp = points[i];
            if (cp == SECTION && i + 1 < points.length) {
                legacy = updateLegacy(legacy, points[i + 1]);
                plain.appendCodePoint(cp).appendCodePoint(points[++i]);
                afterImage = false;
                continue;
            }
            // Variantes d'un emoji que la police n'a pas (ton de peau, liaison) : l'emoji de base seul.
            if (afterImage && (cp == VARIATION_SELECTOR || cp == JOINER || (cp >= SKIN_TONE_FIRST && cp <= SKIN_TONE_LAST))) {
                changed = true;
                continue;
            }
            int glyph = imageFor(cp, i + 1 < points.length ? points[i + 1] : -1);
            if (glyph < 0) {
                flushImages(out, style, images);
                if (afterImage && !legacy.isEmpty()) {
                    plain.append(legacy);
                }
                plain.appendCodePoint(cp);
                afterImage = false;
                continue;
            }
            flushPlain(out, style, plain);
            images.appendCodePoint(glyph);
            changed = true;
            afterImage = true;
        }
        flushImages(out, style, images);
        flushPlain(out, style, plain);
        return changed;
    }

    /** Caractere de la police pour ce caractere du message, ou -1 s'il reste du texte. */
    private static int imageFor(int cp, int next) {
        if (cp >= 0x1F000) {
            return GLYPHS.contains(cp) ? cp : -1;
        }
        Emoji symbol = next == VARIATION_SELECTOR ? SYMBOLS.get(cp) : null;
        return symbol == null ? -1 : symbol.glyph();
    }

    private static String replaceShortcodes(String text) {
        if (text.indexOf(':') < 0) {
            return text;
        }
        Matcher matcher = SHORTCODE.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            Emoji emoji = BY_NAME.get(matcher.group(1));
            matcher.appendReplacement(out, Matcher.quoteReplacement(emoji == null ? matcher.group() : emoji.text()));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** Une couleur (ou §r) annule ce qui precede ; un style (gras...) s'y ajoute. */
    private static String updateLegacy(String legacy, int code) {
        ChatFormatting formatting = ChatFormatting.getByCode((char) code);
        String pair = String.valueOf(SECTION) + (char) code;
        if (formatting == null) {
            return legacy;
        }
        return formatting.isFormat() ? legacy + pair : pair;
    }

    private static void flushPlain(MutableComponent out, Style style, StringBuilder plain) {
        if (plain.length() > 0) {
            out.append(Component.literal(plain.toString()).withStyle(style));
            plain.setLength(0);
        }
    }

    private static void flushImages(MutableComponent out, Style style, StringBuilder images) {
        if (images.length() > 0) {
            out.append(Component.literal(images.toString())
                    .withStyle(style.withColor(ChatFormatting.WHITE).withBold(false).withItalic(false)));
            images.setLength(0);
        }
    }
}
