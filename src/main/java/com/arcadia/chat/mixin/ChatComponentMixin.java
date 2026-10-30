package com.arcadia.chat.mixin;

import java.util.List;
import java.util.Map;

import com.arcadia.chat.client.ChatHeads;
import com.arcadia.chat.tabs.ChatView;
import com.google.common.collect.MapMaker;
import com.arcadia.chat.tabs.TabState;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
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

    private static final String ADD_MESSAGE =
            "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V";

    @Shadow
    @Final
    private List<GuiMessage> allMessages;

    @Shadow
    @Final
    private List<GuiMessage.Line> trimmedMessages;

    /** Pour chaque ligne affichee, le message dont elle fait partie (un long message tient sur plusieurs lignes). */
    @Unique
    private Map<GuiMessage.Line, GuiMessage> arcadiachat$lineOwners;
    @Unique
    private GuiMessage arcadiachat$displaying;

    /** Message en cours d'ajout ; null en dehors d'un ajout (reaffichage, redimensionnement...). */
    @Unique
    private TabState.Incoming arcadiachat$adding;
    @Unique
    private Component arcadiachat$content;

    @Shadow
    private void refreshTrimmedMessages() {
        throw new AssertionError("mixin");
    }

    @Shadow
    public abstract void resetChatScroll();

    @Shadow
    private int getMessageLineIndexAt(double mouseX, double mouseY) {
        throw new AssertionError("mixin");
    }

    @Shadow
    private double screenToChatX(double x) {
        throw new AssertionError("mixin");
    }

    @Shadow
    private double screenToChatY(double y) {
        throw new AssertionError("mixin");
    }

    /**
     * Tables creees au premier usage : un message peut arriver avant que les initialisations ajoutees au
     * constructeur n'aient ete appliquees (le chat est parfois recree par d'autres mods).
     */
    @Unique
    private Map<GuiMessage.Line, GuiMessage> arcadiachat$owners() {
        if (arcadiachat$lineOwners == null) {
            arcadiachat$lineOwners = new MapMaker().weakKeys().makeMap();
        }
        return arcadiachat$lineOwners;
    }

    @Unique
    private Map<FormattedCharSequence, String> arcadiachat$headLines() {
        if (arcadiachat$heads == null) {
            arcadiachat$heads = new MapMaker().weakKeys().makeMap();
        }
        return arcadiachat$heads;
    }

    @Inject(method = "addMessageToDisplayQueue", at = @At("HEAD"))
    private void arcadiachat$beginLines(GuiMessage message, CallbackInfo ci) {
        arcadiachat$displaying = message;
        String sender = arcadiachat$adding != null ? arcadiachat$adding.sender() : null;
        if (sender == null || sender.isEmpty()) {
            TabState.Meta meta = TabState.metaOf(message);
            sender = meta != null && !meta.sender().isEmpty() ? meta.sender() : ChatHeads.senderIn(message.content());
        }
        arcadiachat$headSender = ChatHeads.hasHead(sender) ? sender : null;
        arcadiachat$firstLine = true;
    }

    /** Place de la tete retiree de la largeur des lignes. */
    @ModifyVariable(method = "addMessageToDisplayQueue", ordinal = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/ComponentRenderUtils;wrapComponents(Lnet/minecraft/network/chat/FormattedText;ILnet/minecraft/client/gui/Font;)Ljava/util/List;"))
    private int arcadiachat$narrow(int width) {
        return arcadiachat$headSender != null ? width - ChatHeads.SPACE : width;
    }

    /** Historique plus long que les 100 messages de Minecraft. */
    @ModifyConstant(method = "addMessageToQueue", constant = @Constant(intValue = 100))
    private int arcadiachat$maxMessages(int vanilla) {
        return Math.max(vanilla, ChatConfig.HISTORY.get());
    }

    /** Lignes que l'on peut faire defiler (un message peut en prendre plusieurs). */
    @ModifyConstant(method = "addMessageToDisplayQueue", constant = @Constant(intValue = 100))
    private int arcadiachat$maxLines(int vanilla) {
        return Math.max(vanilla, ChatConfig.HISTORY.get() * 3);
    }

    /** Tete devant la premiere ligne d'un message, et texte decale sur toutes ses lignes. */
    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)I"))
    private int arcadiachat$drawHead(GuiGraphics graphics, Font font, FormattedCharSequence text, int x, int y, int color,
                                     Operation<Integer> original) {
        String sender = arcadiachat$headLines().get(text);
        if (sender == null) {
            return original.call(graphics, font, text, x, y, color);
        }
        if (!sender.isEmpty()) {
            ChatHeads.draw(graphics, sender, x, y, color);
        }
        return original.call(graphics, font, text, x + ChatHeads.SPACE, y, color);
    }

    /** Survol et clic : le texte commence apres la tete. */
    @WrapOperation(method = "getClickedComponentStyleAt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/StringSplitter;componentStyleAtWidth(Lnet/minecraft/util/FormattedCharSequence;I)Lnet/minecraft/network/chat/Style;"))
    private Style arcadiachat$styleAfterHead(StringSplitter splitter, FormattedCharSequence text, int x,
                                              Operation<Style> original) {
        return original.call(splitter, text, arcadiachat$headLines().containsKey(text) ? x - ChatHeads.SPACE : x);
    }

    @Inject(method = "getTagIconLeft", at = @At("RETURN"), cancellable = true)
    private void arcadiachat$tagAfterHead(GuiMessage.Line line, CallbackInfoReturnable<Integer> cir) {
        if (arcadiachat$headLines().containsKey(line.content())) {
            cir.setReturnValue(cir.getReturnValue() + ChatHeads.SPACE);
        }
    }

    @ModifyArg(method = "addMessageToDisplayQueue",
            at = @At(value = "INVOKE", target = "Ljava/util/List;add(ILjava/lang/Object;)V"), index = 1)
    private Object arcadiachat$ownLine(Object line) {
        if (arcadiachat$displaying != null && line instanceof GuiMessage.Line l) {
            arcadiachat$owners().put(l, arcadiachat$displaying);
            if (arcadiachat$headSender != null) {
                arcadiachat$headLines().put(l.content(), arcadiachat$firstLine ? arcadiachat$headSender : "");
                arcadiachat$firstLine = false;
            }
        }
        return line;
    }

    /** Heure du message (en tete de ligne ou au survol). */
    @ModifyVariable(method = ADD_MESSAGE, at = @At("HEAD"), argsOnly = true)
    private Component arcadiachat$decorate(Component content) {
        return TabState.decorate(content);
    }

    /** Barre orange pour les messages qui mentionnent le joueur. */
    @ModifyVariable(method = ADD_MESSAGE, at = @At("HEAD"), argsOnly = true)
    private GuiMessageTag arcadiachat$mention(GuiMessageTag tag, Component content, MessageSignature signature,
                                              GuiMessageTag original) {
        return TabState.mentionTag(content, tag);
    }

    @Inject(method = ADD_MESSAGE, at = @At("HEAD"))
    private void arcadiachat$beginAdd(Component content, MessageSignature signature, GuiMessageTag tag, CallbackInfo ci) {
        arcadiachat$content = content;
        arcadiachat$adding = TabState.currentMessage(content);
    }

    @Inject(method = ADD_MESSAGE, at = @At("RETURN"))
    private void arcadiachat$endAdd(Component content, MessageSignature signature, GuiMessageTag tag, CallbackInfo ci) {
        arcadiachat$adding = null;
        arcadiachat$content = null;
        TabState.endMessage();
    }

    /** Pas de lignes a l'ecran pour un message d'un autre onglet (ou filtre). */
    @Inject(method = "addMessageToDisplayQueue", at = @At("HEAD"), cancellable = true)
    private void arcadiachat$filterDisplay(GuiMessage message, CallbackInfo ci) {
        if (arcadiachat$adding != null && !TabState.displays(arcadiachat$adding, message.content())) {
            ci.cancel();
        }
    }

    /** Range le message (l'objet definitif, avec ses decorations) ; hors de l'onglet actif, il n'est pas affiche. */
    @Inject(method = "addMessageToQueue", at = @At("HEAD"), cancellable = true)
    private void arcadiachat$record(GuiMessage message, CallbackInfo ci) {
        if (arcadiachat$adding != null) {
            TabState.record(message, arcadiachat$adding);
            if (!TabState.displays(arcadiachat$adding, message.content())) {
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

    @Override
    public GuiMessage arcadiachat$messageAt(double mouseX, double mouseY) {
        // Meme conversion que Minecraft : la recherche de ligne attend des coordonnees dans le repere du chat.
        int index = getMessageLineIndexAt(screenToChatX(mouseX), screenToChatY(mouseY));
        if (index < 0 || index >= trimmedMessages.size()) {
            return null;
        }
        return arcadiachat$owners().get(trimmedMessages.get(index));
    }
}
