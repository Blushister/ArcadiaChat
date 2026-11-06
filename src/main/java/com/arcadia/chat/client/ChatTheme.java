package com.arcadia.chat.client;

import com.arcadia.chat.ChatConfig;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;

/** Themes de l'ecran des salons : sombre, clair, et Arcadia (cuivre et patine). */
public enum ChatTheme {
    DARK("dark", 0xFF313338, 0xFF2B2D31, 0xFF1E1F22, 0xFF3F4147, 0xFF4E5058, 0xFF35373C, 0xFF404249, 0x0D000000,
            0xFFDBDEE1, 0xFF949BA4, 0xFFF2F3F5, 0xFF5865F2, 0xFFFFFFFF, 0xFFF23F42, 0x1AF0B232, 0xFF2B2D31, 0xFFDBDEE1, 0xFF23A55A, 0xFFF0B232,
            0xFF1E1F22, 0xFF383A40),
    LIGHT("light", 0xFFFFFFFF, 0xFFF2F3F5, 0xFFFFFFFF, 0xFFE1E2E4, 0xFFC4C9CE, 0xFFE8E9EB, 0xFFD7D9DC, 0x08000000,
            0xFF313338, 0xFF5C5E66, 0xFF060607, 0xFF5865F2, 0xFFFFFFFF, 0xFFDA373C, 0x26F0B232, 0xFFF2F3F5, 0xFF313338, 0xFF23A55A, 0xFFB5770F,
            0xFFE3E5E8, 0xFFEBEDEF),
    ARCADIA("arcadia", 0xFF2C2421, 0xFF251E1B, 0xFF1A1513, 0xFF40352F, 0xFF5A4A40, 0xFF352B27, 0xFF43372F, 0x0D000000,
            0xFFEDE3DA, 0xFFA89A8E, 0xFFFFF8F2, 0xFF3F9E88, 0xFFFFFFFF, 0xFFE8705A, 0x1AE09A6B, 0xFF251E1B, 0xFFEDE3DA, 0xFF58B8A0, 0xFFE09A6B,
            0xFF1A1513, 0xFF3A2F2A);

    public final String id;
    /** Colonne des messages. */
    public final int ground;
    /** Colonnes des salons et des joueurs. */
    public final int rail;
    /** Zones de saisie. */
    public final int field;
    public final int line;
    public final int outline;
    public final int hover;
    public final int selected;
    public final int rowHover;
    public final int text;
    public final int muted;
    public final int strong;
    public final int accent;
    public final int onAccent;
    public final int danger;
    public final int mention;
    public final int pinBackground;
    public final int pinText;
    public final int online;
    /** Deuxieme couleur : le nom du joueur et celui du reseau. */
    public final int secondary;
    /** Barre des serveurs, tout a gauche. */
    public final int bar;
    /** Fond de la zone de saisie des messages. */
    public final int composer;

    ChatTheme(String id, int ground, int rail, int field, int line, int outline, int hover, int selected, int rowHover, int text,
              int muted, int strong, int accent, int onAccent, int danger, int mention, int pinBackground, int pinText, int online, int secondary,
              int bar, int composer) {
        this.bar = bar;
        this.composer = composer;
        this.id = id;
        this.ground = ground;
        this.rail = rail;
        this.field = field;
        this.line = line;
        this.outline = outline;
        this.hover = hover;
        this.selected = selected;
        this.rowHover = rowHover;
        this.text = text;
        this.muted = muted;
        this.strong = strong;
        this.accent = accent;
        this.onAccent = onAccent;
        this.danger = danger;
        this.mention = mention;
        this.pinBackground = pinBackground;
        this.pinText = pinText;
        this.online = online;
        this.secondary = secondary;
    }

    /** Couleur montree dans le choix du theme. */
    public int swatch() {
        return this == ARCADIA ? secondary : ground;
    }

    public static ChatTheme current() {
        String id = ChatConfig.ROOMS_THEME.get();
        for (ChatTheme theme : values()) {
            if (theme.id.equals(id)) {
                return theme;
            }
        }
        return ARCADIA;
    }

    public static void set(ChatTheme theme) {
        ChatConfig.ROOMS_THEME.set(theme.id);
        ChatConfig.SPEC.save();
    }

    public boolean isLight() {
        return this == LIGHT;
    }

    /**
     * Le texte d'un serveur porte ses propres couleurs, pensees pour un fond sombre : elles sont adaptees au fond du theme.
     * Les emojis (au-dela du plan de base) gardent leur blanc : Minecraft multiplie leurs couleurs par celle du texte.
     */
    public FormattedCharSequence adapt(FormattedCharSequence sequence) {
        return sink -> sequence.accept((position, style, codePoint) ->
                sink.accept(position, codePoint >= 0x10000 ? style : adapt(style.isBold() ? style.withBold(false).withFont(UiFont.bold().getFont()) : style), codePoint));
    }

    /** Fond d'un panneau pose sur le jeu : la couleur du theme, laissant deviner le jeu derriere. */
    public int panel(int color) {
        return (color & 0xFFFFFF) | (isLight() ? 0xE6000000 : 0xD4000000);
    }

    /** Voile pose sur le jeu derriere les panneaux. */
    public int scrim() {
        return isLight() ? 0x30FFFFFF : 0x48000000;
    }

    /** Ligne survolee ou choisie dans un panneau translucide : un voile, jamais un aplat opaque. */
    public int hoverWash() {
        return isLight() ? 0x0F000000 : 0x14FFFFFF;
    }

    public int selectWash() {
        return isLight() ? 0x1C000000 : 0x24FFFFFF;
    }

    /** Une couleur de grade, lisible sur le fond du theme. */
    public int adaptColor(int rgb) {
        TextColor color = adapt(Style.EMPTY.withColor(rgb)).getColor();
        return 0xFF000000 | (color == null ? rgb : color.getValue());
    }

    private Style adapt(Style style) {
        TextColor color = style.getColor();
        if (color == null) {
            return style;
        }
        int rgb = color.getValue();
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        double luminance = (0.2126 * red + 0.7152 * green + 0.0722 * blue) / 255;
        if (isLight() && luminance > 0.5) {
            return style.withColor(rgb == 0xFFFFFF ? text & 0xFFFFFF : (red * 45 / 100) << 16 | (green * 45 / 100) << 8 | (blue * 45 / 100));
        }
        if (!isLight() && luminance < 0.25) {
            return style.withColor(muted & 0xFFFFFF);
        }
        return style;
    }
}
