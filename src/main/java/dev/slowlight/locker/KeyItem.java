package dev.slowlight.locker;

import dev.slowlight.SlowlightLockers;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * A key. Each new key gets its own number; a Lock crafted with a key takes that number and opens only for keys
 * that carry it. Crafting a key with copper ingots copies it. Sneak-using a key on its locker takes the lock off.
 */
public class KeyItem extends Item {
    public KeyItem(Properties properties) {
        super(properties);
    }

    public static Integer number(ItemStack stack) {
        return stack.get(SlowlightLockers.LOCK_ID.get());
    }

    private static void ensureNumber(ItemStack stack, Level level) {
        if (!level.isClientSide && !stack.has(SlowlightLockers.LOCK_ID.get())) {
            stack.set(SlowlightLockers.LOCK_ID.get(), 1 + level.random.nextInt(32000));
        }
    }

    /** True when every lock on these lockers fits the key in hand, or none of them is locked. */
    static boolean opens(ItemStack key, List<LockerBlockEntity> pieces) {
        Integer number = key.is(SlowlightLockers.KEY.get()) ? number(key) : null;
        for (LockerBlockEntity be : pieces) {
            if (be.isLocked() && (number == null || !number.equals(number(be.lock())))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onCraftedBy(ItemStack stack, Level level, Player player) {
        ensureNumber(stack, level);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        ensureNumber(stack, level);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        Level level = ctx.getLevel();
        BlockState state = level.getBlockState(ctx.getClickedPos());
        if (player == null || !player.isSecondaryUseActive() || !(state.getBlock() instanceof LockerBlock)) {
            return InteractionResult.PASS;
        }
        LockerBlockEntity be = LockerBlock.entity(level, state, ctx.getClickedPos());
        if (be == null) {
            return InteractionResult.PASS;
        }
        Integer number = number(ctx.getItemInHand());
        for (LockerBlockEntity piece : LockerBlock.pieces(be)) {
            if (piece.isLocked() && number != null && number.equals(number(piece.lock()))) {
                if (!level.isClientSide) {
                    player.getInventory().placeItemBackInInventory(piece.takeLock());
                    level.playSound(null, piece.getBlockPos().above(), SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.6F, 1.3F);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Integer number = number(stack);
        if (number != null) {
            tooltip.add(Component.translatable("item.slowlight.key.number", number).withStyle(ChatFormatting.GRAY));
        }
    }
}
