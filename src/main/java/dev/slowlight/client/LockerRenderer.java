package dev.slowlight.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.slowlight.Slowlight;
import dev.slowlight.locker.LockerBlock;
import dev.slowlight.locker.LockerBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.ModelEvent;

/**
 * Draws a locker's door. The carcass is a plain block model; the door is a separate baked model turned on its hinge.
 * A single locker hinges on the viewer's left. In a wide locker the LEFT piece (the one on the viewer's right)
 * hinges on the right, so the pair opens like a wardrobe.
 */
public class LockerRenderer implements BlockEntityRenderer<LockerBlockEntity> {
    private static final ModelResourceLocation DOOR = model("block/locker_door");
    private static final ModelResourceLocation DOOR_MIRRORED = model("block/locker_door_mirrored");
    private static final ModelResourceLocation REINFORCED_DOOR = model("block/reinforced_locker_door");
    private static final ModelResourceLocation REINFORCED_DOOR_MIRRORED = model("block/reinforced_locker_door_mirrored");

    public LockerRenderer(BlockEntityRendererProvider.Context context) {
    }

    private static ModelResourceLocation model(String path) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(Slowlight.MOD_ID, path));
    }

    public static void registerModels(ModelEvent.RegisterAdditional event) {
        event.register(DOOR);
        event.register(DOOR_MIRRORED);
        event.register(REINFORCED_DOOR);
        event.register(REINFORCED_DOOR_MIRRORED);
    }

    @Override
    public void render(LockerBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof LockerBlock block)) {
            return;
        }
        Direction facing = state.getValue(LockerBlock.FACING);
        boolean mirrored = state.getValue(LockerBlock.TYPE) == ChestType.LEFT;
        float open = 1.0F - be.openness(partialTick);
        open = 1.0F - open * open * open;
        Minecraft mc = Minecraft.getInstance();
        ModelResourceLocation name = block.lockable()
                ? (mirrored ? REINFORCED_DOOR_MIRRORED : REINFORCED_DOOR)
                : (mirrored ? DOOR_MIRRORED : DOOR);
        BakedModel model = mc.getModelManager().getModel(name);

        pose.pushPose();
        pose.translate(0.5F, 0.0F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - facing.toYRot()));
        pose.translate(-0.5F, 0.0F, -0.5F);
        // The model faces north; its hinge edge is x = 1 (x = 0 when mirrored), on the front face z = 0.
        float hinge = mirrored ? 0.0F : 1.0F;
        pose.translate(hinge, 0.0F, 0.0F);
        pose.mulPose(Axis.YP.rotationDegrees((mirrored ? 90.0F : -90.0F) * open));
        pose.translate(-hinge, 0.0F, 0.0F);
        mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(Sheets.cutoutBlockSheet()),
                null, model, 1.0F, 1.0F, 1.0F, light, overlay);
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(LockerBlockEntity be) {
        BlockPos p = be.getBlockPos();
        return new AABB(p.getX() - 1, p.getY(), p.getZ() - 1, p.getX() + 2, p.getY() + 2, p.getZ() + 2);
    }
}
