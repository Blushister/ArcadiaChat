package com.arcadia.chat.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Dessin des ecrans du mod : formes lissees au pixel reel de l'ecran, icones vectorielles, texte sans ombre. */
public final class Ui {

    public static final int ICON = 7;
    private static final ResourceLocation ATLAS = ResourceLocation.fromNamespaceAndPath("arcadiachat", "textures/gui/icons.png");
    private static final int CELL = 32;
    private static final int COLUMNS = 7;
    private static final int ATLAS_WIDTH = 224;
    private static final int ATLAS_HEIGHT = 128;

    /** Une icone de l'image commune : blanche, teintee a l'affichage. */
    public record Icon(int index) {
    }

    public static final Icon HASH = new Icon(0);
    public static final Icon GLOBE = new Icon(1);
    public static final Icon SERVER = new Icon(2);
    public static final Icon SHIELD = new Icon(3);
    public static final Icon PEOPLE = new Icon(4);
    public static final Icon INFO = new Icon(5);
    public static final Icon SEARCH = new Icon(6);
    public static final Icon PIN = new Icon(7);
    public static final Icon REPLY = new Icon(8);
    public static final Icon SMILE = new Icon(9);
    public static final Icon DOTS = new Icon(10);
    public static final Icon GEAR = new Icon(11);
    public static final Icon PLUS = new Icon(12);
    public static final Icon CLOSE = new Icon(13);
    public static final Icon DOWN = new Icon(14);
    public static final Icon SPEAKER = new Icon(15);
    public static final Icon LOCK = new Icon(16);
    public static final Icon SWAP = new Icon(17);
    public static final Icon UP = new Icon(18);
    public static final Icon LEFT = new Icon(19);
    public static final Icon RIGHT = new Icon(20);
    public static final Icon SEND = new Icon(21);
    public static final Icon AT = new Icon(22);
    public static final Icon MIC = new Icon(23);
    public static final Icon HEADSET = new Icon(24);
    public static final Icon SLASH = new Icon(25);


    private Ui() {
    }

    private static int withAlpha(int color, float coverage) {
        return ((int) ((color >>> 24) * coverage) << 24) | (color & 0xFFFFFF);
    }

    /** Dessine en pixels reels de l'ecran : les bords restent lisses quelle que soit l'echelle d'affichage. */
    private static void inScreenPixels(GuiGraphics graphics, double scale, Runnable draw) {
        graphics.pose().pushPose();
        graphics.pose().scale((float) (1 / scale), (float) (1 / scale), 1);
        graphics.drawManaged(draw);
        graphics.pose().popPose();
    }

    /** Rectangle aux coins arrondis et antialiases ; un rayon egal a la moitie du cote donne un cercle. */
    public static void round(GuiGraphics graphics, int x, int y, int w, int h, int radius, int color) {
        double scale = Minecraft.getInstance().getWindow().getGuiScale();
        int left = (int) Math.round(x * scale);
        int top = (int) Math.round(y * scale);
        int width = (int) Math.round(w * scale);
        int height = (int) Math.round(h * scale);
        int r = (int) Math.round(Math.min(radius, Math.min(w, h) / 2.0) * scale);
        inScreenPixels(graphics, scale, () -> {
            for (int row = 0; row < r; row++) {
                double d = r - row - 0.5;
                double in = r - Math.sqrt(r * (double) r - d * d);
                int solid = (int) Math.ceil(in);
                int edge = (int) Math.floor(in);
                float coverage = (float) (solid - in);
                for (int line : new int[] {top + row, top + height - row - 1}) {
                    graphics.fill(left + solid, line, left + width - solid, line + 1, color);
                    if (coverage > 0.02F && solid != edge) {
                        int soft = withAlpha(color, coverage);
                        graphics.fill(left + edge, line, left + solid, line + 1, soft);
                        graphics.fill(left + width - solid, line, left + width - edge, line + 1, soft);
                    }
                }
            }
            graphics.fill(left, top + r, left + width, top + height - r, color);
        });
    }

    /** Masque les coins d'un carre (une tete de joueur) avec la couleur du fond, pour l'arrondir. */
    public static void maskCorners(GuiGraphics graphics, int x, int y, int size, int radius, int background) {
        double scale = Minecraft.getInstance().getWindow().getGuiScale();
        int left = (int) Math.round(x * scale);
        int top = (int) Math.round(y * scale);
        int side = (int) Math.round(size * scale);
        int r = (int) Math.round(Math.min(radius, size / 2.0) * scale);
        inScreenPixels(graphics, scale, () -> {
            for (int row = 0; row < r; row++) {
                double d = r - row - 0.5;
                double in = r - Math.sqrt(r * (double) r - d * d);
                int solid = (int) Math.floor(in);
                float coverage = (float) (in - solid);
                int soft = withAlpha(background, coverage);
                for (int line : new int[] {top + row, top + side - row - 1}) {
                    if (solid > 0) {
                        graphics.fill(left, line, left + solid, line + 1, background);
                        graphics.fill(left + side - solid, line, left + side, line + 1, background);
                    }
                    if (coverage > 0.02F) {
                        graphics.fill(left + solid, line, left + solid + 1, line + 1, soft);
                        graphics.fill(left + side - solid - 1, line, left + side - solid, line + 1, soft);
                    }
                }
            }
        });
    }

    /** Couleur obtenue en posant {@code overlay}, translucide, sur le fond opaque {@code base}. */
    public static int blend(int base, int overlay) {
        int alpha = overlay >>> 24;
        int red = (((base >> 16) & 0xFF) * (255 - alpha) + ((overlay >> 16) & 0xFF) * alpha) / 255;
        int green = (((base >> 8) & 0xFF) * (255 - alpha) + ((overlay >> 8) & 0xFF) * alpha) / 255;
        int blue = ((base & 0xFF) * (255 - alpha) + (overlay & 0xFF) * alpha) / 255;
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    /** Petit rectangle aux coins adoucis. */
    public static void pill(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        round(graphics, x, y, w, h, 2, color);
    }

    /** Contour d'un pixel, sans fond : le fond translucide dessous reste visible. */
    public static void outline(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + h - 1, x + w, y + h, color);
        graphics.fill(x, y + 1, x + 1, y + h - 1, color);
        graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /** Forme adoucie avec un contour. */
    public static void framed(GuiGraphics graphics, int x, int y, int w, int h, int fill, int border) {
        pill(graphics, x, y, w, h, border);
        pill(graphics, x + 1, y + 1, w - 2, h - 2, fill);
    }

    public static void icon(GuiGraphics graphics, Icon icon, int x, int y, int color) {
        icon(graphics, icon, x, y, 1, color);
    }

    /** L'icone deborde d'un point autour de sa place : son dessin, lui, garde des marges. */
    public static void icon(GuiGraphics graphics, Icon icon, int x, int y, int scale, int color) {
        int size = ICON * scale + 2;
        RenderSystem.enableBlend();
        graphics.setColor(((color >> 16) & 0xFF) / 255F, ((color >> 8) & 0xFF) / 255F, (color & 0xFF) / 255F, (color >>> 24) / 255F);
        graphics.blit(ATLAS, x - 1, y - 1, size, size, (icon.index() % COLUMNS) * CELL, (float) (icon.index() / COLUMNS * CELL), CELL, CELL, ATLAS_WIDTH, ATLAS_HEIGHT);
        graphics.setColor(1F, 1F, 1F, 1F);
        RenderSystem.disableBlend();
    }

    public static void center(GuiGraphics graphics, Font font, Component text, int middle, int y, int color) {
        graphics.drawString(font, text, middle - font.width(text) / 2, y, color, false);
    }

    public static void center(GuiGraphics graphics, Font font, String text, int middle, int y, int color) {
        graphics.drawString(font, text, middle - font.width(text) / 2, y, color, false);
    }

    /** Le texte s'il tient, sinon coupe avec des points de suspension. */
    public static String fit(Font font, String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width("…"))) + "…";
    }
}
