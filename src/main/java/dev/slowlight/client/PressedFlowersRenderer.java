package dev.slowlight.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.slowlight.Slowlight;
import dev.slowlight.block.PressedFlowersBlock;
import dev.slowlight.block.PressedFlowersBlockEntity;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;

/**
 * Pressed Flowers laid down, as Pink Petals are: each flower fills its quarter of the block with petals pressed flat,
 * from one of many looks (textures/block/pressed, made by tools/make_crafts.py pressed_petals) tinted with that
 * flower's own colours (FlowerColours). The petal masks keep their donors' shading; only the colour comes from the
 * flower. Which look is PressedLooks' choice: by the size of the bloom and whether it has a second colour, then by id.
 */
public class PressedFlowersRenderer implements BlockEntityRenderer<PressedFlowersBlockEntity> {
    /** The quarters' corners in the block's own plane, in the order they fill (as Pink Petals fill theirs). */
    private static final float[][] QUARTERS = {{0F, 0F}, {0.5F, 0F}, {0.5F, 0.5F}, {0F, 0.5F}};
    public PressedFlowersRenderer(BlockEntityRendererProvider.Context context) {
    }

    private static TextureAtlasSprite sprite(String name) {
        return Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS)
                .getSprite(ResourceLocation.fromNamespaceAndPath(Slowlight.MOD_ID, "block/pressed/" + name));
    }

    @Override
    public void render(PressedFlowersBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (!state.hasProperty(PressedFlowersBlock.FACING)) {
            return;
        }
        Direction facing = state.getValue(PressedFlowersBlock.FACING);
        boolean wall = state.getValue(PressedFlowersBlock.FACE) == AttachFace.WALL;
        VertexConsumer vc = buffers.getBuffer(RenderType.cutout());
        List<Holder<Item>> flowers = be.flowers();
        pose.pushPose();
        pose.translate(0.5F, 0.5F, 0.5F);
        if (wall) {
            // Lay the block's plane against the wall behind, its front (+z) facing out.
            pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
            pose.translate(0F, 0F, -0.5F + 0.01F);
        } else {
            // Turn by FACING, then lay the plane on the ground, its front up.
            pose.mulPose(Axis.YP.rotationDegrees(180F - facing.toYRot()));
            pose.translate(0F, -0.5F + 0.01F, 0F);
            pose.mulPose(Axis.XP.rotationDegrees(-90F));
        }
        PoseStack.Pose last = pose.last();
        for (int i = 0; i < flowers.size(); i++) {
            Item flower = flowers.get(i).value();
            FlowerColours.Look l = FlowerColours.of(flower);
            String name = PressedLooks.of(flower, l);
            // A glowing flower's petals stay as bright as the flower is drawn, even in the dark.
            int lit = l.glow() > LightTexture.block(light) ? LightTexture.pack(l.glow(), LightTexture.sky(light)) : light;
            quarter(vc, last, sprite(name), QUARTERS[i], l.petals(), lit, overlay);
            quarter(vc, last, sprite(name + "_centre"), QUARTERS[i], l.centre(), lit, overlay);
        }
        pose.popPose();
    }

    /** One quarter of the plane (-0.5..0.5 in x and y, facing +z), showing the same quarter of the texture. */
    private static void quarter(VertexConsumer vc, PoseStack.Pose pose, TextureAtlasSprite s, float[] q, int rgb, int light, int overlay) {
        float x0 = q[0] - 0.5F, x1 = x0 + 0.5F;
        float y1 = 0.5F - q[1], y0 = y1 - 0.5F;
        float u0 = Mth.lerp(q[0], s.getU0(), s.getU1()), u1 = Mth.lerp(q[0] + 0.5F, s.getU0(), s.getU1());
        float v0 = Mth.lerp(q[1], s.getV0(), s.getV1()), v1 = Mth.lerp(q[1] + 0.5F, s.getV0(), s.getV1());
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        vertex(vc, pose, x0, y0, u0, v1, r, g, b, light, overlay);
        vertex(vc, pose, x1, y0, u1, v1, r, g, b, light, overlay);
        vertex(vc, pose, x1, y1, u1, v0, r, g, b, light, overlay);
        vertex(vc, pose, x0, y1, u0, v0, r, g, b, light, overlay);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float u, float v, int r, int g, int b,
                               int light, int overlay) {
        vc.addVertex(pose, x, y, 0F).setColor(r, g, b, 255).setUv(u, v).setOverlay(overlay).setLight(light)
                .setNormal(pose, 0F, 0F, 1F);
    }
}
