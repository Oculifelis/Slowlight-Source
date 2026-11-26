package dev.slowlight.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.slowlight.block.SandglassBlock;
import dev.slowlight.block.SandglassBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.neoforged.neoforge.client.RenderTypeHelper;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * The Sandglass turning over. While the block is TURNING its own model stands aside and this draws the glass as it was
 * a moment before, with all the sand in the bottom bulb, tumbling end over end. Half a turn puts that sand on top,
 * which is exactly the full model it lands as.
 *
 * <p>While it runs, the model in the world has no sand in it and this draws the sand instead, its height worked out
 * from the clock every frame, so the top sinks and the heap grows a hair at a time and the thread through the neck
 * keeps falling (the reviewer 2026-11-16: "the sand needs to actually continuously drain even if its such miniscule amount
 * of movement"). The shapes are the generated models' own (tools/make_models.py, sandglass).
 *
 * <p>The tumbling glass is lit the way the world lights the resting block: it is tesselated by the block renderer at its
 * own spot, with the same face shading, smooth light and ambient occlusion, and drawn through the moving-block render
 * types, so the first and last frames of the turn match the block in the world. While it swings, each face is shaded by
 * the way it faces now, and the occlusion fades out towards the half turn and back in as it lands, so nothing jumps in
 * brightness.
 */
public class SandglassRenderer implements BlockEntityRenderer<SandglassBlockEntity> {
    /** Ticks the landed glass is still drawn here after the turn ends. */
    private static final int LANDING_TICKS = 3;

    public SandglassRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(SandglassBlockEntity glass, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        BlockState state = glass.getBlockState();
        // Just landed: the world's own model of it is rebuilt a frame or two later, so the landed glass is drawn here
        // until then, or it blinks out for a frame at the end of the turn.
        if (glass.getLevel() != null && !state.getValue(SandglassBlock.TURNING) && glass.turnSeenAt >= 0L) {
            glass.landedAt = glass.getLevel().getGameTime();
        }
        boolean landing = glass.getLevel() != null && glass.landedAt >= 0L
                && glass.getLevel().getGameTime() - glass.landedAt < LANDING_TICKS;
        if (glass.getLevel() == null || !state.getValue(SandglassBlock.TURNING)) {
            glass.turnSeenAt = -1L;
            if (glass.getLevel() != null && state.getValue(SandglassBlock.RUNNING)) {
                float top = glass.sandOnTop(partialTick);
                drawSand(pose, buffers.getBuffer(RenderType.cutout()), top < 0.0F ? 1.0F : top,
                        glass.getLevel().getGameTime() + partialTick);
            }
            if (!landing) {
                return;
            }
        } else {
            glass.landedAt = -1L;
        }
        float now = glass.getLevel().getGameTime() + partialTick;
        // The client only sees the state flip; the turn is timed from the first frame that shows it.
        if (!landing && (glass.turnSeenAt < 0L || now - glass.turnSeenAt > SandglassBlock.TURN_TICKS + 20)) {
            glass.turnSeenAt = glass.getLevel().getGameTime();
        }
        float t = landing ? 1.0F : Mth.clamp((now - glass.turnSeenAt) / (SandglassBlock.TURN_TICKS - 2), 0.0F, 1.0F);
        // Eased: it is lifted, swings, and settles.
        float eased = t * t * (3.0F - 2.0F * t);
        BlockState before = state.setValue(SandglassBlock.TURNING, false).setValue(SandglassBlock.SAND, 0)
                .setValue(SandglassBlock.RUNNING, false).setValue(SandglassBlock.POWERED, false);
        // Past halfway it is drawn as it will land and turned back the rest of the way, so nothing changes when the
        // world takes over drawing it: the one swap happens edge-on, mid-swing.
        boolean second = eased >= 0.5F;
        BlockState shown = second ? state.setValue(SandglassBlock.TURNING, false).setValue(SandglassBlock.POWERED, false)
                : before;
        float degrees = second ? 180.0F * eased - 180.0F : 180.0F * eased;
        Quaternionf turn = Axis.XP.rotationDegrees(degrees);
        // The resting occlusion and smooth light are kept at the ends and fade out edge-on, where the models swap.
        float w = Math.abs(Mth.cos(degrees * Mth.DEG_TO_RAD));
        pose.pushPose();
        // Lift it clear of the floor while its corners swing past (a 12px frame reaches 10px from its middle).
        pose.translate(0.5D, 0.5D + Mth.sin(t * Mth.PI) * 0.16F, 0.5D);
        pose.mulPose(turn);
        if (landing) {
            // The world's own model may already be drawn here: sit just inside it so the two never flicker.
            pose.scale(0.998F, 0.998F, 0.998F);
        }
        pose.translate(-0.5D, -0.5D, -0.5D);
        renderGlass(glass, shown, pose, buffers, packedLight, packedOverlay, turn, w);
        if (second && !landing && shown.getValue(SandglassBlock.RUNNING)) {
            float top = glass.sandOnTop(partialTick);
            drawSand(pose, buffers.getBuffer(RenderType.cutout()), top < 0.0F ? 1.0F : top, now);
        }
        pose.popPose();
    }

    /** The glass as the chunk renderer would draw it at its own spot, under the pose's turn. */
    private static void renderGlass(SandglassBlockEntity glass, BlockState shown, PoseStack pose, MultiBufferSource buffers,
                                    int packedLight, int packedOverlay, Quaternionf turn, float w) {
        BlockAndTintGetter level = glass.getLevel();
        BlockPos pos = glass.getBlockPos();
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        BakedModel model = dispatcher.getBlockModel(shown);
        long seed = shown.getSeed(pos);
        RandomSource random = RandomSource.create(seed);
        for (RenderType type : model.getRenderTypes(shown, random, ModelData.EMPTY)) {
            VertexConsumer out = new TurnLit(buffers.getBuffer(RenderTypeHelper.getMovingBlockRenderType(type)), level, turn,
                    w, packedLight);
            dispatcher.getModelRenderer().tesselateBlock(level, model, shown, pos, pose, out, false, random, seed,
                    packedOverlay, ModelData.EMPTY, type);
        }
    }

    /** Packed light from {@code a} towards {@code b}: all of {@code a} at w = 1, all of {@code b} at w = 0. */
    private static int lerpLight(int a, int b, float w) {
        int blockA = a & 0xFFFF, skyA = a >>> 16, blockB = b & 0xFFFF, skyB = b >>> 16;
        int block = Math.round(blockB + (blockA - blockB) * w), sky = Math.round(skyB + (skyA - skyB) * w);
        return (sky << 16) | block;
    }

    /**
     * Passes the block renderer's quads on with each face shaded by where it points after the turn, and the resting
     * occlusion and smooth light kept by {@code w} (1 at rest, 0 edge-on).
     */
    private static final class TurnLit implements VertexConsumer {
        private final VertexConsumer out;
        private final BlockAndTintGetter level;
        private final Quaternionf turn;
        private final float w;
        private final int flatLight;
        private final Vector3f n = new Vector3f();

        TurnLit(VertexConsumer out, BlockAndTintGetter level, Quaternionf turn, float w, int flatLight) {
            this.out = out;
            this.level = level;
            this.turn = turn;
            this.w = w;
            this.flatLight = flatLight;
        }

        /** The world's face shading for a face whose normal points along {@code v}, blended between the six sides. */
        private float shadeFor(Vector3f v) {
            return v.x * v.x * level.getShade(v.x < 0 ? Direction.WEST : Direction.EAST, true)
                    + v.y * v.y * level.getShade(v.y < 0 ? Direction.DOWN : Direction.UP, true)
                    + v.z * v.z * level.getShade(v.z < 0 ? Direction.NORTH : Direction.SOUTH, true);
        }

        @Override
        public void putBulkData(PoseStack.Pose pose, BakedQuad quad, float[] brightness, float r, float g, float b, float a,
                                int[] lightmap, int overlay, boolean readColor) {
            Direction dir = quad.getDirection();
            float restShade = level.getShade(dir, quad.isShade());
            float nowShade = quad.isShade()
                    ? shadeFor(turn.transform(n.set(dir.getStepX(), dir.getStepY(), dir.getStepZ()))) : restShade;
            float[] bright = new float[brightness.length];
            int[] light = new int[lightmap.length];
            for (int v = 0; v < bright.length; v++) {
                // the corner's brightness is shade x occlusion: take the occlusion back out
                float occlusion = restShade <= 0.0F ? 1.0F : Math.min(1.0F, brightness[v] / restShade);
                bright[v] = nowShade * (1.0F + (occlusion - 1.0F) * w);
            }
            for (int v = 0; v < light.length; v++) {
                light[v] = lerpLight(lightmap[v], flatLight, w);
            }
            out.putBulkData(pose, quad, bright, r, g, b, a, light, overlay, readColor);
        }

        @Override public VertexConsumer addVertex(float x, float y, float z) { out.addVertex(x, y, z); return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { out.setColor(r, g, b, a); return this; }
        @Override public VertexConsumer setUv(float u, float v) { out.setUv(u, v); return this; }
        @Override public VertexConsumer setUv1(int u, int v) { out.setUv1(u, v); return this; }
        @Override public VertexConsumer setUv2(int u, int v) { out.setUv2(u, v); return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { out.setNormal(x, y, z); return this; }
    }

    private static final ResourceLocation SAND = ResourceLocation.fromNamespaceAndPath("slowlight", "block/sandglass_sand");

    /** Pixels of sand in a full bulb: base, belly and shoulder (1 + 3 + 1), filled from the bottom up. */
    private static final float FILL = 5.0F;

    /** The sand, `top` of it (1 to 0) still in the upper bulb, the rest in the lower, heaped in the middle. */
    static void drawSand(PoseStack pose, VertexConsumer out, float top, float time) {
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(SAND);
        float bottom = 1.0F - top;
        // Each bulb holds FILL pixels of sand: its 1px base, the 3px belly and the 1px shoulder. Both bulbs fill and
        // empty strictly by the clock, so a glass that has just turned shows no sand at all in its lower bulb.
        if (top > 0.0F) {
            float h = top * FILL;
            box(pose, out, sprite, 6.5F, 9.0F, 6.5F, 9.5F, 9.0F + Math.min(1.0F, h), 9.5F, h < 1.0F, 0.0F);
            if (h > 1.0F) {
                box(pose, out, sprite, 5.5F, 10.0F, 5.5F, 10.5F, 10.0F + Math.min(3.0F, h - 1.0F), 10.5F, true, 0.0F);
            }
            if (h > 4.0F) {
                box(pose, out, sprite, 6.5F, 13.0F, 6.5F, 9.5F, 9.0F + h, 9.5F, true, 0.0F);
            }
        }
        float heapTop = 2.0F;
        if (bottom > 0.0F) {
            float h = bottom * FILL;
            box(pose, out, sprite, 6.5F, 2.0F, 6.5F, 9.5F, 2.0F + Math.min(1.0F, h), 9.5F, h < 1.0F, 0.0F);
            if (h > 1.0F) {
                box(pose, out, sprite, 5.5F, 3.0F, 5.5F, 10.5F, 3.0F + Math.min(3.0F, h - 1.0F), 10.5F, true, 0.0F);
            }
            if (h > 4.0F) {
                box(pose, out, sprite, 6.5F, 6.0F, 6.5F, 9.5F, 2.0F + h, 9.5F, true, 0.0F);
            }
            heapTop = 2.0F + h;
            if (h < 4.5F) {
                // the little heap where the thread lands, never more sand than has fallen
                float heap = Math.min(0.5F, h);
                box(pose, out, sprite, 7.0F, heapTop, 7.0F, 9.0F, heapTop + heap, 9.0F, true, 0.0F);
                heapTop += heap;
            }
        }
        if (top > 0.0F && heapTop < 10.0F) {
            // the thread through the neck; its grain slides down the sprite so it is seen to fall
            box(pose, out, sprite, 7.75F, heapTop, 7.75F, 8.25F, 10.0F, 8.25F, false, (time * 0.35F) % 16.0F);
        }
    }

    /** A sand box in sixteenths, lit full (it glows, like the model's), sides always, top only when asked. */
    private static void box(PoseStack pose, VertexConsumer out, TextureAtlasSprite sprite, float x0, float y0, float z0,
                            float x1, float y1, float z1, boolean ends, float scroll) {
        if (y1 - y0 < 0.001F) {
            return;
        }
        PoseStack.Pose p = pose.last();
        float a = x0 / 16, b = y0 / 16, c = z0 / 16, d = x1 / 16, e = y1 / 16, f = z1 / 16;
        // north (-z), south (+z), west (-x), east (+x)
        quad(p, out, sprite, d, b, c, a, b, c, a, e, c, d, e, c, 0, 0, -1, 16 - x1, 16 - y1, 16 - x0, 16 - y0, scroll);
        quad(p, out, sprite, a, b, f, d, b, f, d, e, f, a, e, f, 0, 0, 1, x0, 16 - y1, x1, 16 - y0, scroll);
        quad(p, out, sprite, a, b, c, a, b, f, a, e, f, a, e, c, -1, 0, 0, z0, 16 - y1, z1, 16 - y0, scroll);
        quad(p, out, sprite, d, b, f, d, b, c, d, e, c, d, e, f, 1, 0, 0, 16 - z1, 16 - y1, 16 - z0, 16 - y0, scroll);
        if (ends) {
            quad(p, out, sprite, a, e, f, d, e, f, d, e, c, a, e, c, 0, 1, 0, x0, z0, x1, z1, 0.0F);
            quad(p, out, sprite, a, b, c, d, b, c, d, b, f, a, b, f, 0, -1, 0, x0, 16 - z1, x1, 16 - z0, 0.0F);
        }
    }

    /** Corners counter-clockwise from bottom left as seen from outside; u/v in the sprite's sixteenths. */
    private static void quad(PoseStack.Pose p, VertexConsumer out, TextureAtlasSprite sprite,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4,
                             float nx, float ny, float nz, float u0, float v0, float u1, float v1, float scroll) {
        if (scroll != 0.0F) {
            // slide the window up the sprite (grains move down), wrapping within the room the sprite leaves
            float len = v1 - v0, room = 16.0F - len;
            v0 = room <= 0.0F ? 0.0F : room - (scroll % room);
            v1 = v0 + len;
        }
        float ua = sprite.getU(u0 / 16), ub = sprite.getU(u1 / 16), va = sprite.getV(v0 / 16), vb = sprite.getV(v1 / 16);
        vertex(p, out, x1, y1, z1, ua, vb, nx, ny, nz);
        vertex(p, out, x2, y2, z2, ub, vb, nx, ny, nz);
        vertex(p, out, x3, y3, z3, ub, va, nx, ny, nz);
        vertex(p, out, x4, y4, z4, ua, va, nx, ny, nz);
    }

    private static void vertex(PoseStack.Pose p, VertexConsumer out, float x, float y, float z, float u, float v,
                               float nx, float ny, float nz) {
        out.addVertex(p, x, y, z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(p, nx, ny, nz);
    }
}
