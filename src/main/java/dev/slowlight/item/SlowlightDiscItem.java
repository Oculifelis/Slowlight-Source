package dev.slowlight.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * A record. The jukebox does the playing; this only adds one grey line under the title, because
 * a disc that took a week to make has earned a remark.
 */
public class SlowlightDiscItem extends Item {
    public SlowlightDiscItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable(getDescriptionId() + ".remark").withStyle(ChatFormatting.GRAY));
    }
}
