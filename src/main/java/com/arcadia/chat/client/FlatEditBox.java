package com.arcadia.chat.client;

import com.arcadia.chat.mixin.EditBoxAccessor;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * Zone de saisie aux couleurs du theme. Celle de Minecraft dessine son texte avec une ombre, illisible sur fond clair, et
 * cache son invitation des qu'elle a le clavier : ici, le texte est plat et l'invitation reste tant que rien n'est ecrit.
 */
public final class FlatEditBox extends EditBox {

    private Component placeholder;
    private final boolean framed;

    /** Sans cadre : c'est l'ecran qui le dessine autour. */
    public FlatEditBox(Font font, int x, int y, int width, int height, Component message) {
        this(font, x, y, width, height, message, false);
    }

    private FlatEditBox(Font font, int x, int y, int width, int height, Component message, boolean framed) {
        super(UiFont.get(), x, y, width, height, message);
        this.framed = framed;
        this.placeholder = message;
        setBordered(framed);
    }

    /** Avec son propre cadre. */
    public static FlatEditBox framed(Font font, int x, int y, int width, int height, Component message) {
        return new FlatEditBox(font, x, y, width, height, message, true);
    }

    public void placeholder(Component text) {
        placeholder = text;
    }

    @Override
    public void setHint(Component hint) {
        placeholder = hint;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!isVisible()) {
            return;
        }
        ChatTheme theme = ChatTheme.current();
        Font font = UiFont.get();
        String value = getValue();
        int x = getX();
        int y = getY();
        if (framed) {
            Ui.framed(graphics, x, y, getWidth(), getHeight(), theme.field, isFocused() ? theme.accent : theme.line);
            x += 4;
            y += (getHeight() - 8) / 2;
        }
        if (value.isEmpty()) {
            graphics.drawString(font, Ui.fit(font, placeholder.getString(), getInnerWidth()), x, y, theme.muted, false);
        }
        EditBoxAccessor state = (EditBoxAccessor) (Object) this;
        int start = Math.min(state.arcadiachat$displayPos(), value.length());
        String shown = font.plainSubstrByWidth(value.substring(start), getInnerWidth());
        int cursor = getCursorPosition() - start;
        int highlight = Math.min(state.arcadiachat$highlightPos() - start, shown.length());
        if (highlight != cursor && !shown.isEmpty()) {
            int from = Math.max(0, Math.min(cursor, highlight));
            int to = Math.min(shown.length(), Math.max(cursor, highlight));
            if (to > from) {
                graphics.fill(x + font.width(shown.substring(0, from)), y - 1, x + font.width(shown.substring(0, to)), y + 9,
                        (theme.accent & 0xFFFFFF) | 0x66000000);
            }
        }
        graphics.drawString(font, shown, x, y, theme.text, false);
        if (isFocused() && (Util.getMillis() / 500) % 2 == 0 && cursor >= 0 && cursor <= shown.length()) {
            int cursorX = x + font.width(shown.substring(0, cursor));
            graphics.fill(cursorX, y - 1, cursorX + 1, y + 9, theme.text);
        }
    }
}
