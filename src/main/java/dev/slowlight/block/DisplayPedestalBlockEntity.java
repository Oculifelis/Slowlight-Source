package dev.slowlight.block;

import dev.slowlight.SlowlightWays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/** The one thing on show. */
public class DisplayPedestalBlockEntity extends BlockEntity {
    private ItemStack item = ItemStack.EMPTY;

    public DisplayPedestalBlockEntity(BlockPos pos, BlockState state) {
        super(SlowlightWays.DISPLAY_PEDESTAL_BE.get(), pos, state);
    }

    /** One slot: a hopper or pipe sets an item on the pedestal from above or the side and takes it from below. */
    public IItemHandler itemHandler(@Nullable Direction side) {
        boolean in = side != Direction.DOWN;
        boolean out = side == null || side == Direction.DOWN;
        return new IItemHandler() {
            @Override
            public int getSlots() {
                return 1;
            }

            @Override
            public ItemStack getStackInSlot(int slot) {
                return item;
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                if (!in || stack.isEmpty() || !item.isEmpty()) {
                    return stack;
                }
                if (!simulate) {
                    setItem(stack.copyWithCount(1));
                }
                return stack.copyWithCount(stack.getCount() - 1);
            }

            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                if (!out || amount <= 0 || item.isEmpty()) {
                    return ItemStack.EMPTY;
                }
                ItemStack got = item.copy();
                if (!simulate) {
                    setItem(ItemStack.EMPTY);
                }
                return got;
            }

            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return true;
            }
        };
    }

    public ItemStack item() {
        return item;
    }

    public void setItem(ItemStack item) {
        this.item = item;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!item.isEmpty()) {
            tag.put("item", item.save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        item = ItemStack.parseOptional(registries, tag.getCompound("item"));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
