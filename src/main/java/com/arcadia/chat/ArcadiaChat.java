package com.arcadia.chat;

import com.arcadia.chat.client.ClientEvents;
import com.arcadia.chat.net.ClientNetwork;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/** Chat a onglets d'Arcadia. Mod client : sur un serveur sans ArcadiaBridge, le chat reste celui de Minecraft. */
@Mod(value = ArcadiaChat.MOD_ID, dist = Dist.CLIENT)
public final class ArcadiaChat {

    public static final String MOD_ID = "arcadiachat";

    private static ModContainer container;

    public ArcadiaChat(IEventBus modBus, ModContainer container) {
        ArcadiaChat.container = container;
        container.registerConfig(ModConfig.Type.CLIENT, ChatConfig.SPEC, "arcadiachat-client.toml");
        // Reglages accessibles depuis le menu des mods.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modBus.addListener(ClientNetwork::register);
        NeoForge.EVENT_BUS.register(ClientEvents.class);
    }

    /** Ecran des reglages (bouton roue dentee de la barre d'onglets). */
    public static Screen settingsScreen(Screen parent) {
        return new ConfigurationScreen(container, parent);
    }
}
