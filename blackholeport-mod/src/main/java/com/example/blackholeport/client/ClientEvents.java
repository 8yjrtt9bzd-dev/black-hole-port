package com.example.blackholeport.client;

import com.example.blackholeport.BlackHolePortMod;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.Iterator;

/** Draws the black sphere and the debris, and blacks out the screen while the camera is inside a hole. */
@Mod.EventBusSubscriber(modid = BlackHolePortMod.MODID, value = Dist.CLIENT)
public class ClientEvents {
    private static final int LAT_STEPS = 24;
    private static final int LON_STEPS = 48;
    private static long lastFrameNanos = -1L;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            ClientBlackHoles.blackedOut = false;
        } else {
            ClientBlackHoles.tick();
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientBlackHoles.clear();
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Pre event) {
        if (ClientBlackHoles.blackedOut) {
            event.getGuiGraphics().fill(0, 0,
                    event.getWindow().getGuiScaledWidth(), event.getWindow().getGuiScaledHeight(), 0xFF000000);
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ClientBlackHoles.HOLES.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }

        // Frame-time based, like the original: debris moves about the same speed at any frame rate.
        long now = System.nanoTime();
        if (lastFrameNanos == -1L) {
            lastFrameNanos = now;
        }
        float tickDelta = Math.min(0.375F, (now - lastFrameNanos) / 40000000.0F);
        lastFrameNanos = now;

        PoseStack pose = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();

        for (ClientBlackHoles.Hole hole : ClientBlackHoles.HOLES) {
            // Sphere.
            pose.pushPose();
            pose.translate(hole.pos.x - camera.x, hole.pos.y - camera.y, hole.pos.z - camera.z);
            drawSphere(pose.last().pose(), hole.size);
            pose.popPose();

            // Black out the screen when the camera is inside it.
            if (hole.pos.subtract(0.0, 1.75, 0.0).distanceTo(player.position()) <= hole.size) {
                ClientBlackHoles.blackedOut = true;
            }

            // Debris, pulled toward the hole.
            if (!ClientBlackHoles.pull) {
                continue;
            }
            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
            Iterator<ClientBlackHoles.Particle> it = hole.particles.iterator();
            while (it.hasNext()) {
                ClientBlackHoles.Particle particle = it.next();
                double toHole = particle.pos.distanceTo(hole.pos);
                double toPlayer = particle.pos.distanceTo(player.position());
                if (toHole < hole.size || toPlayer > 256.0) {
                    it.remove();
                    continue;
                }
                Vec3 step = particle.pos.subtract(hole.pos).normalize().scale(tickDelta * 0.5F);
                particle.pos = particle.pos.subtract(step);

                pose.pushPose();
                pose.translate(particle.pos.x - camera.x, particle.pos.y - camera.y, particle.pos.z - camera.z);
                mc.getItemRenderer().renderStatic(particle.stack, ItemDisplayContext.GROUND, 15728880,
                        OverlayTexture.NO_OVERLAY, pose, buffers, mc.level, 0);
                pose.popPose();
            }
            buffers.endBatch();
        }
    }

    /** A solid black sphere of the given radius around the current origin. Visible from inside as well. */
    private static void drawSphere(Matrix4f matrix, float radius) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.disableCull();
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();

        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < LAT_STEPS; i++) {
            double lat0 = Math.PI * i / LAT_STEPS - Math.PI / 2.0;
            double lat1 = Math.PI * (i + 1) / LAT_STEPS - Math.PI / 2.0;
            for (int j = 0; j < LON_STEPS; j++) {
                double lon0 = 2.0 * Math.PI * j / LON_STEPS;
                double lon1 = 2.0 * Math.PI * (j + 1) / LON_STEPS;
                vertex(buffer, matrix, radius, lat0, lon0);
                vertex(buffer, matrix, radius, lat1, lon0);
                vertex(buffer, matrix, radius, lat1, lon1);
                vertex(buffer, matrix, radius, lat0, lon1);
            }
        }
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.enableCull();
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix, float radius, double lat, double lon) {
        float x = (float) (Math.cos(lat) * Math.cos(lon)) * radius;
        float y = (float) Math.sin(lat) * radius;
        float z = (float) (Math.cos(lat) * Math.sin(lon)) * radius;
        buffer.vertex(matrix, x, y, z).color(0, 0, 0, 255).endVertex();
    }
}
