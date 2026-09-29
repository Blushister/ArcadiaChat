package com.arcadia.chat.tabs;

/** Nature d'un message de chat, qui decide des onglets ou il apparait. */
public enum Category {
    /** Chat venu d'un autre serveur Arcadia. */
    REMOTE,
    /** Chat des joueurs de ce serveur, et messages venus du salon Discord de ce serveur. */
    LOCAL,
    /** Messages prives (/msg, /tell, messages d'equipe). */
    PRIVATE,
    /** Tout le reste : annonces, reponses de commandes, messages des autres mods. */
    SYSTEM
}
