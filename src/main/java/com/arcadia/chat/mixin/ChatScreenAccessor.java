package com.arcadia.chat.mixin;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Zone de saisie du chat : pour savoir si le joueur est en train d'ecrire, et lui rendre la main. */
@Mixin(ChatScreen.class)
public interface ChatScreenAccessor {

    @Accessor("input")
    EditBox arcadiachat$input();
}
