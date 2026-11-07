package dev.slowlight.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.slowlight.block.LongKilnBlock;
import dev.slowlight.block.PatientKilnBlock;
import net.minecraft.core.Direction;
import dev.slowlight.block.PatientKilnBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * What is waiting in an open kiln, standing in its mouth. A working kiln has its lid on, so there is nothing to see
 * until the wait is over; then the finished work stands up over the rim, turned toward whoever is looking, so it can
 * be noticed from across the room. It does not spin: the lid is the kiln's only movement.
 */
public class PatientKilnRenderer implements BlockEntityRenderer<PatientKilnBlockEntity> {
    /** Height of the mouth's floor, as in the block model and PatientKilnBlock's shape. */
    private static final double FLOOR = 11.0D / 16.0D;

    public PatientKilnRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(PatientKilnBlockEntity kiln, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        if (kiln.getLevel() == null || kiln.getBlockState().getValue(PatientKilnBlock.WORKING)) {
            return;
        }
        ItemStack shown = kiln.finished();
        if (shown.isEmpty()) {
            shown = kiln.working();
        }
        if (shown.isEmpty()) {
            return;
        }
        pose.pushPose();
        int light;
        if (kiln.getBlockState().getBlock() instanceof LongKilnBlock) {
            // In the door arch at the front, standing on its floor and facing out of the door.
            Direction facing = kiln.getBlockState().getValue(LongKilnBlock.FACING);
            light = LevelRenderer.getLightColor(kiln.getLevel(), kiln.getBlockPos().relative(facing));
            pose.translate(0.5D + facing.getStepX() * 0.3D, LongKilnBlock.DOOR_FLOOR / 16.0D + 0.125D * 0.75D,
                    0.5D + facing.getStepZ() * 0.3D);
            pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
            // The arch is 6px wide; the ground transform's 8px item would overhang it.
            pose.scale(0.75F, 0.75F, 0.75F);
        } else {
            // The mouth is walled in, so light it as the air above the kiln is lit rather than as the block itself.
            light = LevelRenderer.getLightColor(kiln.getLevel(), kiln.getBlockPos().above());
            Vec3 eye = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            double dx = eye.x - (kiln.getBlockPos().getX() + 0.5D);
            double dz = eye.z - (kiln.getBlockPos().getZ() + 0.5D);
            // The ground transform lifts the item 2px and halves it; this puts its foot on the mouth's floor (y 11).
            pose.translate(0.5D, FLOOR + 0.125D, 0.5D);
            pose.mulPose(Axis.YP.rotation((float) Math.atan2(dx, dz)));
        }
        Minecraft.getInstance().getItemRenderer().renderStatic(shown, ItemDisplayContext.GROUND, light, packedOverlay,
                pose, buffers, kiln.getLevel(), (int) kiln.getBlockPos().asLong());
        pose.popPose();
    }
}
