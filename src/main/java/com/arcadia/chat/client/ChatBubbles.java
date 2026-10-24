package com.arcadia.chat.client;

import com.arcadia.chat.ChatConfig;
import com.arcadia.chat.tabs.TabState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import org.joml.Matrix4f;

/**
 * Petite bulle "..." au-dessus de la tete d'un joueur proche pendant qu'il ecrit dans le chat, comme sur les
 * messageries. Desactivable dans les reglages.
 */
public final class ChatBubbles {

    private static final int BUBBLE = 0xF0FFFFFF;
    private static final int DOT = 0xFF3A3A3A;
    private static final int DOT_IDLE = 0xFFA8A8A8;
    /** Demi-largeur et hauteur de la bulle, en pixels de texte. */
    private static final float HALF_WIDTH = 8;
    private static final float HEIGHT = 8;
    private static final float TAIL = 2;
    /** Duree d'un pas de l'animation des points. */
    private static final long STEP_MS = 300;

    private ChatBubbles() {
    }

    /** Dessine la bulle d'un joueur en train d'ecrire, juste au-dessus de son pseudo. */
    public static void render(RenderNameTagEvent event) {
        if (!ChatConfig.BUBBLES.get() || !(event.getEntity() instanceof Player player)
                || !TabState.isTypingHere(player.getGameProfile().getName())) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        double maxDistance = ChatConfig.BUBBLE_DISTANCE.get();
        if (mc.getEntityRenderDispatcher().distanceToSqr(player) > maxDistance * maxDistance) {
            return;
        }
        Vec3 anchor = player.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0,
                player.getViewYRot(event.getPartialTick()));
        if (anchor == null) {
            return;
        }
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        // Au-dessus du pseudo (place a +0.5).
        pose.translate(anchor.x, anchor.y + 0.8, anchor.z);
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(0.025F, -0.025F, 0.025F);
        Matrix4f matrix = pose.last().pose();
        VertexConsumer out = event.getMultiBufferSource().getBuffer(RenderType.textBackground());
        int light = event.getPackedLight();

        // Fond aux coins arrondis (trois rectangles), puis la pointe vers le pseudo.
        float top = -HEIGHT - TAIL;
        float bottom = -TAIL;
        quad(out, matrix, -HALF_WIDTH, top + 1, HALF_WIDTH, bottom - 1, 0.02F, BUBBLE, light);
        quad(out, matrix, -HALF_WIDTH + 1, top, HALF_WIDTH - 1, top + 1, 0.02F, BUBBLE, light);
        quad(out, matrix, -HALF_WIDTH + 1, bottom - 1, HALF_WIDTH - 1, bottom, 0.02F, BUBBLE, light);
        quad(out, matrix, -2, bottom, 2, bottom + 1, 0.02F, BUBBLE, light);
        quad(out, matrix, -1, bottom + 1, 1, bottom + TAIL, 0.02F, BUBBLE, light);

        // Trois points : celui en cours monte d'un pixel et fonce.
        int active = (int) ((System.currentTimeMillis() / STEP_MS) % 3);
        float dotsY = top + HEIGHT / 2 - 1;
        for (int i = 0; i < 3; i++) {
            float x = -5 + i * 4;
            float y = dotsY - (i == active ? 1 : 0);
            quad(out, matrix, x, y, x + 2, y + 2, 0.01F, i == active ? DOT : DOT_IDLE, light);
        }
        pose.popPose();
    }

    private static void quad(VertexConsumer out, Matrix4f matrix, float x1, float y1, float x2, float y2, float z,
                             int color, int light) {
        out.addVertex(matrix, x1, y1, z).setColor(color).setLight(light);
        out.addVertex(matrix, x1, y2, z).setColor(color).setLight(light);
        out.addVertex(matrix, x2, y2, z).setColor(color).setLight(light);
        out.addVertex(matrix, x2, y1, z).setColor(color).setLight(light);
    }
}
