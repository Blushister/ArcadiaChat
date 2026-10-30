package com.arcadia.chat.client;

import java.util.HashMap;
import java.util.Map;

import com.arcadia.chat.mixin.ChatScreenAccessor;
import com.arcadia.chat.tabs.ChatTab;
import com.arcadia.chat.tabs.TabState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;

/**
 * Brouillons du chat, par onglet : un texte commence puis abandonne (chat ferme, autre onglet) revient tel quel.
 * Un message envoye efface le brouillon de son onglet.
 */
public final class Drafts {

    private static final Map<ChatTab, String> DRAFTS = new HashMap<>();
    /** Le chat se ferme parce que le message vient de partir : rien a garder. */
    private static boolean sent;

    private Drafts() {
    }

    /** Message (ou commande) envoye depuis le chat. */
    public static void markSent() {
        sent = true;
    }

    /** Fermeture du chat : garde ce qui n'a pas ete envoye. */
    public static void onClose(ChatScreen screen) {
        String text = ((ChatScreenAccessor) screen).arcadiachat$input().getValue();
        if (sent) {
            // Le texte envoye a pu suivre le joueur depuis un autre onglet : aucun brouillon ne le garde.
            sent = false;
            DRAFTS.remove(TabState.active());
            DRAFTS.values().removeIf(text::equals);
            return;
        }
        save(TabState.active(), text);
    }

    /** Ouverture du chat : le brouillon de l'onglet, sauf ouverture avec "/" (touche des commandes). */
    public static void onOpen(ChatScreen screen) {
        sent = false;
        ChatScreenAccessor accessor = (ChatScreenAccessor) screen;
        String draft = DRAFTS.get(TabState.active());
        if (draft != null && accessor.arcadiachat$initial().isEmpty() && accessor.arcadiachat$input().getValue().isEmpty()) {
            accessor.arcadiachat$input().setValue(draft);
        }
    }

    /**
     * Changement d'onglet chat ouvert : un texte en cours suit le joueur (il choisit souvent l'onglet apres avoir
     * ecrit) ; une saisie vide reprend le brouillon du nouvel onglet.
     */
    public static void onTabChange(ChatTab next) {
        if (!(Minecraft.getInstance().screen instanceof ChatScreen screen)) {
            return;
        }
        EditBox input = ((ChatScreenAccessor) screen).arcadiachat$input();
        String draft = DRAFTS.get(next);
        if (input.getValue().isEmpty() && draft != null) {
            input.setValue(draft);
        }
    }

    private static void save(ChatTab tab, String text) {
        if (tab == null) {
            return;
        }
        if (text == null || text.isBlank()) {
            DRAFTS.remove(tab);
        } else {
            DRAFTS.put(tab, text);
        }
    }

    public static void clear() {
        DRAFTS.clear();
        sent = false;
    }
}
