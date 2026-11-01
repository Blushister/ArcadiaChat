# ArcadiaChat

Chat à onglets pour les serveurs Arcadia (NeoForge 1.21.1). Mod **client** : il se met dans le modpack des
joueurs ; côté serveur, c'est [ArcadiaBridge](https://github.com/Blushister/ArcadiaBridge) qui alimente les onglets.

## Fonctionnalités

- **Onglets** : Tout, Global (tous les serveurs), Serveur, Privé, Staff, Système, plus un onglet par canal
  thématique et par conversation privée. Compteur de messages non lus. Ctrl+Tab pour passer d'un onglet à l'autre.
- **Écrire au bon endroit** : l'onglet actif décide où part le message (tous les serveurs, ce serveur seulement,
  le staff, un canal, un joueur en privé).
- **Mentions** : `@pseudo` est mis en valeur, avec un son et l'onglet concerné signalé.
- **Messages venus d'un autre serveur** : leur survol indique leur origine, un clic propose de rejoindre ce serveur.
- **« … écrit »** sous le chat, comme sur Discord.
- **Recherche** dans l'onglet ouvert (Ctrl+F), **heure** des messages, **filtres** pour masquer des messages
  automatiques.
- **Réglages** : roue dentée de la barre d'onglets, ou menu des mods.

## Compatibilité

- Sans ArcadiaBridge sur le serveur, le chat reste celui de Minecraft (les mentions, l'heure et les filtres
  fonctionnent quand même).
- Les joueurs sans ArcadiaChat gardent le chat habituel.
- Placé par erreur dans les mods d'un serveur, il est simplement ignoré.
- Testé avec Chat Heads, Emojiful, LongerChatHistory et No Chat Reports.

Le protocole réseau (`net/TabsProtocol.java`) est partagé avec ArcadiaBridge : toute modification doit être
reportée à l'identique dans les deux mods.

## Développement

```bash
./gradlew build        # jar dans build/libs/
./gradlew runClient    # client de test (Dev), connecté à localhost:25565
./gradlew runClient2   # second client (Dev2), connecté à localhost:25566
```

Les serveurs de test sont ceux d'ArcadiaBridge (`./gradlew runServer` et `runServer2`).

## Licence

GNU Lesser General Public License v3.0 (LGPL-3.0-only) : voir [`COPYING.LESSER`](COPYING.LESSER), qui complète
la GNU GPL v3 fournie dans [`COPYING`](COPYING).

## Credits

Emoji graphics by [Twemoji](https://github.com/jdecked/twemoji), Copyright 2020 Twitter, Inc and other contributors, licensed under [CC-BY 4.0](https://creativecommons.org/licenses/by/4.0/) and arranged into font sheets. Emoji names from [emoji-data](https://github.com/iamcal/emoji-data) (MIT).
