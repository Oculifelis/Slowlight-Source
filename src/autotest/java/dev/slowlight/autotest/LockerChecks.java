package dev.slowlight.autotest;

import dev.slowlight.SlowlightLockers;
import dev.slowlight.locker.LockerBlock;
import dev.slowlight.locker.LockerBlockEntity;
import dev.slowlight.locker.LockerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import static dev.slowlight.autotest.ServerChecks.check;

/** Lockers: sizes from the data map, hoppers and comparators, and a numbered lock that only its own key opens. */
final class LockerChecks {
    private static final int Y = -60;
    private static final BlockPos PLAIN = new BlockPos(-30, Y, -10);
    private static final BlockPos REINFORCED = new BlockPos(-33, Y, -10);
    private static final int NUMBER = 4242;

    private LockerChecks() {
    }

    static void run(ServerLevel level, ServerPlayer player) {
        LockerBlockEntity plain = place(level, PLAIN, SlowlightLockers.LOCKER.get());
        LockerBlockEntity reinforced = place(level, REINFORCED, SlowlightLockers.REINFORCED_LOCKER.get());
        if (plain == null || reinforced == null) {
            ServerChecks.fail("locker.placed", "no locker block entity: " + level.getBlockState(PLAIN) + ", " + level.getBlockState(REINFORCED));
            return;
        }
        check("locker.size_from_data_map", plain.getContainerSize() == 27 && reinforced.getContainerSize() == 39,
                "locker " + plain.size() + " = " + plain.getContainerSize() + " slots, reinforced " + reinforced.size() + " = "
                        + reinforced.getContainerSize() + " slots (expected 27 and 39)");

        ItemStack left = plain.itemHandler().insertItem(0, new ItemStack(Items.COBBLESTONE, 64), false);
        int signal = level.getBlockState(PLAIN).getAnalogOutputSignal(level, PLAIN);
        check("locker.hopper_inserts", left.isEmpty() && plain.getItem(0).is(Items.COBBLESTONE), "left over " + left + ", slot 0 " + plain.getItem(0));
        check("locker.comparator", signal > 0, "signal " + signal + " with one full stack in 27 slots");

        // Only the reinforced locker takes a lock.
        ItemStack lock = new ItemStack(SlowlightLockers.LOCK.get());
        lock.set(SlowlightLockers.LOCK_ID.get(), NUMBER);
        level.getBlockState(PLAIN).useItemOn(lock.copy(), level, player, InteractionHand.MAIN_HAND, hit(PLAIN));
        check("locker.plain_takes_no_lock", !plain.isLocked(), "plain locker locked=" + plain.isLocked());

        reinforced.itemHandler().insertItem(0, new ItemStack(Items.COBBLESTONE, 64), false);
        BlockState state = level.getBlockState(REINFORCED);
        state.useItemOn(lock, level, player, InteractionHand.MAIN_HAND, hit(REINFORCED));
        check("locker.lock_fits", reinforced.isLocked() && reinforced.guarded(), "locked=" + reinforced.isLocked() + ", lock " + reinforced.lock());

        ItemStack refused = reinforced.itemHandler().insertItem(1, new ItemStack(Items.DIRT, 8), false);
        ItemStack taken = reinforced.itemHandler().extractItem(0, 64, false);
        check("locker.locked_blocks_hoppers", refused.getCount() == 8 && taken.isEmpty() && reinforced.getItem(0).getCount() == 64,
                "insert left " + refused + ", extract got " + taken);

        ItemStack held = player.getMainHandItem().copy();
        player.closeContainer();
        ItemStack wrong = new ItemStack(SlowlightLockers.KEY.get());
        wrong.set(SlowlightLockers.LOCK_ID.get(), NUMBER + 1);
        player.setItemInHand(InteractionHand.MAIN_HAND, wrong);
        state.useWithoutItem(level, player, hit(REINFORCED));
        boolean wrongOpened = player.containerMenu instanceof LockerMenu;
        player.closeContainer();
        ItemStack right = new ItemStack(SlowlightLockers.KEY.get());
        right.set(SlowlightLockers.LOCK_ID.get(), NUMBER);
        player.setItemInHand(InteractionHand.MAIN_HAND, right);
        state.useWithoutItem(level, player, hit(REINFORCED));
        boolean rightOpened = player.containerMenu instanceof LockerMenu;
        player.closeContainer();
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        check("locker.only_its_key_opens", !wrongOpened && rightOpened, "wrong key opened=" + wrongOpened + ", right key opened=" + rightOpened);
    }

    private static LockerBlockEntity place(ServerLevel level, BlockPos pos, Block block) {
        BlockState lower = block.defaultBlockState().setValue(LockerBlock.FACING, Direction.SOUTH);
        level.setBlock(pos.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(pos, lower, 3);
        level.setBlock(pos.above(), lower.setValue(LockerBlock.HALF, DoubleBlockHalf.UPPER), 3);
        return level.getBlockEntity(pos) instanceof LockerBlockEntity be ? be : null;
    }

    private static BlockHitResult hit(BlockPos pos) {
        return new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0, 0.5), Direction.SOUTH, pos, false);
    }
}
