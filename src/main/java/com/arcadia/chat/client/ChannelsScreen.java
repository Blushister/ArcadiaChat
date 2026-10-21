package com.arcadia.chat.client;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import com.arcadia.chat.net.ClientNetwork;
import com.arcadia.chat.net.TabsProtocol;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Ecran des canaux thematiques (bouton # de la barre d'onglets) : rejoindre ou quitter un canal, et pour le
 * staff, creer un canal public ou prive, y ajouter des joueurs, voir ses membres ou le supprimer.
 *
 * <p>Chaque action passe par les commandes /canal du serveur : les permissions et les verifications restent
 * celles du serveur, l'ecran ne fait que les rendre accessibles.
 */
public final class ChannelsScreen extends Screen {

    private static final Pattern NAME = Pattern.compile("[a-z0-9_-]{2,24}");
    private static final int ROWS = 7;
    private static final int ROW_HEIGHT = 22;
    private static final int WIDTH = 320;
    /** Delai avant de relire la liste apres une action (le serveur repond en base, de facon asynchrone). */
    private static final int REFRESH_TICKS = 10;

    private final Screen parent;
    private List<TabsProtocol.ChannelEntry> channels = List.of();
    private boolean manager;
    private boolean loaded;
    private int page;
    private String selected;
    private boolean newPrivate;
    private String newNameText = "";
    private String playersText = "";
    private int refreshIn = -1;

    private EditBox newName;
    private EditBox players;

    public ChannelsScreen(Screen parent) {
        super(Component.translatable("arcadiachat.channels.title"));
        this.parent = parent;
    }

    /** Ecran ouvert, s'il l'est : pour lui transmettre la liste recue du serveur. */
    public static ChannelsScreen current() {
        return Minecraft.getInstance().screen instanceof ChannelsScreen screen ? screen : null;
    }

    public void onDirectory(TabsProtocol.ChannelDirectory directory) {
        channels = directory.channels();
        manager = directory.manager();
        loaded = true;
        if (selected != null && channels.stream().noneMatch(c -> c.name().equals(selected))) {
            selected = null;
        }
        page = Math.min(page, Math.max(0, (channels.size() - 1) / ROWS));
        rebuildWidgets();
    }

    /** Les canaux du joueur ont change (ajout, retrait...) : on relit la liste. */
    public void refreshSoon() {
        refreshIn = REFRESH_TICKS;
    }

    @Override
    protected void init() {
        if (!loaded) {
            ClientNetwork.requestChannels();
        }
        int left = width / 2 - WIDTH / 2;
        int top = 36;

        // ---- liste des canaux, page par page
        for (int i = 0; i < ROWS; i++) {
            int index = page * ROWS + i;
            if (index >= channels.size()) {
                break;
            }
            TabsProtocol.ChannelEntry entry = channels.get(index);
            int y = top + i * ROW_HEIGHT;
            Button nameButton = Button.builder(rowLabel(entry), b -> {
                selected = entry.name();
                rebuildWidgets();
            }).bounds(left, y, 200, 20).build();
            nameButton.active = manager;
            addRenderableWidget(nameButton);

            Button action;
            if (entry.member()) {
                action = Button.builder(Component.translatable("arcadiachat.channels.leave"),
                        b -> run("canal leave " + entry.name())).bounds(left + 204, y, 116, 20).build();
            } else {
                action = Button.builder(Component.translatable(entry.isPrivate()
                        ? "arcadiachat.channels.private" : "arcadiachat.channels.join"),
                        b -> run("canal join " + entry.name())).bounds(left + 204, y, 116, 20).build();
                action.active = !entry.isPrivate();
            }
            addRenderableWidget(action);
        }
        int pages = Math.max(1, (channels.size() + ROWS - 1) / ROWS);
        int pagerY = top + ROWS * ROW_HEIGHT;
        if (pages > 1) {
            Button previous = Button.builder(Component.literal("<"), b -> {
                page--;
                rebuildWidgets();
            }).bounds(left, pagerY, 20, 20).build();
            previous.active = page > 0;
            Button next = Button.builder(Component.literal(">"), b -> {
                page++;
                rebuildWidgets();
            }).bounds(left + WIDTH - 20, pagerY, 20, 20).build();
            next.active = page < pages - 1;
            addRenderableWidget(previous);
            addRenderableWidget(next);
        }

        // ---- gestion (staff)
        if (manager) {
            int y = pagerY + 28;
            newName = new EditBox(font, left, y, 150, 20, Component.translatable("arcadiachat.channels.new_name"));
            newName.setHint(Component.translatable("arcadiachat.channels.new_name").withStyle(ChatFormatting.DARK_GRAY));
            newName.setMaxLength(24);
            newName.setValue(newNameText);
            newName.setResponder(value -> newNameText = value);
            addRenderableWidget(newName);
            addRenderableWidget(Button.builder(Component.translatable(newPrivate
                    ? "arcadiachat.channels.mode_private" : "arcadiachat.channels.mode_public"), b -> {
                newPrivate = !newPrivate;
                rebuildWidgets();
            }).bounds(left + 154, y, 74, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("arcadiachat.channels.create"), b -> {
                String name = newNameText.trim().toLowerCase(Locale.ROOT).replaceFirst("^#", "");
                if (NAME.matcher(name).matches()) {
                    run("canal create " + name + (newPrivate ? " prive" : " public"));
                    newNameText = "";
                    selected = name;
                    rebuildWidgets();
                }
            }).bounds(left + 232, y, 88, 20).build());

            y += 24;
            players = new EditBox(font, left, y, 150, 20, Component.translatable("arcadiachat.channels.players"));
            players.setHint(Component.translatable("arcadiachat.channels.players").withStyle(ChatFormatting.DARK_GRAY));
            players.setMaxLength(200);
            players.setValue(playersText);
            players.setResponder(value -> playersText = value);
            addRenderableWidget(players);
            Button add = Button.builder(Component.translatable("arcadiachat.channels.add", selectedLabel()), b -> {
                if (selected != null && !playersText.isBlank()) {
                    run("canal add " + selected + " " + playersText.trim());
                    playersText = "";
                    rebuildWidgets();
                }
            }).bounds(left + 154, y, 166, 20).build();
            add.active = selected != null;
            addRenderableWidget(add);

            y += 24;
            Button members = Button.builder(Component.translatable("arcadiachat.channels.members", selectedLabel()),
                    b -> run("canal members " + selected)).bounds(left, y, 158, 20).build();
            members.active = selected != null;
            addRenderableWidget(members);
            Button delete = Button.builder(Component.translatable("arcadiachat.channels.delete", selectedLabel()),
                    b -> confirmDelete()).bounds(left + 162, y, 158, 20).build();
            delete.active = selected != null;
            addRenderableWidget(delete);
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(width / 2 - 100, height - 28, 200, 20).build());
    }

    private Component rowLabel(TabsProtocol.ChannelEntry entry) {
        MutableComponent label = Component.literal("#" + entry.name());
        if (entry.isPrivate()) {
            label.append(Component.literal(" ").append(Component.translatable("arcadiachat.channels.private_tag"))
                    .withStyle(ChatFormatting.GRAY));
        }
        if (entry.member()) {
            label.append(Component.literal(" ✔").withStyle(ChatFormatting.GREEN));
        }
        if (entry.name().equals(selected)) {
            label = Component.literal("> ").withStyle(ChatFormatting.GOLD).append(label);
        }
        return label;
    }

    private String selectedLabel() {
        return selected == null ? "..." : "#" + selected;
    }

    private void confirmDelete() {
        String name = selected;
        if (name == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new ConfirmScreen(yes -> {
            if (yes) {
                run("canal delete " + name);
                selected = null;
            }
            mc.setScreen(this);
        }, Component.translatable("arcadiachat.channels.delete_title", name),
                Component.translatable("arcadiachat.channels.delete_text")));
    }

    /** Lance une commande /canal, puis relit la liste un peu plus tard. */
    private void run(String command) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.sendCommand(command);
            refreshSoon();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (refreshIn > 0 && --refreshIn == 0) {
            refreshIn = -1;
            ClientNetwork.requestChannels();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);
        int left = width / 2 - WIDTH / 2;
        if (!loaded) {
            graphics.drawCenteredString(font, Component.translatable("arcadiachat.channels.loading"), width / 2, 40, 0xAAAAAA);
        } else if (channels.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("arcadiachat.channels.none"), width / 2, 40, 0xAAAAAA);
        }
        if (manager) {
            int y = 36 + ROWS * ROW_HEIGHT + 16;
            graphics.drawString(font, Component.translatable("arcadiachat.channels.manage")
                    .withStyle(ChatFormatting.GOLD), left, y, 0xFFFFFF);
        }
        graphics.drawCenteredString(font, Component.translatable("arcadiachat.channels.feedback")
                .withStyle(ChatFormatting.DARK_GRAY), width / 2, height - 40, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
