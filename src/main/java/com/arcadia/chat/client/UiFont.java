package com.arcadia.chat.client;

import java.util.function.Function;

import com.arcadia.chat.mixin.FontAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * La police des ecrans du mod, lisse (Lato) au lieu de la police pixel du jeu. Une version par echelle d'affichage : ses
 * lettres sont rendues a la taille reelle de l'ecran, donc nettes. Les caracteres qu'elle n'a pas (emojis, symboles)
 * retombent sur la police du jeu.
 */
public final class UiFont {

    private static final int MAX_SCALE = 4;
    private static final Font[] FONTS = new Font[MAX_SCALE + 1];

    private UiFont() {
    }

    private static int scale() {
        return Mth.clamp((int) Math.round(Minecraft.getInstance().getWindow().getGuiScale()), 1, MAX_SCALE);
    }

    private static ResourceLocation id(String kind) {
        return ResourceLocation.fromNamespaceAndPath("arcadiachat", kind + "_" + scale());
    }

    public static Font get() {
        int scale = scale();
        if (FONTS[scale] == null) {
            Function<ResourceLocation, FontSet> game = ((FontAccessor) Minecraft.getInstance().font).arcadiachat$fonts();
            ResourceLocation ui = id("ui");
            FONTS[scale] = new Font(location -> game.apply(Style.DEFAULT_FONT.equals(location) ? ui : location), false);
        }
        return FONTS[scale];
    }

    /** Le style des titres et des noms : une vraie graisse, pas le faux gras du jeu. */
    public static Style bold() {
        return Style.EMPTY.withFont(id("ui_bold"));
    }
}
