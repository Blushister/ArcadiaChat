package com.arcadia.chat.mixin;

import com.arcadia.chat.ChatConfig;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.world.entity.player.ProfileKeyPair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Chat non signalable : sur un serveur qui n'exige pas la signature des messages, la cle de signature n'est pas
 * transmise, donc aucun message ne peut etre signale a Mojang. Sur un serveur qui l'exige, rien ne change (le
 * joueur ne pourrait plus ecrire).
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @Shadow
    private boolean serverEnforcesSecureChat;

    @Shadow
    private boolean seenInsecureChatWarning;

    @Inject(method = "setKeyPair", at = @At("HEAD"), cancellable = true)
    private void arcadiachat$keepUnsigned(ProfileKeyPair keyPair, CallbackInfo ci) {
        if (ChatConfig.UNSIGNED_CHAT.get() && !serverEnforcesSecureChat) {
            ci.cancel();
        }
    }

    /** Avertissement "le chat ne peut pas etre verifie" : marque comme deja vu. */
    @Inject(method = "handleLogin", at = @At("HEAD"))
    private void arcadiachat$hideUnsecureWarning(ClientboundLoginPacket packet, CallbackInfo ci) {
        if (ChatConfig.HIDE_UNSECURE_WARNING.get()) {
            seenInsecureChatWarning = true;
        }
    }
}
