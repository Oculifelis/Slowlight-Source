package dev.slowlight.block;

import dev.slowlight.SlowlightCrafts;
import dev.slowlight.recipe.AgeingRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One flower between two boards. Anything in #slowlight:pressable goes in; after {@link #DAYS} days it is a Pressed
 * Flower. It holds one thing at a time, so a hopper can feed it from above or the side and take the pressed flower
 * from below, and a comparator reads how far along it is.
 */
public class FlowerPressBlockEntity extends BlockEntity implements WorldlyContainer {
    public static final int DAYS = 3;
    private static final int[] SLOT = {0};
    private static final int[] NONE = {};

    private ItemStack held = ItemStack.EMPTY;
    /** Game time the flower went in, or -1 when it is pressed (or there is none). */
    /** When the flower went in; UNSET once it is pressed (a game time can be below 0 when time is set back). */
    private static final long UNSET = Long.MIN_VALUE;
    private long since = UNSET;

    public FlowerPressBlockEntity(BlockPos pos, BlockState state) {
        super(SlowlightCrafts.FLOWER_PRESS_BE.get(), pos, state);
    }

    public boolean pressing() {
        return !held.isEmpty() && since != UNSET;
    }

    public boolean done() {
        return !held.isEmpty() && since == UNSET;
    }

    public ItemStack held() {
        return held;
    }

    /** Puts one flower in; true if it took it. */
    public boolean insert(ItemStack flower) {
        if (!held.isEmpty() || !flower.is(SlowlightCrafts.PRESSABLE) || level == null) {
            return false;
        }
        held = flower.copyWithCount(1);
        since = level.getGameTime();
        changed(true);
        level.playSound(null, worldPosition, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.8F, 0.8F);
        return true;
    }

    /** Takes out what is there: the pressed flower, or (sneaking) the flower before it is done. */
    public ItemStack take() {
        ItemStack out = held;
        held = ItemStack.EMPTY;
        since = UNSET;
        changed(false);
        return out;
    }

    public Component status() {
        if (held.isEmpty() || level == null) {
            return Component.translatable("block.slowlight.flower_press.empty");
        }
        if (done()) {
            return Component.translatable("block.slowlight.flower_press.done", held.getHoverName());
        }
        long left = since + (long) DAYS * AgeingRecipe.TICKS_PER_DAY - level.getGameTime();
        if (left <= AgeingRecipe.TICKS_PER_DAY) {
            return Component.translatable("block.slowlight.flower_press.today", held.getHoverName());
        }
        return Component.translatable("block.slowlight.flower_press.days", held.getHoverName(),
                (left + AgeingRecipe.TICKS_PER_DAY - 1L) / AgeingRecipe.TICKS_PER_DAY);
    }

    /** 0 empty, 1 to 14 while pressing, 15 when done. */
    public int signal() {
        if (held.isEmpty() || level == null) {
            return 0;
        }
        if (done()) {
            return 15;
        }
        double f = (level.getGameTime() - since) / (double) (DAYS * AgeingRecipe.TICKS_PER_DAY);
        return 1 + (int) Math.min(13, Math.floor(f * 14));
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FlowerPressBlockEntity press) {
        if (level.getGameTime() % 20L != 0L || !press.pressing()) {
            return;
        }
        if (level.getGameTime() - press.since >= (long) DAYS * AgeingRecipe.TICKS_PER_DAY) {
            press.held = SlowlightCrafts.pressed(press.held.getItemHolder());
            press.since = UNSET;
            press.changed(true);
            level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1.0F, 1.0F);
        } else if (level.getGameTime() % 1200L == 0L) {
            level.updateNeighbourForOutputSignal(pos, state.getBlock());
        }
    }

    private void changed(boolean full) {
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            if (state.getValue(FlowerPressBlock.FULL) != full) {
                level.setBlock(worldPosition, state.setValue(FlowerPressBlock.FULL, full), 3);
            }
            level.updateNeighbourForOutputSignal(worldPosition, state.getBlock());
        }
    }

    // ---- container: one slot, in from above or the side, out from below once pressed ---------------------------

    @Override
    public int[] getSlotsForFace(Direction side) {
        return SLOT;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return side != Direction.DOWN && held.isEmpty() && stack.is(SlowlightCrafts.PRESSABLE);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return side == Direction.DOWN && done();
    }

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return held.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return held;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return amount > 0 && !held.isEmpty() ? take() : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return take();
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (stack.isEmpty()) {
            take();
        } else if (held.isEmpty()) {
            insert(stack);
        }
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean stillValid(Player player) {
        return false;
    }

    @Override
    public void clearContent() {
        held = ItemStack.EMPTY;
        since = UNSET;
    }

    // ---- saving -------------------------------------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!held.isEmpty()) {
            tag.put("Held", held.save(registries));
        }
        tag.putLong("Since", since);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        held = tag.contains("Held") ? ItemStack.parseOptional(registries, tag.getCompound("Held")) : ItemStack.EMPTY;
        since = tag.contains("Since") ? tag.getLong("Since") : UNSET;
    }
}
