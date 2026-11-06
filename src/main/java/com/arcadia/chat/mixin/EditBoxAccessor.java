package com.arcadia.chat.mixin;

import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Position du texte affiche et de la selection, pour dessiner la zone de saisie de l'ecran des salons. */
@Mixin(EditBox.class)
public interface EditBoxAccessor {

    @Accessor("displayPos")
    int arcadiachat$displayPos();

    @Accessor("highlightPos")
    int arcadiachat$highlightPos();
}
