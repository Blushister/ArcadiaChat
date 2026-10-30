package com.arcadia.chat.mixin;

import com.arcadia.chat.client.Drafts;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Message ou commande envoye depuis le chat : son brouillon n'a plus lieu d'etre. */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {

    @Inject(method = "handleChatInput", at = @At("HEAD"))
    private void arcadiachat$sent(String message, boolean addToRecentChat, CallbackInfo ci) {
        Drafts.markSent();
    }
}
