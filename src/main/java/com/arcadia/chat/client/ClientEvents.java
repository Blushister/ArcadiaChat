package com.arcadia.chat.client;

import com.arcadia.chat.net.ClientNetwork;
import com.arcadia.chat.tabs.Category;
import com.arcadia.chat.tabs.TabState;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ChatType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;

/** Evenements du jeu : classement des messages recus et barre d'onglets du chat. */
public final class ClientEvents {

    private ClientEvents() {
    }

    /**
     * Classe chaque message juste avant qu'il n'arrive dans le chat. En priorite la plus basse : si un
     * autre mod annule le message, on n'est pas appele et rien n'est classe a tort.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onChatReceived(ClientChatReceivedEvent event) {
        if (!TabState.isEnabled()) {
            return;
        }
        if (event instanceof ClientChatReceivedEvent.System system && system.isOverlay()) {
            return; // barre d'action : ne passe pas par le chat
        }
        ChatType.Bound bound = event.getBoundChatType();
        Category category;
        if (bound == null) {
            category = Category.SYSTEM;
        } else if (bound.chatType().is(ChatType.MSG_COMMAND_INCOMING) || bound.chatType().is(ChatType.MSG_COMMAND_OUTGOING)
                || bound.chatType().is(ChatType.TEAM_MSG_COMMAND_INCOMING)
                || bound.chatType().is(ChatType.TEAM_MSG_COMMAND_OUTGOING)) {
            category = Category.PRIVATE;
        } else {
            category = Category.LOCAL;
        }
        TabState.expect(category);
    }

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        ClientNetwork.sendReady();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        TabState.disable();
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (event.getScreen() instanceof ChatScreen && TabState.isEnabled()) {
            TabBar.render(event.getGuiGraphics(), event.getScreen().height, event.getMouseX(), event.getMouseY());
        }
    }

    @SubscribeEvent
    public static void onMouseClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getScreen() instanceof ChatScreen && TabState.isEnabled() && event.getButton() == 0
                && TabBar.click(event.getMouseX(), event.getMouseY())) {
            event.setCanceled(true);
        }
    }

    /** Ctrl+Tab : onglet suivant, Ctrl+Maj+Tab : onglet precedent. */
    @SubscribeEvent
    public static void onKey(ScreenEvent.KeyPressed.Pre event) {
        if (event.getScreen() instanceof ChatScreen && TabState.isEnabled()
                && event.getKeyCode() == GLFW.GLFW_KEY_TAB && Screen.hasControlDown()) {
            TabState.cycle(Screen.hasShiftDown() ? -1 : 1);
            event.setCanceled(true);
        }
    }
}
