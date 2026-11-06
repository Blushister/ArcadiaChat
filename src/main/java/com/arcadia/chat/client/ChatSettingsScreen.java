package com.arcadia.chat.client;

import java.util.ArrayList;
import java.util.List;

import com.arcadia.chat.ChatConfig;
import com.arcadia.chat.tabs.TabState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.lwjgl.glfw.GLFW;

/**
 * Reglages d'ArcadiaChat : les sections a gauche, les options a droite. Chaque changement s'applique et s'enregistre
 * aussitot : il n'y a rien a valider.
 */
public final class ChatSettingsScreen extends ThemedScreen {

    private static final int WHITE_KNOB = 0xFFFFFFFF;

    private enum Section { APPEARANCE, TRANSLATION, TABS, MENTIONS, DISPLAY, PRIVACY, BUBBLES, TYPING, FILTERS, DIAGNOSTIC }

    private static final int RAIL = 112;
    private static final int NAV_ROW = 17;
    private static final int ROW = 30;
    private static final int MAX_CONTENT = 380;
    private static Section section = Section.APPEARANCE;

    private record Hit(int x, int y, int w, int h, Runnable action) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        }
    }

    /** Curseur : sa valeur, ses bornes, sa piste, et la zone ou il se saisit. */
    private record Slider(ModConfigSpec.IntValue value, int min, int max, int step, int x, int w, int boxX, int boxY, int boxW, int boxH) {
    }

    private final List<Hit> hits = new ArrayList<>();
    private final List<Slider> sliders = new ArrayList<>();
    private Slider dragging;
    private int scroll;
    private int contentHeight;
    private FlatEditBox filterBox;

    public ChatSettingsScreen(Screen parent) {
        super(Component.translatable("arcadiachat.configuration.title"), parent);
    }

    @Override
    protected void init() {
        font = UiFont.get();
        filterBox = new FlatEditBox(font, 0, 0, 100, 10, Component.translatable("arcadiachat.settings.filter_hint"));
        filterBox.setMaxLength(64);
        filterBox.setVisible(section == Section.FILTERS);
        addRenderableWidget(filterBox);
    }

    private int contentX() {
        return RAIL + Math.max(12, (width - RAIL - MAX_CONTENT) / 2);
    }

    private int contentWidth() {
        return Math.min(MAX_CONTENT, width - RAIL - 24);
    }

    @Override
    protected void drawContent(GuiGraphics graphics, int mouseX, int mouseY) {
        hits.clear();
        sliders.clear();
        int x = contentX();
        int w = contentWidth();
        scroll = Mth.clamp(scroll, 0, Math.max(0, contentHeight - (height - HEADER - 12)));
        graphics.enableScissor(RAIL, HEADER, width, height);
        int y = HEADER + 10 - scroll;
        int start = y;
        y = drawSection(graphics, x, y, w, mouseX, mouseY);
        contentHeight = y - start + 8;
        graphics.disableScissor();

        graphics.fill(0, HEADER, RAIL, height, theme.isLight() ? 0x10000000 : 0x26000000);
        graphics.fill(RAIL - 1, HEADER, RAIL, height, theme.line);
        int navY = HEADER + 6;
        for (Section option : Section.values()) {
            boolean selected = option == section;
            boolean hover = mouseX >= 4 && mouseX < RAIL - 4 && mouseY >= navY && mouseY < navY + NAV_ROW - 1;
            if (selected || hover) {
                Ui.round(graphics, 4, navY, RAIL - 8, NAV_ROW - 1, 3, selected ? theme.selected : theme.hover);
            }
            Ui.icon(graphics, iconOf(option), 9, navY + 4, selected ? theme.accent : theme.muted);
            graphics.drawString(font, Ui.fit(font, sectionName(option).getString(), RAIL - 28), 20, navY + 4,
                    selected || hover ? theme.strong : theme.text, false);
            hits.add(new Hit(4, navY, RAIL - 8, NAV_ROW - 1, () -> {
                section = option;
                scroll = 0;
                filterBox.setVisible(option == Section.FILTERS);
                if (option == Section.FILTERS) {
                    setFocused(filterBox);
                }
            }));
            navY += NAV_ROW;
        }
    }

    private static Ui.Icon iconOf(Section option) {
        return switch (option) {
            case APPEARANCE -> Ui.SMILE;
            case TRANSLATION -> Ui.GLOBE;
            case TABS -> Ui.HASH;
            case MENTIONS -> Ui.AT;
            case DISPLAY -> Ui.INFO;
            case PRIVACY -> Ui.SHIELD;
            case BUBBLES -> Ui.PEOPLE;
            case TYPING -> Ui.DOTS;
            case FILTERS -> Ui.SEARCH;
            case DIAGNOSTIC -> Ui.SERVER;
        };
    }

    private static Component sectionName(Section option) {
        return switch (option) {
            case APPEARANCE -> Component.translatable("arcadiachat.settings.appearance");
            case TRANSLATION -> Component.translatable("arcadiachat.settings.translation");
            case TABS -> Component.translatable("arcadiachat.configuration.tabs");
            case MENTIONS -> Component.translatable("arcadiachat.configuration.mentions");
            case DISPLAY -> Component.translatable("arcadiachat.configuration.display");
            case PRIVACY -> Component.translatable("arcadiachat.configuration.privacy");
            case BUBBLES -> Component.translatable("arcadiachat.configuration.bubbles");
            case TYPING -> Component.translatable("arcadiachat.configuration.typing");
            case FILTERS -> Component.translatable("arcadiachat.configuration.filters");
            case DIAGNOSTIC -> Component.translatable("arcadiachat.settings.diagnostic");
        };
    }

    private int drawSection(GuiGraphics graphics, int x, int y, int w, int mouseX, int mouseY) {
        switch (section) {
            case APPEARANCE -> {
                y = drawThemes(graphics, x, y, w, mouseX, mouseY);
                if (RoomsUi.installed()) {
                    y = toggle(graphics, x, y, w, "roomsView", ChatConfig.ROOMS_VIEW, mouseX, mouseY);
                }
                y = toggle(graphics, x, y, w, "heads", ChatConfig.HEADS, mouseX, mouseY);
            }
            case TRANSLATION -> y = drawTranslation(graphics, x, y, w, mouseX, mouseY);
            case TABS -> {
                y = toggle(graphics, x, y, w, "rememberTab", ChatConfig.REMEMBER_TAB, mouseX, mouseY);
                y = toggle(graphics, x, y, w, "systemInAll", ChatConfig.SYSTEM_IN_ALL, mouseX, mouseY);
                y = slider(graphics, x, y, w, "history", ChatConfig.HISTORY, 100, 5000, 100, "");
                y = drawTabList(graphics, x, y, w, mouseX, mouseY);
            }
            case MENTIONS -> {
                y = toggle(graphics, x, y, w, "enabled", ChatConfig.MENTIONS, mouseX, mouseY);
                y = toggle(graphics, x, y, w, "plainName", ChatConfig.MENTION_PLAIN_NAME, mouseX, mouseY);
                y = toggle(graphics, x, y, w, "sound", ChatConfig.MENTION_SOUND, mouseX, mouseY);
            }
            case DISPLAY -> {
                y = toggle(graphics, x, y, w, "timestamps", ChatConfig.TIMESTAMPS, mouseX, mouseY);
                y = toggle(graphics, x, y, w, "timestampHover", ChatConfig.TIMESTAMP_HOVER, mouseX, mouseY);
                y = toggle(graphics, x, y, w, "groupRepeats", ChatConfig.GROUP_REPEATS, mouseX, mouseY);
                if (RoomsUi.installed()) {
                    y = toggle(graphics, x, y, w, "voiceHud", ChatConfig.VOICE_HUD, mouseX, mouseY);
                }
                y = toggle(graphics, x, y, w, "toasts", ChatConfig.TOASTS, mouseX, mouseY);
                y = toggle(graphics, x, y, w, "absenceSummary", ChatConfig.ABSENCE_SUMMARY, mouseX, mouseY);
            }
            case PRIVACY -> {
                y = toggle(graphics, x, y, w, "unsignedChat", ChatConfig.UNSIGNED_CHAT, mouseX, mouseY);
                y = toggle(graphics, x, y, w, "hideUnsecureWarning", ChatConfig.HIDE_UNSECURE_WARNING, mouseX, mouseY);
            }
            case BUBBLES -> {
                y = toggle(graphics, x, y, w, "visible", ChatConfig.BUBBLES, mouseX, mouseY);
                y = slider(graphics, x, y, w, "distance", ChatConfig.BUBBLE_DISTANCE, 4, 64, 4, "");
            }
            case TYPING -> {
                y = toggle(graphics, x, y, w, "show", ChatConfig.SHOW_TYPING, mouseX, mouseY);
                y = toggle(graphics, x, y, w, "send", ChatConfig.SEND_TYPING, mouseX, mouseY);
            }
            case FILTERS -> y = drawFilters(graphics, x, y, w, mouseX, mouseY);
            case DIAGNOSTIC -> y = drawDiagnostic(graphics, x, y, w);
        }
        return y;
    }

    // ---------------------------------------------------------------- diagnostic

    /** Ce qui marche ou pas, en un coup d'oeil : a envoyer au staff quand quelque chose cloche. */
    private int drawDiagnostic(GuiGraphics graphics, int x, int y, int w) {
        boolean linked = TabState.isEnabled();
        y = diagnosticRow(graphics, x, y, w, "server", linked ? TabState.serverName() : text("no"), linked ? theme.online : theme.danger);
        y = diagnosticRow(graphics, x, y, w, "addon", text(RoomsUi.installed() ? "installed" : "missing"), RoomsUi.installed() ? theme.online : theme.muted);
        y = diagnosticRow(graphics, x, y, w, "translation", state(ServiceState.translator()), color(ServiceState.translator()));
        y = diagnosticRow(graphics, x, y, w, "voice", text(ChatVoice.available() ? "up" : "off"), ChatVoice.available() ? theme.online : theme.muted);
        y = diagnosticRow(graphics, x, y, w, "relay", state(ServiceState.relay()), color(ServiceState.relay()));
        String version = net.neoforged.fml.ModList.get().getModContainerById("arcadiachat")
                .map(container -> container.getModInfo().getVersion().toString()).orElse("?");
        return diagnosticRow(graphics, x, y, w, "version", version, theme.text);
    }

    private static String text(String key) {
        return Component.translatable("arcadiachat.diag." + key).getString();
    }

    private static String state(int value) {
        return text(switch (value) {
            case ServiceState.UP -> "up";
            case ServiceState.DOWN -> "down";
            case ServiceState.OFF -> "off";
            default -> "unknown";
        });
    }

    private int color(int value) {
        return value == ServiceState.UP ? theme.online : value == ServiceState.DOWN ? theme.danger : theme.muted;
    }

    private int diagnosticRow(GuiGraphics graphics, int x, int y, int w, String key, String value, int color) {
        graphics.drawString(font, Component.translatable("arcadiachat.diag." + key), x, y + 4, theme.strong, false);
        String shown = Ui.fit(font, value, w / 2);
        graphics.drawString(font, shown, x + w - font.width(shown), y + 4, color, false);
        graphics.fill(x, y + 17, x + w, y + 18, theme.line);
        return y + 22;
    }

    // ---------------------------------------------------------------- lignes

    /** Libelle et explication d'une option, a gauche ; la commande reste sur la droite. */
    private void describe(GuiGraphics graphics, int x, int y, int w, String key) {
        graphics.drawString(font, Ui.fit(font, Component.translatable("arcadiachat.configuration." + key).getString(), w), x, y + 3, theme.strong, false);
        graphics.drawString(font, Ui.fit(font, Component.translatable("arcadiachat.settings." + key).getString(), w), x, y + 14, theme.muted, false);
    }

    private int toggle(GuiGraphics graphics, int x, int y, int w, String key, ModConfigSpec.BooleanValue value, int mouseX, int mouseY) {
        describe(graphics, x, y, w - 34, key);
        boolean on = value.get();
        int bx = x + w - 24;
        int by = y + 6;
        Ui.round(graphics, bx, by, 22, 12, 6, on ? theme.accent : theme.outline);
        Ui.round(graphics, on ? bx + 12 : bx + 2, by + 2, 8, 8, 4, WHITE_KNOB);
        if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + ROW - 2) {
            graphics.fill(x - 4, y + ROW - 3, x + w + 2, y + ROW - 2, theme.line);
        }
        hits.add(new Hit(x - 4, y, w + 6, ROW - 2, () -> {
            value.set(!value.get());
            ChatConfig.SPEC.save();
        }));
        return y + ROW;
    }

    private int slider(GuiGraphics graphics, int x, int y, int w, String key, ModConfigSpec.IntValue value, int min, int max, int step, String unit) {
        int trackWidth = Math.min(110, w / 3);
        describe(graphics, x, y, w - trackWidth - 44, key);
        String shown = value.get() + unit;
        int trackX = x + w - trackWidth - 2;
        graphics.drawString(font, shown, trackX - font.width(shown) - 6, y + 7, theme.text, false);
        int trackY = y + 11;
        Ui.pill(graphics, trackX, trackY, trackWidth, 3, theme.selected);
        int filled = trackWidth * (Mth.clamp(value.get(), min, max) - min) / (max - min);
        Ui.pill(graphics, trackX, trackY, Math.max(3, filled), 3, theme.accent);
        Ui.round(graphics, trackX + filled - 4, trackY - 3, 9, 9, 5, WHITE_KNOB);
        sliders.add(new Slider(value, min, max, step, trackX, trackWidth, trackX - 4, y + 4, trackWidth + 8, 16));
        return y + ROW;
    }

    private void applySlider(Slider slider, double mouseX) {
        double ratio = Mth.clamp((mouseX - slider.x()) / slider.w(), 0, 1);
        int raw = slider.min() + (int) Math.round(ratio * (slider.max() - slider.min()));
        int snapped = slider.min() + Math.round((raw - slider.min()) / (float) slider.step()) * slider.step();
        slider.value().set(Mth.clamp(snapped, slider.min(), slider.max()));
    }

    private int drawThemes(GuiGraphics graphics, int x, int y, int w, int mouseX, int mouseY) {
        graphics.drawString(font, Component.translatable("arcadiachat.rooms.theme"), x, y + 2, theme.strong, false);
        y += 14;
        ChatTheme[] options = ChatTheme.values();
        int gap = 8;
        int cardWidth = (w - gap * (options.length - 1)) / options.length;
        int cardHeight = 58;
        for (int i = 0; i < options.length; i++) {
            ChatTheme option = options[i];
            int cx = x + i * (cardWidth + gap);
            boolean selected = option == theme;
            boolean hover = mouseX >= cx && mouseX < cx + cardWidth && mouseY >= y && mouseY < y + cardHeight;
            Ui.framed(graphics, cx, y, cardWidth, cardHeight, option.ground, selected ? theme.accent : hover ? theme.outline : theme.line);
            // Apercu : la colonne des salons, un salon choisi, quelques lignes de message.
            int railW = cardWidth / 3;
            graphics.fill(cx + 1, y + 1, cx + railW, y + cardHeight - 15, option.rail);
            Ui.pill(graphics, cx + 3, y + 5, railW - 5, 6, option.selected);
            graphics.fill(cx + 3, y + 5, cx + 5, y + 11, option.accent);
            graphics.fill(cx + 4, y + 15, cx + railW - 5, y + 17, option.muted);
            graphics.fill(cx + 4, y + 21, cx + railW - 8, y + 23, option.muted);
            int lineX = cx + railW + 4;
            int lineW = cardWidth - railW - 8;
            graphics.fill(lineX, y + 6, lineX + lineW * 2 / 5, y + 8, option.secondary);
            graphics.fill(lineX, y + 11, lineX + lineW, y + 13, option.text);
            graphics.fill(lineX, y + 16, lineX + lineW * 3 / 4, y + 18, option.text);
            graphics.fill(lineX, y + 24, lineX + lineW * 2 / 5, y + 26, option.strong);
            graphics.fill(lineX, y + 29, lineX + lineW * 4 / 5, y + 31, option.text);
            graphics.fill(cx + 1, y + cardHeight - 15, cx + cardWidth - 1, y + cardHeight - 1, theme.field);
            Ui.center(graphics, font, Component.translatable("arcadiachat.rooms.theme." + option.id), cx + cardWidth / 2, y + cardHeight - 12,
                    selected ? theme.accent : theme.text);
            hits.add(new Hit(cx, y, cardWidth, cardHeight, () -> ChatTheme.set(option)));
        }
        return y + cardHeight + 12;
    }

    private int drawTranslation(GuiGraphics graphics, int x, int y, int w, int mouseX, int mouseY) {
        graphics.drawString(font, Component.translatable("arcadiachat.settings.translation"), x, y + 2, theme.strong, false);
        graphics.drawString(font, Ui.fit(font, Component.translatable("arcadiachat.settings.translation.desc").getString(), w), x, y + 13, theme.muted, false);
        y += 28;
        for (String choice : List.of("auto", "fr", "en", "off")) {
            boolean selected = choice.equals(ChatConfig.TRANSLATION.get());
            boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 17;
            if (selected || hover) {
                Ui.pill(graphics, x, y, w, 17, selected ? theme.selected : theme.hover);
            }
            Ui.framed(graphics, x + 5, y + 4, 9, 9, selected ? theme.accent : theme.field, theme.outline);
            graphics.drawString(font, Component.translatable("arcadiachat.translation." + choice), x + 19, y + 5, selected ? theme.strong : theme.text, false);
            hits.add(new Hit(x, y, w, 17, () -> {
                ChatConfig.TRANSLATION.set(choice);
                ChatRooms.onTranslationChanged();
                ChatConfig.SPEC.save();
            }));
            y += 19;
        }
        return y + 6;
    }

    private int drawTabList(GuiGraphics graphics, int x, int y, int w, int mouseX, int mouseY) {
        y += 4;
        graphics.drawString(font, Component.translatable("arcadiachat.configuration.order"), x, y, theme.strong, false);
        graphics.drawString(font, Ui.fit(font, Component.translatable("arcadiachat.settings.order").getString(), w), x, y + 11, theme.muted, false);
        y += 25;
        List<String> order = currentOrder();
        for (int i = 0; i < order.size(); i++) {
            String id = order.get(i);
            int index = i;
            boolean hidden = ChatConfig.HIDDEN_TABS.get().contains(id);
            Ui.pill(graphics, x, y, w, 17, theme.rail);
            graphics.drawString(font, Component.translatable("arcadiachat.tab." + id), x + 6, y + 5, hidden ? theme.muted : theme.text, false);
            int bx = x + w - 17;
            bx = smallButton(graphics, bx, y + 2, Ui.DOWN, i < order.size() - 1, mouseX, mouseY, () -> moveTab(order, index, 1));
            bx = smallButton(graphics, bx, y + 2, Ui.UP, i > 0, mouseX, mouseY, () -> moveTab(order, index, -1));
            if (!"all".equals(id)) {
                Component label = Component.translatable(hidden ? "arcadiachat.settings.tab_hidden" : "arcadiachat.settings.tab_visible");
                int labelWidth = font.width(label) + 8;
                bx -= labelWidth + 2;
                boolean hover = mouseX >= bx && mouseX < bx + labelWidth && mouseY >= y + 2 && mouseY < y + 15;
                Ui.pill(graphics, bx, y + 2, labelWidth, 13, hover ? theme.hover : theme.field);
                graphics.drawString(font, label, bx + 4, y + 5, hidden ? theme.muted : theme.accent, false);
                hits.add(new Hit(bx, y + 2, labelWidth, 13, () -> toggleHidden(id)));
            }
            y += 19;
        }
        return y + 6;
    }

    private int smallButton(GuiGraphics graphics, int x, int y, Ui.Icon icon, boolean enabled, int mouseX, int mouseY, Runnable action) {
        boolean hover = enabled && mouseX >= x && mouseX < x + 13 && mouseY >= y && mouseY < y + 13;
        if (hover) {
            Ui.pill(graphics, x, y, 13, 13, theme.hover);
        }
        Ui.icon(graphics, icon, x + 3, y + 3, !enabled ? theme.line : hover ? theme.strong : theme.muted);
        if (enabled) {
            hits.add(new Hit(x, y, 13, 13, action));
        }
        return x - 14;
    }

    private static List<String> currentOrder() {
        List<String> order = new ArrayList<>(ChatConfig.TAB_ORDER.get());
        for (String id : ChatConfig.DEFAULT_ORDER) {
            if (!order.contains(id)) {
                order.add(id);
            }
        }
        return order;
    }

    private static void moveTab(List<String> order, int index, int step) {
        List<String> next = new ArrayList<>(order);
        String moved = next.remove(index);
        next.add(index + step, moved);
        ChatConfig.TAB_ORDER.set(next);
        ChatConfig.SPEC.save();
    }

    private static void toggleHidden(String id) {
        List<String> hidden = new ArrayList<>(ChatConfig.HIDDEN_TABS.get());
        if (!hidden.remove(id)) {
            hidden.add(id);
        }
        ChatConfig.HIDDEN_TABS.set(hidden);
        ChatConfig.SPEC.save();
    }

    private int drawFilters(GuiGraphics graphics, int x, int y, int w, int mouseX, int mouseY) {
        graphics.drawString(font, Component.translatable("arcadiachat.configuration.hide"), x, y + 2, theme.strong, false);
        graphics.drawString(font, Ui.fit(font, Component.translatable("arcadiachat.settings.hide").getString(), w), x, y + 13, theme.muted, false);
        y += 26;
        Ui.framed(graphics, x, y, w, 18, theme.field, getFocused() == filterBox ? theme.accent : theme.line);
        filterBox.setPosition(x + 6, y + 5);
        filterBox.setWidth(w - 12);
        y += 24;
        List<? extends String> filters = ChatConfig.FILTERS.get();
        if (filters.isEmpty()) {
            graphics.drawString(font, Component.translatable("arcadiachat.settings.no_filters"), x + 2, y + 2, theme.muted, false);
            return y + 14;
        }
        for (String filter : filters) {
            Ui.pill(graphics, x, y, w, 17, theme.rail);
            graphics.drawString(font, Ui.fit(font, filter, w - 28), x + 6, y + 5, theme.text, false);
            smallButton(graphics, x + w - 16, y + 2, Ui.CLOSE, true, mouseX, mouseY, () -> removeFilter(filter));
            y += 19;
        }
        return y;
    }

    private void addFilter() {
        String text = filterBox.getValue().trim();
        if (text.isEmpty()) {
            return;
        }
        List<String> filters = new ArrayList<>(ChatConfig.FILTERS.get());
        if (!filters.contains(text)) {
            filters.add(text);
            ChatConfig.FILTERS.set(filters);
            ChatConfig.SPEC.save();
        }
        filterBox.setValue("");
    }

    private static void removeFilter(String filter) {
        List<String> filters = new ArrayList<>(ChatConfig.FILTERS.get());
        filters.remove(filter);
        ChatConfig.FILTERS.set(filters);
        ChatConfig.SPEC.save();
    }

    // ---------------------------------------------------------------- entrees

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseY >= HEADER) {
            for (Slider slider : sliders) {
                if (mouseX >= slider.boxX() && mouseX < slider.boxX() + slider.boxW() && mouseY >= slider.boxY() && mouseY < slider.boxY() + slider.boxH()) {
                    dragging = slider;
                    applySlider(slider, mouseX);
                    return true;
                }
            }
            for (int i = hits.size() - 1; i >= 0; i--) {
                if (hits.get(i).contains(mouseX, mouseY)) {
                    hits.get(i).action().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging != null) {
            applySlider(dragging, mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging != null) {
            dragging = null;
            ChatConfig.SPEC.save();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll -= (int) (scrollY * 20);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && getFocused() == filterBox) {
            addFilter();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }
}
