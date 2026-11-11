package dev.slowlight.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.slowlight.block.SandglassBlock;
import dev.slowlight.block.SandglassBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * The Sandglass turning over. While the block is TURNING its own model stands aside and this draws the glass as it was
 * a moment before, with all the sand in the bottom bulb, tumbling end over end. Half a turn puts that sand on top,
 * which is exactly the full model it lands as.
 */
public class SandglassRenderer implements BlockEntityRenderer<SandglassBlockEntity> {
    public SandglassRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(SandglassBlockEntity glass, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        BlockState state = glass.getBlockState();
        if (glass.getLevel() == null || !state.getValue(SandglassBlock.TURNING)) {
            glass.turnSeenAt = -1L;
            return;
        }
        float now = glass.getLevel().getGameTime() + partialTick;
        // The client only sees the state flip; the turn is timed from the first frame that shows it.
        if (glass.turnSeenAt < 0L || now - glass.turnSeenAt > SandglassBlock.TURN_TICKS + 20) {
            glass.turnSeenAt = glass.getLevel().getGameTime();
        }
        float t = Mth.clamp((now - glass.turnSeenAt) / (SandglassBlock.TURN_TICKS - 2), 0.0F, 1.0F);
        // Eased: it is lifted, swings, and settles.
        float eased = t * t * (3.0F - 2.0F * t);
        BlockState before = state.setValue(SandglassBlock.TURNING, false).setValue(SandglassBlock.SAND, 0)
                .setValue(SandglassBlock.RUNNING, false).setValue(SandglassBlock.POWERED, false);
        pose.pushPose();
        // Lift it clear of the floor while its corners swing past (a 12px frame reaches 10px from its middle).
        pose.translate(0.5D, 0.5D + Mth.sin(t * Mth.PI) * 0.16F, 0.5D);
        pose.mulPose(Axis.XP.rotationDegrees(180.0F * eased));
        pose.translate(-0.5D, -0.5D, -0.5D);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(before, pose, buffers, packedLight, packedOverlay,
                ModelData.EMPTY, null);
        pose.popPose();
    }
}
