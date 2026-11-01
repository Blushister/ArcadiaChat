package com.arcadia.chat.mixin;

import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mentions et emojis : des qu'on tape "@" ou ":" suivi d'une lettre dans le chat, les pseudos, les mentions de groupe
 * ou les emojis sont proposes, sans attendre la touche Tab.
 */
@Mixin(CommandSuggestions.class)
public abstract class CommandSuggestionsMixin {

    @Shadow
    @Final
    EditBox input;

    @Shadow
    private CompletableFuture<Suggestions> pendingSuggestions;

    @Shadow
    public abstract void showSuggestions(boolean narrateFirstSuggestion);

    @Inject(method = "updateCommandInfo", at = @At("RETURN"))
    private void arcadiachat$showMentions(CallbackInfo ci) {
        String text = input.getValue();
        int cursor = input.getCursorPosition();
        if (text.startsWith("/") || cursor <= 0 || cursor > text.length()) {
            return;
        }
        String beforeCursor = text.substring(0, cursor);
        int start = beforeCursor.lastIndexOf(' ') + 1;
        String word = beforeCursor.substring(start);
        CompletableFuture<Suggestions> pending = pendingSuggestions;
        if (word.length() >= 2 && (word.startsWith("@") || word.startsWith(":")) && pending != null && pending.isDone()
                && !pending.join().isEmpty()) {
            showSuggestions(false);
        }
    }
}
