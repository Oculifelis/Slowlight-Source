package dev.slowlight.locker;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** A lock for a Reinforced Locker. It carries the number of the key it was crafted with. */
public class LockItem extends Item {
    public LockItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Integer number = KeyItem.number(stack);
        tooltip.add((number == null ? Component.translatable("item.slowlight.lock.blank")
                : Component.translatable("item.slowlight.lock.number", number)).withStyle(ChatFormatting.GRAY));
    }
}
