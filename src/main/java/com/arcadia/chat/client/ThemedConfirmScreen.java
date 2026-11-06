package com.arcadia.chat.client;

import java.util.List;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Confirmation aux couleurs du theme : une question, deux boutons. Echap vaut non. */
public final class ThemedConfirmScreen extends Screen {

    private static final int WIDTH = 260;

    private final BooleanConsumer callback;
    private final Component question;
    private final Component details;
    private List<FormattedCharSequence> questionLines = List.of();
    private List<FormattedCharSequence> detailLines = List.of();

    public ThemedConfirmScreen(BooleanConsumer callback, Component question, Component details) {
        super(question);
        this.callback = callback;
        this.question = question;
        this.details = details;
    }

    private int boxHeight() {
        return 44 + (questionLines.size() + detailLines.size()) * 10;
    }

    private int boxTop() {
        return (height - boxHeight()) / 2;
    }

    @Override
    protected void init() {
        font = UiFont.get();
        questionLines = font.split(question, WIDTH - 24);
        detailLines = font.split(details, WIDTH - 24);
        int left = (width - WIDTH) / 2;
        int y = boxTop() + boxHeight() - 26;
        int buttonWidth = (WIDTH - 30) / 2;
        addRenderableWidget(FlatButton.of(Component.translatable("gui.no"), b -> callback.accept(false))
                .bounds(left + 12, y, buttonWidth, 18).build());
        addRenderableWidget(FlatButton.of(Component.translatable("gui.yes"), b -> callback.accept(true))
                .bounds(left + 18 + buttonWidth, y, buttonWidth, 18).primary().build());
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ChatTheme theme = ChatTheme.current();
        graphics.fill(0, 0, width, height, 0xB0000000);
        int left = (width - WIDTH) / 2;
        int top = boxTop();
        Ui.framed(graphics, left, top, WIDTH, boxHeight(), theme.ground, theme.outline);
        int y = top + 10;
        for (FormattedCharSequence line : questionLines) {
            graphics.drawString(font, theme.adapt(line), left + 12, y, theme.strong, false);
            y += 10;
        }
        y += 2;
        for (FormattedCharSequence line : detailLines) {
            graphics.drawString(font, theme.adapt(line), left + 12, y, theme.muted, false);
            y += 10;
        }
    }

    @Override
    public void onClose() {
        callback.accept(false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
