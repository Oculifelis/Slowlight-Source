package dev.slowlight.item;

import dev.slowlight.SlowlightCrafts;
import dev.slowlight.block.PressedFlowersBlock;
import dev.slowlight.block.PressedFlowersBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.gameevent.GameEvent;

/** A flower kept flat between boards for a few days. Which flower is in its pressed_from component; the client draws
 * it pressed flat (dev.slowlight.client.PressedFlowerRenderer). Used on the top of a block it is laid on the ground,
 * on a side it goes on the wall; used again on the same spot, up to four share the block, like Pink Petals. */
public class PressedFlowerItem extends Item {
    public PressedFlowerItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        Holder<Item> flower = stack.get(SlowlightCrafts.PRESSED_FROM.get());
        if (flower == null) {
            return super.getName(stack);
        }
        return Component.translatable("item.slowlight.pressed_flower.of", flower.value().getDefaultInstance().getHoverName());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        Holder<Item> flower = stack.get(SlowlightCrafts.PRESSED_FROM.get());
        Level level = context.getLevel();
        Direction side = context.getClickedFace();
        if (flower == null || side == Direction.DOWN) {
            return InteractionResult.PASS;
        }
        // Clicking flowers already down adds to them; otherwise the new ones go in front of the face clicked.
        BlockPos pos = context.getClickedPos();
        BlockState there = level.getBlockState(pos);
        if (!(there.getBlock() instanceof PressedFlowersBlock)) {
            pos = pos.relative(side);
            there = level.getBlockState(pos);
        }
        if (there.getBlock() instanceof PressedFlowersBlock) {
            if (there.getValue(PressedFlowersBlock.FLOWERS) >= PressedFlowersBlock.MAX) {
                return InteractionResult.FAIL;
            }
        } else {
            Player player = context.getPlayer();
            Direction facing = side == Direction.UP
                    ? (player == null ? Direction.NORTH : player.getDirection())
                    : side;
            BlockState state = SlowlightCrafts.PRESSED_FLOWERS.get().defaultBlockState()
                    .setValue(PressedFlowersBlock.FACE, side == Direction.UP ? AttachFace.FLOOR : AttachFace.WALL)
                    .setValue(PressedFlowersBlock.FACING, facing);
            if (!there.canBeReplaced() || !state.canSurvive(level, pos)) {
                return InteractionResult.FAIL;
            }
            if (!level.isClientSide) {
                level.setBlock(pos, state, 3);
            }
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof PressedFlowersBlockEntity be && be.add(flower)) {
            level.playSound(null, pos, SoundEvents.PINK_PETALS_PLACE, SoundSource.BLOCKS, 1.0F, 1.1F);
            level.gameEvent(context.getPlayer(), GameEvent.BLOCK_PLACE, pos);
            stack.consume(1, context.getPlayer());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
