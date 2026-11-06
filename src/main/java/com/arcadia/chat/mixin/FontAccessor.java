package com.arcadia.chat.mixin;

import java.util.function.Function;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Donne acces aux jeux de polices du jeu, pour en faire une version dont la police par defaut est celle de l'interface. */
@Mixin(Font.class)
public interface FontAccessor {

    @Accessor("fonts")
    Function<ResourceLocation, FontSet> arcadiachat$fonts();
}
