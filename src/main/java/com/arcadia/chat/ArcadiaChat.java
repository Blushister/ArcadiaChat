package com.arcadia.chat;

import com.arcadia.chat.client.ClientEvents;
import com.arcadia.chat.net.ClientNetwork;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

/** Chat a onglets d'Arcadia. Mod client : sur un serveur sans ArcadiaBridge, le chat reste celui de Minecraft. */
@Mod(value = ArcadiaChat.MOD_ID, dist = Dist.CLIENT)
public final class ArcadiaChat {

    public static final String MOD_ID = "arcadiachat";

    public ArcadiaChat(IEventBus modBus) {
        modBus.addListener(ClientNetwork::register);
        NeoForge.EVENT_BUS.register(ClientEvents.class);
    }
}
