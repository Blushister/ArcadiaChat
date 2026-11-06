package com.arcadia.chat.client;

import com.arcadia.chat.ChatConfig;
import com.arcadia.chat.mixin.ChatScreenAccessor;
import com.arcadia.chat.net.ClientNetwork;
import com.arcadia.chat.net.TabsProtocol;
import com.arcadia.chat.tabs.Category;
import com.arcadia.chat.tabs.TabState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientChatEvent;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.lwjgl.glfw.GLFW;

/** Evenements du jeu : classement des messages, envoi selon l'onglet, barre d'onglets, recherche, frappe. */
public final class ClientEvents {

    /** Intervalle de rappel "en train d'ecrire" (le serveur l'oublie au bout de 6 s). */
    private static final long TYPING_REFRESH_MS = 3000;

    private static EditBox searchBox;
    private static boolean typing;
    private static long typingSentAt;

    private ClientEvents() {
    }

    // ---------------------------------------------------------------- messages recus

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
        if (bound == null) {
            TabState.expect(Category.SYSTEM, "", "");
        } else if (bound.chatType().is(ChatType.MSG_COMMAND_INCOMING) || bound.chatType().is(ChatType.TEAM_MSG_COMMAND_INCOMING)) {
            TabState.expect(Category.PRIVATE, bound.name().getString(), bound.name().getString());
        } else if (bound.chatType().is(ChatType.MSG_COMMAND_OUTGOING) || bound.chatType().is(ChatType.TEAM_MSG_COMMAND_OUTGOING)) {
            TabState.expect(Category.PRIVATE, bound.targetName().map(Component::getString).orElse(""), "");
        } else {
            TabState.expect(Category.LOCAL, "", bound.name().getString());
        }
    }

    // ---------------------------------------------------------------- message envoye

    /** Onglet Staff, canal ou conversation : le message part la-bas plutot que dans le chat public. */
    @SubscribeEvent
    public static void onChatSent(ClientChatEvent event) {
        if (!TabState.isEnabled()) {
            return;
        }
        String text = event.getMessage();
        switch (TabState.target()) {
            case STAFF -> {
                ClientNetwork.sendTo(TabsProtocol.CHANNEL_STAFF, text);
                event.setCanceled(true);
            }
            case TEAM -> {
                ClientNetwork.sendTo(TabsProtocol.CHANNEL_TEAM, text);
                event.setCanceled(true);
            }
            case CHANNEL -> {
                ClientNetwork.sendTo(TabsProtocol.CHANNEL_PREFIX + TabState.active().key(), text);
                event.setCanceled(true);
            }
            case CONVERSATION -> {
                ClientPacketListener connection = Minecraft.getInstance().getConnection();
                if (connection != null) {
                    connection.sendCommand("msg " + TabState.active().key() + " " + text);
                }
                event.setCanceled(true);
            }
            case NONE -> {
                // Onglet Prive : rien ne doit partir dans le chat public.
                event.setCanceled(true);
                Minecraft.getInstance().gui.setOverlayMessage(
                        Component.translatable("arcadiachat.private.hint").withStyle(ChatFormatting.YELLOW), false);
            }
            default -> {
            }
        }
        stopTyping();
    }

    // ---------------------------------------------------------------- connexion

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        ClientNetwork.sendReady();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        TabState.disable();
        ChatHeads.clear();
        typing = false;
    }

    @SubscribeEvent
    public static void onClientCommands(RegisterClientCommandsEvent event) {
        ServerJoin.register(event.getDispatcher());
    }

    // ---------------------------------------------------------------- ecran du chat

    /** Touche du chat : l'ecran facon Discord s'ouvre a la place du chat habituel si le joueur l'a choisi. */
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (event.getNewScreen() != null && event.getNewScreen().getClass() == ChatScreen.class
                && ChatConfig.ROOMS_VIEW.get() && ChatRooms.available() && RoomsUi.installed()
                && ((ChatScreenAccessor) event.getNewScreen()).arcadiachat$initial().isEmpty()) {
            event.setNewScreen(RoomsUi.create(""));
        }
    }

    /** Les bulles d'aide des ecrans du mod suivent le theme ; leur texte reste clair, sur un fond toujours sombre. */
    @SubscribeEvent
    public static void onTooltipColor(RenderTooltipEvent.Color event) {
        Screen screen = Minecraft.getInstance().screen;
        if (RoomsUi.isRooms(screen) || screen instanceof ThemedScreen || screen instanceof ThemedConfirmScreen) {
            ChatTheme theme = ChatTheme.current();
            event.setBackground(theme == ChatTheme.LIGHT ? 0xF0202028 : theme.field);
            event.setBorderStart(theme.outline);
            event.setBorderEnd(theme.outline);
        }
    }

    /** Sous l'ecran facon Discord, le chat du jeu ne se dessine pas. */
    @SubscribeEvent
    public static void onGuiLayerPre(RenderGuiLayerEvent.Pre event) {
        if (VanillaGuiLayers.CHAT.equals(event.getName()) && RoomsUi.isRooms(Minecraft.getInstance().screen)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof ChatScreen screen)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        TabState.reapplySettings();
        Drafts.onOpen(screen);
        searchBox = new EditBox(mc.font, 2, TabBar.top(screen.height) - TabBar.HEIGHT - 2, 160, TabBar.HEIGHT,
                Component.translatable("arcadiachat.search"));
        searchBox.setHint(Component.translatable("arcadiachat.search.hint"));
        searchBox.setMaxLength(64);
        searchBox.setResponder(TabState::setSearch);
        searchBox.setVisible(false);
        event.addListener(searchBox);
    }

    @SubscribeEvent
    public static void onScreenClosing(ScreenEvent.Closing event) {
        if (event.getScreen() instanceof ChatScreen screen) {
            Drafts.onClose(screen);
            EmojiPicker.close();
            MessageMenu.close();
            closeSearch(null);
            stopTyping();
        }
    }

    /** Bulles de chat au-dessus des joueurs proches. */
    @SubscribeEvent
    public static void onNameTag(RenderNameTagEvent event) {
        ChatBubbles.render(event);
    }

    // ---------------------------------------------------------------- sous-menu de l'historique, sur tous les ecrans

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void onHistoryRender(ScreenEvent.Render.Post event) {
        HistoryPopup.render(event.getGuiGraphics(), event.getMouseX(), event.getMouseY());
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
    public static void onHistoryClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (HistoryPopup.click(event.getMouseX(), event.getMouseY())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
    public static void onHistoryKey(ScreenEvent.KeyPressed.Pre event) {
        if (event.getKeyCode() == GLFW.GLFW_KEY_ESCAPE && HistoryPopup.isOpen()) {
            HistoryPopup.close();
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
    public static void onHistoryScroll(ScreenEvent.MouseScrolled.Pre event) {
        if (HistoryPopup.scroll(event.getMouseX(), event.getMouseY(), event.getScrollDeltaY())) {
            event.setCanceled(true);
        }
    }

    /** "... ecrit" sous les messages du chat, chat ouvert ou ferme. */
    @SubscribeEvent
    public static void onGuiLayer(RenderGuiLayerEvent.Post event) {
        if (VanillaGuiLayers.CHAT.equals(event.getName())) {
            TypingIndicator.render(event.getGuiGraphics());
            ChatToasts.render(event.getGuiGraphics());
        }
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (event.getScreen() instanceof ChatScreen screen && TabState.isEnabled()) {
            TabBar.render(event.getGuiGraphics(), screen.width, screen.height, event.getMouseX(), event.getMouseY());
            ChatOverlay.render(event.getGuiGraphics(), screen.height, event.getMouseX(), event.getMouseY());
            EmojiPicker.render(event.getGuiGraphics(), screen.width, screen.height, event.getMouseX(), event.getMouseY());
            MessageMenu.render(event.getGuiGraphics(), event.getMouseX(), event.getMouseY());
            if (ChatRooms.available() && RoomsUi.installed()) {
                ViewSwitch.render(event.getGuiGraphics(), 2, 2, event.getMouseX(), event.getMouseY());
                if (ViewSwitch.contains(2, 2, event.getMouseX(), event.getMouseY())) {
                    event.getGuiGraphics().renderTooltip(Minecraft.getInstance().font,
                            Component.translatable("arcadiachat.rooms.to_rooms"), event.getMouseX(), event.getMouseY());
                }
            }
        }
    }

    @SubscribeEvent
    public static void onMouseClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.isCanceled()) {
            return;
        }
        if (!(event.getScreen() instanceof ChatScreen screen) || !TabState.isEnabled()) {
            return;
        }
        if (event.getButton() == 0 && ChatRooms.available() && RoomsUi.installed() && ViewSwitch.contains(2, 2, event.getMouseX(), event.getMouseY())) {
            event.setCanceled(true);
            ViewSwitch.toRooms(((ChatScreenAccessor) screen).arcadiachat$input().getValue());
            return;
        }
        // Menu ouvert : le clic le ferme (et lance l'action touchee).
        if (MessageMenu.click(event.getMouseX(), event.getMouseY())) {
            event.setCanceled(true);
            return;
        }
        // Le bouton du selecteur le referme lui-meme : ne pas le fermer d'abord comme un clic exterieur.
        if (event.getButton() == 0 && EmojiPicker.isOpen() && !TabBar.isEmojiButton(event.getMouseX(), event.getMouseY())
                && EmojiPicker.click(screen, event.getMouseX(), event.getMouseY())) {
            event.setCanceled(true);
            return;
        }
        if (event.getButton() == 0 && ChatOverlay.click(event.getMouseX(), event.getMouseY())) {
            event.setCanceled(true);
            return;
        }
        if ((event.getButton() == 0 || event.getButton() == 1)
                && TabBar.click(event.getMouseX(), event.getMouseY(), event.getButton())) {
            event.setCanceled(true);
        } else if (event.getButton() == 1 && MessageMenu.open(screen, event.getMouseX(), event.getMouseY())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMouseScroll(ScreenEvent.MouseScrolled.Pre event) {
        if (event.getScreen() instanceof ChatScreen
                && EmojiPicker.scroll(event.getMouseX(), event.getMouseY(), event.getScrollDeltaY())) {
            event.setCanceled(true);
        }
    }

    /**
     * Ctrl+Tab / Ctrl+Maj+Tab : onglet suivant / precedent. Ctrl+F : rechercher dans l'onglet. Pendant une
     * recherche, Entree garde le resultat et rend la main a la saisie, Echap annule la recherche.
     */
    @SubscribeEvent
    public static void onKey(ScreenEvent.KeyPressed.Pre event) {
        if (!(event.getScreen() instanceof ChatScreen screen)) {
            return;
        }
        int key = event.getKeyCode();
        // Minecraft teste la position de la touche sur un clavier QWERTY : sur AZERTY, son Ctrl+A est le Ctrl+Q.
        if (Screen.hasControlDown() && !Screen.hasShiftDown() && !Screen.hasAltDown()
                && "a".equalsIgnoreCase(GLFW.glfwGetKeyName(key, event.getScanCode()))
                && screen.getFocused() instanceof EditBox box) {
            box.moveCursorToEnd(false);
            box.setHighlightPos(0);
            event.setCanceled(true);
            return;
        }
        if (!TabState.isEnabled()) {
            return;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE && EmojiPicker.isOpen()) {
            EmojiPicker.close();
            event.setCanceled(true);
            return;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE && MessageMenu.isOpen()) {
            MessageMenu.close();
            event.setCanceled(true);
            return;
        }
        if (key == GLFW.GLFW_KEY_TAB && Screen.hasControlDown()) {
            TabState.cycle(Screen.hasShiftDown() ? -1 : 1);
            event.setCanceled(true);
        } else if (key == GLFW.GLFW_KEY_F && Screen.hasControlDown()) {
            if (searchBox != null) {
                if (searchBox.isVisible()) {
                    closeSearch(screen);
                } else {
                    searchBox.setVisible(true);
                    screen.setFocused(searchBox);
                }
            }
            event.setCanceled(true);
        } else if (searchBox != null && searchBox.isVisible() && screen.getFocused() == searchBox) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                closeSearch(screen);
                event.setCanceled(true);
            } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                screen.setFocused(((ChatScreenAccessor) screen).arcadiachat$input());
                event.setCanceled(true);
            }
        }
    }

    private static void closeSearch(ChatScreen screen) {
        if (searchBox == null) {
            return;
        }
        searchBox.setValue("");
        searchBox.setVisible(false);
        TabState.setSearch("");
        if (screen != null) {
            screen.setFocused(((ChatScreenAccessor) screen).arcadiachat$input());
        }
    }

    // ---------------------------------------------------------------- "est en train d'ecrire"

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!TabState.typingEnabled() || !ChatConfig.SEND_TYPING.get()) {
            return;
        }
        boolean now = false;
        if (Minecraft.getInstance().screen instanceof ChatScreen screen) {
            String value = ((ChatScreenAccessor) screen).arcadiachat$input().getValue();
            TabState.Target target = TabState.target();
            now = !value.isBlank() && !value.startsWith("/")
                    && (target == TabState.Target.GLOBAL || target == TabState.Target.LOCAL);
        } else if (RoomsUi.isRooms(Minecraft.getInstance().screen)) {
            now = !RoomsUi.typingText(Minecraft.getInstance().screen).isBlank();
        }
        long time = System.currentTimeMillis();
        if (now && (!typing || time - typingSentAt > TYPING_REFRESH_MS)) {
            ClientNetwork.sendTyping(true);
            typingSentAt = time;
            typing = true;
        } else if (!now && typing) {
            stopTyping();
        }
    }

    private static void stopTyping() {
        if (typing) {
            typing = false;
            ClientNetwork.sendTyping(false);
        }
    }
}
