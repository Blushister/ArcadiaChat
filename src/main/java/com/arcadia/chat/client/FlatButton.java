package com.arcadia.chat.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Bouton aux couleurs du theme, pour tous les ecrans du mod. */
public final class FlatButton extends Button {

    public enum Look { NORMAL, PRIMARY, PLAIN, TAB }

    private final Look look;
    private final boolean selected;

    private FlatButton(int x, int y, int width, int height, Component message, OnPress onPress, Look look, boolean selected) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.look = look;
        this.selected = selected;
    }

    public static Builder of(Component message, OnPress onPress) {
        return new Builder(message, onPress);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ChatTheme theme = ChatTheme.current();
        Font font = UiFont.get();
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        boolean hover = active && isHoveredOrFocused();
        int textColor;
        switch (look) {
            case PRIMARY -> {
                Ui.round(graphics, x, y, w, h, 3, active ? theme.accent : theme.selected);
                textColor = active ? theme.onAccent : theme.muted;
                if (hover) {
                    graphics.fill(x + 1, y + h - 1, x + w - 1, y + h, theme.strong);
                }
            }
            case PLAIN -> textColor = theme.text;
            case TAB -> {
                if (selected || hover) {
                    Ui.round(graphics, x, y, w, h, 3, selected ? theme.selected : theme.hover);
                }
                if (selected) {
                    graphics.fill(x + 3, y + h - 2, x + w - 3, y + h - 1, theme.accent);
                }
                textColor = !active ? theme.line : selected || hover ? theme.strong : theme.muted;
            }
            default -> {
                Ui.round(graphics, x, y, w, h, 3, hover ? theme.selected : theme.hover);
                textColor = active ? hover ? theme.strong : theme.text : theme.muted;
            }
        }
        List<FormattedCharSequence> lines = font.split(getMessage(), Math.max(1, w - 2));
        if (lines.isEmpty()) {
            return;
        }
        FormattedCharSequence line = theme.adapt(lines.get(0));
        int textY = y + (h - 8) / 2;
        int textX = look == Look.PLAIN ? x + 2 : x + (w - font.width(line)) / 2;
        graphics.drawString(font, line, textX, textY, textColor, false);
    }

    public static final class Builder {
        private final Component message;
        private final OnPress onPress;
        private int x;
        private int y;
        private int width = 150;
        private int height = 20;
        private Tooltip tooltip;
        private Look look = Look.NORMAL;
        private boolean selected;

        private Builder(Component message, OnPress onPress) {
            this.message = message;
            this.onPress = onPress;
        }

        public Builder bounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder tooltip(Tooltip tooltip) {
            this.tooltip = tooltip;
            return this;
        }

        /** Simple texte, sans cadre. */
        public Builder plain() {
            look = Look.PLAIN;
            return this;
        }

        /** L'action principale de l'ecran. */
        public Builder primary() {
            look = Look.PRIMARY;
            return this;
        }

        public Builder tab(boolean isSelected) {
            look = Look.TAB;
            selected = isSelected;
            return this;
        }

        public FlatButton build() {
            FlatButton button = new FlatButton(x, y, width, height, message, onPress, look, selected);
            button.setTooltip(tooltip);
            return button;
        }
    }
}
