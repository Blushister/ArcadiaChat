package com.arcadia.chat.client;

import java.util.ArrayList;
import java.util.List;

import com.arcadia.chat.net.ClientNetwork;
import com.arcadia.chat.net.TabsProtocol;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Panel staff (bouton ⚑ de la barre d'onglets) : mode lent et verrou du chat par serveur ou pour tous, annonces
 * automatiques et mots filtres. Le serveur n'envoie que les sections permises, et verifie chaque action.
 */
public final class StaffPanelScreen extends Screen {

    /** Valeurs proposees pour le mode lent, en secondes (0 : desactive). */
    private static final int[] SLOW_STEPS = {0, 3, 5, 10, 15, 30, 60, 120};
    private static final String ALL = "*";
    private static final int WIDTH = 340;
    private static final int TOP = 54;
    private static final int ROW_HEIGHT = 22;
    /** Hauteur du formulaire d'ajout, en bas de l'ecran. */
    private static final int FORM_HEIGHT = 58;

    private final Screen parent;
    private TabsProtocol.PanelData data;
    private Section section;
    private int page;
    private int rows = 6;

    public StaffPanelScreen(Screen parent) {
        super(Component.translatable("arcadiachat.panel.title"));
        this.parent = parent;
    }

    public static StaffPanelScreen current() {
        return Minecraft.getInstance().screen instanceof StaffPanelScreen screen ? screen : null;
    }

    public void onData(TabsProtocol.PanelData received) {
        data = received;
        if (section == null || !allowed(section)) {
            section = firstAllowed();
            page = 0;
        }
        rebuildWidgets();
    }

    private Section firstAllowed() {
        for (Section s : Section.values()) {
            if (allowed(s)) {
                return s;
            }
        }
        return null;
    }

    private Component sectionLabel(Section s) {
        MutableComponent label = Component.translatable("arcadiachat.panel." + s.name().toLowerCase());
        return s == section ? label.withStyle(ChatFormatting.GOLD) : label;
    }

    // ---------------------------------------------------------------- mode lent et verrou

    private void initRules(int left) {
        List<String[]> scopes = new ArrayList<>();
        scopes.add(new String[] {ALL, Component.translatable("arcadiachat.panel.all_servers").getString()});
        data.servers().forEach(server -> scopes.add(new String[] {server.id(), server.name()}));
        rows = Math.max(1, (height - 34 - TOP - 14) / ROW_HEIGHT);
        List<String[]> visible = pageOf(scopes);
        for (int i = 0; i < visible.size(); i++) {
            String scope = visible.get(i)[0];
            TabsProtocol.RuleEntry rule = rule(scope);
            int y = TOP + i * ROW_HEIGHT;
            Button name = Button.builder(Component.literal(visible.get(i)[1]).withStyle(ALL.equals(scope)
                    ? ChatFormatting.AQUA : ChatFormatting.WHITE), b -> { }).bounds(left, y, 140, 20).build();
            name.active = false;
            addRenderableWidget(name);
            Component slowLabel = rule.slowSeconds() > 0
                    ? Component.translatable("arcadiachat.panel.slow", rule.slowSeconds()).withStyle(ChatFormatting.YELLOW)
                    : Component.translatable("arcadiachat.panel.slow_off");
            Button slow = Button.builder(slowLabel, b -> ClientNetwork.panelAction("rule", scope,
                    String.valueOf(nextSlow(rule.slowSeconds())), String.valueOf(rule.locked())))
                    .bounds(left + 144, y, 96, 20).build();
            slow.setTooltip(Tooltip.create(Component.translatable("arcadiachat.panel.slow_hint")));
            addRenderableWidget(slow);
            addRenderableWidget(Button.builder(rule.locked()
                            ? Component.translatable("arcadiachat.panel.locked").withStyle(ChatFormatting.RED)
                            : Component.translatable("arcadiachat.panel.open").withStyle(ChatFormatting.GREEN),
                    b -> ClientNetwork.panelAction("rule", scope, String.valueOf(rule.slowSeconds()),
                            String.valueOf(!rule.locked())))
                    .bounds(left + 244, y, 96, 20).build());
        }
        addPager(left, TOP + rows * ROW_HEIGHT, scopes.size());
    }

    private TabsProtocol.RuleEntry rule(String scope) {
        return data.rules().stream().filter(r -> r.scope().equals(scope)).findFirst()
                .orElse(new TabsProtocol.RuleEntry(scope, 0, false));
    }

    private static int nextSlow(int current) {
        for (int step : SLOW_STEPS) {
            if (step > current) {
                return step;
            }
        }
        return 0;
    }

    // ---------------------------------------------------------------- annonces

    private void initAnnouncements(int left) {
        int formTop = height - 30 - FORM_HEIGHT;
        rows = Math.max(1, (formTop - 6 - TOP - ROW_HEIGHT) / ROW_HEIGHT);
        List<TabsProtocol.AnnouncementEntry> visible = pageOf(data.announcements());
        for (int i = 0; i < visible.size(); i++) {
            TabsProtocol.AnnouncementEntry entry = visible.get(i);
            int y = TOP + i * ROW_HEIGHT;
            MutableComponent label = Component.literal(entry.intervalMinutes() + " min ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(ellipsis(stripCodes(entry.message()), 30))
                            .withStyle(entry.enabled() ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY));
            Button text = Button.builder(label, b -> { }).bounds(left, y, 216, 20).build();
            text.active = false;
            text.setTooltip(Tooltip.create(Component.literal(stripCodes(entry.message())).append("\n")
                    .append(Component.translatable("arcadiachat.panel.announce_info", entry.intervalMinutes(),
                            serversLabel(entry.servers()), Component.translatable(entry.discord()
                                    ? "arcadiachat.panel.yes" : "arcadiachat.panel.no"))
                            .withStyle(ChatFormatting.GRAY))));
            addRenderableWidget(text);
            addRenderableWidget(Button.builder(Component.translatable(entry.enabled()
                            ? "arcadiachat.panel.enabled" : "arcadiachat.panel.disabled")
                            .withStyle(entry.enabled() ? ChatFormatting.GREEN : ChatFormatting.GRAY),
                    b -> ClientNetwork.panelAction("announce_toggle", String.valueOf(entry.id()),
                            String.valueOf(!entry.enabled())))
                    .bounds(left + 220, y, 60, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("arcadiachat.panel.delete"),
                    b -> confirm(Component.translatable("arcadiachat.panel.delete_announce"),
                            () -> ClientNetwork.panelAction("announce_delete", String.valueOf(entry.id()))))
                    .bounds(left + 284, y, 56, 20).build());
        }
        addPager(left, TOP + rows * ROW_HEIGHT, data.announcements().size());

        // ---- formulaire d'ajout
        int y = formTop + 12;
        EditBox message = new EditBox(font, left, y, WIDTH, 20, Component.translatable("arcadiachat.panel.announce_text"));
        message.setHint(Component.translatable("arcadiachat.panel.announce_text").withStyle(ChatFormatting.DARK_GRAY));
        message.setMaxLength(256);
        message.setValue(announceText);
        message.setResponder(value -> announceText = value);
        addRenderableWidget(message);
        y += 24;
        EditBox minutes = new EditBox(font, left, y, 40, 20, Component.translatable("arcadiachat.panel.minutes"));
        minutes.setMaxLength(4);
        minutes.setFilter(value -> value.matches("\\d*"));
        minutes.setValue(announceMinutes);
        minutes.setResponder(value -> announceMinutes = value);
        minutes.setTooltip(Tooltip.create(Component.translatable("arcadiachat.panel.minutes")));
        addRenderableWidget(minutes);
        String serverText = announceServer < 0 || announceServer >= data.servers().size()
                ? Component.translatable("arcadiachat.panel.all_servers").getString()
                : data.servers().get(announceServer).name();
        addRenderableWidget(Button.builder(Component.literal(ellipsis(serverText, 18)), b -> {
            announceServer = announceServer + 1 >= data.servers().size() ? -1 : announceServer + 1;
            rebuildWidgets();
        }).bounds(left + 44, y, 124, 20).tooltip(Tooltip.create(Component.translatable("arcadiachat.panel.servers_hint")))
                .build());
        addRenderableWidget(Button.builder(Component.translatable(announceDiscord
                        ? "arcadiachat.panel.discord_on" : "arcadiachat.panel.discord_off"), b -> {
            announceDiscord = !announceDiscord;
            rebuildWidgets();
        }).bounds(left + 172, y, 84, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("arcadiachat.panel.add"), b -> {
            int value = announceMinutes.isEmpty() ? 0 : Integer.parseInt(announceMinutes);
            if (!announceText.isBlank() && value >= 1 && value <= 1440) {
                String servers = announceServer < 0 || announceServer >= data.servers().size()
                        ? ALL : data.servers().get(announceServer).id();
                ClientNetwork.panelAction("announce_add", announceText.trim(), String.valueOf(value), servers,
                        String.valueOf(announceDiscord));
                announceText = "";
            }
        }).bounds(left + 260, y, 80, 20).build());
    }

    private String serversLabel(String servers) {
        if (ALL.equals(servers)) {
            return Component.translatable("arcadiachat.panel.all_servers").getString();
        }
        List<String> names = new ArrayList<>();
        for (String id : servers.split(",")) {
            names.add(data.servers().stream().filter(s -> s.id().equals(id)).map(TabsProtocol.ServerInfo::name)
                    .findFirst().orElse(id));
        }
        return String.join(", ", names);
    }

    // ---------------------------------------------------------------- mots filtres

    private void initFilters(int left) {
        int formTop = height - 30 - FORM_HEIGHT + 24;
        rows = Math.max(1, (formTop - 6 - TOP - ROW_HEIGHT) / ROW_HEIGHT);
        List<TabsProtocol.FilterEntry> visible = pageOf(data.filters());
        for (int i = 0; i < visible.size(); i++) {
            TabsProtocol.FilterEntry entry = visible.get(i);
            int y = TOP + i * ROW_HEIGHT;
            Button word = Button.builder(Component.literal(entry.word()), b -> { }).bounds(left, y, 200, 20).build();
            word.active = false;
            addRenderableWidget(word);
            addRenderableWidget(Button.builder(actionLabel(entry.block()),
                    b -> ClientNetwork.panelAction("filter_set", entry.word(), String.valueOf(!entry.block())))
                    .bounds(left + 204, y, 76, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("arcadiachat.panel.delete"),
                    b -> ClientNetwork.panelAction("filter_delete", entry.word()))
                    .bounds(left + 284, y, 56, 20).build());
        }
        addPager(left, TOP + rows * ROW_HEIGHT, data.filters().size());

        int y = formTop + 12;
        EditBox word = new EditBox(font, left, y, 200, 20, Component.translatable("arcadiachat.panel.word"));
        word.setHint(Component.translatable("arcadiachat.panel.word").withStyle(ChatFormatting.DARK_GRAY));
        word.setMaxLength(64);
        word.setValue(filterWord);
        word.setResponder(value -> filterWord = value);
        word.setTooltip(Tooltip.create(Component.translatable("arcadiachat.panel.word_hint")));
        addRenderableWidget(word);
        addRenderableWidget(Button.builder(actionLabel(filterBlock), b -> {
            filterBlock = !filterBlock;
            rebuildWidgets();
        }).bounds(left + 204, y, 76, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("arcadiachat.panel.add"), b -> {
            if (!filterWord.isBlank()) {
                ClientNetwork.panelAction("filter_set", filterWord.trim(), String.valueOf(filterBlock));
                filterWord = "";
            }
        }).bounds(left + 284, y, 56, 20).build());
    }

    private static Component actionLabel(boolean block) {
        return block ? Component.translatable("arcadiachat.panel.block").withStyle(ChatFormatting.RED)
                : Component.translatable("arcadiachat.panel.mask").withStyle(ChatFormatting.YELLOW);
    }

    // ---------------------------------------------------------------- messages epingles

    // ---------------------------------------------------------------- signalements

    // ---------------------------------------------------------------- outils

    private <T> List<T> pageOf(List<T> all) {
        int pages = Math.max(1, (all.size() + rows - 1) / rows);
        page = Math.min(page, pages - 1);
        return all.subList(page * rows, Math.min(all.size(), (page + 1) * rows));
    }

    private void addPager(int left, int y, int total) {
        int pages = Math.max(1, (total + rows - 1) / rows);
        if (pages <= 1) {
            return;
        }
        Button previous = Button.builder(Component.literal("<"), b -> {
            page--;
            rebuildWidgets();
        }).bounds(left, y, 20, 20).build();
        previous.active = page > 0;
        Button next = Button.builder(Component.literal(">"), b -> {
            page++;
            rebuildWidgets();
        }).bounds(left + WIDTH - 20, y, 20, 20).build();
        next.active = page < pages - 1;
        addRenderableWidget(previous);
        addRenderableWidget(next);
    }

    private void confirm(Component question, Runnable action) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new ConfirmScreen(yes -> {
            if (yes) {
                action.run();
            }
            mc.setScreen(this);
        }, question, Component.empty()));
    }

    private static String stripCodes(String text) {
        return text.replaceAll("[&§][0-9a-fk-orA-FK-OR]", "");
    }

    private static String ellipsis(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
