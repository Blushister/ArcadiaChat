package com.arcadia.chat.client;

import java.util.function.Function;
import java.util.function.Predicate;

import net.minecraft.client.gui.screens.Screen;

/**
 * Point d'accroche de l'ecran des salons, fourni par l'addon ArcadiaChat Rooms. Sans l'addon, rien n'est enregistre : le chat
 * habituel reste seul, et rien ne propose l'ecran des salons. Les donnees (salons, vocal, traductions) restent gerees ici,
 * dans ArcadiaChat, pour que le chat habituel en profite aussi.
 */
public final class RoomsUi {

    private static Function<String, Screen> opener;
    private static Predicate<Screen> isRooms = screen -> false;
    private static Function<Screen, String> typing = screen -> "";

    private RoomsUi() {
    }

    /** Appele une fois par l'addon, a son chargement. */
    public static void register(Function<String, Screen> open, Predicate<Screen> recognise, Function<Screen, String> typingText) {
        opener = open;
        isRooms = recognise;
        typing = typingText;
    }

    public static boolean installed() {
        return opener != null;
    }

    /** L'ecran des salons, avec ce texte deja tape ; null sans l'addon. */
    public static Screen create(String text) {
        return opener == null ? null : opener.apply(text);
    }

    public static boolean isRooms(Screen screen) {
        return screen != null && isRooms.test(screen);
    }

    /** Ce que le joueur ecrit dans l'ecran des salons, pour "est en train d'ecrire". */
    public static String typingText(Screen screen) {
        return isRooms(screen) ? typing.apply(screen) : "";
    }
}
