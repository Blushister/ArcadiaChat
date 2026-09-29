package com.arcadia.chat.tabs;

import java.util.List;

import net.minecraft.client.GuiMessage;

/** Acces au contenu affiche par le chat de Minecraft (implemente par ChatComponentMixin). */
public interface ChatView {

    /** Remplace les messages affiches (du plus recent au plus ancien). */
    void arcadiachat$show(List<GuiMessage> newestFirst);

    /** Messages actuellement affiches, du plus recent au plus ancien. */
    List<GuiMessage> arcadiachat$shown();
}
