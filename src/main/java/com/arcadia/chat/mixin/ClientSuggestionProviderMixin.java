package com.arcadia.chat.mixin;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import com.arcadia.chat.client.Emojis;
import com.arcadia.chat.tabs.TabState;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Completion des pseudos (touche Tab) : les joueurs des autres serveurs Arcadia s'ajoutent a ceux de ce serveur,
 * dans les commandes (/msg...) comme dans le texte ; et "@pseudo" se complete pour les mentions.
 */
@Mixin(ClientSuggestionProvider.class)
public abstract class ClientSuggestionProviderMixin {

    @Inject(method = "getOnlinePlayerNames", at = @At("RETURN"), cancellable = true)
    private void arcadiachat$addRemotePlayers(CallbackInfoReturnable<Collection<String>> cir) {
        if (TabState.remotePlayers().isEmpty()) {
            return;
        }
        Set<String> names = new LinkedHashSet<>(cir.getReturnValue());
        names.addAll(TabState.remotePlayers());
        cir.setReturnValue(names);
    }

    @Inject(method = "getCustomTabSugggestions", at = @At("RETURN"), cancellable = true)
    private void arcadiachat$addMentions(CallbackInfoReturnable<Collection<String>> cir) {
        Set<String> names = new LinkedHashSet<>(cir.getReturnValue());
        if (TabState.isEnabled()) {
            for (String name : cir.getReturnValue()) {
                if (!name.startsWith("@")) {
                    names.add("@" + name);
                }
            }
            // Mentions de groupe : @staff pour tous ; @everyone et @tous pour le staff (le serveur verifie le droit).
            names.add("@staff");
            if (TabState.isStaff()) {
                names.add("@everyone");
                names.add("@tous");
            }
        }
        names.addAll(Emojis.suggestions());
        cir.setReturnValue(names);
    }
}
