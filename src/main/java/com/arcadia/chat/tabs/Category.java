package com.arcadia.chat.tabs;

/** Nature d'un message de chat, qui decide des onglets ou il apparait. */
public enum Category {
    /** Chat venu d'un autre serveur Arcadia. */
    REMOTE,
    /** Chat des joueurs de ce serveur, et messages venus du salon Discord de ce serveur. */
    LOCAL,
    /** Messages prives (/msg, /tell, messages d'equipe) ; la cle est le correspondant. */
    PRIVATE,
    /** Chat du staff et alertes. */
    STAFF,
    /** Canal thematique ; la cle est le nom du canal. */
    CHANNEL,
    /** Tout le reste : annonces, reponses de commandes, messages des autres mods. */
    SYSTEM,
    /** Message systeme masque par un filtre du joueur : garde dans aucun onglet. */
    HIDDEN
}
