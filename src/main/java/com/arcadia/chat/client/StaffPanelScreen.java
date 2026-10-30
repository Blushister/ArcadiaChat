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

    private enum Section { RULES, ANNOUNCEMENTS, FILTERS, PINS, REPORTS }

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

    // Formulaires (gardes d'une reconstruction a l'autre).
    private String announceText = "";
    private String announceMinutes = "15";
    private int announceServer = -1;
    private boolean announceDiscord;
    private String filterWord = "";
    private boolean filterBlock;
    private String pinText = "";
    private int pinServer = -1;

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

    private boolean allowed(Section s) {
        int bit = switch (s) {
            case RULES -> TabsProtocol.PANEL_RULES;
            case ANNOUNCEMENTS -> TabsProtocol.PANEL_ANNOUNCEMENTS;
            case FILTERS -> TabsProtocol.PANEL_FILTERS;
            case PINS -> TabsProtocol.PANEL_PINS;
            case REPORTS -> TabsProtocol.PANEL_REPORTS;
        };
        return data != null && (data.access() & bit) != 0;
    }

    private Section firstAllowed() {
        for (Section s : Section.values()) {
            if (allowed(s)) {
                return s;
            }
        }
        return null;
    }

    @Override
    protected void init() {
        if (data == null) {
            ClientNetwork.requestPanel();
        }
        int left = width / 2 - WIDTH / 2;
        // Onglets des sections (un peu plus larges que le contenu si l'ecran le permet).
        Section[] sections = Section.values();
        int tabsWidth = Math.min(width - 8, 420);
        int tabWidth = (tabsWidth - (sections.length - 1) * 4) / sections.length;
        int tabsLeft = width / 2 - tabsWidth / 2;
        for (int i = 0; i < sections.length; i++) {
            Section s = sections[i];
            Button tab = Button.builder(sectionLabel(s), b -> {
                section = s;
                page = 0;
                rebuildWidgets();
            }).bounds(tabsLeft + i * (tabWidth + 4), 28, tabWidth, 20).build();
            tab.active = allowed(s) && s != section;
            addRenderableWidget(tab);
        }
        if (section != null) {
            switch (section) {
                case RULES -> initRules(left);
                case ANNOUNCEMENTS -> initAnnouncements(left);
                case FILTERS -> initFilters(left);
                case PINS -> initPins(left);
                case REPORTS -> initReports(left);
            }
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(width / 2 - 100, height - 26, 200, 20).build());
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

    private void initPins(int left) {
        int formTop = height - 30 - FORM_HEIGHT;
        rows = Math.max(1, (formTop - 6 - TOP - ROW_HEIGHT) / ROW_HEIGHT);
        List<TabsProtocol.PinEntry> visible = pageOf(data.pins());
        for (int i = 0; i < visible.size(); i++) {
            TabsProtocol.PinEntry entry = visible.get(i);
            int y = TOP + i * ROW_HEIGHT;
            Button scope = Button.builder(Component.literal(ellipsis(scopeName(entry.scope()), 16))
                    .withStyle(ALL.equals(entry.scope()) ? ChatFormatting.AQUA : ChatFormatting.WHITE), b -> { })
                    .bounds(left, y, 110, 20).build();
            scope.active = false;
            addRenderableWidget(scope);
            Button text = Button.builder(Component.literal(ellipsis(stripCodes(entry.message()), 28)), b -> { })
                    .bounds(left + 114, y, 166, 20).tooltip(Tooltip.create(Component.literal(stripCodes(entry.message()))))
                    .build();
            text.active = false;
            addRenderableWidget(text);
            addRenderableWidget(Button.builder(Component.translatable("arcadiachat.panel.unpin"),
                    b -> ClientNetwork.panelAction("pin_delete", entry.scope())).bounds(left + 284, y, 56, 20).build());
        }
        addPager(left, TOP + rows * ROW_HEIGHT, data.pins().size());

        int y = formTop + 12;
        EditBox message = new EditBox(font, left, y, WIDTH, 20, Component.translatable("arcadiachat.panel.pin_text"));
        message.setHint(Component.translatable("arcadiachat.panel.pin_text").withStyle(ChatFormatting.DARK_GRAY));
        message.setMaxLength(256);
        message.setValue(pinText);
        message.setResponder(value -> pinText = value);
        addRenderableWidget(message);
        y += 24;
        String serverText = pinServer < 0 || pinServer >= data.servers().size()
                ? Component.translatable("arcadiachat.panel.all_servers").getString()
                : data.servers().get(pinServer).name();
        addRenderableWidget(Button.builder(Component.literal(ellipsis(serverText, 24)), b -> {
            pinServer = pinServer + 1 >= data.servers().size() ? -1 : pinServer + 1;
            rebuildWidgets();
        }).bounds(left, y, 168, 20).tooltip(Tooltip.create(Component.translatable("arcadiachat.panel.servers_hint"))).build());
        addRenderableWidget(Button.builder(Component.translatable("arcadiachat.panel.pin"), b -> {
            if (!pinText.isBlank()) {
                String scope = pinServer < 0 || pinServer >= data.servers().size() ? ALL : data.servers().get(pinServer).id();
                ClientNetwork.panelAction("pin_set", scope, pinText.trim());
                pinText = "";
            }
        }).bounds(left + 172, y, 168, 20).build());
    }

    private String scopeName(String scope) {
        return ALL.equals(scope) ? Component.translatable("arcadiachat.panel.all_servers").getString() : serversLabel(scope);
    }

    // ---------------------------------------------------------------- signalements

    private void initReports(int left) {
        rows = Math.max(1, (height - 34 - TOP - ROW_HEIGHT) / ROW_HEIGHT);
        List<TabsProtocol.ReportEntry> visible = pageOf(data.reports());
        for (int i = 0; i < visible.size(); i++) {
            TabsProtocol.ReportEntry entry = visible.get(i);
            int y = TOP + i * ROW_HEIGHT;
            String id = String.valueOf(entry.id());
            MutableComponent label = Component.literal(entry.target()).withStyle(ChatFormatting.GOLD);
            if (entry.count() > 1) {
                label.append(Component.literal(" x" + entry.count()).withStyle(ChatFormatting.RED));
            }
            label.append(Component.literal(" " + ellipsis(entry.reason(), 18)).withStyle(ChatFormatting.GRAY));
            Button text = Button.builder(label, b -> { }).bounds(left, y, 162, 20)
                    .tooltip(Tooltip.create(Component.translatable("arcadiachat.panel.report_info", entry.reporter(),
                            scopeName(entry.serverId()), ago(entry.createdAt()), entry.reason())))
                    .build();
            text.active = false;
            addRenderableWidget(text);
            addRenderableWidget(Button.builder(Component.translatable("arcadiachat.panel.report_close"),
                    b -> ClientNetwork.panelAction("report", id, "ignore"))
                    .bounds(left + 166, y, 48, 20)
                    .tooltip(Tooltip.create(Component.translatable("arcadiachat.panel.report_close_hint"))).build());
            addRenderableWidget(Button.builder(Component.translatable("arcadiachat.panel.report_mute")
                            .withStyle(ChatFormatting.YELLOW),
                    b -> confirm(Component.translatable("arcadiachat.panel.report_mute_confirm", entry.target()),
                            () -> ClientNetwork.panelAction("report", id, "mute")))
                    .bounds(left + 218, y, 40, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("arcadiachat.panel.report_ban")
                            .withStyle(ChatFormatting.RED),
                    b -> confirm(Component.translatable("arcadiachat.panel.report_ban_confirm", entry.target()),
                            () -> ClientNetwork.panelAction("report", id, "ban")))
                    .bounds(left + 262, y, 36, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("arcadiachat.panel.report_history"), b -> {
                var connection = Minecraft.getInstance().getConnection();
                if (connection != null) {
                    connection.sendCommand("arcadia_history " + entry.target());
                }
            }).bounds(left + 302, y, 38, 20)
                    .tooltip(Tooltip.create(Component.translatable("arcadiachat.panel.report_history_hint"))).build());
        }
        addPager(left, TOP + rows * ROW_HEIGHT, data.reports().size());
    }

    private static Component ago(long time) {
        long minutes = Math.max(0, (System.currentTimeMillis() - time) / 60_000);
        if (minutes < 60) {
            return Component.translatable("arcadiachat.panel.ago_minutes", minutes);
        }
        long hours = minutes / 60;
        return hours < 48 ? Component.translatable("arcadiachat.panel.ago_hours", hours)
                : Component.translatable("arcadiachat.panel.ago_days", hours / 24);
    }

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
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);
        int left = width / 2 - WIDTH / 2;
        if (data == null) {
            graphics.drawCenteredString(font, Component.translatable("arcadiachat.panel.loading"), width / 2, TOP + 6, 0xAAAAAA);
            return;
        }
        if (section == null) {
            graphics.drawCenteredString(font, Component.translatable("arcadiachat.panel.no_access"), width / 2, TOP + 6, 0xAAAAAA);
            return;
        }
        switch (section) {
            case RULES -> graphics.drawString(font, Component.translatable("arcadiachat.panel.rules_hint")
                    .withStyle(ChatFormatting.GRAY), left, height - 44, 0xFFFFFF);
            case ANNOUNCEMENTS -> {
                if (data.announcements().isEmpty()) {
                    graphics.drawCenteredString(font, Component.translatable("arcadiachat.panel.none"), width / 2, TOP + 6, 0xAAAAAA);
                }
                graphics.drawString(font, Component.translatable("arcadiachat.panel.new_announce")
                        .withStyle(ChatFormatting.GOLD), left, height - 30 - FORM_HEIGHT, 0xFFFFFF);
            }
            case PINS -> {
                if (data.pins().isEmpty()) {
                    graphics.drawCenteredString(font, Component.translatable("arcadiachat.panel.none"), width / 2, TOP + 6, 0xAAAAAA);
                }
                graphics.drawString(font, Component.translatable("arcadiachat.panel.new_pin")
                        .withStyle(ChatFormatting.GOLD), left, height - 30 - FORM_HEIGHT, 0xFFFFFF);
            }
            case REPORTS -> {
                if (data.reports().isEmpty()) {
                    graphics.drawCenteredString(font, Component.translatable("arcadiachat.panel.no_reports"), width / 2,
                            TOP + 6, 0xAAAAAA);
                }
            }
            case FILTERS -> {
                if (data.filters().isEmpty()) {
                    graphics.drawCenteredString(font, Component.translatable("arcadiachat.panel.none"), width / 2, TOP + 6, 0xAAAAAA);
                }
                graphics.drawString(font, Component.translatable("arcadiachat.panel.new_word")
                        .withStyle(ChatFormatting.GOLD), left, height - 30 - FORM_HEIGHT + 24, 0xFFFFFF);
            }
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
