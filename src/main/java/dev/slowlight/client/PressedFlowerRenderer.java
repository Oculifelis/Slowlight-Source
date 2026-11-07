package dev.slowlight.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.slowlight.SlowlightCrafts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * A Pressed Flower: the flower's own item icon, pressed flat (a flower drawn as a block, like Cherry Leaves, is
 * squashed thin). Any flower in #slowlight:pressable draws this way, modded ones too, with no art of its own. Also
 * draws them laid down (PressedFlowersRenderer).
 */
public class PressedFlowerRenderer extends BlockEntityWithoutLevelRenderer {
    private static PressedFlowerRenderer instance;

    private PressedFlowerRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public static PressedFlowerRenderer get() {
        if (instance == null) {
            instance = new PressedFlowerRenderer();
        }
        return instance;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers,
                             int light, int overlay) {
        Holder<Item> flower = stack.get(SlowlightCrafts.PRESSED_FROM.get());
        if (flower == null) {
            return;
        }
        var items = Minecraft.getInstance().getItemRenderer();
        ItemStack shown = flower.value().getDefaultInstance();
        BakedModel model = items.getModel(shown, null, null, 0);
        // This runs inside the item's own transform, in the 0..1 box; the inner render wants the box's centre.
        pose.pushPose();
        pose.translate(0.5F, 0.5F, 0.5F);
        pose.scale(1.0F, 1.0F, 0.15F);
        items.render(shown, ItemDisplayContext.NONE, false, pose, buffers, light, overlay, model);
        pose.popPose();
    }
}
