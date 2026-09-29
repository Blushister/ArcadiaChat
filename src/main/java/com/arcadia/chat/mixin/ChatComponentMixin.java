package com.arcadia.chat.mixin;

import java.util.List;

import com.arcadia.chat.tabs.Category;
import com.arcadia.chat.tabs.ChatView;
import com.arcadia.chat.tabs.TabState;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Le chat de Minecraft n'affiche que l'onglet actif.
 *
 * <p>Chaque message suit le chemin normal de Minecraft jusqu'au bout de sa creation : les autres mods de chat
 * (tetes des joueurs, dechiffrement de No Chat Reports...) le decorent comme d'habitude. On intervient
 * seulement au moment de l'afficher et de le ranger : il est garde dans l'historique d'ArcadiaChat, tel
 * quel, et n'entre dans l'affichage que si l'onglet actif l'accepte. En changeant d'onglet, on reaffiche ces
 * memes messages, avec leurs decorations.
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin implements ChatView {

    @Shadow
    @Final
    private List<GuiMessage> allMessages;

    /** Categorie du message en cours d'ajout ; null en dehors d'un ajout (reaffichage, redimensionnement...). */
    @Unique
    private Category arcadiachat$adding;

    @Shadow
    private void refreshTrimmedMessages() {
        throw new AssertionError("mixin");
    }

    @Shadow
    public abstract void resetChatScroll();

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
            at = @At("HEAD"))
    private void arcadiachat$beginAdd(Component content, MessageSignature signature, GuiMessageTag tag, CallbackInfo ci) {
        arcadiachat$adding = TabState.isEnabled() ? TabState.takeCategory() : null;
    }

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
            at = @At("RETURN"))
    private void arcadiachat$endAdd(Component content, MessageSignature signature, GuiMessageTag tag, CallbackInfo ci) {
        arcadiachat$adding = null;
    }

    /** Pas de lignes a l'ecran pour un message d'un autre onglet. */
    @Inject(method = "addMessageToDisplayQueue", at = @At("HEAD"), cancellable = true)
    private void arcadiachat$filterDisplay(GuiMessage message, CallbackInfo ci) {
        if (arcadiachat$adding != null && !TabState.shows(arcadiachat$adding)) {
            ci.cancel();
        }
    }

    /** Range le message (l'objet definitif, avec ses decorations) ; hors de l'onglet actif, il n'est pas affiche. */
    @Inject(method = "addMessageToQueue", at = @At("HEAD"), cancellable = true)
    private void arcadiachat$record(GuiMessage message, CallbackInfo ci) {
        if (arcadiachat$adding != null) {
            TabState.record(message, arcadiachat$adding);
            if (!TabState.shows(arcadiachat$adding)) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "clearMessages", at = @At("HEAD"))
    private void arcadiachat$clear(boolean clearSentMsgHistory, CallbackInfo ci) {
        TabState.clearHistory();
    }

    @Override
    public void arcadiachat$show(List<GuiMessage> newestFirst) {
        allMessages.clear();
        allMessages.addAll(newestFirst);
        resetChatScroll();
        refreshTrimmedMessages();
    }

    @Override
    public List<GuiMessage> arcadiachat$shown() {
        return allMessages;
    }
}
