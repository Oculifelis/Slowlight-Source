package dev.slowlight.locker;

import dev.slowlight.SlowlightLockers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.ChestLidController;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import org.jetbrains.annotations.Nullable;

/**
 * The lower half of a locker: its items, its lock and its door. Its size comes from the
 * {@code slowlight:locker_size} data map when it is placed and is saved with it, so a pack that changes the sizes
 * later never shrinks a locker that already holds things.
 */
public class LockerBlockEntity extends RandomizableContainerBlockEntity {
    private LockerSize size;
    private NonNullList<ItemStack> items;
    private ItemStack lock = ItemStack.EMPTY;
    private boolean lockedOnClient;
    private final ChestLidController door = new ChestLidController();
    private final IItemHandler handler = new Guarded(this);
    private final ContainerOpenersCounter openers = new ContainerOpenersCounter() {
        @Override
        protected void onOpen(Level level, BlockPos pos, BlockState state) {
            sound(state, SoundEvents.CHEST_OPEN);
        }

        @Override
        protected void onClose(Level level, BlockPos pos, BlockState state) {
            sound(state, SoundEvents.CHEST_CLOSE);
        }

        @Override
        protected void openerCountChanged(Level level, BlockPos pos, BlockState state, int before, int now) {
            level.blockEvent(pos, state.getBlock(), 1, now);
        }

        @Override
        protected boolean isOwnContainer(Player player) {
            if (player.containerMenu instanceof LockerMenu menu) {
                return menu.container() == LockerBlockEntity.this
                        || menu.container() instanceof CompoundContainer both && both.contains(LockerBlockEntity.this);
            }
            return false;
        }
    };

    public LockerBlockEntity(BlockPos pos, BlockState state) {
        super(SlowlightLockers.LOCKER_BE.get(), pos, state);
        this.size = defaultSize(state.getBlock());
        this.items = NonNullList.withSize(size.slots(), ItemStack.EMPTY);
    }

    public static LockerSize defaultSize(Block block) {
        LockerSize data = BuiltInRegistries.BLOCK.wrapAsHolder(block).getData(SlowlightLockers.LOCKER_SIZE);
        if (data != null) {
            return data;
        }
        return block instanceof LockerBlock locker && locker.lockable() ? new LockerSize(13, 3) : new LockerSize(9, 3);
    }

    public LockerSize size() {
        return size;
    }

    // --- container ---

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> list) {
        items = list;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new LockerMenu(id, inventory, this, size.columns(), size.rows());
    }

    @Override
    public void startOpen(Player player) {
        if (!remove && !player.isSpectator()) {
            openers.incrementOpeners(player, level, worldPosition, getBlockState());
        }
    }

    @Override
    public void stopOpen(Player player) {
        if (!remove && !player.isSpectator()) {
            openers.decrementOpeners(player, level, worldPosition, getBlockState());
        }
    }

    void recheckOpen() {
        if (!remove) {
            openers.recheckOpeners(level, worldPosition, getBlockState());
        }
    }

    private void sound(BlockState state, SoundEvent sound) {
        // A wide locker plays one sound, from its RIGHT piece.
        if (level == null || state.getValue(LockerBlock.TYPE) == ChestType.LEFT) {
            return;
        }
        level.playSound(null, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5,
                sound, SoundSource.BLOCKS, 0.5F, level.random.nextFloat() * 0.1F + 0.9F);
    }

    // --- the door ---

    public static void clientTick(Level level, BlockPos pos, BlockState state, LockerBlockEntity be) {
        be.door.tickLid();
    }

    @Override
    public boolean triggerEvent(int id, int type) {
        if (id == 1) {
            door.shouldBeOpen(type > 0);
            return true;
        }
        return super.triggerEvent(id, type);
    }

    /** How far the door stands open, 0 shut to 1 open. */
    public float openness(float partialTick) {
        return door.getOpenness(partialTick);
    }

    // --- the lock ---

    public boolean isLocked() {
        return level != null && level.isClientSide ? lockedOnClient : !lock.isEmpty();
    }

    /** Locked, or paired with a locked locker. */
    public boolean guarded() {
        if (isLocked()) {
            return true;
        }
        LockerBlockEntity other = partner();
        return other != null && other.isLocked();
    }

    public ItemStack lock() {
        return lock;
    }

    void setLock(ItemStack stack) {
        lock = stack;
        changed();
    }

    ItemStack takeLock() {
        ItemStack old = lock;
        lock = ItemStack.EMPTY;
        if (!old.isEmpty()) {
            changed();
        }
        return old;
    }

    private void changed() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Nullable
    public LockerBlockEntity partner() {
        return level == null ? null : LockerBlock.partner(level, getBlockState(), worldPosition);
    }

    /** What hoppers, pipes and funnels see: the items, unless the locker is locked. */
    public IItemHandler itemHandler() {
        return handler;
    }

    private static final class Guarded extends InvWrapper {
        private final LockerBlockEntity be;

        Guarded(LockerBlockEntity be) {
            super(be);
            this.be = be;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return be.guarded() ? stack : super.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return be.guarded() ? ItemStack.EMPTY : super.extractItem(slot, amount, simulate);
        }
    }

    // --- saving ---

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Columns")) {
            size = new LockerSize(Math.max(1, tag.getInt("Columns")), Math.max(1, tag.getInt("Rows")));
            if (items.size() != size.slots()) {
                items = NonNullList.withSize(size.slots(), ItemStack.EMPTY);
            }
        }
        if (tag.contains("Items") || tag.contains("LootTable")) {
            items = NonNullList.withSize(size.slots(), ItemStack.EMPTY);
            if (!tryLoadLootTable(tag)) {
                ContainerHelper.loadAllItems(tag, items, registries);
            }
        }
        lock = tag.contains("Lock") ? ItemStack.parseOptional(registries, tag.getCompound("Lock")) : ItemStack.EMPTY;
        lockedOnClient = tag.getBoolean("Locked");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Columns", size.columns());
        tag.putInt("Rows", size.rows());
        if (!trySaveLootTable(tag)) {
            ContainerHelper.saveAllItems(tag, items, true, registries);
        }
        if (!lock.isEmpty()) {
            tag.put("Lock", lock.save(registries));
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Locked", !lock.isEmpty());
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
